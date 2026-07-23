const { providerById, providersForGame } = require("../lib/game-data/provider-registry");

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    res.status(405).json({ ok: false, error: "Method not allowed" });
    return;
  }

  const gameId = String(req.query.game || "apex-legends").trim();
  const requestedProvider = String(req.query.provider || "").trim();
  const provider = requestedProvider
    ? providerById(requestedProvider)
    : providersForGame(gameId)[0] || providerById("tracker-network");

  if (!provider) {
    res.status(400).json({
      ok: false,
      code: "provider_not_supported_for_game",
      error: "No approved provider supports this game yet."
    });
    return;
  }

  const result = await provider.fetchProfile({
    gameId,
    platform: String(req.query.platform || "").trim(),
    handle: String(req.query.handle || "").trim()
  });

  if (!result.ok) {
    const status = statusForError(result.error?.code);
    res.status(status).json({
      ok: false,
      code: result.error?.code || "sync_failed",
      error: result.error?.message || "Game data sync failed.",
      details: publicDetails(result.error?.details)
    });
    return;
  }

  res.status(200).json({
    ok: true,
    provider: result.normalizedProfile.provider,
    game: result.normalizedProfile.gameId,
    platform: result.normalizedProfile.platform,
    handle: result.normalizedProfile.username,
    rank: result.normalizedRank.displayText,
    stats: legacyStats(result.normalizedStats),
    normalizedProfile: result.normalizedProfile,
    normalizedRank: result.normalizedRank,
    normalizedStats: result.normalizedStats,
    sourceType: result.normalizedProfile.sourceType,
    verificationStatus: result.normalizedProfile.verificationStatus,
    cache: result.cache,
    rateLimit: result.rateLimit || {}
  });
};

function statusForError(code) {
  if (code === "provider_disabled") return 403;
  if (code === "provider_not_configured") return 501;
  if (code === "provider_not_supported_for_game" || code === "provider_not_supported_for_platform") return 400;
  if (code === "invalid_identifier") return 400;
  if (code === "account_not_found") return 404;
  if (code === "provider_rate_limited") return 429;
  if (code === "provider_unavailable") return 502;
  if (code === "provider_approval_required") return 403;
  return 500;
}

function publicDetails(details = {}) {
  const safe = { ...details };
  delete safe.apiKey;
  delete safe.token;
  return safe;
}

function legacyStats(stats) {
  return {
    level: stats.providerSpecificSummary?.level || stats.providerSpecificSummary?.clearanceLevel || "",
    kills: stats.kills || "",
    damage: stats.providerSpecificSummary?.damage || "",
    matchesPlayed: stats.providerSpecificSummary?.matchesPlayed || stats.matches || "",
    winRate: stats.winRate || "",
    kd: stats.kdRatio || ""
  };
}
