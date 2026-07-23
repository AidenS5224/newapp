# Gamer Connect

First working build path for the Gamer Connect product:

- `backend/`: Raspberry Pi-friendly API server using Python standard library + SQLite.
- `android/`: native Android test client scaffold that can call the backend over Wi-Fi.

## Current Backend Features

- Health check
- Seeded games, players, LFG posts, and squads
- Player discovery filters by game/platform
- Compatibility score for discovery cards
- Connection request creation
- CORS enabled for later web/PWA clients

## Run Backend On Windows

```powershell
cd backend
python app.py
```

Then open:

```text
http://localhost:8080/api/health
```

## Run Backend On Raspberry Pi OS

```bash
cd gamer-connect/backend
python3 app.py
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
POST /api/connections
```

Example connection request:

```json
{
  "fromPlayerId": "p_novapulse",
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
