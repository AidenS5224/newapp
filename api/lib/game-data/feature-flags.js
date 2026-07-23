function flag(name, defaultValue = true) {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return defaultValue;
  return !["0", "false", "off", "disabled", "no"].includes(String(raw).trim().toLowerCase());
}

function providerFlags() {
  return {
    tracker: {
      enabled: flag("PROVIDER_TRACKER_ENABLED", true),
      games: {
        "apex-legends": flag("PROVIDER_TRACKER_APEX_ENABLED", true),
        "the-division-2": flag("PROVIDER_TRACKER_DIVISION2_ENABLED", false)
      }
    },
    r6data: {
      enabled: flag("PROVIDER_R6DATA_ENABLED", true),
      games: {
        "rainbow-six-siege": flag("PROVIDER_R6DATA_R6S_ENABLED", true)
      }
    },
    steam: { enabled: flag("PROVIDER_STEAM_ENABLED", false) },
    riot: {
      enabled: flag("PROVIDER_RIOT_ENABLED", false),
      valorant: flag("PROVIDER_RIOT_VALORANT_ENABLED", false)
    },
    bungie: { enabled: flag("PROVIDER_BUNGIE_ENABLED", false) },
    gameAccounts: {
      manualProfiles: flag("GAME_ACCOUNTS_MANUAL_PROFILES_ENABLED", true),
      backgroundSync: flag("GAME_ACCOUNTS_BACKGROUND_SYNC_ENABLED", false)
    },
    matchmaking: {
      useVerifiedRank: flag("MATCHMAKING_USE_VERIFIED_RANK_ENABLED", true)
    }
  };
}

module.exports = {
  flag,
  providerFlags
};
