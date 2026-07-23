const { getCache, setCache, cacheKey } = require("../cache");
const { errorCodes, appError, mapHttpStatus } = require("../errors");
const { normalizedGameProfile, normalizedGameStats, normalizedRank } = require("../models");

const BASE_URL = "https://public-api.tracker.gg/v2";
const CACHE_TTL_MS = 15 * 60 * 1000;
const STALE_TTL_MS = 45 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 8000;
const RETRIES = 1;

const supportedTitles = {
  "apex-legends": {
    titleSlug: "apex",
    displayName: "Apex Legends",
    sourceType: "approved_third_party",
    requiresApproval: true,
    supportedPlatforms: ["origin", "xbl", "psn"],
    capabilities: ["publicIdentifierLookup", "profile", "rank", "aggregateStats"]
  },
  "the-division-2": {
    titleSlug: "division-2",
    displayName: "The Division 2",
    sourceType: "approved_third_party",
    requiresApproval: true,
    supportedPlatforms: ["uplay", "xbl", "psn"],
    capabilities: ["publicIdentifierLookup", "profile", "aggregateStats"]
  }
};

module.exports = function trackerNetworkProvider(flags = {}) {
  return {
    providerId: "tracker-network",
    displayName: "Tracker Network",

    getCapabilities() {
      return {
        oauth: false,
        publicIdentifierLookup: true,
        accountSearch: false,
        ownershipVerification: false,
        profile: true,
        rank: true,
        aggregateStats: true,
        recentMatches: false,
        matchHistory: false,
        gameLibrary: false,
        presence: false,
        crossPlatformAccounts: false,
        requiresProductionApproval: true
      };
    },

    getGameSupport(gameId) {
      const support = supportedTitles[gameId];
      if (!support) return null;
      return {
        ...support,
        gameId,
        enabled: Boolean(flags.enabled && flags.games?.[gameId])
      };
    },

    normalizeAccountIdentifier(input) {
      return {
        gameId: String(input.gameId || "apex-legends").trim(),
        platform: String(input.platform || "").trim().toLowerCase(),
        identifier: String(input.identifier || input.handle || "").trim()
      };
    },

    async resolveAccount(input) {
      const account = this.normalizeAccountIdentifier(input);
      const support = this.getGameSupport(account.gameId);
      if (!flags.enabled) {
        return { ok: false, error: appError(errorCodes.PROVIDER_DISABLED, "Tracker Network is disabled.") };
      }
      if (!support) {
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_SUPPORTED_FOR_GAME, "Tracker Network does not support this game.") };
      }
      if (!support.enabled) {
        return { ok: false, error: appError(errorCodes.PROVIDER_DISABLED, "Tracker support for this game is disabled.") };
      }
      if (!support.supportedPlatforms.includes(account.platform)) {
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_SUPPORTED_FOR_PLATFORM, `Use one of: ${support.supportedPlatforms.join(", ")}.`) };
      }
      if (!account.identifier) {
        return { ok: false, error: appError(errorCodes.INVALID_IDENTIFIER, "Tracker handle is required.") };
      }
      return {
        ok: true,
        account: {
          provider: "tracker-network",
          gameId: account.gameId,
          platform: account.platform,
          providerAccountId: account.identifier,
          providerAccountName: account.identifier,
          displayName: account.identifier,
          verificationStatus: "public_account_unverified"
        }
      };
    },

    async fetchProfile(input) {
      const resolved = await this.resolveAccount(input);
      if (!resolved.ok) return resolved;
      const apiKey = apiKeyFromEnv();
      if (!apiKey) {
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_CONFIGURED, "Tracker Network API key is not configured.") };
      }
      const account = resolved.account;
      const support = this.getGameSupport(account.gameId);
      const key = cacheKey(["tracker", "profile", account.gameId, account.platform, account.providerAccountId.toLowerCase()]);
      const cached = getCache(key);
      if (cached) {
        return { ...cached.value, cache: cached.stale ? "stale" : "hit" };
      }
      const endpoint = `${BASE_URL}/${support.titleSlug}/standard/profile/${encodeURIComponent(account.platform)}/${encodeURIComponent(account.providerAccountId)}`;
      const startedAt = Date.now();
      const response = await trackerRequest(endpoint, apiKey);
      const latencyMs = Date.now() - startedAt;
      logProviderRequest({ gameId: account.gameId, status: response.status, latencyMs, cache: "miss" });
      if (!response.ok) return response;

      const normalized = normalizeTrackerPayload(account, support, response.payload);
      const value = { ok: true, ...normalized, rateLimit: response.rateLimit };
      setCache(key, value, CACHE_TTL_MS, STALE_TTL_MS);
      return { ...value, cache: "miss" };
    },

    async fetchRank(input) {
      const profile = await this.fetchProfile(input);
      if (!profile.ok) return profile;
      return { ok: true, rank: profile.normalizedRank };
    },

    async fetchStats(input) {
      const profile = await this.fetchProfile(input);
      if (!profile.ok) return profile;
      return { ok: true, stats: profile.normalizedStats };
    },

    async healthCheck() {
      return {
        provider: "tracker-network",
        enabled: Boolean(flags.enabled),
        configured: Boolean(apiKeyFromEnv()),
        supportedGames: Object.keys(supportedTitles).filter(gameId => this.getGameSupport(gameId)?.enabled),
        checkedAt: new Date().toISOString()
      };
    }
  };
};

function apiKeyFromEnv() {
  return (process.env.TRACKER_NETWORK_API_KEY || process.env.TRN_API_KEY || "").trim();
}

async function trackerRequest(url, apiKey) {
  let lastError = null;
  for (let attempt = 0; attempt <= RETRIES; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch(url, {
        signal: controller.signal,
        headers: {
          "TRN-Api-Key": apiKey,
          "Accept": "application/json"
        }
      });
      const payload = await response.json().catch(() => ({}));
      const rateLimit = parseRateLimit(response.headers);
      if (response.ok) return { ok: true, status: response.status, payload, rateLimit };
      if (![429, 500, 502, 503, 504].includes(response.status) || attempt === RETRIES) {
        return {
          ok: false,
          status: response.status,
          error: appError(mapHttpStatus(response.status), safeProviderMessage(payload, response.status), { rateLimit })
        };
      }
      await sleep(backoffMs(attempt, response.headers.get("retry-after")));
    } catch (error) {
      lastError = error;
      if (attempt === RETRIES) break;
      await sleep(backoffMs(attempt));
    } finally {
      clearTimeout(timeout);
    }
  }
  return {
    ok: false,
    status: 502,
    error: appError(errorCodes.PROVIDER_UNAVAILABLE, "Could not reach Tracker Network.", { name: lastError?.name })
  };
}

function normalizeTrackerPayload(account, support, payload) {
  const profile = payload?.data || {};
  const overview = Array.isArray(profile.segments)
    ? profile.segments.find(segment => segment.type === "overview") || profile.segments[0]
    : null;
  const stats = overview?.stats || {};
  const displayName = profile.platformInfo?.platformUserHandle || account.providerAccountName;
  const rankText = firstText([
    stats.rankScore?.metadata?.rankName,
    stats.rankScore?.displayValue,
    stats.rankedScore?.metadata?.rankName,
    stats.rankedScore?.displayValue,
    stats.rank?.displayValue,
    stats.arenaRankScore?.metadata?.rankName,
    stats.arenaRankScore?.displayValue
  ]);
  const updatedAt = profile.metadata?.lastUpdated?.value || profile.expiryDate || new Date().toISOString();

  return {
    normalizedProfile: normalizedGameProfile({
      provider: "tracker-network",
      gameId: account.gameId,
      accountId: account.providerAccountId,
      username: account.providerAccountName,
      displayName,
      platform: account.platform,
      avatarUrl: profile.platformInfo?.avatarUrl || "",
      profileUrl: "",
      verificationStatus: "public_account_unverified",
      sourceType: support.sourceType,
      lastUpdatedAt: updatedAt
    }),
    normalizedRank: normalizedRank({
      displayText: rankText,
      sourceType: support.sourceType,
      updatedAt
    }),
    normalizedStats: normalizedGameStats({
      gameId: account.gameId,
      matches: statNumber(stats.matchesPlayed),
      wins: statNumber(stats.wins),
      winRate: statDisplay(stats.winRate),
      kills: statNumber(stats.kills),
      deaths: statNumber(stats.deaths),
      kdRatio: statDisplay(stats.kd),
      providerSpecificSummary: {
        level: statDisplay(stats.level),
        damage: statDisplay(stats.damage),
        matchesPlayed: statDisplay(stats.matchesPlayed)
      },
      sourceType: support.sourceType,
      updatedAt
    }),
    rawProvider: {
      provider: "tracker-network",
      segments: Array.isArray(profile.segments) ? profile.segments.map(segment => segment.type).filter(Boolean) : [],
      expiresAt: profile.expiryDate || null
    }
  };
}

function parseRateLimit(headers) {
  return {
    limit: headers.get("x-ratelimit-limit") || headers.get("ratelimit-limit") || "",
    remaining: headers.get("x-ratelimit-remaining") || headers.get("ratelimit-remaining") || "",
    reset: headers.get("x-ratelimit-reset") || headers.get("ratelimit-reset") || "",
    retryAfter: headers.get("retry-after") || ""
  };
}

function safeProviderMessage(payload, status) {
  return payload?.errors?.[0]?.message || payload?.message || `Tracker Network returned HTTP ${status}.`;
}

function statDisplay(stat) {
  return stat?.displayValue ?? stat?.value ?? "";
}

function statNumber(stat) {
  const number = Number(stat?.value ?? stat?.displayValue);
  return Number.isFinite(number) ? number : null;
}

function firstText(values) {
  return values.find(value => typeof value === "string" && value.trim()) || "";
}

function backoffMs(attempt, retryAfter) {
  const retrySeconds = Number(retryAfter);
  if (Number.isFinite(retrySeconds) && retrySeconds > 0) return Math.min(retrySeconds * 1000, 5000);
  return Math.min(250 * 2 ** attempt + Math.floor(Math.random() * 100), 1500);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function logProviderRequest(event) {
  console.info(JSON.stringify({
    event: "game_provider_request",
    provider: "tracker-network",
    gameId: event.gameId,
    status: event.status,
    latencyMs: event.latencyMs,
    cache: event.cache
  }));
}

module.exports.supportedTitles = supportedTitles;
module.exports.normalizeTrackerPayload = normalizeTrackerPayload;
