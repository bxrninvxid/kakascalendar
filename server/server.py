#!/usr/bin/env python3
"""
Kakascalendar Squad Server
==========================
The social backend for Kakascalendar: accounts, friends, activity feed,
reactions, nudges and a friends leaderboard.

Pure Python standard library. No pip install, no framework, no excuses.

    python3 server.py                  # listens on 0.0.0.0:8000
    PORT=9000 KAKAS_DB=/data/k.db python3 server.py

Optional private-server switches (environment variables):
    KAKAS_INVITE_CODE=...   only people who send this code can register
    KAKAS_AUTO_FRIENDS=1    everyone on the server is automatically friends
"""

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

USERNAME_RE = re.compile(r"^[a-z0-9_]{3,20}$")
EVENT_KINDS = {"log", "achievement", "level"}
REACTIONS = {"💩", "🔥", "👑", "😂", "👏"}
SHARE_LEVELS = {"full", "stats", "private"}
NUDGE_COOLDOWN_MS = 60 * 60 * 1000  # one nudge per friend per hour
MAX_BODY = 512 * 1024

# Set by make_server()
CONFIG = {"invite_code": None, "auto_friends": False}
MAX_PHOTO = 300 * 1024  # decoded JPEG bytes

SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    pw_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    display_name TEXT NOT NULL,
    avatar TEXT NOT NULL DEFAULT '💩',
    share_level TEXT NOT NULL DEFAULT 'stats',
    created_at INTEGER NOT NULL,
    current_streak INTEGER NOT NULL DEFAULT 0,
    best_streak INTEGER NOT NULL DEFAULT 0,
    total INTEGER NOT NULL DEFAULT 0,
    week_count INTEGER NOT NULL DEFAULT 0,
    xp INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    photo BLOB,
    photo_version INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS tokens (
    token TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS friendships (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    requester_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    addressee_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    UNIQUE (requester_id, addressee_id)
);
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    target_id INTEGER,
    payload TEXT NOT NULL DEFAULT '{}',
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_user ON events(user_id, id);
CREATE TABLE IF NOT EXISTS reactions (
    event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji TEXT NOT NULL,
    PRIMARY KEY (event_id, user_id, emoji)
);
"""


class Raw:
    """A non-JSON response (e.g. an image)."""

    def __init__(self, data, content_type):
        self.data = data
        self.content_type = content_type


class ApiError(Exception):
    def __init__(self, status, detail):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def now_ms():
    return int(time.time() * 1000)


def hash_pw(password, salt):
    return hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 200_000).hex()


class Store:
    """All database logic. One SQLite file, serialized writes."""

    def __init__(self, path):
        self.path = path
        self.lock = threading.Lock()
        con = self._con()
        con.executescript(SCHEMA)
        cols = {r["name"] for r in con.execute("PRAGMA table_info(users)")}
        if "photo" not in cols:  # upgrade databases created by 2.0
            con.execute("ALTER TABLE users ADD COLUMN photo BLOB")
            con.execute("ALTER TABLE users ADD COLUMN photo_version INTEGER NOT NULL DEFAULT 0")
        con.commit()
        con.close()

    def _con(self):
        con = sqlite3.connect(self.path, check_same_thread=False)
        con.row_factory = sqlite3.Row
        con.execute("PRAGMA foreign_keys = ON")
        return con

    def run(self, fn, *args):
        with self.lock:
            con = self._con()
            try:
                result = fn(con, *args)
                con.commit()
                return result
            except Exception:
                con.rollback()
                raise
            finally:
                con.close()


# ───────────────────────── helpers ─────────────────────────

def user_out(row):
    return {k: row[k] for k in (
        "id", "username", "display_name", "avatar", "share_level", "current_streak",
        "best_streak", "total", "week_count", "xp", "level", "photo_version")}


def get_user(con, uid):
    return con.execute("SELECT * FROM users WHERE id = ?", (uid,)).fetchone()


def friend_ids(con, uid):
    if CONFIG["auto_friends"]:
        return {r["id"] for r in con.execute("SELECT id FROM users WHERE id != ?", (uid,))}
    rows = con.execute(
        "SELECT requester_id, addressee_id FROM friendships "
        "WHERE status = 'accepted' AND (requester_id = ? OR addressee_id = ?)", (uid, uid)).fetchall()
    return {r["addressee_id"] if r["requester_id"] == uid else r["requester_id"] for r in rows}


def add_event(con, uid, kind, payload, target_id=None):
    clean = {str(k)[:32]: str(v)[:80] for k, v in list(payload.items())[:8]}
    cur = con.execute(
        "INSERT INTO events (user_id, kind, target_id, payload, created_at) VALUES (?, ?, ?, ?, ?)",
        (uid, kind, target_id, json.dumps(clean, ensure_ascii=False), now_ms()))
    return cur.lastrowid


def issue_token(con, uid):
    tok = secrets.token_urlsafe(32)
    con.execute("INSERT INTO tokens (token, user_id, created_at) VALUES (?, ?, ?)", (tok, uid, now_ms()))
    return tok


def visible(con, me_id, ev):
    author_id = ev["user_id"]
    if author_id == me_id:
        return True
    if author_id not in friend_ids(con, me_id):
        return False
    if ev["kind"] == "nudge":
        return ev["target_id"] == me_id
    share = get_user(con, author_id)["share_level"]
    if share == "private":
        return False
    return not (ev["kind"] == "log" and share != "full")


def s(body, key, default=None, max_len=200):
    v = body.get(key, default)
    if v is None:
        return None
    if not isinstance(v, str):
        raise ApiError(400, f"'{key}' must be text")
    return v[:max_len]


def i(body, key, lo=0, hi=100_000_000):
    v = body.get(key, 0)
    if not isinstance(v, int) or isinstance(v, bool) or not lo <= v <= hi:
        raise ApiError(400, f"'{key}' must be a number between {lo} and {hi}")
    return v


# ───────────────────────── handlers ─────────────────────────
# Each handler: (con, me_row_or_None, body, params, query) -> JSON-able

def h_health(con, me, body, p, q):
    return {"ok": True, "name": "Kakascalendar Squad Server", "humble": False,
            "invite_required": bool(CONFIG["invite_code"]), "auto_friends": CONFIG["auto_friends"]}


def h_register(con, me, body, p, q):
    invite = CONFIG["invite_code"]
    if invite and not hmac.compare_digest((s(body, "invite_code", "", 128) or "").encode(), invite.encode()):
        raise ApiError(403, "This is a private server. Ask the owner for the app.")
    username = (s(body, "username", "") or "").strip().lower()
    password = s(body, "password", "", 128) or ""
    if not USERNAME_RE.match(username):
        raise ApiError(400, "Username must be 3-20 characters: a-z, 0-9 or _")
    if len(password) < 6:
        raise ApiError(400, "Password needs at least 6 characters")
    if con.execute("SELECT 1 FROM users WHERE username = ?", (username,)).fetchone():
        raise ApiError(409, "That username is taken")
    salt = secrets.token_hex(16)
    name = ((s(body, "display_name", "", 30) or "").strip() or username)
    avatar = s(body, "avatar", "💩", 16) or "💩"
    uid = con.execute(
        "INSERT INTO users (username, pw_hash, salt, display_name, avatar, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        (username, hash_pw(password, salt), salt, name, avatar, now_ms())).lastrowid
    add_event(con, uid, "joined", {})
    return {"token": issue_token(con, uid), "user": user_out(get_user(con, uid))}


def h_login(con, me, body, p, q):
    username = (s(body, "username", "") or "").strip().lower()
    row = con.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
    if not row or not hmac.compare_digest(row["pw_hash"], hash_pw(s(body, "password", "", 128) or "", row["salt"])):
        raise ApiError(401, "Wrong username or password")
    return {"token": issue_token(con, row["id"]), "user": user_out(row)}


def h_logout(con, me, body, p, q, token=None):
    con.execute("DELETE FROM tokens WHERE token = ?", (token,))
    return {"ok": True}


def h_me(con, me, body, p, q):
    return user_out(me)


def h_update_me(con, me, body, p, q):
    name = s(body, "display_name", None, 30)
    avatar = s(body, "avatar", None, 16)
    share = s(body, "share_level", None, 10)
    if name and name.strip():
        con.execute("UPDATE users SET display_name = ? WHERE id = ?", (name.strip(), me["id"]))
    if avatar:
        con.execute("UPDATE users SET avatar = ? WHERE id = ?", (avatar, me["id"]))
    if share:
        if share not in SHARE_LEVELS:
            raise ApiError(400, "share_level must be full, stats or private")
        con.execute("UPDATE users SET share_level = ? WHERE id = ?", (share, me["id"]))
    return user_out(get_user(con, me["id"]))


def h_stats(con, me, body, p, q):
    con.execute(
        "UPDATE users SET current_streak = ?, best_streak = ?, total = ?, week_count = ?, xp = ?, level = ? WHERE id = ?",
        (i(body, "current_streak", 0, 100_000), i(body, "best_streak", 0, 100_000), i(body, "total"),
         i(body, "week_count", 0, 100_000), i(body, "xp"), i(body, "level", 1, 100_000), me["id"]))
    return {"ok": True}


def h_post_event(con, me, body, p, q):
    kind = s(body, "kind", "")
    payload = body.get("payload") or {}
    if kind not in EVENT_KINDS:
        raise ApiError(400, "Unknown event kind")
    if not isinstance(payload, dict):
        raise ApiError(400, "'payload' must be an object")
    share = me["share_level"]
    if share == "private" or (kind == "log" and share != "full"):
        return {"stored": False}
    return {"stored": True, "id": add_event(con, me["id"], kind, payload)}


def h_feed(con, me, body, p, q):
    try:
        limit = min(max(int(q.get("limit", ["50"])[0]), 1), 200)
    except ValueError:
        limit = 50
    ids = friend_ids(con, me["id"]) | {me["id"]}
    marks = ",".join("?" * len(ids))
    rows = con.execute(f"SELECT * FROM events WHERE user_id IN ({marks}) ORDER BY id DESC LIMIT ?",
                       (*ids, limit * 3)).fetchall()
    users, out = {}, []
    for r in rows:
        if not visible(con, me["id"], r):
            continue
        if r["user_id"] not in users:
            users[r["user_id"]] = user_out(get_user(con, r["user_id"]))
        counts = {x["emoji"]: x["n"] for x in con.execute(
            "SELECT emoji, COUNT(*) AS n FROM reactions WHERE event_id = ? GROUP BY emoji", (r["id"],))}
        mine = [x["emoji"] for x in con.execute(
            "SELECT emoji FROM reactions WHERE event_id = ? AND user_id = ?", (r["id"], me["id"]))]
        out.append({
            "id": r["id"], "user": users[r["user_id"]], "kind": r["kind"],
            "payload": {k: str(v) for k, v in json.loads(r["payload"] or "{}").items()},
            "created_at": r["created_at"], "reactions": counts, "my_reactions": mine,
        })
        if len(out) >= limit:
            break
    return out


def h_react(con, me, body, p, q):
    emoji = s(body, "emoji", "")
    if emoji not in REACTIONS:
        raise ApiError(400, "Unsupported reaction")
    ev = con.execute("SELECT * FROM events WHERE id = ?", (p[0],)).fetchone()
    if not ev or not visible(con, me["id"], ev):
        raise ApiError(404, "Post not found")
    key = (p[0], me["id"], emoji)
    if con.execute("SELECT 1 FROM reactions WHERE event_id = ? AND user_id = ? AND emoji = ?", key).fetchone():
        con.execute("DELETE FROM reactions WHERE event_id = ? AND user_id = ? AND emoji = ?", key)
        return {"active": False}
    con.execute("INSERT INTO reactions (event_id, user_id, emoji) VALUES (?, ?, ?)", key)
    return {"active": True}


def h_friends(con, me, body, p, q):
    mid = me["id"]
    pending = "SELECT * FROM friendships WHERE {} = ? AND status = 'pending' ORDER BY id DESC"
    return {
        "friends": [user_out(get_user(con, f)) for f in sorted(friend_ids(con, mid))],
        "incoming": [{"id": r["id"], "user": user_out(get_user(con, r["requester_id"]))}
                     for r in con.execute(pending.format("addressee_id"), (mid,))],
        "outgoing": [{"id": r["id"], "user": user_out(get_user(con, r["addressee_id"]))}
                     for r in con.execute(pending.format("requester_id"), (mid,))],
    }


def h_request_friend(con, me, body, p, q):
    if CONFIG["auto_friends"]:
        raise ApiError(400, "Everyone on this server is already in your squad")
    mid = me["id"]
    name = (s(body, "username", "") or "").strip().lower().lstrip("@")
    other = con.execute("SELECT * FROM users WHERE username = ?", (name,)).fetchone()
    if not other:
        raise ApiError(404, "No user with that username")
    if other["id"] == mid:
        raise ApiError(400, "You can't befriend yourself (even if you are that great)")
    if other["id"] in friend_ids(con, mid):
        raise ApiError(409, "You're already friends")
    reverse = con.execute(
        "SELECT * FROM friendships WHERE requester_id = ? AND addressee_id = ? AND status = 'pending'",
        (other["id"], mid)).fetchone()
    if reverse:
        con.execute("UPDATE friendships SET status = 'accepted' WHERE id = ?", (reverse["id"],))
        return {"status": "accepted"}
    if con.execute("SELECT 1 FROM friendships WHERE requester_id = ? AND addressee_id = ?",
                   (mid, other["id"])).fetchone():
        raise ApiError(409, "Request already sent")
    con.execute("INSERT INTO friendships (requester_id, addressee_id, created_at) VALUES (?, ?, ?)",
                (mid, other["id"], now_ms()))
    return {"status": "pending"}


def h_accept(con, me, body, p, q):
    r = con.execute("SELECT * FROM friendships WHERE id = ? AND addressee_id = ? AND status = 'pending'",
                    (p[0], me["id"])).fetchone()
    if not r:
        raise ApiError(404, "Request not found")
    con.execute("UPDATE friendships SET status = 'accepted' WHERE id = ?", (p[0],))
    return {"ok": True}


def h_decline(con, me, body, p, q):
    con.execute("DELETE FROM friendships WHERE id = ? AND status = 'pending' AND (addressee_id = ? OR requester_id = ?)",
                (p[0], me["id"], me["id"]))
    return {"ok": True}


def h_unfriend(con, me, body, p, q):
    if CONFIG["auto_friends"]:
        raise ApiError(400, "Everyone on this server is in your squad")
    con.execute("DELETE FROM friendships WHERE (requester_id = ? AND addressee_id = ?) "
                "OR (requester_id = ? AND addressee_id = ?)", (me["id"], p[0], p[0], me["id"]))
    return {"ok": True}


def h_nudge(con, me, body, p, q):
    fid = p[0]
    if fid not in friend_ids(con, me["id"]):
        raise ApiError(404, "You can only nudge friends")
    last = con.execute("SELECT created_at FROM events WHERE kind = 'nudge' AND user_id = ? AND target_id = ? "
                       "ORDER BY id DESC LIMIT 1", (me["id"], fid)).fetchone()
    if last and now_ms() - last["created_at"] < NUDGE_COOLDOWN_MS:
        raise ApiError(429, "Easy there. One nudge per hour per friend")
    add_event(con, me["id"], "nudge", {"target_name": get_user(con, fid)["display_name"]}, target_id=fid)
    return {"ok": True}


def h_set_photo(con, me, body, p, q):
    data = body.get("data")
    if not isinstance(data, str) or not data:
        raise ApiError(400, "'data' must be a base64 JPEG")
    try:
        raw = base64.b64decode(data, validate=True)
    except (binascii.Error, ValueError):
        raise ApiError(400, "'data' is not valid base64")
    if len(raw) > MAX_PHOTO:
        raise ApiError(413, "Photo too large (max 300 KB)")
    if not raw.startswith(b"\xff\xd8"):
        raise ApiError(400, "Photo must be a JPEG")
    con.execute("UPDATE users SET photo = ?, photo_version = photo_version + 1 WHERE id = ?", (raw, me["id"]))
    return user_out(get_user(con, me["id"]))


def h_delete_photo(con, me, body, p, q):
    con.execute("UPDATE users SET photo = NULL, photo_version = 0 WHERE id = ?", (me["id"],))
    return user_out(get_user(con, me["id"]))


def h_get_photo(con, me, body, p, q):
    uid = p[0]
    if uid != me["id"] and uid not in friend_ids(con, me["id"]):
        raise ApiError(404, "Photo not found")
    row = con.execute("SELECT photo FROM users WHERE id = ?", (uid,)).fetchone()
    if not row or row["photo"] is None:
        raise ApiError(404, "Photo not found")
    return Raw(bytes(row["photo"]), "image/jpeg")


def h_leaderboard(con, me, body, p, q):
    out = []
    for uid in friend_ids(con, me["id"]) | {me["id"]}:
        u = get_user(con, uid)
        if uid == me["id"] or u["share_level"] != "private":
            out.append(user_out(u))
    out.sort(key=lambda u: (-u["current_streak"], -u["xp"]))
    return out


# (method, pattern, handler, needs_auth)
ROUTES = [
    ("GET", r"/health", h_health, False),
    ("POST", r"/auth/register", h_register, False),
    ("POST", r"/auth/login", h_login, False),
    ("POST", r"/auth/logout", h_logout, True),
    ("GET", r"/me", h_me, True),
    ("PATCH", r"/me", h_update_me, True),
    ("POST", r"/me/stats", h_stats, True),
    ("POST", r"/events", h_post_event, True),
    ("GET", r"/feed", h_feed, True),
    ("POST", r"/events/(\d+)/react", h_react, True),
    ("GET", r"/friends", h_friends, True),
    ("POST", r"/friends/request", h_request_friend, True),
    ("POST", r"/friends/requests/(\d+)/accept", h_accept, True),
    ("POST", r"/friends/requests/(\d+)/decline", h_decline, True),
    ("DELETE", r"/friends/(\d+)", h_unfriend, True),
    ("POST", r"/nudge/(\d+)", h_nudge, True),
    ("GET", r"/leaderboard", h_leaderboard, True),
    ("POST", r"/me/photo", h_set_photo, True),
    ("DELETE", r"/me/photo", h_delete_photo, True),
    ("GET", r"/users/(\d+)/photo", h_get_photo, True),
]
ROUTES = [(m, re.compile(f"^{pat}/?$"), h, a) for m, pat, h, a in ROUTES]


def make_handler(store):
    class Handler(BaseHTTPRequestHandler):
        server_version = "KakasSquad/2.1"

        def log_message(self, fmt, *args):
            if os.environ.get("KAKAS_QUIET") != "1":
                sys.stderr.write("%s %s\n" % (self.address_string(), fmt % args))

        def _send(self, status, data):
            if isinstance(data, Raw):
                self.send_response(status)
                self.send_header("Content-Type", data.content_type)
                self.send_header("Content-Length", str(len(data.data)))
                self.send_header("Cache-Control", "private, max-age=31536000")
                self.end_headers()
                self.wfile.write(data.data)
                return
            raw = json.dumps(data, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)

        def _dispatch(self, method):
            url = urlparse(self.path)
            try:
                for m, pat, handler, needs_auth in ROUTES:
                    match = pat.match(url.path)
                    if not match or m != method:
                        continue
                    length = int(self.headers.get("Content-Length") or 0)
                    if length > MAX_BODY:
                        raise ApiError(413, "Request too large")
                    body = {}
                    if length:
                        try:
                            body = json.loads(self.rfile.read(length) or b"{}")
                        except ValueError:
                            raise ApiError(400, "Body must be JSON")
                        if not isinstance(body, dict):
                            raise ApiError(400, "Body must be a JSON object")
                    params = [int(g) for g in match.groups()]
                    query = parse_qs(url.query)
                    auth = self.headers.get("Authorization") or ""
                    token = auth[7:] if auth.startswith("Bearer ") else None

                    def work(con):
                        me = None
                        if needs_auth:
                            if not token:
                                raise ApiError(401, "Not signed in")
                            me = con.execute("SELECT u.* FROM tokens t JOIN users u ON u.id = t.user_id "
                                             "WHERE t.token = ?", (token,)).fetchone()
                            if not me:
                                raise ApiError(401, "Session expired, please sign in again")
                        if handler is h_logout:
                            return h_logout(con, me, body, params, query, token=token)
                        return handler(con, me, body, params, query)

                    return self._send(200, store.run(work))
                raise ApiError(404, "Not found")
            except ApiError as e:
                self._send(e.status, {"detail": e.detail})
            except Exception as e:  # pragma: no cover
                sys.stderr.write(f"error: {e!r}\n")
                self._send(500, {"detail": "Internal server error"})

        def do_GET(self):
            self._dispatch("GET")

        def do_POST(self):
            self._dispatch("POST")

        def do_PATCH(self):
            self._dispatch("PATCH")

        def do_DELETE(self):
            self._dispatch("DELETE")

    return Handler


def make_server(host="0.0.0.0", port=8000, db_path="kakas.db", invite_code=None, auto_friends=False):
    CONFIG["invite_code"] = invite_code or None
    CONFIG["auto_friends"] = bool(auto_friends)
    return ThreadingHTTPServer((host, port), make_handler(Store(db_path)))


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    db = os.environ.get("KAKAS_DB", "kakas.db")
    invite = os.environ.get("KAKAS_INVITE_CODE", "").strip()
    auto = os.environ.get("KAKAS_AUTO_FRIENDS", "") in ("1", "true", "yes")
    host = os.environ.get("HOST", "0.0.0.0")
    srv = make_server(host=host, port=port, db_path=db, invite_code=invite, auto_friends=auto)
    print(f"💩 Kakascalendar Squad Server on http://{host}:{port}  (db: {db}, "
          f"invite: {'on' if invite else 'off'}, auto-friends: {'on' if auto else 'off'})", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
