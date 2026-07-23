# Supabase And Vercel Plan

This repo now supports two paths:

- Local testing: Python + SQLite backend on Windows or Raspberry Pi OS.
- Hosted production path: Supabase for auth/database/storage/realtime, Vercel for the web/PC client.

## Supabase

Create a Supabase project, then run the migration in:

```text
supabase/migrations/0001_gamer_connect.sql
```

Supabase should own production auth, public profiles, protected linked accounts, feed posts, comments, reactions, conversations, and messages.

## Vercel

Vercel is prepared as the web/PC host with:

```text
vercel.json
public/index.html
.env.example
```

When the web app is added, set these Vercel environment variables:

```text
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_ANON_KEY
SUPABASE_SERVICE_ROLE_KEY
```

Keep `SUPABASE_SERVICE_ROLE_KEY` server-only. Never expose it in Android, iOS, or browser code.

## Recommended Split

- Android/iOS app: call Supabase directly for auth/feed/messages once ready.
- Web/PC app on Vercel: use Supabase client for logged-in user features.
- Raspberry Pi backend: keep for local prototyping, LAN testing, owner panel, and backend experiments.
