const { providers, providersForGame } = require("./lib/game-data/provider-registry");

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    res.setHeader("Allow", "GET");
    res.status(405).json({ ok: false, error: "Method not allowed" });
    return;
  }

  const gameId = String(req.query.game || "").trim();
  const selectedProviders = gameId ? providersForGame(gameId) : providers();
  const statuses = await Promise.all(selectedProviders.map(async provider => {
    const health = await provider.healthCheck();
    return {
      providerId: provider.providerId,
      displayName: provider.displayName,
      capabilities: provider.getCapabilities(),
      health
    };
  }));

  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    ok: true,
    gameId: gameId || null,
    providers: statuses
  });
};
