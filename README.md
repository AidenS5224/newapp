# Gamer Connect

First working build path for the Gamer Connect product:

- `backend/`: Raspberry Pi-friendly API server using Python standard library + SQLite.
- `android/`: native Android test client scaffold that can call the backend over Wi-Fi.
- `supabase/`: hosted Postgres/Auth schema for the production path.
- `public/` + `api/` + `vercel.json`: Vercel web/PC client wired for Supabase.

## Current Backend Features

- Health check
- Seeded games, players, LFG posts, and squads
- Player discovery filters by game/platform
- Compatibility score for discovery cards
- Connection request creation
- Sign up, login, logout, and bearer-token sessions
- Protected linked account/contact info that only unlocks for the player, the owner panel, or accepted connections
- Private owner dashboard with analytics and backend controls
- Feed posts, clips, reactions, and comments
- Conversations and messages
- Local control terminal for inspecting and editing test data
- CORS enabled for later web/PWA clients

## Run Backend On Windows

```powershell
python gamer.py dev
```

Then open:

```text
http://localhost:8080/api/health
```

The startup banner also prints your private owner panel URL:

```text
http://127.0.0.1:8080/owner?token=YOUR_LOCAL_ADMIN_TOKEN
```

You can print it again with:

```powershell
python gamer.py admin
```

## Run Backend On Raspberry Pi OS

```bash
cd gamer-connect
python3 gamer.py dev
```

For phone testing, find the Pi IP address:

```bash
hostname -I
```

Then use this API URL in the Android app:

```text
http://PI_IP_ADDRESS:8080
```

Example:

```text
http://192.168.1.50:8080
```

## Android Test Client

Open `android/` in Android Studio. The app starts with the emulator URL:

```text
http://10.0.2.2:8080
```

For a real Android phone on the same Wi-Fi as the Raspberry Pi, replace that with the Pi URL:

```text
http://PI_IP_ADDRESS:8080
```

The Android project uses Java and basic Android SDK components only. It has no third-party app dependencies.
The current screen is a concept-style discovery app with filter chips, profile cards,
stats, protected-info notices, Login, Sign Up, LFG, squads, and a bottom nav.

The seeded test login is:

```text
Handle: NovaPulse
Password: testpass123
```

## API Routes

```text
GET  /api/health
GET  /api/games
GET  /api/players
GET  /api/players?game=apex-legends
GET  /api/players?platform=PC
GET  /api/players/{id}
GET  /api/lfg
GET  /api/squads
GET  /api/feed
GET  /api/feed/{post_id}/comments
GET  /api/conversations
GET  /api/conversations/{conversation_id}/messages
GET  /api/me
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
POST /api/me/profile
POST /api/connections
POST /api/feed/posts
POST /api/feed/{post_id}/react
POST /api/feed/{post_id}/comments
POST /api/conversations
POST /api/conversations/{conversation_id}/messages
```

Authenticated routes use:

```text
Authorization: Bearer YOUR_SESSION_TOKEN
```

Public player discovery shows whether Discord, Steam, Tracker Network, Xbox, Riot, and other platform accounts are connected, but hides the actual handles. Protected values are visible to the profile owner and to players with an accepted connection.

The owner panel loads at `/owner`. The private admin API routes require
`X-Admin-Token`, `Authorization: Bearer TOKEN`, or the `?token=` query value:

```text
GET  /api/admin/overview
GET  /api/admin/export
POST /api/admin/player-online
POST /api/admin/connection-status
POST /api/admin/reset
```

Example connection request:

```json
{
  "toPlayerId": "p_ghost",
  "message": "Want to squad up?"
}
```

## Next Backend Steps

- Replace seeded test user with real local auth.
- Add profile editing endpoints.
- Add linked account visibility settings.
- Add migrations once schema changes become frequent.
- Add HTTPS/tunnel guidance before outside testers use the Pi.
- Add Discord OAuth test integration.

## Supabase And Vercel

Hosted project files are included:

```text
supabase/migrations/0001_gamer_connect.sql
supabase/migrations/0002_conversation_policy_helpers.sql
vercel.json
api/config.js
api/tracker/profile.js
public/index.html
public/app.js
public/styles.css
.env.example
docs/SUPABASE_VERCEL.md
```

Use the local Python backend for Raspberry Pi testing. Use Supabase + Vercel for the hosted web/PC client with auth, feed, discovery, messages, events/servers, and profile.

## Backend Control Terminal

```powershell
python gamer.py control
```

Useful commands:

```text
status
players
player p_novapulse
online p_ghost on
lfg
squads
feed
conversations
connections
export ./data/export.json
reset
```

One-off commands:

```powershell
python gamer.py status
python gamer.py players
python gamer.py online p_ghost on
python gamer.py health
python gamer.py doctor
python gamer.py urls
python gamer.py admin
python gamer.py token
```
