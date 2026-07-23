module.exports = function handler(_req, res) {
  const rawSupabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || process.env.SUPABASE_URL || "";
  const supabaseUrl = normalizeSupabaseUrl(rawSupabaseUrl);
  const supabaseAnonKey = (process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || "").trim();
  const ready = Boolean(supabaseUrl && supabaseAnonKey);

  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    supabaseUrl,
    supabaseAnonKey,
    ready,
    error: ready ? "" : "Set NEXT_PUBLIC_SUPABASE_URL to https://YOUR_PROJECT_REF.supabase.co and NEXT_PUBLIC_SUPABASE_ANON_KEY to the publishable anon key."
  });
};

function normalizeSupabaseUrl(value) {
  const trimmed = String(value || "").trim().replace(/\/+$/, "");
  if (!trimmed) return "";
  const withProtocol = trimmed.startsWith("http://") || trimmed.startsWith("https://")
    ? trimmed
    : `https://${trimmed}`;
  try {
    const parsed = new URL(withProtocol);
    if (!parsed.hostname.includes(".")) {
      parsed.hostname = `${parsed.hostname}.supabase.co`;
    }
    if (!parsed.hostname.endsWith(".supabase.co")) return "";
    return parsed.origin;
  } catch (_error) {
    return "";
  }
}
