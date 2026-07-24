# Gamer Connect Android Test App

Open this folder in Android Studio.

This Android project is a lightweight WebView wrapper for the hosted Gamer Connect app:

```text
https://newapp-silk.vercel.app
```

That means Android tests the same Vercel deployment and Supabase project as the browser app:

- Supabase Auth sign in and account creation
- Supabase database reads/writes
- Feed posting and media upload
- Discovery/LFG
- Messages
- Profile setup
- Events/Servers Coming Soon page

## Run In Android Studio

1. Open Android Studio.
2. Choose `File > Open`.
3. Select the `android` folder in this repo.
4. Let Gradle sync finish.
5. Select an emulator or connected Android phone.
6. Press Run.

No local backend URL is needed for this version. The app loads Vercel directly and Vercel/Supabase handle the backend.

## Notes

The WebView has JavaScript, local storage, and file picking enabled so the hosted app can sign in, keep a session, and upload feed media for testing.
