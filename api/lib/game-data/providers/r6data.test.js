const test = require("node:test");
const assert = require("node:assert/strict");
const r6DataProvider = require("./r6data");

test("normalizes R6Data fullStats payload into shared models", () => {
  const account = {
    gameId: "rainbow-six-siege",
    platform: "uplay",
    providerAccountId: "NovaPulse",
    providerAccountName: "NovaPulse"
  };
  const support = {
    sourceType: "approved_third_party"
  };
  const payload = {
    seasonYear: "Y10S4",
    platformType: "uplay",
    sessionType: "ranked",
    platform_families_full_profiles: [
      {
        board_ids_full_profiles: [
          {
            board_id: "ranked",
            full_profiles: [
              {
                profile: {
                  rank_points: 3300,
                  max_rank_points: 3450,
                  kills: 1240,
                  deaths: 1180,
                  wins: 64,
                  losses: 51,
                  update_time: "2025-10-14T21:43:27.315Z"
                },
                season_statistics: {
                  match_outcomes: { wins: 64, losses: 51 }
                }
              }
            ]
          }
        ]
      }
    ],
    data: {
      platformInfo: {
        platformUserId: "0c0b",
        platformUserHandle: "NovaPulse",
        avatarUrl: "https://example.com/avatar.png"
      },
      metadata: {
        clearanceLevel: 312,
        battlepassLevel: 45
      },
      segments: [
        {
          attributes: { sessionType: "ranked" },
          metadata: { shortName: "Y10S4", rankType: "ranked" },
          stats: {
            matchesPlayed: { value: 117, displayValue: "117" },
            kills: { value: 1240, displayValue: "1,240" },
            deaths: { value: 1180, displayValue: "1,180" },
            kdRatio: { value: 1.05, displayValue: "1.05" },
            rankPoints: { value: 3300, displayValue: "3,300" }
          }
        }
      ]
    }
  };

  const normalized = r6DataProvider.normalizeR6DataPayload(account, support, payload);

  assert.equal(normalized.normalizedProfile.provider, "r6data");
  assert.equal(normalized.normalizedProfile.gameId, "rainbow-six-siege");
  assert.equal(normalized.normalizedProfile.accountId, "0c0b");
  assert.equal(normalized.normalizedRank.playlist, "Ranked");
  assert.equal(normalized.normalizedRank.numericValue, 3300);
  assert.equal(normalized.normalizedRank.displayText, "3300 RP");
  assert.equal(normalized.normalizedStats.matches, 117);
  assert.equal(normalized.normalizedStats.kills, 1240);
  assert.equal(normalized.normalizedStats.kdRatio, "1.05");
  assert.equal(normalized.normalizedStats.providerSpecificSummary.clearanceLevel, "312");
});

test("R6Data rejects unsupported platforms", async () => {
  const provider = r6DataProvider({
    enabled: true,
    games: { "rainbow-six-siege": true }
  });

  const result = await provider.resolveAccount({
    gameId: "rainbow-six-siege",
    platform: "steam",
    handle: "NovaPulse"
  });

  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_not_supported_for_platform");
});
