module.exports = function handler(_req, res) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || process.env.SUPABASE_URL || "";
  const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || process.env.SUPABASE_ANON_KEY || "";

  res.setHeader("Cache-Control", "no-store");
  res.status(200).json({
    supabaseUrl,
    supabaseAnonKey,
    ready: Boolean(supabaseUrl && supabaseAnonKey)
  });
};
