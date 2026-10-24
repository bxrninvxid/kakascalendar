"""Run: python3 -m unittest test_server.py"""
import json
import os
import tempfile
import threading
import unittest
import urllib.error
import urllib.request

os.environ["KAKAS_QUIET"] = "1"
import server  # noqa: E402


class SquadServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = server.make_server("127.0.0.1", 0, os.path.join(tempfile.mkdtemp(), "t.db"))
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    def call(self, method, path, body=None, tok=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if tok:
            req.add_header("Authorization", "Bearer " + tok)
        try:
            with urllib.request.urlopen(req) as r:
                return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def reg(self, name, share=None):
        st, js = self.call("POST", "/auth/register", {"username": name, "password": "secret123",
                                                       "display_name": name.title(), "avatar": "🦊"})
        self.assertEqual(st, 200, js)
        if share:
            self.assertEqual(self.call("PATCH", "/me", {"share_level": share}, js["token"])[0], 200)
        return js["token"], js["user"]

    def befriend(self, ta, tb, b_name):
        self.assertEqual(self.call("POST", "/friends/request", {"username": b_name}, ta)[1]["status"], "pending")
        rid = self.call("GET", "/friends", tok=tb)[1]["incoming"][0]["id"]
        self.assertEqual(self.call("POST", f"/friends/requests/{rid}/accept", tok=tb)[0], 200)

    def test_auth(self):
        tok, u = self.reg("alice")
        self.assertEqual(u["avatar"], "🦊")
        self.assertEqual(self.call("GET", "/me", tok=tok)[1]["display_name"], "Alice")
        self.assertEqual(self.call("POST", "/auth/register", {"username": "alice", "password": "secret123"})[0], 409)
        self.assertEqual(self.call("POST", "/auth/register", {"username": "A!", "password": "secret123"})[0], 400)
        self.assertEqual(self.call("POST", "/auth/register", {"username": "shorty", "password": "123"})[0], 400)
        self.assertEqual(self.call("POST", "/auth/login", {"username": "alice", "password": "nope"})[0], 401)
        st, js = self.call("POST", "/auth/login", {"username": "ALICE", "password": "secret123"})
        self.assertEqual(st, 200)
        self.assertEqual(self.call("GET", "/me")[0], 401)
        self.assertEqual(self.call("GET", "/me", tok="bad")[0], 401)
        self.assertEqual(self.call("POST", "/auth/logout", tok=js["token"])[0], 200)
        self.assertEqual(self.call("GET", "/me", tok=js["token"])[0], 401)
        self.assertEqual(self.call("GET", "/nope")[0], 404)
        self.assertEqual(self.call("GET", "/health")[1]["humble"], False)

    def test_social(self):
        ta, a = self.reg("bob", share="full")
        tb, b = self.reg("carol")  # default share level: stats
        tp, p = self.reg("dave", share="private")

        self.befriend(ta, tb, "carol")
        self.assertEqual(self.call("POST", "/friends/request", {"username": "bob"}, tp)[1]["status"], "pending")
        self.assertEqual(self.call("POST", "/friends/request", {"username": "dave"}, ta)[1]["status"], "accepted")
        self.assertEqual(self.call("POST", "/friends/request", {"username": "bob"}, ta)[0], 400)
        self.assertEqual(self.call("POST", "/friends/request", {"username": "carol"}, ta)[0], 409)
        self.assertEqual(self.call("POST", "/friends/request", {"username": "ghost"}, ta)[0], 404)

        # share levels decide what gets stored
        self.assertTrue(self.call("POST", "/events", {"kind": "log", "payload": {"type": "4"}}, ta)[1]["stored"])
        self.assertFalse(self.call("POST", "/events", {"kind": "log", "payload": {"type": "2"}}, tb)[1]["stored"])
        self.assertTrue(self.call("POST", "/events", {"kind": "achievement",
                                                      "payload": {"title": "First Drop", "emoji": "🎉"}}, tb)[1]["stored"])
        self.assertFalse(self.call("POST", "/events", {"kind": "level", "payload": {"level": "2"}}, tp)[1]["stored"])
        self.assertEqual(self.call("POST", "/events", {"kind": "hack"}, ta)[0], 400)

        feed_b = self.call("GET", "/feed", tok=tb)[1]
        kinds = {(x["user"]["username"], x["kind"]) for x in feed_b}
        self.assertIn(("bob", "log"), kinds)
        self.assertIn(("carol", "achievement"), kinds)
        self.assertNotIn("dave", {x["user"]["username"] for x in feed_b})

        # reactions toggle
        log_id = next(x["id"] for x in feed_b if x["kind"] == "log")
        self.assertTrue(self.call("POST", f"/events/{log_id}/react", {"emoji": "🔥"}, tb)[1]["active"])
        item = next(x for x in self.call("GET", "/feed", tok=ta)[1] if x["id"] == log_id)
        self.assertEqual(item["reactions"], {"🔥": 1})
        self.assertEqual(item["my_reactions"], [])
        self.assertFalse(self.call("POST", f"/events/{log_id}/react", {"emoji": "🔥"}, tb)[1]["active"])
        self.assertEqual(self.call("POST", f"/events/{log_id}/react", {"emoji": "🍕"}, tb)[0], 400)
        ach_id = next(x["id"] for x in feed_b if x["kind"] == "achievement")
        self.assertEqual(self.call("POST", f"/events/{ach_id}/react", {"emoji": "👑"}, tp)[0], 404)

        # private users stay off the leaderboard
        self.assertEqual({u["username"] for u in self.call("GET", "/leaderboard", tok=ta)[1]}, {"bob", "carol"})
        self.assertEqual(self.call("POST", "/me/stats", {"current_streak": 3, "best_streak": 5, "total": 9,
                                                         "week_count": 4, "xp": 300, "level": 3}, tb)[0], 200)
        self.assertEqual(self.call("POST", "/me/stats", {"current_streak": -1}, tb)[0], 400)
        self.assertEqual(self.call("GET", "/leaderboard", tok=ta)[1][0]["username"], "carol")

        # nudges: only the target sees them, one per hour
        self.assertEqual(self.call("POST", f"/nudge/{b['id']}", tok=ta)[0], 200)
        self.assertEqual(self.call("POST", f"/nudge/{b['id']}", tok=ta)[0], 429)
        self.assertTrue(any(x["kind"] == "nudge" for x in self.call("GET", "/feed", tok=tb)[1]))
        self.assertEqual(self.call("POST", f"/nudge/{b['id']}", tok=tp)[0], 404)

        # unfriend
        self.assertEqual(self.call("DELETE", f"/friends/{b['id']}", tok=ta)[0], 200)
        self.assertNotIn(b["id"], [u["id"] for u in self.call("GET", "/friends", tok=ta)[1]["friends"]])


    def test_photos(self):
        import base64
        ta, a = self.reg("photo_a")
        tb, b = self.reg("photo_b")
        tc, c = self.reg("photo_c")
        self.befriend(ta, tb, "photo_b")
        jpeg = b"\xff\xd8\xff\xe0" + b"x" * 200
        st, me = self.call("POST", "/me/photo", {"data": base64.b64encode(jpeg).decode()}, ta)
        self.assertEqual(st, 200)
        self.assertEqual(me["photo_version"], 1)
        self.assertEqual(self.call("POST", "/me/photo", {"data": base64.b64encode(b"GIF89a").decode()}, ta)[0], 400)
        self.assertEqual(self.call("POST", "/me/photo", {"data": "%%%"}, ta)[0], 400)

        def get(path, tok):
            req = urllib.request.Request(self.base + path)
            req.add_header("Authorization", "Bearer " + tok)
            try:
                with urllib.request.urlopen(req) as r:
                    return r.status, r.headers["Content-Type"], r.read()
            except urllib.error.HTTPError as e:
                return e.code, None, None

        self.assertEqual(get(f"/users/{a['id']}/photo", tb), (200, "image/jpeg", jpeg))   # friend
        self.assertEqual(get(f"/users/{a['id']}/photo", ta)[0], 200)                      # self
        self.assertEqual(get(f"/users/{a['id']}/photo", tc)[0], 404)                      # stranger
        friends = self.call("GET", "/friends", tok=tb)[1]["friends"]
        self.assertEqual(friends[0]["photo_version"], 1)
        self.assertEqual(self.call("DELETE", "/me/photo", tok=ta)[1]["photo_version"], 0)
        self.assertEqual(get(f"/users/{a['id']}/photo", tb)[0], 404)


class StoriesAndLimitsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = server.make_server("127.0.0.1", 0, os.path.join(tempfile.mkdtemp(), "s.db"))
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()

    call = SquadServerTest.call
    reg = SquadServerTest.reg
    befriend = SquadServerTest.befriend

    def test_daily_log_cap(self):
        tok, _ = self.reg("capper", share="full")
        for _ in range(server.MAX_LOGS_PER_DAY):
            self.assertEqual(self.call("POST", "/events", {"kind": "log", "payload": {"type": "4"}}, tok)[0], 200)
        st, js = self.call("POST", "/events", {"kind": "log", "payload": {"type": "4"}}, tok)
        self.assertEqual(st, 429)
        self.assertIn("Daily limit", js["detail"])
        # achievements aren't capped
        self.assertEqual(self.call("POST", "/events", {"kind": "achievement", "payload": {"title": "x"}}, tok)[0], 200)

    def test_stories(self):
        import base64
        ta, a = self.reg("story_a")
        tb, b = self.reg("story_b")
        tc, c = self.reg("story_c")
        self.befriend(ta, tb, "story_b")
        jpeg = b"\xff\xd8\xff\xe0" + b"y" * 500

        self.assertEqual(self.call("POST", "/stories", {"kind": "text"}, ta)[0], 400)
        self.assertEqual(self.call("POST", "/stories", {"kind": "video", "text": "hi"}, ta)[0], 400)
        self.assertEqual(self.call("POST", "/stories", {"kind": "photo", "image": base64.b64encode(b"GIF8").decode()}, ta)[0], 400)
        st, t1 = self.call("POST", "/stories", {"kind": "text", "text": "Type 4 today 🍌", "bg": 3, "emoji": "🔥"}, ta)
        self.assertEqual(st, 200)
        st, p1 = self.call("POST", "/stories", {"kind": "photo", "image": base64.b64encode(jpeg).decode(), "text": "view"}, ta)
        self.assertEqual(st, 200)

        # friend sees both, unseen; stranger sees nothing
        groups = self.call("GET", "/stories", tok=tb)[1]
        self.assertEqual(len(groups), 1)
        g = groups[0]
        self.assertEqual(g["user"]["username"], "story_a")
        self.assertEqual([x["kind"] for x in g["stories"]], ["text", "photo"])
        self.assertFalse(g["all_seen"])
        self.assertNotIn("views", g["stories"][0])
        self.assertEqual(self.call("GET", "/stories", tok=tc)[1], [])
        self.assertEqual(self.call("POST", f"/stories/{t1['id']}/view", tok=tc)[0], 404)

        # image download (friend ok, stranger 404)
        req = urllib.request.Request(f"{self.base}/stories/{p1['id']}/image", headers={"Authorization": "Bearer " + tb})
        with urllib.request.urlopen(req) as r:
            self.assertEqual(r.read(), jpeg)
        self.assertEqual(self.call("GET", f"/stories/{t1['id']}/image", tok=tb)[0], 404)

        # views
        for sid in (t1["id"], p1["id"]):
            self.assertEqual(self.call("POST", f"/stories/{sid}/view", tok=tb)[0], 200)
        self.assertTrue(self.call("GET", "/stories", tok=tb)[1][0]["all_seen"])
        mine = self.call("GET", "/stories", tok=ta)[1][0]
        self.assertEqual(mine["stories"][0]["views"], 1)
        viewers = self.call("GET", f"/stories/{t1['id']}/viewers", tok=ta)[1]
        self.assertEqual([v["username"] for v in viewers], ["story_b"])
        self.assertEqual(self.call("GET", f"/stories/{t1['id']}/viewers", tok=tb)[0], 403)

        # privacy "Nothing" blocks posting and hides existing stories
        self.call("PATCH", "/me", {"share_level": "private"}, ta)
        self.assertEqual(self.call("POST", "/stories", {"kind": "text", "text": "x"}, ta)[0], 403)
        self.assertEqual(self.call("GET", "/stories", tok=tb)[1], [])
        self.call("PATCH", "/me", {"share_level": "stats"}, ta)

        # delete + expiry
        self.assertEqual(self.call("DELETE", f"/stories/{t1['id']}", tok=ta)[0], 200)
        self.assertEqual(len(self.call("GET", "/stories", tok=tb)[1][0]["stories"]), 1)
        old = server.STORY_TTL_MS
        server.STORY_TTL_MS = -1000          # everything is "expired"
        try:
            self.assertEqual(self.call("GET", "/stories", tok=tb)[1], [])
        finally:
            server.STORY_TTL_MS = old

        # daily story cap
        for _ in range(server.MAX_STORIES_PER_DAY):
            self.assertEqual(self.call("POST", "/stories", {"kind": "text", "emoji": "💩"}, tb)[0], 200)
        self.assertEqual(self.call("POST", "/stories", {"kind": "text", "emoji": "💩"}, tb)[0], 429)


class PrivateServerTest(unittest.TestCase):
    """Invite code + auto-friends mode."""

    @classmethod
    def setUpClass(cls):
        cls.srv = server.make_server("127.0.0.1", 0, os.path.join(tempfile.mkdtemp(), "p.db"),
                                     invite_code="s3cret", auto_friends=True)
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        server.CONFIG.update(invite_code=None, auto_friends=False, directory=False)

    call = SquadServerTest.call

    def test_invite_and_auto_friends(self):
        self.assertTrue(self.call("GET", "/health")[1]["invite_required"])
        self.assertEqual(self.call("POST", "/auth/register", {"username": "nope", "password": "secret123"})[0], 403)
        self.assertEqual(self.call("POST", "/auth/register",
                                   {"username": "nope", "password": "secret123", "invite_code": "wrong"})[0], 403)
        toks = []
        for n in ("ann", "ben", "cat"):
            st, js = self.call("POST", "/auth/register", {"username": n, "password": "secret123", "invite_code": "s3cret"})
            self.assertEqual(st, 200, js)
            toks.append(js["token"])
        # login doesn't need the code
        self.assertEqual(self.call("POST", "/auth/login", {"username": "ann", "password": "secret123"})[0], 200)
        friends = self.call("GET", "/friends", tok=toks[0])[1]["friends"]
        self.assertEqual({f["username"] for f in friends}, {"ben", "cat"})
        self.assertEqual(len(self.call("GET", "/leaderboard", tok=toks[0])[1]), 3)
        self.assertEqual(self.call("POST", "/friends/request", {"username": "ben"}, toks[0])[0], 400)
        # everyone sees each other's achievements
        self.call("POST", "/events", {"kind": "achievement", "payload": {"title": "First Drop"}}, toks[1])
        feed = self.call("GET", "/feed", tok=toks[2])[1]
        self.assertTrue(any(x["user"]["username"] == "ben" and x["kind"] == "achievement" for x in feed))


class DirectoryServerTest(unittest.TestCase):
    """People directory with normal friend requests."""

    @classmethod
    def setUpClass(cls):
        cls.srv = server.make_server("127.0.0.1", 0, os.path.join(tempfile.mkdtemp(), "d.db"),
                                     invite_code="k", directory=True)
        cls.base = f"http://127.0.0.1:{cls.srv.server_address[1]}"
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        server.CONFIG.update(invite_code=None, auto_friends=False, directory=False)

    call = SquadServerTest.call

    def test_directory_flow(self):
        toks, users = [], []
        for n in ("dora", "eli", "finn"):
            st, js = self.call("POST", "/auth/register", {"username": n, "password": "secret123", "invite_code": "k"})
            self.assertEqual(st, 200, js)
            toks.append(js["token"]); users.append(js["user"])
        d, e, f = toks
        self.assertTrue(self.call("GET", "/health")[1]["directory"])

        listing = self.call("GET", "/users", tok=d)[1]
        self.assertEqual([x["user"]["username"] for x in listing], ["eli", "finn"])
        self.assertTrue(all(x["status"] == "none" for x in listing))
        self.assertNotIn("current_streak", listing[0]["user"])   # no stats for strangers

        # not friends yet: no feed visibility
        self.call("POST", "/me/stats", {"current_streak": 9, "best_streak": 9, "total": 9, "week_count": 9,
                                        "xp": 9, "level": 2}, e)
        self.call("PATCH", "/me", {"share_level": "full"}, e)
        self.call("POST", "/events", {"kind": "log", "payload": {"type": "4"}}, e)
        self.assertFalse(any(x["user"]["username"] == "eli" for x in self.call("GET", "/feed", tok=d)[1]))

        # request by id from the list
        self.assertEqual(self.call("POST", "/friends/request", {"user_id": users[1]["id"]}, d)[1]["status"], "pending")
        self.assertEqual(next(x for x in self.call("GET", "/users", tok=d)[1] if x["user"]["username"] == "eli")["status"], "outgoing")
        inc = next(x for x in self.call("GET", "/users", tok=e)[1] if x["user"]["username"] == "dora")
        self.assertEqual(inc["status"], "incoming")
        self.assertEqual(self.call("POST", f"/friends/requests/{inc['request_id']}/accept", tok=e)[0], 200)

        # now friends: full profile + feed
        eli = next(x for x in self.call("GET", "/users", tok=d)[1] if x["user"]["username"] == "eli")
        self.assertEqual(eli["status"], "friend")
        self.assertEqual(eli["user"]["current_streak"], 9)
        self.assertTrue(any(x["user"]["username"] == "eli" and x["kind"] == "log" for x in self.call("GET", "/feed", tok=d)[1]))
        # finn still sees nothing of eli
        self.assertFalse(any(x["user"]["username"] == "eli" for x in self.call("GET", "/feed", tok=f)[1]))
        self.assertEqual(self.call("POST", "/friends/request", {"user_id": 99999}, d)[0], 404)

    def test_directory_off_elsewhere(self):
        server.CONFIG["directory"] = False
        try:
            tok = self.call("POST", "/auth/register", {"username": "gus", "password": "secret123", "invite_code": "k"})[1]["token"]
            self.assertEqual(self.call("GET", "/users", tok=tok)[0], 404)
        finally:
            server.CONFIG["directory"] = True


if __name__ == "__main__":
    unittest.main()
