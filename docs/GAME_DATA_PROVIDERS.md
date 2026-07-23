# Game Data Providers

Gamer Connect now has a provider-based integration layer for game accounts and stats.

## Current Architecture

- `api/lib/game-data/`: provider registry, feature flags, cache helpers, error mapping, and normalized data models.
- `api/lib/game-data/providers/tracker-network.js`: Tracker Network provider implementation.
- `api/tracker/profile.js`: backwards-compatible Vercel endpoint used by the current Profile editor.
- `api/providers.js`: provider capability and health summary endpoint. Use `?game=apex-legends` to filter by game.
- `supabase/migrations/0003_game_account_integrations.sql`: additive schema for providers, supported games, user game accounts, authorization records, snapshots, manual profiles, sync jobs, and provider request events.

The browser never calls Tracker Network directly and never receives the Tracker API key.

## Account Flow

The intended user flow is platform-first:

1. The user signs in.
2. The user adds the platform accounts they play on, such as EA/Origin, Steam, Xbox, PlayStation, Riot, Bungie, Nintendo, or Epic.
3. The user selects games for their public profile.
4. Each selected game chooses its own stats source platform and username/account ID.
5. If an approved provider supports that game/source, the app can pull stats from the backend.
6. If no provider supports it, the game still works with manual, user-provided data.

This means Gamer Connect is not dependent on Tracker Network or Apex Legends. Tracker is one provider behind the abstraction, and games without provider support remain usable.

## Provider Interface

Providers are plain CommonJS modules shaped around these capabilities:

- `getCapabilities()`
- `getGameSupport(gameId)`
- `normalizeAccountIdentifier(input)`
- `resolveAccount(input)`
- `fetchProfile(account)`
- `fetchStats(account, gameId)`
- `fetchRank(account, gameId)`
- `healthCheck()`

OAuth-oriented methods such as `beginAuthorization`, `completeAuthorization`, `refreshAuthorization`, and `revokeAuthorization` are intentionally not implemented until a provider is approved and ready.

## Normalized Models

Provider responses are converted into:

- `normalizedProfile`
- `normalizedRank`
- `normalizedStats`

Manual data must use `sourceType = user_provided` and `verificationStatus = user_provided_unverified`.

## Error Codes

Provider-specific failures map to stable app codes such as:

- `provider_disabled`
- `provider_not_configured`
- `provider_not_supported_for_game`
- `provider_not_supported_for_platform`
- `provider_approval_required`
- `account_not_found`
- `provider_rate_limited`
- `provider_unavailable`
- `sync_failed`

## Feature Flags

Use environment variables to disable providers or specific titles without a deployment:

```text
PROVIDER_TRACKER_ENABLED=true
PROVIDER_TRACKER_APEX_ENABLED=true
PROVIDER_STEAM_ENABLED=false
PROVIDER_RIOT_ENABLED=false
PROVIDER_RIOT_VALORANT_ENABLED=false
PROVIDER_BUNGIE_ENABLED=false
GAME_ACCOUNTS_MANUAL_PROFILES_ENABLED=true
GAME_ACCOUNTS_BACKGROUND_SYNC_ENABLED=false
MATCHMAKING_USE_VERIFIED_RANK_ENABLED=true
```

## Tracker Network

Tracker support is data-driven. Do not infer developer API availability from games visible on tracker.gg.

Current enabled support:

- Apex Legends via public identifier lookup.
- Platforms: `origin`, `xbl`, `psn`.
- Source type: `approved_third_party`.
- Verification status: `public_account_unverified`.

The UI also supports selecting non-Tracker platforms as stats sources so game accounts can be modeled before a provider exists. Those entries are labelled as user-provided until an approved provider can fetch data.

Rocket League is currently enabled in the profile editor as manual tracking: users can save rank, playlist, MMR/rating, source platform, and source username. Do not call undocumented Tracker Network Rocket League endpoints; add automatic Rocket League sync only after an approved public provider is available.

Tracker Network requests require the `TRN-Api-Key` header. Store the key only as `TRACKER_NETWORK_API_KEY` or `TRN_API_KEY` in Vercel/server environment variables.

## Steam, Riot, And Bungie

These providers are planned but disabled by default.

- Steam requires `STEAM_WEB_API_KEY`; private profiles must return a clear `private` state.
- Riot requires approved API access. Development keys must be treated as development-only, and production support must be explicitly approved.
- Bungie requires `BUNGIE_API_KEY` and OAuth for private/linked Destiny access. Bungie membership and Destiny membership are separate concepts.

## Caching And Syncing

The current provider layer has an in-memory stale cache suitable for Vercel request reuse, but not a durable queue. The migration adds `sync_jobs` for the next phase.

Next implementation steps:

- Add authenticated game-account management endpoints.
- Add manual profile CRUD.
- Add durable sync worker or Vercel scheduled job.
- Add provider request event persistence.
- Add OAuth token encryption before any OAuth provider is enabled.
