# Gamer Connect Android Test Client

Open this folder in Android Studio.

The project is a simple Java Android client for testing the Raspberry Pi backend:

- configurable API base URL
- concept-inspired dark Gamer Connect UI
- sign up and login controls on the Profile tab
- backend status indicator
- Discover tab with a reference-style featured player card, filters, pass/more/approve actions, and a Great Match panel
- Messages tab for matched players, groups, and starting new chats
- protected Discord/platform info messaging
- LFG posts
- squads
- authenticated connection request button

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

## Test Login

```text
Handle: NovaPulse
Password: testpass123
```

Open Profile to log in or sign up before tapping Connect. Guest mode can browse discovery, but connection requests and matched-player chats require a session token.
