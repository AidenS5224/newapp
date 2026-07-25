alter table public.conversation_participants
add column if not exists last_read_at timestamptz;

create policy "participants can update own read timestamp"
on public.conversation_participants
for update
to authenticated
using (
    profile_id = auth.uid()
)
with check (
    profile_id = auth.uid()
);