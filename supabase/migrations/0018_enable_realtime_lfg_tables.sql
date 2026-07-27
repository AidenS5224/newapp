do $$
begin
  alter publication supabase_realtime add table public.lfg_posts;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.lfg_join_requests;
exception
  when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.lfg_members;
exception
  when duplicate_object then null;
end $$;
