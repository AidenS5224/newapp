const { providerFlags } = require("./feature-flags");
const trackerNetworkProvider = require("./providers/tracker-network");

function providers() {
  const flags = providerFlags();
  return [
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
