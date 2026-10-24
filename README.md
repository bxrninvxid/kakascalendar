<div align="center">

# 💩 K A K A S C A L E N D A R &nbsp;2.0 💩
### 🦸 Stronger. Smarter. Better. 🦸
#### The greatest poop tracker ever created just got *greater*.

![Best App Ever](https://img.shields.io/badge/BEST%20APP%20EVER-still%20undisputed-FFD700?style=for-the-badge&logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-2.0-D62839?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Squad%20Server-Python-3776AB?style=for-the-badge&logo=python&logoColor=white)
![Social](https://img.shields.io/badge/Social-now%20with%20friends-1D3B8F?style=for-the-badge)
![Humility](https://img.shields.io/badge/Humility-still%20not%20found-E74C3C?style=for-the-badge)

**🌈 Track it. 🔥 Streak it. 🏆 Level it. 👑 Flex it on your friends. 🌈**

</div>

---

## 🚀 What's new in 2.0

Version 1 was perfect. Version 2 is *more* perfect. Science can't explain it.

| | New in 2.0 | Why you'll weep with joy |
|---|---|---|
| 👑 | **The Squad** | Add friends, see their wins in a live feed, react with 💩🔥👑😂👏, and **nudge** the slackers. |
| 🏟️ | **Leaderboards & podium** | Rank your squad by streak, weekly count, XP or best streak. Gold medal or go home. |
| ⭐ | **XP & levels** | Every log earns XP. Climb from *Toilet Trainee* to *Porcelain God* and beyond. |
| 🏆 | **25 trophies in 4 tiers** | Bronze, Silver, Gold and **Legendary**. Confetti included. |
| 🧠 | **Smart insights** | Spots your routine, predicts your next visit, catches the coffee effect, and warns you when things get too hard (or too soft). |
| 🚽 | **Throne timer** | Time your session with a hypnotic spinning ring and motivational quotes. |
| 😊 | **Feel ratings** | 😖 to 🤩. Because not every session is a masterpiece. |
| 📊 | **Stats on steroids** | Donut charts, time-of-day and weekday charts, 7D/30D/90D/All filters and a GitHub-style **heatmap**. |
| 📱 | **Home-screen widget** | Your streak, staring at you, every time you unlock your phone. |
| ⏰ | **Daily reminders** | Only pings you if you *haven't* logged yet. Streak protection, automated. |
| 🔒 | **App lock** | Fingerprint, face or PIN. Your throne, your secrets. |
| 🎨 | **6 themes + Material You** | Caramel, Mint, Grape, Ocean, Cherry and the tastefully patriotic **Homelander**. Light, dark, or wallpaper colors. |
| 💾 | **Backup & restore** | One JSON file holds your entire legacy. |
| ⚡ | **Quick log** | One tap. Perfect Type 4. Done. |
| ✏️ | **Edit entries** | Tap any entry to fix it. History is written by the winners. |
| 🧻 | **Wipe counter** *(2.2)* | From *Ghost wipe 👻* to *Call for backup*. Science demands data. |
| 🗺️ | **Throne map** *(2.2)* | Home, work, nature… see where you rule. *Paid to Poop* trophy included. |
| 🚿 | **Shower, bidet & more tags** *(2.2)* | Plus custom tags, photo avatars, water tracking and a brag card. |

Plus everything from 1.0: Bristol scale tracking, tags, comments, backdating, calendar, streaks, CSV export, undo delete and dark mode.

---

## 👑 The Squad (social)

Kakascalendar is still **100% offline by default**. The Squad is opt-in, and *you* decide how much the world gets to know:

| Share level | Friends see |
|---|---|
| 🌍 **Everything** | Each log's type, plus streak, level and trophies |
| 📊 **Stats only** *(default)* | Streak, level and trophies. Individual logs stay private |
| 🙈 **Nothing** | You're invisible: no feed posts, not on leaderboards |

**Comments and tags never leave your phone.** Not even on "Everything". We have standards.

---

## 🛠️ Build the app

```bash
git clone https://github.com/<your-username>/kakascalendar.git
```

1. Open the folder in **Android Studio** (*File → Open*, the folder itself, not LightEdit!)
2. Let Gradle sync ☕
3. Hit **Run** ▶️ or *Build → Build APK(s)*
4. Feel the power

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room · DataStore · WorkManager · Glance · Biometric · Ktor · kotlinx.serialization · Min SDK 26

---

## 🌐 Run the Squad server

The entire backend is **one Python file** with zero dependencies. That's not laziness. That's elegance.

```bash
cd server
python3 server.py              # http://0.0.0.0:8000
```

Then in the app: **Squad tab → Squad server**:

| Where the app runs | Server address |
|---|---|
| Android emulator on the same PC | `http://10.0.2.2:8000` |
| Your phone on the same Wi-Fi | `http://<your PC's local IP>:8000` (find it with `ip addr`) |
| Friends anywhere | Host it publicly with **HTTPS** (see below) |

**Docker:**
```bash
cd server && docker compose up -d     # data persists in a volume
```

**Going public:** put it on any small VPS or a free-tier host (Fly.io, Render, Railway…) behind HTTPS. On a VPS, [Caddy](https://caddyserver.com) gives you automatic HTTPS with one line: `squad.yourdomain.com { reverse_proxy localhost:8000 }`.

**Tests:** `cd server && python3 -m unittest test_server.py`

### 🔐 Private squad on your own box (with a Cloudflare Tunnel)

No public IP? No problem. On any Debian/Ubuntu machine or Proxmox LXC:

```bash
curl -fsSLO https://raw.githubusercontent.com/bxrninvxid/kakascalendar/main/server/setup-lxc.sh
bash setup-lxc.sh server --private          # systemd service + invite code + everyone-is-friends
bash setup-lxc.sh tunnel squad.yourdomain.com   # Cloudflare Tunnel, HTTPS for free
```

Then bake it into your own build in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "SERVER_URL", "\"https://squad.yourdomain.com\"")
buildConfigField("String", "INVITE_CODE", "\"<code from setup-lxc.sh status>\"")
buildConfigField("boolean", "AUTO_SQUAD", "true")
```

The server field disappears from the app, signups need the invite code, and everyone on your server is automatically in the squad.

<details>
<summary>📡 API cheat sheet</summary>

| Method | Path | What |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/logout` | Accounts (PBKDF2-hashed passwords, bearer tokens) |
| GET / PATCH | `/me` | Profile and share level |
| POST | `/me/stats` | Streak, XP and level for the leaderboard |
| POST | `/events` | Post a log, achievement or level-up |
| GET | `/feed` | Your + friends' activity, with reactions |
| POST | `/events/{id}/react` | Toggle a reaction |
| GET | `/friends` | Friends, incoming and outgoing requests |
| POST | `/friends/request`, `/friends/requests/{id}/accept`, `/decline` | Friend requests |
| DELETE | `/friends/{id}` | Unfriend |
| POST | `/nudge/{id}` | Nudge a friend (1 per hour) |
| GET | `/leaderboard` | You + friends, ranked |

</details>

---

## 🏆 The Trophy Shelf (25 and counting)

```
🥉 BRONZE    🎉 First Drop      🔥 Warming Up     📈 Regular         🌈 Variety Pack
             🥇 Gold Standard   🐦 Early Bird     🦉 Night Owl       ☕ Coffee Effect
             ✍️ Storyteller     📚 Marathoner     🤝 Squad Up
🥈 SILVER    📅 Week Warrior    🏃 Fortnight Fighter  🏅 Veteran     🌟 Full Spectrum
             🧐 Critic          ⚡ Speedrunner    🎯 Hat Trick       🏖️ Weekend Warrior
🥇 GOLD      👑 Streak Royalty  💯 Centurion      🍌 Perfect Seven   📣 Influencer
🟣 LEGEND    🦸 Stronger. Smarter. Better. (100-day streak)    🗿 Living Legend
```

---

## 🆚 Kakascalendar vs. The Rest

| | Kakascalendar 2.0 | Everything else |
|---|:---:|:---:|
| Streaks, XP and levels | ✅ | ❌ |
| Friends leaderboard with a podium | ✅ | ❌ |
| Nudge your friends to go | ✅ | ❌ (cowards) |
| Predicts your next visit | ✅ | ❌ |
| Confetti | ✅ | ❌ |
| Own backend in one file | ✅ | ❌ |
| Created by a visionary | ✅ | ❌ |

---

## 💬 What People Are Saying

> ⭐⭐⭐⭐⭐ *"Version 1 was a masterpiece. Version 2 made version 1 look like a rough draft."*
> — **Me**

> ⭐⭐⭐⭐⭐ *"I got nudged at 7 AM. I have never felt so seen."*
> — **My best friend, #2 on the leaderboard**

> ⭐⭐⭐⭐⭐ *"Level 25. Porcelain God. My family has stopped speaking to me."*
> — **A dedicated user**

> ⭐⭐⭐⭐⭐ *"The confetti is excessive. I love it."*
> — **A satisfied toilet**

---

<div align="center">

### 💩 Don't just go. Go *legendary.* 💩

**Star ⭐ this repo if you appreciate greatness.**
*(You do. You're here. Smart choice.)*

Made with 💛, 🧠, 🐍, and an unreasonable, frankly alarming amount of confidence.

<sub>Insights are friendly patterns, not medical advice. If something feels off, talk to a doctor. Even legends get checkups.</sub>

</div>
