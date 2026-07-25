alter table public.conversation_participants
add column if not exists last_read_at timestamptz;