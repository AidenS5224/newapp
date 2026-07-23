const test = require("node:test");
const assert = require("node:assert/strict");
const trackerNetworkProvider = require("./tracker-network");

test("normalizes Tracker overview payload into shared models", () => {
  const account = {
    gameId: "apex-legends",
    platform: "origin",
    providerAccountId: "NovaPulse",
    providerAccountName: "NovaPulse"
  };
  const support = {
    sourceType: "approved_third_party"
  };
  const payload = {
    data: {
      platformInfo: { platformUserHandle: "NovaPulse" },
      metadata: { lastUpdated: { value: "2026-07-23T10:00:00.000Z" } },
      segments: [
        {
          type: "overview",
          stats: {
            rankScore: { metadata: { rankName: "Diamond II" }, displayValue: "Diamond II" },
            level: { displayValue: "247", value: 247 },
            kills: { displayValue: "2,400", value: 2400 },
            wins: { displayValue: "120", value: 120 },
            winRate: { displayValue: "57%" },
            kd: { displayValue: "1.32" }
          }
        }
      ]
    }
  };

  const normalized = trackerNetworkProvider.normalizeTrackerPayload(account, support, payload);

  assert.equal(normalized.normalizedProfile.provider, "tracker-network");
  assert.equal(normalized.normalizedProfile.verificationStatus, "public_account_unverified");
  assert.equal(normalized.normalizedRank.displayText, "Diamond II");
  assert.equal(normalized.normalizedStats.kills, 2400);
  assert.equal(normalized.normalizedStats.providerSpecificSummary.level, "247");
});

test("rejects unsupported platforms before provider calls", async () => {
  const provider = trackerNetworkProvider({
    enabled: true,
    games: { "apex-legends": true }
  });

  const result = await provider.resolveAccount({
    gameId: "apex-legends",
    platform: "steam",
    handle: "NovaPulse"
  });

  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_not_supported_for_platform");
});

test("feature flag can disable a supported Tracker title", async () => {
  const provider = trackerNetworkProvider({
    enabled: true,
    games: { "apex-legends": false }
  });

  const result = await provider.resolveAccount({
    gameId: "apex-legends",
    platform: "origin",
    handle: "NovaPulse"
  });

  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_disabled");
});
