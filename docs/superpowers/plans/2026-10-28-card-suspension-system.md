# 红黄牌禁赛系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 根据已完赛比赛的红黄牌自动生成、执行和调整球员停赛，并在阵容端强制拦截停赛球员。

**Architecture:** 新增停赛记录与执行记录两张表。`SuspensionService` 负责规则重算、阵容资格检查和完赛执行；比赛事件、完赛流程、阵容服务调用该服务；小程序显示停赛并提供管理员调整界面。

**Tech Stack:** Spring Boot、MyBatis、MySQL/Flyway、JUnit、原生微信小程序 TypeScript、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-card-suspension-system-design.md`

## Global Constraints

- 黄牌累计阈值为 3，红牌停赛 1 场。
- 仅同一赛季和已完赛比赛计入。
- 同一场只执行一项停赛。
- 已执行自动停赛的来源事件不可修改或删除。
- 后端必须强制校验停赛，不能只靠前端。
- 管理员和授权管理员可调整，领队只能查看本队。
- 测试使用独立测试数据库。

## Review Focus

- 第 3、6、9 张黄牌都要各自生成一场停赛，不能只生成一次。
- 重算和重复完赛不能重复扣减停赛场次。
- 停赛来源比赛自身不能消耗该停赛。
- 停赛球员无法通过单条或批量阵容接口绕过。
- 领队无法查看其他球队或调整任何停赛。

---

### Task 1: 数据库与后端模型

**Files:**
- Create: `src/main/resources/db/migration/V7__add_player_suspensions.sql`
- Create: `src/main/java/com/campusfootball/competition/PlayerSuspension.java`
- Create: `src/main/java/com/campusfootball/competition/SuspensionStatus.java`
- Create: `src/main/java/com/campusfootball/competition/SuspensionSourceType.java`
- Create: `src/main/java/com/campusfootball/competition/SuspensionMapper.java`
- Create: `src/main/resources/mappers/SuspensionMapper.xml`
- Test: `src/test/java/com/campusfootball/SchemaMigrationTest.java`

- [ ] Add V7 tables and constraints.
- [ ] Add domain enums/model and mapper XML.
- [ ] Verify migration and mapper queries through schema tests.
- [ ] Commit `feat: add suspension data model`.

### Task 2: 自动生成与完赛执行

**Files:**
- Create: `src/main/java/com/campusfootball/competition/SuspensionService.java`
- Modify: `src/main/java/com/campusfootball/competition/FixtureService.java`
- Modify: `src/main/java/com/campusfootball/competition/EventService.java`
- Test: `src/test/java/com/campusfootball/SuspensionIntegrationTest.java`

**Interfaces:**
- `SuspensionService.recalculateSeason(long seasonId): void`
- `SuspensionService.serveAfterFinish(Fixture fixture): void`
- `SuspensionService.assertCardEventMutable(long eventId): void`

- [ ] Add tests for third/sixth yellow, red card, idempotent recalc, serving, and served-source protection.
- [ ] Implement chronological card scan and automatic suspension reconciliation.
- [ ] Trigger recalc on finished event mutations and fixture finish.
- [ ] Implement serving records and one-suspension-at-a-time execution.
- [ ] Run focused tests.
- [ ] Commit `feat: automate card suspensions`.

### Task 3: 阵容与事件资格拦截

**Files:**
- Modify: `src/main/java/com/campusfootball/competition/AppearanceService.java`
- Modify: `src/main/java/com/campusfootball/competition/EventService.java`
- Test: `src/test/java/com/campusfootball/AppearanceIntegrationTest.java`
- Test: `src/test/java/com/campusfootball/EventIntegrationTest.java`

**Interfaces:**
- `SuspensionService.assertPlayersEligible(long fixtureId, long teamId, Collection<Long> playerIds): void`
- `SuspensionService.activeForFixture(long fixtureId): List<SuspensionView>`

- [ ] Add tests rejecting suspended players in lineup and events.
- [ ] Implement eligibility checks for single and bulk appearance writes.
- [ ] Implement event eligibility checks.
- [ ] Run focused tests.
- [ ] Commit `feat: block suspended players from match data`.

### Task 4: 管理 API

**Files:**
- Create: `src/main/java/com/campusfootball/competition/SuspensionManagementController.java`
- Modify: `src/main/java/com/campusfootball/competition/SuspensionService.java`
- Test: `src/test/java/com/campusfootball/SuspensionIntegrationTest.java`

**Interfaces:**
- `GET /api/v1/manage/teams/{teamId}/suspensions`
- `GET /api/v1/manage/fixtures/{fixtureId}/suspensions`
- `POST /api/v1/manage/suspensions`
- `PUT /api/v1/manage/suspensions/{id}`

- [ ] Add tests for team/fixture listing, manual create/extend/cancel, and role restrictions.
- [ ] Implement team-scoped reads and management-only mutations.
- [ ] Audit manual create/update operations.
- [ ] Run focused tests.
- [ ] Commit `feat: expose suspension management APIs`.

### Task 5: 小程序服务与停赛展示

**Files:**
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/services/manage.ts`
- Modify: `miniprogram/pages/manage-team/index.ts`
- Modify: `miniprogram/pages/manage-team/index.wxml`
- Modify: `miniprogram/pages/manage-team/index.wxss`
- Test: `miniprogram/tests/teams.test.ts`
- Test: `miniprogram/tests/fixture-management.test.ts`

- [ ] Add API types and service helpers.
- [ ] Load current-season suspensions in team management.
- [ ] Show suspension badges on player rows.
- [ ] Add manager-only create/extend/cancel controls.
- [ ] Add request payload tests.
- [ ] Run frontend verify.
- [ ] Commit `feat: show and manage player suspensions`.

### Task 6: 阵容页面停赛拦截

**Files:**
- Modify: `miniprogram/pages/manage-fixture-data/index.ts`
- Modify: `miniprogram/pages/manage-fixture-data/index.wxml`
- Modify: `miniprogram/pages/manage-fixture-data/index.wxss`
- Test: `miniprogram/tests/fixture-management.test.ts`

- [ ] Fetch fixture suspension list with fixture detail.
- [ ] Mark suspended lineup players unavailable.
- [ ] Display “停赛 X 场” and block taps.
- [ ] Show current fixture suspension summary.
- [ ] Run frontend verify.
- [ ] Commit `feat: block suspended players in lineup UI`.

### Task 7: Full verification and deployment

**Files:**
- Modify: `README.md`
- Modify: `docs/acceptance/wechat-miniprogram-acceptance.md`

- [ ] Run backend full tests.
- [ ] Run mini-program full verify.
- [ ] Build JAR.
- [ ] Back up production database and deploy.
- [ ] Verify Flyway V7 and production API.
- [ ] Commit documentation.
