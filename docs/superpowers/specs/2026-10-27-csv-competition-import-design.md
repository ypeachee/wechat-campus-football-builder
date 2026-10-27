# CSV Competition Import Design

**Date:** 2026-10-27

**Status:** Approved direction, pending written-spec review
**Target:** Local Windows one-click import for CUGB campus football data

## 1. Goal

Allow an administrator to maintain competition data in Excel-friendly CSV files and import a complete competition package with one command. The package must support leagues and cups, including group-stage standings, knockout progression, penalties, third-place matches, and the final bracket.

The import must be safe for repeated development use: validate before writing, use one database transaction, protect unrelated competitions, and clearly report errors in Chinese.

## 2. Scope

### Included

- Competition and season metadata
- League, group, and knockout stages
- Competition groups
- Teams and players
- Season participation and group membership
- League/group fixtures, scores, statuses, and penalties
- Goal, own-goal, assist, yellow-card, and red-card events
- Optional appearance records
- Cup bracket generation from final group standings
- Knockout results, automatic winner progression, champion, and optional third-place match
- Dry-run validation
- Replacement of an existing season only with an explicit flag
- A working CSV example based on the 2022 World Cup sample

### Excluded from V1

- Direct `.xlsx` parsing. Users edit CSV files in Excel.
- Uploading CSV files from inside the mini program.
- Team logos, player photos, or other binary media.
- External match-feed integrations.
- Tournament formats other than 4, 8, or 16 knockout qualifiers.
- Importing user accounts or team-manager grants.

## 3. User Workflow

1. Copy `import-data/templates` to a new folder, for example `import-data/2026-spring-cup`.
2. Fill the CSV files in Excel.
3. Save each sheet as **CSV UTF-8 (comma-delimited)**.
4. Run a dry run:

```powershell
.\scripts\import-competition.ps1 -DataDir .\import-data\2026-spring-cup -DryRun
```

5. Fix any reported validation errors.
6. Import:

```powershell
.\scripts\import-competition.ps1 -DataDir .\import-data\2026-spring-cup
```

7. To rebuild an existing season:

```powershell
.\scripts\import-competition.ps1 -DataDir .\import-data\2026-spring-cup -Replace
```

`-Replace` requires an interactive confirmation. `-Yes` suppresses the prompt for automation.

## 4. Command-Line Interface

Primary wrapper:

```text
scripts/import-competition.ps1
  -DataDir <folder>          Required. Folder containing the CSV files.
  -DryRun                    Validate and print a summary without writing.
  -Replace                   Rebuild an existing competition season.
  -Yes                       Skip the destructive replacement confirmation.
  -MysqlPath <path>          Optional path to mysql.exe.
  -Host <host>               Default: 127.0.0.1
  -Port <port>               Default: 3307
  -Database <name>           Default: campus_football
  -User <user>               Default: campus
  -Password <password>       Optional. If omitted, read `MYSQL_PWD` or prompt.
```

The PowerShell wrapper validates that Python 3 and `mysql.exe` are available, then invokes the Python importer as a child process.

Implementation files:

- `scripts/import-competition.ps1`: user-facing wrapper and environment/path checks
- `scripts/import_competition.py`: CSV parsing, validation, SQL generation, and execution
- `scripts/tests/test_import_competition.py`: standard-library unit tests
- `import-data/templates/`: empty headers and one example row per file
- `import-data/world-cup-2022/`: complete working example

No third-party Python packages are required.

## 5. CSV Package

All files are UTF-8 CSV files with exact Chinese headers. Leading and trailing whitespace is ignored. Empty optional values are allowed.

### 5.1 `赛事.csv`

Exactly one data row.

| Column | Required | Meaning |
|---|---:|---|
| 赛事名称 | Yes | Competition name; import key with season |
| 赛事类型 | Yes | `LEAGUE` or `CUP`; Chinese `联赛`/`杯赛` also accepted |
| 赛事说明 | No | Description |
| 赛季名称 | Yes | Season name |
| 开始日期 | No | `YYYY-MM-DD` |
| 结束日期 | No | `YYYY-MM-DD` |
| 赛季状态 | Yes | `DRAFT`, `ACTIVE`, `FINISHED`; Chinese labels accepted |

### 5.2 `阶段小组.csv`

One row per stage/group. A stage without groups has one row with blank group columns.

| Column | Required | Meaning |
|---|---:|---|
| 阶段名称 | Yes | Stage name |
| 阶段类型 | Yes | `LEAGUE`, `GROUP`, `KNOCKOUT` |
| 排序 | Yes | Non-negative integer |
| 小组名称 | For group rows | Example: `A组` |
| 小组排序 | For group rows | Non-negative integer |

The combination `(阶段名称, 小组名称)` must be unique.

### 5.3 `球队.csv`

One row per team-stage membership in the imported season. A team may appear multiple times only when it participates in different stages.

| Column | Required | Meaning |
|---|---:|---|
| 球队名称 | Yes | Team key |
| 简称 | No | Display short name |
| 教练 | No | Coach name |
| 主场 | No | Home venue |
| 参赛阶段 | For group/league stages | Stage name |
| 参赛小组 | For group stages | Group name |
| 种子序号 | No | Positive integer |

If a team participates in more than one group stage, use one row per stage/group. The same team may only appear once in a given group stage.

### 5.4 `球员.csv`

| Column | Required | Meaning |
|---|---:|---|
| 球队名称 | Yes | Team represented by a row in `球队.csv` |
| 球员姓名 | Yes | Player key within the team |
| 球衣号码 | No | 0-999 |
| 位置 | No | 前锋/中场/后卫/门将 or similar |
| 状态 | No | `ACTIVE`/`INACTIVE`; default `ACTIVE` |

Existing players with the same normalized team/player name are updated rather than duplicated.

### 5.5 `赛程.csv`

Contains only league and group-stage fixtures. Knockout fixtures are generated from bracket data.

| Column | Required | Meaning |
|---|---:|---|
| 比赛编号 | Yes | Unique package key across all fixture and knockout-result files |
| 阶段名称 | Yes | League or group stage |
| 小组名称 | For group stages | Group name |
| 轮次 | No | Positive integer |
| 主队 | Yes | Team name |
| 客队 | Yes | Team name |
| 开赛时间 | No | `YYYY-MM-DD HH:mm` or ISO date-time |
| 场地 | No | Venue |
| 状态 | Yes | `DRAFT`, `SCHEDULED`, `LIVE`, `FINISHED`, `CANCELLED` |
| 主队比分 | For finished matches | Non-negative integer |
| 客队比分 | For finished matches | Non-negative integer |

Knockout scores and penalty scores belong only in `淘汰赛结果.csv`.

A finished match is imported with `data_locked = 1`. A non-finished match must have blank scores.

### 5.6 `比赛事件.csv`

| Column | Required | Meaning |
|---|---:|---|
| 比赛编号 | Yes | Fixture key from `赛程.csv` or knockout results |
| 球队名称 | Yes | Team that benefits from the event |
| 球员姓名 | Yes | Player involved in the event |
| 事件类型 | Yes | `GOAL`, `OWN_GOAL`, `YELLOW_CARD`, `RED_CARD`; Chinese labels accepted |
| 助攻球员 | For goals optionally | Player in the same team |
| 分钟 | Yes | 1-130 |
| 补时 | No | 0-30; default 0 |
| 备注 | No | Free text |

Rules:

- A normal goal may have at most one assisting player.
- Assister and scorer must belong to the event team.
- Own goals and cards cannot have an assister.
- For an own goal, `球队名称` is the team receiving the goal and `球员姓名` must belong to the opposing participating team.
- The `球队名称` side receives the goal in score validation.
- If a finished fixture has any imported events, credited goal totals must equal the official score.
- If a finished fixture has no imported events, validation emits a warning but permits import.

### 5.7 `出场.csv` (optional)

| Column | Required | Meaning |
|---|---:|---|
| 比赛编号 | Yes | Fixture key |
| 球队名称 | Yes | Participating team |
| 球员姓名 | Yes | Player in that team |
| 是否首发 | No | Boolean; default `是` |
| 出场分钟 | No | 0-130; default 90 |

If the file is absent, no appearance records are created.

### 5.8 `淘汰赛配置.csv` (optional)

| Column | Required | Meaning |
|---|---:|---|
| 淘汰赛阶段 | Yes | Stage with type `KNOCKOUT` |
| 来源小组阶段 | Yes | Stage with type `GROUP` |
| 每组晋级数 | Yes | 1 or 2 |
| 三四名决赛 | No | Boolean; default `否` |

The qualified-team count must be exactly 4, 8, or 16.

### 5.9 `淘汰赛结果.csv` (optional)

| Column | Required | Meaning |
|---|---:|---|
| 淘汰赛阶段 | Yes | Configured knockout stage |
| 轮次 | Yes | Positive integer, or `三四名` |
| 对阵序号 | Yes | Position within that round |
| 比赛编号 | Yes | Unique package key |
| 开赛时间 | No | Date-time |
| 场地 | No | Venue |
| 主队比分 | Yes for played tie | Non-negative integer |
| 客队比分 | Yes for played tie | Non-negative integer |
| 主队点球 | For draws | Non-negative integer |
| 客队点球 | For draws | Non-negative integer |

Bracket semantics match the backend:

- Groups are sorted by `sort_order`, then ID.
- Standings use points, goal difference, goals scored, wins, then team name.
- Qualifiers are ordered by group order and rank.
- First-round pairing uses the standard first-half vs second-half mapping.
- Rounds and ties are generated before results are processed.
- Results are processed in round order and position order.
- A draw requires different penalty scores.
- Winners advance automatically to the next tie.
- When third place is enabled, semifinal losers populate the third-place tie.
- Missing later-round results leave those ties pending rather than failing the import.

## 6. Identity and Replacement Rules

### 6.1 Import Identity

`(赛事名称, 赛季名称)` identifies the imported season.

- If neither exists, create both.
- If the competition exists but the season does not, append the season.
- If the season exists, fail by default.
- With `-Replace`, rebuild only that season.

`-Replace` deletes this season's fixtures, events, appearances, knockout rounds/ties/seeds, groups, stages, and season-team links before recreating them. It does not delete teams or players, and it never touches other seasons or competitions.

### 6.2 Teams and Players

- Teams are matched case-insensitively by normalized name.
- Multiple existing teams with the same normalized name are an error.
- Existing team metadata is updated.
- Players are matched by team plus normalized player name and are updated rather than duplicated.
- Teams and players remain reusable across competitions.
- No team or player deletion is performed by the importer.

### 6.3 Admin Attribution

Created/updated rows use the first active `ADMIN` account. If none exists, validation fails with instructions to start the backend or seed an administrator first.

## 7. Validation and Failure Behavior

Validation runs before SQL generation.

Required checks:

- Required files exist except optional `出场.csv`, `淘汰赛配置.csv`, and `淘汰赛结果.csv`.
- Headers match the documented columns.
- Required values are present.
- Enum-like values are recognized.
- Dates and times parse correctly.
- Competition file has exactly one row.
- Stage names, group names, team names, player names, and match IDs are unique where required.
- Team group membership references an existing group stage and group.
- Season participation exists before group membership.
- Fixture teams belong to the season and, for group fixtures, the selected group.
- Home and away teams differ.
- Scores and penalty pairs are valid.
- Finished matches have scores.
- Event players belong to the credited team.
- Goal totals match scores when events are supplied.
- Appearance players belong to participating teams.
- Bracket source stage and knockout stage exist and belong to the same season.
- Qualifier count is 4, 8, or 16.
- Knockout results reference valid round/position combinations.
- Replacement requires `-Replace`.

Validation collects all actionable errors instead of stopping at the first problem. Output example:

```text
CSV 校验失败，共 3 个问题：
1. 赛程.csv 第 5 行：主队“法国”不属于 A组
2. 比赛事件.csv 第 12 行：助攻球员“梅西”不属于法国
3. 淘汰赛结果.csv 第 3 行：决赛平局必须填写不同点球比分
```

Warnings do not block import:

- Finished fixture without event details
- Match without kickoff time or venue
- Player without jersey number or position

All database writes occur in a single transaction. Any SQL error rolls back the complete import.

## 8. Technical Architecture

```text
PowerShell wrapper
  -> environment and executable checks
  -> Python importer
       -> load CSV package
       -> validate and normalize
       -> detect existing season
       -> build import plan
       -> generate transaction SQL
       -> run mysql.exe with UTF-8
       -> print summary
```

The Python importer will not depend on the running Spring Boot server. It will use the MySQL client directly so batch imports work even when WeChat login is not configured. It must still honor database constraints and existing business rules.

SQL values are escaped centrally. A dedicated test covers names containing single quotes, backslashes, commas, and line breaks in notes.

## 9. Testing

### Unit tests

Using Python `unittest`:

- UTF-8 and UTF-8-BOM CSV input
- Missing and extra headers
- Duplicate stage/group/team/player/match IDs
- Missing referenced team/group/stage/player
- League fixture with penalties
- Finished fixture without score
- Partially filled penalty score
- Event score mismatch
- Wrong-team assister
- Appearance wrong-team player
- Invalid qualifier count
- Draw without penalties
- Invalid knockout round/position
- Replacement guard
- SQL escaping for quotes, backslashes, commas, and newlines

### Integration tests

Run against a dedicated import-test database, never the daily database:

1. Import `import-data/world-cup-2022`.
2. Verify 8 teams, 25 players, 12 group fixtures, 3 knockout fixtures, and 47 events.
3. Verify the top ten scorers.
4. Verify Argentina is champion after a 3-3 final and 4-2 penalties.
5. Import again without `-Replace`; expect refusal.
6. Import again with `-Replace`; expect identical counts and no duplicates.
7. Verify another competition remains untouched.

### Backend/public verification

After import, query the existing public APIs:

- `/api/v1/competitions`
- `/api/v1/seasons`
- `/api/v1/stages`
- `/api/v1/stats/scorers`
- `/api/v1/standings`
- `/api/v1/brackets`

The mini program must display the imported data without additional code changes.

## 10. Deliverables

- PowerShell wrapper
- Python importer
- Unit tests
- Blank/example CSV templates
- Complete 2022 World Cup CSV example
- Short usage guide at `import-data/README.md`
- All frontend tests remain passing
- Importer tests passing
- Working import demonstrated against the local MySQL instance

## 11. Acceptance Criteria

- A user can fill CSV files in Excel and import a full league with one command.
- A user can import a full cup, generate the knockout tree, enter penalties, and obtain the correct champion.
- Dry run reports all validation errors without changing the database.
- A failed import leaves the database unchanged.
- Replacing a season only affects that season.
- Importing the World Cup example reproduces the current sample standings and bracket.
- No credential or password is written into CSV files or committed configuration.
