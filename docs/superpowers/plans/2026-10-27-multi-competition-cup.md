# Multi-Competition and Cup Bracket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the campus football platform from one league table into multiple competitions with seasons, stages, groups, group standings, single-leg knockout brackets, automatic winner advancement, penalties, and a final tree.

**Architecture:** Introduce `competition -> season -> stage -> group` above the existing fixture model, migrate existing data into a default league competition/stage, preserve legacy season-based APIs through deterministic fallback, then add bracket graph and advancement services. Extend the mini program with shared competition/stage/group selection and a public bracket view.

**Tech Stack:** Existing Spring Boot 3.4.5 + MyBatis + MySQL 8 backend, existing native TypeScript + TDesign WeChat Mini Program, JUnit/Testcontainers, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-27-multi-competition-cup-design.md`

## Global Constraints

- Keep existing league data visible after migration by assigning it to default competition `CUGB校园足球联赛` and a default `LEAGUE` stage.
- Competition types are exactly `LEAGUE` and `CUP`.
- Stage types are exactly `LEAGUE`, `GROUP`, and `KNOCKOUT`.
- Single-leg knockout only. No two-leg ties, away goals, or replay matches.
- A knockout tie must produce one winner.
- A tied knockout score requires different penalty scores.
- Bracket sizes are exactly 4, 8, or 16 in V1. No byes.
- Third-place matches are optional but use the same tie/fixture model.
- Only `ADMIN` may create or modify competitions, stages, groups, seeds, ties, penalties, and bracket progression.
- `TEAM_MANAGER` permissions remain scoped to existing team/appearance/event rules.
- A downstream bracket result must never be silently overwritten by an upstream change.
- Preserve the existing public `seasonId` API behavior when only one stage exists.
- Every new API path starts with `/api/v1`.
- No new frontend state library. Keep the existing local selection store approach.
- All tests must run with both the backend MySQL test path and the mini-program Vitest path.

## Review Focus

- Migration silently loses or hides existing seasons, fixtures, standings, or player statistics: test a pre-V2 database with real rows and compare public results after V2.
- A fixture is assigned to a group from another stage or a team outside the group: reject it at the service boundary and test both create and update paths.
- Group standings leak matches from other groups or include cup knockout matches: test groups with interleaved fixtures and a separate knockout stage.
- Winner propagation overwrites a downstream tie that already has a fixture or result: test the guard and preserve downstream data.
- Competition/stage/group selection becomes stale when switching a parent selector: test that changing competition resets season/stage/group and never requests the old child IDs.

## File Structure

### Backend

```text
src/main/resources/db/migration/V2__multi_competition_and_cup.sql
src/main/java/com/campusfootball/competition/Competition.java
src/main/java/com/campusfootball/competition/CompetitionType.java
src/main/java/com/campusfootball/competition/CompetitionStatus.java
src/main/java/com/campusfootball/competition/Stage.java
src/main/java/com/campusfootball/competition/StageType.java
src/main/java/com/campusfootball/competition/StageStatus.java
src/main/java/com/campusfootball/competition/CompetitionGroup.java
src/main/java/com/campusfootball/competition/GroupTeam.java
src/main/java/com/campusfootball/competition/KnockoutRound.java
src/main/java/com/campusfootball/competition/KnockoutTie.java
src/main/java/com/campusfootball/competition/KnockoutSeed.java
src/main/java/com/campusfootball/competition/TieStatus.java
src/main/java/com/campusfootball/competition/BracketSlot.java
src/main/java/com/campusfootball/competition/CompetitionMapper.java
src/main/java/com/campusfootball/competition/CompetitionService.java
src/main/java/com/campusfootball/competition/CompetitionController.java
src/main/java/com/campusfootball/competition/CompetitionAdminController.java
src/main/java/com/campusfootball/competition/StageMapper.java
src/main/java/com/campusfootball/competition/StageService.java
src/main/java/com/campusfootball/competition/StageController.java
src/main/java/com/campusfootball/competition/StageAdminController.java
src/main/java/com/campusfootball/competition/GroupMapper.java
src/main/java/com/campusfootball/competition/GroupService.java
src/main/java/com/campusfootball/competition/GroupAdminController.java
src/main/java/com/campusfootball/competition/KnockoutMapper.java
src/main/java/com/campusfootball/competition/BracketService.java
src/main/java/com/campusfootball/competition/BracketAdminController.java
src/main/java/com/campusfootball/competition/BracketController.java
src/main/resources/mappers/CompetitionMapper.xml
src/main/resources/mappers/StageMapper.xml
src/main/resources/mappers/GroupMapper.xml
src/main/resources/mappers/KnockoutMapper.xml
```

Modify existing backend files:

```text
src/main/java/com/campusfootball/competition/Season.java
src/main/java/com/campusfootball/competition/Fixture.java
src/main/java/com/campusfootball/competition/SeasonMapper.java
src/main/java/com/campusfootball/competition/FixtureMapper.java
src/main/java/com/campusfootball/competition/SeasonService.java
src/main/java/com/campusfootball/competition/FixtureService.java
src/main/java/com/campusfootball/competition/SeasonController.java
src/main/java/com/campusfootball/competition/SeasonAdminController.java
src/main/java/com/campusfootball/competition/FixtureController.java
src/main/java/com/campusfootball/competition/FixtureAdminController.java
src/main/java/com/campusfootball/stats/StatsMapper.java
src/main/java/com/campusfootball/stats/StatsService.java
src/main/java/com/campusfootball/stats/StatsController.java
src/main/resources/mappers/SeasonMapper.xml
src/main/resources/mappers/FixtureMapper.xml
src/main/resources/mappers/StatsMapper.xml
```

### Mini Program

All `index.*` entries below mean the four native files `index.ts`, `index.json`, `index.wxml`, and `index.wxss`.

```text
miniprogram/state/competition.ts
miniprogram/state/selection.ts
miniprogram/services/competition-hierarchy.ts
miniprogram/components/competition-picker/index.*
miniprogram/components/stage-picker/index.*
miniprogram/components/group-picker/index.*
miniprogram/components/bracket-round/index.*
miniprogram/components/bracket-tie/index.*
miniprogram/pages/bracket/index.*
miniprogram/pages/admin-competition/index.*
miniprogram/pages/admin-stage/index.*
miniprogram/pages/admin-bracket/index.*
```

Modify existing mini-program files:

```text
miniprogram/types/domain.ts
miniprogram/services/competition.ts
miniprogram/services/stats.ts
miniprogram/state/season.ts
miniprogram/pages/home/index.*
miniprogram/pages/fixtures/index.*
miniprogram/pages/stats/index.*
miniprogram/pages/manage/index.*
miniprogram/pages/admin-season/index.*
miniprogram/pages/admin-fixtures/index.*
miniprogram/pages/manage-fixture-data/index.*
miniprogram/app.json
```

---

### Task 1: Migrate the Database Without Losing Existing League Data

**Files:**
- Create: `src/main/resources/db/migration/V2__multi_competition_and_cup.sql`
- Modify: `src/test/java/com/campusfootball/SchemaMigrationTest.java`
- Create: `src/test/java/com/campusfootball/MultiCompetitionMigrationTest.java`

**Interfaces:**
- Produces tables `competition`, `stage`, `competition_group`, `group_team`, `knockout_round`, `knockout_tie`, and `knockout_seed`.
- Adds `season.competition_id`, `fixture.stage_id`, `fixture.group_id`, `fixture.knockout_tie_id`, `fixture.home_penalty_score`, and `fixture.away_penalty_score`.
- Seeds default competition `CUGB校园足球联赛` with type `LEAGUE`.
- Creates one default `LEAGUE` stage for every pre-existing season and backfills every pre-existing fixture to that stage.

- [ ] **Step 1: Write failing schema and migration tests**

Update `SchemaMigrationTest` to assert the seven new tables exist. Add `MultiCompetitionMigrationTest.migratesV1RowsIntoDefaultLeagueAndStage()` using an isolated MySQL schema and the Flyway Java API:

1. Migrate to target `1`.
2. Insert a season, two teams, a season-team row, a fixture, an appearance, and an event through `JdbcTemplate`.
3. Migrate to target `2`.
4. Assert one default `LEAGUE` competition exists.
5. Assert the old season has the default `competition_id`.
6. Assert one default `LEAGUE` stage exists for the season.
7. Assert the old fixture has that stage and null group/tie/penalty fields.
8. Assert the old appearance and event remain queryable.
9. Assert no nullable score or stage fields were introduced on the old fixture.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=SchemaMigrationTest,MultiCompetitionMigrationTest test`

Expected: FAIL because V2 and the new tables/columns do not exist.

- [ ] **Step 3: Implement V2 migration**

Create the new tables and constraints in the order required by foreign keys. Add `season.competition_id` as nullable, backfill the default competition, then make it NOT NULL. Create a default stage for each season and backfill fixtures before making `fixture.stage_id` NOT NULL. Add `fixture.knockout_tie_id` after the tie table exists and add a unique key for it. Preserve all existing rows.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=SchemaMigrationTest,MultiCompetitionMigrationTest test`

Expected: PASS with all existing rows preserved.

- [ ] **Step 5: Commit**

```powershell
git add src/main/resources/db/migration/V2__multi_competition_and_cup.sql src/test/java/com/campusfootball/SchemaMigrationTest.java src/test/java/com/campusfootball/MultiCompetitionMigrationTest.java
git commit -m "feat: migrate data for multi-competition support"
```

---

### Task 2: Add Competition, Season, Stage, Group, and Group-Team APIs

**Files:**
- Create the competition/stage/group entities, enums, mappers, services, XML, and controllers listed in the file structure.
- Modify: `Season.java`, `SeasonMapper.java`, `SeasonMapper.xml`, `SeasonService.java`, `SeasonController.java`, `SeasonAdminController.java`
- Test: `src/test/java/com/campusfootball/CompetitionStageIntegrationTest.java`

**Interfaces:**
- `CompetitionService.findActive(): List<Competition>`
- `CompetitionService.create(long actorUserId, String name, CompetitionType type, String description): long`
- `StageService.findBySeason(long seasonId): List<Stage>`
- `StageService.create(long actorUserId, long seasonId, String name, StageType type, int sortOrder): long`
- `GroupService.findByStage(long stageId): List<CompetitionGroup>`
- `GroupService.create(long actorUserId, long stageId, String name, int sortOrder): long`
- `GroupService.addTeam(long actorUserId, long groupId, long teamId, Integer seedNo): void`
- Public endpoints: `GET /api/v1/competitions`, `GET /api/v1/seasons?competitionId=`, `GET /api/v1/stages?seasonId=`, `GET /api/v1/groups?stageId=`
- Admin endpoints under `/api/v1/admin/competitions`, `/api/v1/admin/stages`, `/api/v1/admin/groups`

- [ ] **Step 1: Write failing hierarchy tests**

Assert:

- An admin creates a league competition, a season under it, a league stage, and a cup competition with group/knockout stages.
- Public hierarchy endpoints return competitions, seasons, stages, and groups.
- A season cannot reference a missing competition.
- A group cannot be created under a non-`GROUP` stage.
- A team can be added only when registered in the season.
- A team cannot appear twice in the same group or in two groups of the same group stage.
- A viewer cannot call admin hierarchy endpoints.
- Existing season APIs still work for legacy callers when `competitionId` is omitted, using the default league competition.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=CompetitionStageIntegrationTest test`

Expected: FAIL because the hierarchy APIs do not exist.

- [ ] **Step 3: Implement the hierarchy slice**

Add mapper CRUD and validation. `SeasonService.createSeason` must accept a competition ID while preserving a default fallback for backward compatibility. Add public read controllers and admin write controllers. Validate stage/group/team relationships in the service layer.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=CompetitionStageIntegrationTest test`

Expected: PASS with all hierarchy and authorization assertions.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition src/main/resources/mappers src/test/java/com/campusfootball/CompetitionStageIntegrationTest.java
git commit -m "feat: add competition stage and group hierarchy"
```

---

### Task 3: Extend Fixtures with Stage, Group, and Penalty Fields

**Files:**
- Modify: `Fixture.java`, `FixtureMapper.java`, `FixtureMapper.xml`, `FixtureService.java`, `FixtureAdminController.java`, `FixtureController.java`, `SeasonMapper.java`, `SeasonMapper.xml`
- Test: `src/test/java/com/campusfootball/FixtureStageIntegrationTest.java`
- Modify: existing fixture test helpers and `TestApiSupport.java`

**Interfaces:**
- `Fixture` adds `stageId`, `groupId`, `knockoutTieId`, `homePenaltyScore`, `awayPenaltyScore`.
- `FixtureService.createFixture(long actorUserId, long seasonId, Long stageId, Long groupId, Integer roundNo, long homeTeamId, long awayTeamId, LocalDateTime kickoffAt, String venue): long`
- Legacy requests without `stageId` resolve the season's default `LEAGUE` stage.
- Admin fixture responses expose stage/group/tie IDs and penalty scores.

- [ ] **Step 1: Write failing fixture validation tests**

Assert:

- A league fixture uses the season's league stage and rejects a group.
- A group fixture requires a group belonging to the selected stage and a participating team in that group.
- A team outside the group cannot be used in a group fixture.
- A knockout fixture must have a tie ID and no group.
- Penalty scores are allowed only for knockout fixtures and must be paired.
- Existing tests that omit `stageId` continue to create league fixtures through fallback.
- Updating a fixture cannot move a fixture with data to a different stage/group.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=FixtureStageIntegrationTest test`

Expected: FAIL because fixture stage/group support is absent.

- [ ] **Step 3: Implement fixture stage/group support**

Extend the fixture entity, mapper, DTOs, create/update paths, and validation. Keep legacy season-only calls working by selecting a single `LEAGUE` stage. Add `homePenaltyScore` and `awayPenaltyScore` to admin responses and update requests.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=FixtureStageIntegrationTest test`

Expected: PASS, including legacy fallback and invalid group combinations.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition src/main/resources/mappers/FixtureMapper.xml src/test/java/com/campusfootball/FixtureStageIntegrationTest.java
git commit -m "feat: attach fixtures to stages and groups"
```

---
### Task 4: Add Group-Aware Standings and Filtered Player Statistics

**Files:**
- Create: `src/main/java/com/campusfootball/stats/StatsFilter.java`
- Modify: `src/main/java/com/campusfootball/stats/StatsMapper.java`
- Modify: `src/main/resources/mappers/StatsMapper.xml`
- Modify: `src/main/java/com/campusfootball/stats/StatsService.java`
- Modify: `src/main/java/com/campusfootball/stats/StatsController.java`
- Test: `src/test/java/com/campusfootball/GroupedStatsIntegrationTest.java`

**Interfaces:**
- `StatsFilter(long competitionId, Long seasonId, Long stageId, Long groupId)`
- `StatsService.standings(StatsFilter filter): List<StandingRow>`
- `StatsService.scorers(StatsFilter filter): List<ScorerRow>`
- `StatsService.assists(StatsFilter filter): List<AssistRow>`
- `StatsService.appearances(StatsFilter filter): List<AppearanceRow>`
- Public endpoints accept `competitionId`, `seasonId`, `stageId`, `groupId`.

- [ ] **Step 1: Write failing group-stat tests**

Assert:

- A league stage still produces one standings table per season.
- A group stage produces one table per group.
- A group's standings exclude matches from another group and from the knockout stage.
- A cup knockout fixture never appears in group standings.
- Group player statistics include only the selected group's finished fixtures.
- A stage-level filter can aggregate group results where requested.
- Switching from group A to group B uses a new query and does not reuse A's rows.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=GroupedStatsIntegrationTest test`

Expected: FAIL because standings and stats are still season-only.

- [ ] **Step 3: Implement filtered aggregates**

Add SQL filters for competition, season, stage, and group. Keep existing season-only behavior by resolving a default stage when exactly one exists. Use `group_team` as the standings base for group stages and `season_team` for league stages. Preserve existing tie-break ordering.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=GroupedStatsIntegrationTest test`

Expected: PASS for league, group, knockout exclusion, and stat filtering.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/stats src/main/resources/mappers/StatsMapper.xml src/test/java/com/campusfootball/GroupedStatsIntegrationTest.java
git commit -m "feat: add group-aware standings and stats"
```

---

### Task 5: Add Knockout Rounds, Ties, Seeds, and Bracket Generation

**Files:**
- Create: `KnockoutRound.java`, `KnockoutTie.java`, `KnockoutSeed.java`, `TieStatus.java`, `BracketSlot.java`
- Create: `KnockoutMapper.java`, `BracketService.java`, `BracketAdminController.java`, `BracketController.java`
- Create: `src/main/resources/mappers/KnockoutMapper.xml`
- Test: `src/test/java/com/campusfootball/BracketSetupIntegrationTest.java`

**Interfaces:**
- `BracketService.createRound(long actorUserId, long stageId, int roundNo, String name, int sortOrder): long`
- `BracketService.createTie(long actorUserId, long stageId, long roundId, int bracketPosition, Long homeTeamId, Long awayTeamId, Long nextTieId, BracketSlot nextSlot, boolean thirdPlace): long`
- `BracketService.saveSeed(long actorUserId, long stageId, long groupId, int sourceRank, long targetTieId, BracketSlot slot): void`
- `BracketService.generateBracket(long actorUserId, long knockoutStageId, long groupStageId, int qualifiersPerGroup, boolean thirdPlace): BracketView`
- `BracketService.createTieFixture(long actorUserId, long tieId, LocalDateTime kickoffAt, String venue): long`
- `BracketService.getBracket(long stageId): BracketView`
- `BracketView` returns rounds in order, ties with teams/scores/winner/status/fixture ID, and champion.

- [ ] **Step 1: Write failing bracket setup tests**

Assert:

- 4, 8, and 16 qualifiers produce rounds named appropriately and a single final.
- A non-power-of-two qualifier count is rejected.
- Seeds map specified group ranks to first-round home/away slots.
- A team cannot occupy two bracket slots.
- Only first-round ties may be seed targets.
- The final tie has no next tie; non-final ties have valid next tie/slot.
- A third-place tie is created only when requested and is excluded from champion progression.
- `createTieFixture` creates exactly one fixture for a ready tie.
- A second fixture for the same tie is rejected.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=BracketSetupIntegrationTest test`

Expected: FAIL because knockout tables and services do not exist.

- [ ] **Step 3: Implement bracket setup**

Implement knockout mapper methods and a deterministic standard bracket generator. The generator reads group standings, selects the configured top N, creates the required rounds/ties, writes seed mappings, and fills first-round slots. Allow admin edits to team slots and next-tie links before the first fixture exists.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=BracketSetupIntegrationTest test`

Expected: PASS for bracket sizes, seeds, graph integrity, third-place handling, and one-fixture-per-tie rules.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition/KnockoutRound.java src/main/java/com/campusfootball/competition/KnockoutTie.java src/main/java/com/campusfootball/competition/KnockoutSeed.java src/main/java/com/campusfootball/competition/TieStatus.java src/main/java/com/campusfootball/competition/BracketSlot.java src/main/java/com/campusfootball/competition/KnockoutMapper.java src/main/java/com/campusfootball/competition/BracketService.java src/main/java/com/campusfootball/competition/BracketAdminController.java src/main/java/com/campusfootball/competition/BracketController.java src/main/resources/mappers/KnockoutMapper.xml src/test/java/com/campusfootball/BracketSetupIntegrationTest.java
git commit -m "feat: add knockout bracket setup"
```

---

### Task 6: Finish Knockout Ties and Advance Winners Safely

**Files:**
- Modify: `FixtureService.java`
- Modify: `FixtureAdminController.java`
- Modify: `Fixture.java`, `FixtureMapper.java`, `FixtureMapper.xml`
- Modify: `BracketService.java`
- Modify: `BracketController.java`
- Test: `src/test/java/com/campusfootball/BracketAdvancementIntegrationTest.java`

**Interfaces:**
- Finish request adds optional `homePenaltyScore` and `awayPenaltyScore`.
- `BracketService.finishTie(long actorUserId, long tieId, int homeScore, int awayScore, Integer homePenaltyScore, Integer awayPenaltyScore): long`
- `BracketService.resolveBracket(long stageId): BracketView`
- The existing finish endpoint delegates to bracket advancement when `fixture.knockout_tie_id` is present.
- Winner propagation uses `winner_team_id`, `next_tie_id`, and `next_slot`.

- [ ] **Step 1: Write failing advancement tests**

Assert:

- A decisive knockout result sets the winner and fills the next tie slot.
- A draw without penalties is rejected.
- A draw with equal penalties is rejected.
- A draw with different penalties produces the correct winner.
- Filling the second side of the next tie changes its status to `READY`.
- A final tie winner becomes the bracket champion.
- Trying to change an upstream winner after the downstream tie has a fixture is rejected.
- Trying to change an upstream winner after the downstream tie is finished is rejected.
- Third-place results do not alter the final tree.
- Bracket API returns rounds, ties, scores, penalty scores, winner, and champion.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=BracketAdvancementIntegrationTest test`

Expected: FAIL because knockout finish and advancement are not implemented.

- [ ] **Step 3: Implement winner resolution and propagation**

Add penalty validation, winner resolution, downstream occupancy checks, next-slot propagation, tie status transitions, and champion resolution. Update the finish endpoint and bracket response. Reject edits that would mutate an already materialized downstream path.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=BracketAdvancementIntegrationTest test`

Expected: PASS for winner propagation, penalty rules, downstream guards, third place, and champion resolution.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition src/main/resources/mappers/FixtureMapper.xml src/test/java/com/campusfootball/BracketAdvancementIntegrationTest.java
git commit -m "feat: advance knockout winners and resolve brackets"
```

---
### Task 7: Add Mini Program Competition, Season, Stage, and Group Selection

**Files:**
- Create: `miniprogram/state/competition.ts`
- Create: `miniprogram/state/selection.ts`
- Create: `miniprogram/services/competition-hierarchy.ts`
- Create: `miniprogram/components/competition-picker/index.*`
- Create: `miniprogram/components/stage-picker/index.*`
- Create: `miniprogram/components/group-picker/index.*`
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/services/competition.ts`
- Modify: `miniprogram/services/stats.ts`
- Modify: `miniprogram/pages/home/index.*`
- Modify: `miniprogram/pages/fixtures/index.*`
- Modify: `miniprogram/pages/stats/index.*`
- Test: `miniprogram/tests/hierarchy-selection.test.ts`

**Interfaces:**
- `fetchCompetitions(): Promise<Competition[]>`
- `fetchSeasonsByCompetition(competitionId: number): Promise<Season[]>`
- `fetchStages(seasonId: number): Promise<Stage[]>`
- `fetchGroups(stageId: number): Promise<CompetitionGroup[]>`
- `SelectionState` stores `competitionId`, `seasonId`, `stageId`, `groupId`.
- `selectCompetition(id)` resets season/stage/group.
- `selectSeason(id)` resets stage/group.
- `selectStage(id)` resets group and hides group picker for non-group stages.
- Home, fixtures, and stats read the same selection store.

- [ ] **Step 1: Write failing selection tests**

Assert:

- Switching competition clears stale child selections.
- Switching season clears stage/group selections.
- Switching from a group stage to a league stage clears `groupId`.
- Selecting a valid child persists only IDs valid for the new parent.
- A missing stored ID is replaced by the first available valid child.
- No page can request a season from a different competition.
- Fixtures and stats build queries with competition/season/stage/group parameters.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/hierarchy-selection.test.ts`

Expected: FAIL because the hierarchy state and selectors are absent.

- [ ] **Step 3: Implement the hierarchy state and selectors**

Add shared pickers and update the service request parameters. For a single-stage season, select the stage automatically. Only render the group picker for `GROUP` stages. Add loading/empty/error handling for each hierarchy level.

- [ ] **Step 4: Run checks to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify competition/stage/group changes in DevTools with both league and cup fixtures.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add competition hierarchy selectors"
```

---

### Task 8: Add the Public Knockout Bracket View

**Files:**
- Create: `miniprogram/components/bracket-round/index.*`
- Create: `miniprogram/components/bracket-tie/index.*`
- Create: `miniprogram/pages/bracket/index.*`
- Modify: `miniprogram/services/competition.ts`
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/bracket.test.ts`

**Interfaces:**
- `fetchBracket(stageId: number): Promise<BracketView>`
- `BracketView` contains rounds, ties, scores, penalty scores, winners, and champion.
- Bracket page is reachable from cup stages and the fixtures page.

- [ ] **Step 1: Write failing bracket mapping tests**

Assert:

- Bracket rounds preserve API order.
- Tie cards map home/away teams and nullable teams safely.
- Penalty scores render without replacing regular-time scores.
- Winner highlighting uses `winnerTeamId`.
- A third-place tie is rendered separately.
- Final champion is displayed when the final tie is finished.
- A non-knockout stage returns a clear validation error.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/bracket.test.ts`

Expected: FAIL because bracket services and components are absent.

- [ ] **Step 3: Implement the bracket page**

Build a horizontally scrollable round-column layout. Each tie card shows both teams, regular score, penalty score, status, and winner. Render the final tie distinctly and show a champion banner when resolved. Add a separate optional third-place section.

- [ ] **Step 4: Run checks to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify the bracket on narrow screens with 4, 8, and 16 team fixtures.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add public knockout bracket view"
```

---

### Task 9: Add Admin Competition, Stage, and Group Management

**Files:**
- Create: `miniprogram/pages/admin-competition/index.*`
- Create: `miniprogram/pages/admin-stage/index.*`
- Create: `miniprogram/pages/admin-group/index.*`
- Modify: `miniprogram/services/competition-hierarchy.ts`
- Modify: `miniprogram/pages/manage/index.*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/admin-hierarchy.test.ts`

**Interfaces:**
- Admin service methods for creating competitions, seasons, stages, groups, and assigning group teams.
- Admin pages are visible only to `ADMIN`.
- Competition type and stage type pickers use the exact enum values.
- Group team assignment uses teams registered to the season.

- [ ] **Step 1: Write failing admin hierarchy tests**

Assert:

- Creating a competition sends `name` and `type`.
- Season creation requires a selected competition.
- A group cannot be created unless the selected stage is `GROUP`.
- Adding a team to a group sends the exact group/team path.
- A viewer/admin gate rejects a viewer before any API call.
- Switching competition resets the admin season list.
- A `409` conflict is preserved for the administrator.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/admin-hierarchy.test.ts`

Expected: FAIL because the admin hierarchy services and pages are absent.

- [ ] **Step 3: Implement admin hierarchy pages**

Build forms for competition/season/stage/group creation, group team assignment and removal, and stage ordering. Reuse the existing submit lock and validation utilities. Add management-home entries for all three pages.

- [ ] **Step 4: Run checks to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify the administrator can build a league hierarchy and a cup group hierarchy in DevTools.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add admin competition hierarchy pages"
```

---
### Task 10: Add Admin Bracket, Seed, and Knockout Result Management

**Files:**
- Create: `miniprogram/pages/admin-bracket/index.*`
- Create: `miniprogram/services/bracket.ts`
- Modify: `miniprogram/pages/manage/index.*`
- Modify: `miniprogram/pages/admin-fixtures/index.*`
- Modify: `miniprogram/pages/manage-fixture-data/index.*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/admin-bracket.test.ts`

**Interfaces:**
- `generateBracket(knockoutStageId, groupStageId, qualifiersPerGroup, thirdPlace): Promise<BracketView>`
- `createTieFixture(tieId, kickoffAt, venue): Promise<IdResponse>`
- `saveSeed(stageId, groupId, sourceRank, targetTieId, slot): Promise<void>`
- Admin bracket page previews rounds/ties and exposes generate/edit/start/finish actions.
- Knockout finish form includes regular-time and penalty scores.

- [ ] **Step 1: Write failing admin-bracket tests**

Assert:

- Generate bracket sends the selected group stage, qualifiers-per-group, and third-place flag.
- A non-power-of-two preview is blocked before the API call.
- Creating a tie fixture sends the tie ID and schedule.
- A knockout finish with level regular-time scores requires penalty scores.
- A finish with penalties sends both penalty fields.
- A downstream tie with an existing fixture shows a locked/error state instead of allowing an upstream overwrite.
- A viewer cannot render admin bracket controls.
- Switching knockout rounds does not reuse the previous round's ties.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/admin-bracket.test.ts`

Expected: FAIL because bracket admin services and controls are absent.

- [ ] **Step 3: Implement bracket administration**

Add a bracket setup preview, seed configuration, first-round generation, tie fixture creation, and finish controls. Reuse the existing fixture finish dialog with optional penalty fields. Show clear errors for invalid bracket sizes, missing qualifiers, downstream locks, and non-knockout stages. Add management-home entries for cup bracket management.

- [ ] **Step 4: Run checks to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify a full cup flow in DevTools: groups -> seeds -> bracket -> fixtures -> penalties -> final champion.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add admin bracket and knockout controls"
```

---

### Task 11: Add Cross-Stack Acceptance and Migration Documentation

**Files:**
- Create: `src/test/java/com/campusfootball/MultiCompetitionAcceptanceIntegrationTest.java`
- Create: `miniprogram/tests/multi-competition-acceptance.test.ts`
- Create: `docs/acceptance/multi-competition-cup-acceptance.md`
- Modify: root `README.md`
- Modify: `miniprogram/README.md`
- Modify: `docs/superpowers/specs/2026-10-27-multi-competition-cup-design.md` only if implementation clarifies a rule

**Interfaces:**
- Produces a repeatable backend acceptance flow and frontend acceptance flow.
- Documents migration, admin setup, public navigation, bracket behavior, and operational caveats.

- [ ] **Step 1: Write the failing cross-stack acceptance tests**

Backend acceptance test:

1. Create a league competition and season.
2. Create a cup competition and season.
3. Add teams and create A/B groups.
4. Create group fixtures and finish them.
5. Generate a 4-team knockout bracket from the top two teams in each group.
6. Create semifinal fixtures, finish them, and verify winners advance.
7. Finish the final with a draw and penalties.
8. Verify the champion, bracket response, group standings, and filtered stats.
9. Verify the default legacy league remains queryable.

Frontend acceptance test must simulate:

- switching from league to cup;
- selecting a group stage and group;
- loading the knockout bracket;
- showing the champion;
- preventing admin bracket generation with an invalid qualifier count.

- [ ] **Step 2: Run the acceptance tests to verify they fail**

Run:

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
.\mvnw.cmd -Dtest=MultiCompetitionAcceptanceIntegrationTest test
cd miniprogram
npm test -- tests/multi-competition-acceptance.test.ts
```

Expected: FAIL until the final wiring and documented flow are complete.

- [ ] **Step 3: Implement only real integration fixes and documentation**

Resolve mismatched DTOs, route names, stage defaults, champion mapping, penalty payloads, and UI selection state. Document the migration default competition/stage, admin setup order, cup bracket generation, and public navigation.

- [ ] **Step 4: Run full verification**

Run:

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
.\mvnw.cmd verify
cd miniprogram
npm run verify
```

Expected: PASS for all backend and frontend tests, including migration and cup acceptance.

- [ ] **Step 5: Commit**

```powershell
git add README.md miniprogram/README.md docs/acceptance src/test/java/com/campusfootball/MultiCompetitionAcceptanceIntegrationTest.java miniprogram/tests/multi-competition-acceptance.test.ts
git commit -m "test: add multi-competition and cup acceptance"
```

---

## Spec Coverage Check

| Spec area | Covered by |
|---|---|
| V2 migration and legacy data preservation | Tasks 1, 11 |
| Competition/season/stage/group hierarchy | Task 2 |
| Fixture stage/group and penalties | Task 3 |
| Group standings and filtered stats | Task 4 |
| Knockout rounds, ties, seeds, generation | Task 5 |
| Winner advancement and final tree | Task 6 |
| Competition/stage/group selectors | Task 7 |
| Public bracket UI | Task 8 |
| Admin hierarchy management | Task 9 |
| Admin bracket and knockout controls | Task 10 |
| Full cup acceptance | Task 11 |
| Migration failure mode | Tasks 1, 11 |
| Cross-group fixture validation | Tasks 3, 4 |
| Downstream advancement guard | Tasks 6, 10 |
| Selector reset consistency | Tasks 7, 11 |

## Execution Handoff

The plan is complete after self-review. Implementation must not begin until the human partner approves this plan and chooses native or subagent-driven execution.
