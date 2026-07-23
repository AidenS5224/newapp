const { getCache, setCache, cacheKey } = require("../cache");
const { errorCodes, appError, mapHttpStatus } = require("../errors");
const { normalizedGameProfile, normalizedGameStats, normalizedRank } = require("../models");

const BASE_URL = "https://api.r6data.com/api/stats";
const CACHE_TTL_MS = 15 * 60 * 1000;
const STALE_TTL_MS = 45 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 8000;
const RETRIES = 1;

const supportedTitles = {
  "rainbow-six-siege": {
    displayName: "Rainbow Six Siege",
    sourceType: "approved_third_party",
    requiresApproval: false,
    supportedPlatforms: ["uplay", "xbl", "psn"],
    capabilities: ["publicIdentifierLookup", "profile", "rank", "aggregateStats"]
  }
};

module.exports = function r6DataProvider(flags = {}) {
  return {
    providerId: "r6data",
    displayName: "R6Data",

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
        requiresProductionApproval: false
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
        gameId: String(input.gameId || "rainbow-six-siege").trim(),
        platform: normalizePlatform(input.platform),
        identifier: String(input.identifier || input.handle || "").trim()
      };
    },

    async resolveAccount(input) {
      const account = this.normalizeAccountIdentifier(input);
      const support = this.getGameSupport(account.gameId);
      if (!flags.enabled) {
        return { ok: false, error: appError(errorCodes.PROVIDER_DISABLED, "R6Data is disabled.") };
      }
      if (!support) {
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_SUPPORTED_FOR_GAME, "R6Data does not support this game.") };
      }
      if (!support.enabled) {
        return { ok: false, error: appError(errorCodes.PROVIDER_DISABLED, "R6Data support for this game is disabled.") };
      }
      if (!support.supportedPlatforms.includes(account.platform)) {
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_SUPPORTED_FOR_PLATFORM, `Use one of: ${support.supportedPlatforms.join(", ")}.`) };
      }
      if (!account.identifier) {
        return { ok: false, error: appError(errorCodes.INVALID_IDENTIFIER, "Rainbow Six Siege username is required.") };
      }
      return {
        ok: true,
        account: {
          provider: "r6data",
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
        return { ok: false, error: appError(errorCodes.PROVIDER_NOT_CONFIGURED, "R6Data API key is not configured.") };
      }
      const account = resolved.account;
      const support = this.getGameSupport(account.gameId);
      const key = cacheKey(["r6data", "fullStats", account.platform, account.providerAccountId.toLowerCase()]);
      const cached = getCache(key);
      if (cached) return { ...cached.value, cache: cached.stale ? "stale" : "hit" };

      const query = new URLSearchParams({
        type: "fullStats",
        nameOnPlatform: account.providerAccountId,
        platformType: account.platform,
        modes: "ranked"
      });
      const endpoint = `${BASE_URL}?${query.toString()}`;
      const startedAt = Date.now();
      const response = await r6DataRequest(endpoint, apiKey);
      const latencyMs = Date.now() - startedAt;
      logProviderRequest({ gameId: account.gameId, status: response.status, latencyMs, cache: "miss" });
      if (!response.ok) return response;

      const normalized = normalizeR6DataPayload(account, support, response.payload);
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
        provider: "r6data",
        enabled: Boolean(flags.enabled),
        configured: Boolean(apiKeyFromEnv()),
        supportedGames: Object.keys(supportedTitles).filter(gameId => this.getGameSupport(gameId)?.enabled),
        checkedAt: new Date().toISOString()
      };
    }
  };
};

function apiKeyFromEnv() {
  return (process.env.R6DATA_API_KEY || "").trim();
}

function normalizePlatform(platform) {
  const value = String(platform || "").trim().toLowerCase();
  if (["ubi", "ubisoft", "uplay", "pc"].includes(value)) return "uplay";
  if (["xbox", "xbl"].includes(value)) return "xbl";
  if (["playstation", "ps", "psn"].includes(value)) return "psn";
  return value;
}

async function r6DataRequest(url, apiKey) {
  let lastError = null;
  for (let attempt = 0; attempt <= RETRIES; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const response = await fetch(url, {
        signal: controller.signal,
        headers: {
          "api-key": apiKey,
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
    error: appError(errorCodes.PROVIDER_UNAVAILABLE, "Could not reach R6Data.", { name: lastError?.name })
  };
}

function normalizeR6DataPayload(account, support, payload) {
  const data = payload?.data || {};
  const platformInfo = data.platformInfo || {};
  const segment = bestRankedSegment(data.segments) || {};
  const segmentStats = segment.stats || {};
  const currentProfile = bestFullProfile(payload);
  const profileStats = currentProfile?.profile || {};
  const seasonStats = currentProfile?.season_statistics || {};
  const outcomes = seasonStats.match_outcomes || {};
  const rankPoints = numberFrom(profileStats.rank_points, segmentStats.rankPoints?.value);
  const rankText = firstText([
    segmentStats.rankPoints?.metadata?.rank,
    segmentStats.rank?.metadata?.name,
    segmentStats.rank?.displayValue,
    rankPoints ? `${rankPoints} RP` : ""
  ]);
  const displayName = platformInfo.platformUserHandle || account.providerAccountName;
  const updatedAt = profileStats.update_time || payload?.lastUpdated || new Date().toISOString();
  const kills = numberFrom(profileStats.kills, seasonStats.kills, segmentStats.kills?.value);
  const deaths = numberFrom(profileStats.deaths, seasonStats.deaths, segmentStats.deaths?.value);
  const wins = numberFrom(profileStats.wins, outcomes.wins, segmentStats.wins?.value);
  const losses = numberFrom(profileStats.losses, outcomes.losses, segmentStats.losses?.value);
  const matches = numberFrom(segmentStats.matchesPlayed?.value, wins + losses);

  return {
    normalizedProfile: normalizedGameProfile({
      provider: "r6data",
      gameId: account.gameId,
      accountId: platformInfo.platformUserId || account.providerAccountId,
      username: account.providerAccountName,
      displayName,
      platform: account.platform,
      avatarUrl: platformInfo.avatarUrl || "",
      profileUrl: "",
      verificationStatus: "public_account_unverified",
      sourceType: support.sourceType,
      lastUpdatedAt: updatedAt
    }),
    normalizedRank: normalizedRank({
      mode: "ranked",
      playlist: "Ranked",
      numericValue: rankPoints,
      displayText: rankText,
      season: segment.metadata?.shortName || segment.metadata?.name || payload?.seasonYear || "",
      sourceType: support.sourceType,
      updatedAt
    }),
    normalizedStats: normalizedGameStats({
      gameId: account.gameId,
      mode: "ranked",
      season: segment.metadata?.shortName || payload?.seasonYear || "",
      matches,
      wins,
      losses,
      winRate: percentFrom(wins, matches),
      kills,
      deaths,
      assists: numberFrom(segmentStats.assists?.value),
      kdRatio: displayFrom(segmentStats.kdRatio, ratioFrom(kills, deaths)),
      providerSpecificSummary: {
        rankPoints: rankPoints ? String(rankPoints) : "",
        maxRankPoints: valueText(profileStats.max_rank_points),
        clearanceLevel: valueText(data.metadata?.clearanceLevel),
        battlepassLevel: valueText(data.metadata?.battlepassLevel)
      },
      sourceType: support.sourceType,
      updatedAt
    }),
    rawProvider: {
      provider: "r6data",
      seasonYear: payload?.seasonYear || "",
      platformType: payload?.platformType || account.platform,
      sessionType: payload?.sessionType || "ranked"
    }
  };
}

function bestRankedSegment(segments = []) {
  if (!Array.isArray(segments)) return null;
  return segments
    .filter(segment => segment?.attributes?.sessionType === "ranked" || segment?.metadata?.rankType === "ranked")
    .sort((a, b) => Number(b?.stats?.rankPoints?.value || 0) - Number(a?.stats?.rankPoints?.value || 0))[0] || segments[0] || null;
}

function bestFullProfile(payload = {}) {
  const profiles = payload.platform_families_full_profiles || [];
  for (const family of profiles) {
    for (const board of family.board_ids_full_profiles || []) {
      if (board.board_id !== "ranked") continue;
      const fullProfile = (board.full_profiles || [])
        .sort((a, b) => Number(b?.profile?.rank_points || 0) - Number(a?.profile?.rank_points || 0))[0];
      if (fullProfile) return fullProfile;
    }
  }
  return null;
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
  return payload?.message || payload?.error || `R6Data returned HTTP ${status}.`;
}

function numberFrom(...values) {
  for (const value of values) {
    const number = Number(value);
    if (Number.isFinite(number)) return number;
  }
  return null;
}

function displayFrom(stat, fallback = "") {
  return stat?.displayValue ?? stat?.value ?? fallback ?? "";
}

function valueText(value) {
  if (value === null || value === undefined) return "";
  return String(value);
}

function ratioFrom(top, bottom) {
  if (!Number.isFinite(top) || !Number.isFinite(bottom) || bottom <= 0) return "";
  return (top / bottom).toFixed(2);
}

function percentFrom(wins, matches) {
  if (!Number.isFinite(wins) || !Number.isFinite(matches) || matches <= 0) return "";
  return `${Math.round((wins / matches) * 100)}%`;
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
    provider: "r6data",
    gameId: event.gameId,
    status: event.status,
    latencyMs: event.latencyMs,
    cache: event.cache
  }));
}

module.exports.supportedTitles = supportedTitles;
module.exports.normalizeR6DataPayload = normalizeR6DataPayload;
