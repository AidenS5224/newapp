function normalizedGameProfile(values) {
  return {
    provider: values.provider,
    gameId: values.gameId,
    accountId: values.accountId,
    username: values.username,
    displayName: values.displayName || values.username,
    platform: values.platform,
    region: values.region || "",
    avatarUrl: values.avatarUrl || "",
    profileUrl: values.profileUrl || "",
    verificationStatus: values.verificationStatus || "public_account_unverified",
    sourceType: values.sourceType || "approved_third_party",
    lastUpdatedAt: values.lastUpdatedAt || new Date().toISOString(),
    privacyState: values.privacyState || "public"
  };
}

function normalizedRank(values = {}) {
  return {
    mode: values.mode || "",
    playlist: values.playlist || "",
    tier: values.tier || "",
    division: values.division || "",
    numericValue: Number.isFinite(values.numericValue) ? values.numericValue : null,
    displayText: values.displayText || "",
    season: values.season || "",
    sourceType: values.sourceType || "approved_third_party",
    updatedAt: values.updatedAt || new Date().toISOString()
  };
}

function normalizedGameStats(values = {}) {
  return {
    gameId: values.gameId,
    mode: values.mode || "",
    season: values.season || "",
    matches: numberOrNull(values.matches),
    wins: numberOrNull(values.wins),
    losses: numberOrNull(values.losses),
    winRate: values.winRate || "",
    kills: numberOrNull(values.kills),
    deaths: numberOrNull(values.deaths),
    assists: numberOrNull(values.assists),
    kdRatio: values.kdRatio || "",
    hoursPlayed: values.hoursPlayed || "",
    providerSpecificSummary: values.providerSpecificSummary || {},
    sourceType: values.sourceType || "approved_third_party",
    updatedAt: values.updatedAt || new Date().toISOString()
  };
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

module.exports = {
  normalizedGameProfile,
  normalizedRank,
  normalizedGameStats
};
