insert into public.games (id, name, modes, platforms, crossplay)
values
  ('apex-legends', 'Apex Legends', '["Ranked", "Trios", "Duos"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch"]'::jsonb, true),
  ('valorant', 'Valorant', '["Competitive", "Unrated", "Premier"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, false),
  ('call-of-duty-warzone', 'Call of Duty: Warzone', '["Battle Royale", "Resurgence", "Ranked"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, true),
  ('fortnite', 'Fortnite', '["Battle Royale", "Zero Build", "Ranked"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch", "Mobile"]'::jsonb, true),
  ('minecraft', 'Minecraft', '["Survival", "Creative", "Realms"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch", "Mobile"]'::jsonb, true),
  ('roblox', 'Roblox', '["Experience", "Party", "Voice"]'::jsonb, '["PC", "Xbox", "PlayStation", "Mobile"]'::jsonb, true),
  ('league-of-legends', 'League of Legends', '["Ranked Solo/Duo", "Flex", "ARAM"]'::jsonb, '["PC"]'::jsonb, false),
  ('counter-strike-2', 'Counter-Strike 2', '["Premier", "Competitive", "Casual"]'::jsonb, '["PC"]'::jsonb, false),
  ('overwatch-2', 'Overwatch 2', '["Competitive", "Quick Play", "Arcade"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch"]'::jsonb, true),
  ('rocket-league', 'Rocket League', '["Ranked 1v1", "Ranked 2v2", "Ranked 3v3", "Casual"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch"]'::jsonb, true),
  ('the-division-2', 'The Division 2', '["PvE", "Dark Zone", "Raid"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, false),
  ('rainbow-six-siege', 'Rainbow Six Siege', '["Ranked", "Standard", "Quick Match"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, true),
  ('destiny-2', 'Destiny 2', '["Strikes", "Raids", "Crucible"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, true),
  ('grand-theft-auto-v', 'Grand Theft Auto V', '["Online", "Heists", "Races"]'::jsonb, '["PC", "PlayStation", "Xbox"]'::jsonb, false),
  ('ea-sports-fc', 'EA Sports FC', '["Ultimate Team", "Clubs", "Seasons"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch"]'::jsonb, true),
  ('nba-2k', 'NBA 2K', '["MyCareer", "Park", "Pro-Am"]'::jsonb, '["PC", "PlayStation", "Xbox", "Nintendo Switch"]'::jsonb, true),
  ('helldivers-2', 'Helldivers 2', '["Missions", "Squads", "Operations"]'::jsonb, '["PC", "PlayStation"]'::jsonb, true)
on conflict (id) do update set
  name = excluded.name,
  modes = excluded.modes,
  platforms = excluded.platforms,
  crossplay = excluded.crossplay;
