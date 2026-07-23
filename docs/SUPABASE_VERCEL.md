# Supabase And Vercel Plan

This repo now supports two paths:

- Local testing: Python + SQLite backend on Windows or Raspberry Pi OS.
- Hosted production path: Supabase for auth/database/storage/realtime, Vercel for the web/PC client.

## Supabase

Create a Supabase project, then run the migration in:

```text
supabase/migrations/0001_gamer_connect.sql
supabase/migrations/0002_conversation_policy_helpers.sql
supabase/migrations/0003_game_account_integrations.sql
supabase/migrations/0004_r6data_provider.sql
supabase/migrations/0005_fix_conversation_participant_rls_recursion.sql
supabase/migrations/0006_blocks_and_unfriend_controls.sql
supabase/migrations/0007_relationship_chat_rpc.sql
```

Supabase should own production auth, public profiles, protected linked accounts, feed posts, comments, reactions, conversations, and messages.

## Vercel

Vercel is prepared as the web/PC host with:

```text
vercel.json
api/config.js
public/index.html
public/app.js
public/styles.css
.env.example
```

Set these Vercel environment variables:

```text
NEXT_PUBLIC_SUPABASE_URL
NEXT_PUBLIC_SUPABASE_ANON_KEY
SUPABASE_SERVICE_ROLE_KEY
TRACKER_NETWORK_API_KEY
R6DATA_API_KEY
PROVIDER_TOKEN_ENCRYPTION_KEY
```

Keep `SUPABASE_SERVICE_ROLE_KEY` server-only. Never expose it in Android, iOS, or browser code.
Keep `TRACKER_NETWORK_API_KEY` and `R6DATA_API_KEY` server-only as well. Browser and mobile clients should call the app's own `/api/tracker/profile` endpoint instead of calling providers directly.

The current Vercel web client reads only the public URL and publishable key through `/api/config`. It does not send the service role key to the browser.

## Tracker Network

The Vercel backend includes:

```text
api/tracker/profile.js
```

The provider endpoint currently supports Apex Legends through Tracker Network and Rainbow Six Siege through R6Data. It keeps the `/api/tracker/profile` path for backwards compatibility and to stay within the Vercel Hobby serverless function limit. Tracker Network's current developer FAQ says supported public API titles are Apex Legends and The Division 2, with older Splitgate and CS:GO APIs deprecated, so add new Tracker games only when Tracker Network documents and approves them.

See `docs/GAME_DATA_PROVIDERS.md` for the provider registry, feature flags, normalized response models, and current limitations.

## Recommended Split

- Android/iOS app: call Supabase directly for auth/feed/messages once ready.
- Web/PC app on Vercel: use Supabase client for logged-in user features.
- Raspberry Pi backend: keep for local prototyping, LAN testing, owner panel, and backend experiments.
