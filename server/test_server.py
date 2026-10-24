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


if __name__ == "__main__":
    unittest.main()
