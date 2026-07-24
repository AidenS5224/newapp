# Gamer Connect Android Test Client

Open this folder in Android Studio.

The project is a simple Java Android client for testing the Raspberry Pi backend:

- configurable API base URL
- concept-inspired dark Gamer Connect UI
- sign up and login controls on the Profile tab
- backend status indicator
- Feed tab for clips, posts, highlights, and squad updates
- Discovery/LFG tab with a start screen, reference-style player deck, filters, Pass, More Info, and Play actions
- Events/Servers tab as a polished Coming Soon page
- Feed tab reads live posts from `/api/feed` and can like posts when signed in
- Messages tab reads `/api/conversations`, creates a test direct chat, opens message threads, and sends messages
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

## Current Android Test Flow

1. Start the local backend from `backend` with `python app.py`.
2. Open `android` in Android Studio.
3. Run the app on an emulator.
4. On Profile, use `http://10.0.2.2:8080`, then log in.
5. Test Discovery/LFG with Start Discovery, Pass, and Play.
6. Test Feed by liking backend feed posts.
7. Test Messages with New Test Chat, opening a conversation, and sending a message.
