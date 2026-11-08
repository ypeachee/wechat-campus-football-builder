UPDATE fixture_event
SET event_type = 'OWN_GOAL',
    team_id = 14,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 66
  AND fixture_id = 22
  AND player_id = 50
  AND event_type = 'GOAL'
  AND note = '乌龙球';
