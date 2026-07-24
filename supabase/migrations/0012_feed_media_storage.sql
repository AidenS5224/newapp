insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'feed-media',
  'feed-media',
  true,
  52428800,
  array[
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'video/mp4',
    'video/webm',
    'video/quicktime'
  ]
)
on conflict (id) do update
set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "feed media public read" on storage.objects;
drop policy if exists "users upload own feed media" on storage.objects;
drop policy if exists "users update own feed media" on storage.objects;
drop policy if exists "users delete own feed media" on storage.objects;

create policy "feed media public read"
on storage.objects for select
using (bucket_id = 'feed-media');

create policy "users upload own feed media"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'feed-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "users update own feed media"
on storage.objects for update
to authenticated
using (
  bucket_id = 'feed-media'
  and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
  bucket_id = 'feed-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "users delete own feed media"
on storage.objects for delete
to authenticated
using (
  bucket_id = 'feed-media'
  and (storage.foldername(name))[1] = auth.uid()::text
);
