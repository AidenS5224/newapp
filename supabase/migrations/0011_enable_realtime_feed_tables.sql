do $$
begin
  alter publication supabase_realtime add table public.feed_posts;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.feed_reactions;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.feed_comments;
exception
  when duplicate_object then null;
end $$;
