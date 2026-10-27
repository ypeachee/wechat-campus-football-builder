# Domain model and invariants

## Core hierarchy

```text
competition
└── season
    └── stage
        ├── competition_group
        │   └── group_team
        └── knockout_round
            └── knockout_tie
                └── fixture
```

Use this hierarchy when the product supports more than one competition. A league can have one or more league stages. A cup can combine a `GROUP` stage and a `KNOCKOUT` stage. Keep stage and group selectors explicit in APIs and the mini program once more than one stage exists.

## Primary entities

| Entity | Purpose | Important fields |
|---|---|---|
| `team` | Persistent club identity | name, short name, coach, venue, status |
| `player` | Team roster member | team_id, jersey_no, name, position, status |
| `season` | A competition edition | competition_id, name, dates, status |
| `stage` | League, group, or knockout phase | season_id, type, sort order, status |
| `competition_group` | Group inside a group stage | stage_id, name, sort order |
| `fixture` | One match | season/stage/group, home/away team, status, scores, penalties, lock |
| `fixture_appearance` | Participation and lineup | fixture, team, player, starter, minutes |
| `fixture_event` | Match event | fixture, team, player, event type, related player, minute |
| `team_manager` | Team-scoped authorization | user_id, team_id |
| `audit_log` | Privileged change history | actor, team scope, entity, before/after |

Recommended unique constraints:

- `fixture_appearance(fixture_id, player_id)`.
- `team_manager(user_id, team_id)`.
- `group_team(group_id, team_id)`.
- A team belongs to at most one group within the same stage.
- A knockout fixture has at most one bracket tie.
- A user has one player rating per `(fixture_id, player_id, user_id)`.

## Match-data semantics

### Scores

Fixtures own the official score. Events are explanatory records and must not automatically mutate the result unless the product explicitly defines an event-driven score workflow. The reference implementation keeps score entry under administrator control.

### Events

Supported event types commonly include goal, own goal, yellow card, and red card. Keep assists related to a goal event, for example through `related_player_id`, when that preserves a single source of truth.

Validate:

- The player belongs to the expected team. For an own goal, the player belongs to the opposing team while the event belongs to the credited team, or follow the repository's established convention consistently.
- An assist player belongs to the same team as the scorer.
- Only goals may have assists.
- Event minute and stoppage minute are within the configured bounds.
- The fixture is not locked.

### Appearances and bulk lineups

An appearance record means a player participated. It is not the same as an event.

For a bulk lineup endpoint:

```http
PUT /api/v1/manage/fixtures/{fixtureId}/teams/{teamId}/appearances
```

```json
{
  "starters": [101, 102, 103],
  "substitutes": [
    { "playerId": 104, "minutesPlayed": 32 },
    { "playerId": 105, "minutesPlayed": 18 }
  ]
}
```

Service rules:

- Replace only the requested team's lineup in one transaction.
- Reject duplicate players across starters and substitutes.
- Reject players outside the team.
- Set starters to a full-match convention such as 90 minutes.
- Validate substitute minutes.
- Preserve any player already referenced by a fixture event. Reject removal with a clear conflict message.
- Record one audit entry for the team-lineup replacement.
- Make a retry idempotent in outcome: saving the same lineup produces the same final participation set.

UI rules:

- Show the full active roster in both starter and substitute sections.
- Starters remain visible in the substitute section, checked and disabled.
- A starter row cannot be toggled as a substitute.
- Sort by jersey number and then name.
- Display `number name`; do not show `number号 name` unless that label is already a project convention.
- Show minutes only for selected non-starters.

## Permissions

| Role | Read | Team profile | Fixtures/events/lineups | Competition/bracket | Moderation |
|---|---|---|---|---|---|
| `VIEWER` | Public | No | No | No | No |
| `TEAM_MANAGER` | Public + own management scope | Own team only | Own team in authorized fixtures | No | No |
| `ADMIN` | All | All | All | All | All |

Rules:

- Load role and team assignments from the database on each request.
- Treat the frontend as navigation only. Never trust a client-provided role or team ID.
- Require the team to be a participant in the fixture before any match-data write.
- Revalidate authorization after every grant or revoke; stale JWTs must not preserve removed team access.
- Restrict sensitive status transitions such as finish, lock, unlock, penalty winner, and bracket propagation to administrators.

## Fixture lifecycle

A practical state model:

```text
DRAFT -> SCHEDULED -> LIVE -> FINISHED
                    └────> CANCELLED
SCHEDULED/LIVE -> CANCELLED
FINISHED -> locked by default
```

Application rules:

- Public endpoints do not expose `DRAFT`.
- Finishing requires a valid final score.
- Finishing normally sets `data_locked = true`.
- A locked fixture rejects appearance, event, score, penalty, and status mutations until an administrator unlocks it.
- Team managers may be restricted to `LIVE` and unlocked `FINISHED` fixtures. Admin write windows can include `SCHEDULED`.

## Standings and leaderboards

Compute standings from finished fixtures in the relevant season/stage/group:

- Played, wins, draws, losses.
- Goals for, goals against, goal difference.
- Points using an explicit rule such as 3/1/0.
- Deterministic sort order, for example points, goal difference, goals for, then a documented fallback.

Do not persist a second standings table unless performance measurements justify a cache. If a cache is added, make its invalidation rules explicit.

Leaderboards:

- Scorer: count goal events for the correct credited team/player.
- Assist: aggregate goal-event assist references.
- Appearances: count distinct appearance records, normally only finished fixtures.
- Apply season, stage, and group filters consistently in all ranking queries.

## Cup and bracket invariants

- Use single-match knockout only when that is the chosen scope.
- A knockout fixture must produce one winner. A draw requires a valid penalty shootout result.
- Store regulation score and penalty score separately.
- After a tie finishes, place the winner into the configured downstream slot.
- Refuse upstream result changes if the downstream fixture already has data that depends on the winner.
- A final winner becomes the competition champion.
- Keep seeding mappings from group rank to bracket slot explicit and editable by an administrator.
- Do not automate unresolved ties based on an arbitrary hidden rule.

## Ratings and moderation

A practical score model:

- Input: integer stars `1..5`.
- Canonical storage: `stars`.
- Display: `stars * 2` on a 10-point scale.
- Public average: `AVG(stars) * 2`, display one decimal.

Rules:

- Only logged-in users can rate.
- Only finished fixtures and participating players are eligible.
- One rating per user, fixture, and player.
- Optional comments have a strict maximum length and pass sensitive-term/content-security checks before public display.
- Pending, rejected, hidden, or reported-until-reviewed content does not contribute to the public average unless the product explicitly defines otherwise.
- Support an edit window and record whether it was used.
- Support reporting and an administrator moderation queue.
- Referee ratings may share the scale but omit comments; they should not invoke comment moderation.
- Keep moderation and sensitive-term changes in the audit log.
- Public APIs must not expose OpenID or other unnecessary personal identifiers.

## Suggested API surface

Public:

- `GET /api/v1/competitions`
- `GET /api/v1/seasons?competitionId=`
- `GET /api/v1/stages?seasonId=`
- `GET /api/v1/groups?stageId=`
- `GET /api/v1/fixtures?seasonId=&stageId=&groupId=`
- `GET /api/v1/fixtures/{fixtureId}`
- `GET /api/v1/standings?stageId=&groupId=`
- `GET /api/v1/stats/scorers?seasonId=&stageId=&groupId=`
- `GET /api/v1/stats/assists?seasonId=&stageId=&groupId=`
- `GET /api/v1/stats/appearances?seasonId=&stageId=&groupId=`
- `GET /api/v1/brackets?stageId=`

Management:

- Team profile and roster: `/api/v1/manage/teams/**`
- Fixture events: `/api/v1/manage/fixtures/**`
- Bulk lineup: `PUT /api/v1/manage/fixtures/{fixtureId}/teams/{teamId}/appearances`
- Ratings and moderation: `/api/v1/manage/ratings/**` and `/api/v1/admin/ratings/**`
- Competition, stages, groups, and brackets: `/api/v1/admin/**`

Keep list filters consistent across standings, fixtures, and player rankings so switching a group in the UI cannot silently mix scopes.
