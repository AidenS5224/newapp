const cache = new Map();

function getCache(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  const now = Date.now();
  if (entry.expiresAt > now) {
    return { hit: true, stale: false, value: entry.value };
  }
  if (entry.staleUntil > now) {
    return { hit: true, stale: true, value: entry.value };
  }
  cache.delete(key);
  return null;
}

function setCache(key, value, ttlMs, staleMs = ttlMs) {
  const now = Date.now();
  cache.set(key, {
    value,
    expiresAt: now + ttlMs,
    staleUntil: now + ttlMs + staleMs
  });
}

function cacheKey(parts) {
  return parts.map(part => encodeURIComponent(String(part ?? ""))).join(":");
}

module.exports = {
  getCache,
  setCache,
  cacheKey
};
