# Gamer Connect Android Test Client

Open this folder in Android Studio.

The project is a simple Java Android client for testing the Raspberry Pi backend:

- configurable API base URL
- backend health check
- discovery player cards
- LFG posts
- squads
- connection request button

## Backend URL

Android emulator talking to backend on the Windows host:

```text
http://10.0.2.2:8080
```

Real Android phone talking to Raspberry Pi on the same Wi-Fi:

```text
http://PI_IP_ADDRESS:8080
```

The app currently permits cleartext HTTP for local testing. Before real public testing, use HTTPS or a secure tunnel.
