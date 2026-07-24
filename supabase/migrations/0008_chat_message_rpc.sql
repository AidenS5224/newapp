create or replace function public.send_chat_message(target_conversation_id uuid, message_body text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile_id uuid := auth.uid();
  new_message_id uuid := gen_random_uuid();
  clean_body text := trim(coalesce(message_body, ''));
begin
  if current_profile_id is null then
    raise exception 'sign in required';
  end if;

  if clean_body = '' then
    raise exception 'message cannot be empty';
  end if;

  if not public.is_conversation_member(target_conversation_id, current_profile_id) then
    raise exception 'not a conversation member';
  end if;

  if public.is_conversation_blocked(target_conversation_id) then
    raise exception 'blocked conversations cannot receive messages';
  end if;

  insert into public.messages (id, conversation_id, sender_profile_id, body)
  values (new_message_id, target_conversation_id, current_profile_id, clean_body);

  update public.conversations
  set updated_at = now()
  where id = target_conversation_id;

  return new_message_id;
end;
$$;

grant execute on function public.send_chat_message(uuid, text) to authenticated;
