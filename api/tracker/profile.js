const supportedGames = {
  "apex-legends": {
    title: "apex",
    displayName: "Apex Legends",
    platforms: ["origin", "xbl", "psn"]
  }
};

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    res.status(405).json({ ok: false, error: "Method not allowed" });
    return;
  }

  const apiKey = (process.env.TRACKER_NETWORK_API_KEY || process.env.TRN_API_KEY || "").trim();
  if (!apiKey) {
    res.status(501).json({
      ok: false,
      error: "Tracker Network API key is not configured. Add TRACKER_NETWORK_API_KEY in Vercel."
    });
    return;
  }

  const game = String(req.query.game || "apex-legends").trim();
  const platform = String(req.query.platform || "").trim().toLowerCase();
  const handle = String(req.query.handle || "").trim();
  const gameConfig = supportedGames[game];

  if (!gameConfig) {
    res.status(400).json({ ok: false, error: "This game is not wired to Tracker Network yet." });
    return;
  }
  if (!gameConfig.platforms.includes(platform)) {
    res.status(400).json({
      ok: false,
      error: `Unsupported platform for ${gameConfig.displayName}. Use: ${gameConfig.platforms.join(", ")}.`
    });
    return;
  }
  if (!handle) {
    res.status(400).json({ ok: false, error: "Tracker handle is required." });
    return;
  }

  const trackerUrl = `https://public-api.tracker.gg/v2/${gameConfig.title}/standard/profile/${encodeURIComponent(platform)}/${encodeURIComponent(handle)}`;

  try {
    const response = await fetch(trackerUrl, {
      headers: {
        "TRN-Api-Key": apiKey,
        "Accept": "application/json"
      }
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      res.status(response.status).json({
        ok: false,
        error: payload?.errors?.[0]?.message || payload?.message || "Tracker Network request failed.",
        status: response.status
      });
      return;
    }

    const normalized = normalizeTrackerProfile(gameConfig.displayName, platform, handle, payload);
    res.status(200).json({ ok: true, ...normalized });
  } catch (_error) {
    res.status(502).json({ ok: false, error: "Could not reach Tracker Network." });
  }
};

function normalizeTrackerProfile(game, platform, handle, payload) {
  const profile = payload?.data || {};
  const overview = Array.isArray(profile.segments)
    ? profile.segments.find(segment => segment.type === "overview") || profile.segments[0]
    : null;
  const stats = overview?.stats || {};
  const rank = firstText([
    stats.rankScore?.metadata?.rankName,
    stats.rankScore?.displayValue,
    stats.rankedScore?.metadata?.rankName,
    stats.rankedScore?.displayValue,
    stats.rank?.displayValue,
    stats.arenaRankScore?.metadata?.rankName,
    stats.arenaRankScore?.displayValue
  ]);

  return {
    game,
    platform,
    handle,
    rank: rank || "",
    stats: {
      level: statValue(stats.level),
      kills: statValue(stats.kills),
      damage: statValue(stats.damage),
      matchesPlayed: statValue(stats.matchesPlayed),
      winRate: statValue(stats.winRate),
      kd: statValue(stats.kd)
    },
    rawUpdatedAt: profile.metadata?.lastUpdated?.value || profile.expiryDate || null
  };
}

function statValue(stat) {
  return stat?.displayValue ?? stat?.value ?? "";
}

function firstText(values) {
  return values.find(value => typeof value === "string" && value.trim()) || "";
}
