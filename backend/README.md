# Gamer Connect Backend

No external Python packages are required.

## Start

From the project root:

```bash
python3 gamer.py dev
```

From this `backend/` folder:

```bash
python3 app.py
```

On Windows:

```powershell
python app.py
```

## Configuration

Environment variables:

```text
GAMER_CONNECT_HOST=0.0.0.0
GAMER_CONNECT_PORT=8080
GAMER_CONNECT_DB=./data/gamer_connect.sqlite3
GAMER_CONNECT_ADMIN_TOKEN=optional-owner-token
```

If `GAMER_CONNECT_ADMIN_TOKEN` is not set, the backend creates a local token at
`backend/data/admin_token.txt`. That file is ignored by git.

## Owner Panel

Start the backend, then open the printed owner URL:

```text
http://127.0.0.1:8080/owner?token=YOUR_LOCAL_ADMIN_TOKEN
```

From the project root you can print the URL or token:

```bash
python3 gamer.py admin
python3 gamer.py token
```

The panel includes live counts, players, connection requests, LFG posts, squads,
export, reset, and test controls for online state and request approval.

## Test

```bash
python3 test_api.py
```

The test starts the API, checks seeded endpoints, creates a connection request, and shuts the server down.

## Auth And Protected Info

Seeded local accounts:

```text
NovaPulse  / testpass123
GhostRider / testpass123
ZaneFPS    / testpass123
```

Useful routes:

```text
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
GET  /api/me
POST /api/me/profile
```

Use the returned session token on protected routes:

```text
Authorization: Bearer YOUR_SESSION_TOKEN
```

Discovery only returns linked account summaries publicly. Actual Discord, Steam,
Tracker Network, Xbox, Riot, and protected profile values are shown to the
profile owner, the owner panel, or players with an accepted connection.

## Feed

```text
GET  /api/feed
GET  /api/feed/{post_id}/comments
POST /api/feed/posts
POST /api/feed/{post_id}/react
POST /api/feed/{post_id}/comments
```

Create a post or clip:

```json
{
  "type": "clip",
  "gameId": "apex-legends",
  "title": "Ranked clutch",
  "body": "Looking for two calm teammates.",
  "mediaType": "video"
}
```

## Messages

```text
GET  /api/conversations
GET  /api/conversations/{conversation_id}/messages
POST /api/conversations
POST /api/conversations/{conversation_id}/messages
```

Create a chat:

```json
{
  "participantPlayerIds": ["p_ghost"],
  "message": "Want to squad up?"
}
```

## Control Terminal

From the project root:

```bash
python3 gamer.py control
```

Interactive admin/control shell:

```bash
python3 control.py
```

Windows:

```powershell
python control.py
```

One-off commands:

```bash
python3 control.py status
python3 control.py players
python3 control.py lfg
python3 control.py feed
python3 control.py conversations
python3 control.py connections
python3 control.py online p_ghost on
python3 control.py export ./data/export.json
```

The control terminal edits the same SQLite database used by the running backend.
