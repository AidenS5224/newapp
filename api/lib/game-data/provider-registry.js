const { providerFlags } = require("./feature-flags");
const r6DataProvider = require("./providers/r6data");
const trackerNetworkProvider = require("./providers/tracker-network");

function providers() {
  const flags = providerFlags();
  return [
    r6DataProvider(flags.r6data),
    trackerNetworkProvider(flags.tracker)
  ];
}

function providerById(providerId) {
  return providers().find(provider => provider.providerId === providerId) || null;
}

function providersForGame(gameId) {
  return providers().filter(provider => {
    const support = provider.getGameSupport(gameId);
    return support && support.enabled;
  });
}

module.exports = {
  providers,
  providerById,
  providersForGame
};
