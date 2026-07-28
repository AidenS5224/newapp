insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-avatars',
  'profile-avatars',
  true,
  5242880,
  array[
    'image/jpeg',
    'image/png',
    'image/webp'
  ]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "profile avatars public read" on storage.objects;
drop policy if exists "users upload own profile avatar" on storage.objects;
drop policy if exists "users update own profile avatar" on storage.objects;
drop policy if exists "users delete own profile avatar" on storage.objects;

create policy "profile avatars public read"
on storage.objects for select
using (bucket_id = 'profile-avatars');

create policy "users upload own profile avatar"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and (storage.filename(name)) = 'avatar'
);

create policy "users update own profile avatar"
on storage.objects for update
to authenticated
using (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and (storage.filename(name)) = 'avatar'
)
with check (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and (storage.filename(name)) = 'avatar'
);

create policy "users delete own profile avatar"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'profile-avatars'
  and (storage.foldername(name))[1] = auth.uid()::text
  and (storage.filename(name)) = 'avatar'
);
