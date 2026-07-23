const errorCodes = {
  PROVIDER_DISABLED: "provider_disabled",
  PROVIDER_NOT_CONFIGURED: "provider_not_configured",
  PROVIDER_NOT_SUPPORTED_FOR_GAME: "provider_not_supported_for_game",
  PROVIDER_NOT_SUPPORTED_FOR_PLATFORM: "provider_not_supported_for_platform",
  PROVIDER_APPROVAL_REQUIRED: "provider_approval_required",
  ACCOUNT_NOT_FOUND: "account_not_found",
  ACCOUNT_AMBIGUOUS: "account_ambiguous",
  ACCOUNT_PRIVATE: "account_private",
  ACCOUNT_UNVERIFIED: "account_unverified",
  INVALID_IDENTIFIER: "invalid_identifier",
  INVALID_REGION: "invalid_region",
  AUTHORIZATION_DENIED: "authorization_denied",
  AUTHORIZATION_EXPIRED: "authorization_expired",
  TOKEN_REFRESH_FAILED: "token_refresh_failed",
  PROVIDER_RATE_LIMITED: "provider_rate_limited",
  PROVIDER_UNAVAILABLE: "provider_unavailable",
  SYNC_FAILED: "sync_failed",
  DATA_NOT_SUPPORTED: "data_not_supported"
};

function appError(code, message, details = {}) {
  return { code, message, details };
}

function mapHttpStatus(status) {
  if (status === 400) return errorCodes.INVALID_IDENTIFIER;
  if (status === 401 || status === 403) return errorCodes.PROVIDER_APPROVAL_REQUIRED;
  if (status === 404) return errorCodes.ACCOUNT_NOT_FOUND;
  if (status === 429) return errorCodes.PROVIDER_RATE_LIMITED;
  if (status >= 500) return errorCodes.PROVIDER_UNAVAILABLE;
  return errorCodes.SYNC_FAILED;
}

module.exports = {
  errorCodes,
  appError,
  mapHttpStatus
};
