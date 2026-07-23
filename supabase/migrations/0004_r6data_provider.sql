insert into public.game_providers (provider_key, display_name, enabled, configuration_status, health_status)
values
  ('r6data', 'R6Data', true, 'env_required', 'unknown')
on conflict (provider_key) do update set
  display_name = excluded.display_name,
  enabled = excluded.enabled,
  configuration_status = excluded.configuration_status,
  updated_at = now();

insert into public.provider_game_support (
  provider_id, game_id, capabilities, enabled, requires_approval, supported_platforms, supported_regions
)
select gp.id, 'rainbow-six-siege',
  '["publicIdentifierLookup", "profile", "rank", "aggregateStats"]'::jsonb,
  true,
  false,
  '["uplay", "xbl", "psn"]'::jsonb,
  '[]'::jsonb
from public.game_providers gp
where gp.provider_key = 'r6data'
on conflict (provider_id, game_id) do update set
  capabilities = excluded.capabilities,
  enabled = excluded.enabled,
  requires_approval = excluded.requires_approval,
  supported_platforms = excluded.supported_platforms,
  updated_at = now();
