# 三级管理员权限 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有权限体系升级为超级管理员、授权管理员、领队三级管理角色，并保持审计和评分审核仅超级管理员可用。

**Architecture:** 在 `app_user.role_code` 保存用户最高全局角色，在 `team_manager.role_code` 保存每条球队授权类型。`ADMIN` 和 `TEAM_MANAGER` 走全局管理授权；`TEAM_LEADER` 只能通过球队归属校验访问本队人员与出场接口。小程序按角色显示不同的管理入口和比赛数据编辑范围。

**Tech Stack:** Spring Boot 3.4、Spring Security、MyBatis、MySQL/Flyway、JUnit 5、原生微信小程序 TypeScript、TDesign、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-28-authorized-admin-permissions-design.md`

## Global Constraints

- 角色顺序固定为 `ADMIN > TEAM_MANAGER > TEAM_LEADER > VIEWER`。
- `ADMIN` 是唯一可读取审计日志、审核评分和管理敏感词的角色。
- `TEAM_MANAGER` 可管理全部球队和赛事，但不能读取审计、审核评分或管理敏感词。
- `TEAM_LEADER` 只能编辑被授权球队的资料、球员和本队出场；不能编辑球队状态、事件、比分、赛程或淘汰赛。
- 领队可维护 `SCHEDULED`、`LIVE`、未锁定 `FINISHED` 比赛的本队阵容。
- 所有权限最终由后端判断，前端只负责入口展示。
- 不把 AppSecret、数据库密码或生产 OpenID 写入仓库。
- 测试数据库使用 `campus_football_test`，不得连接日常数据库。

## Review Focus

- 用户拥有多条不同类型的球队授权时，全局角色必须取最高授权，不能因撤销一条记录误降级。
- 领队请求其他球队的球员或阵容接口时必须返回 `403`，不能只靠前端隐藏。
- 授权管理员请求 `/api/v1/admin/ratings/**`、`/api/v1/admin/sensitive-terms/**` 和审计接口时必须返回 `403`。
- 领队在赛前 `SCHEDULED` 比赛可以保存阵容，但不能写入比赛事件。
- 角色变化后，小程序重新进入管理页必须刷新角色和球队范围，不能沿用旧会话。

---

### Task 1: 数据库角色与共享类型

**Files:**
- Create: `src/main/resources/db/migration/V6__add_team_leader_role.sql`
- Modify: `src/main/java/com/campusfootball/user/UserRole.java`
- Modify: `src/main/java/com/campusfootball/team/TeamManagerMapper.java`
- Modify: `src/main/resources/mappers/TeamManagerMapper.xml`
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/utils/status.ts`
- Test: `src/test/java/com/campusfootball/SchemaMigrationTest.java`
- Test: `miniprogram/tests/role.test.ts`

**Interfaces:**
- Produces: enum `UserRole.TEAM_LEADER`.
- Produces: `team_manager.role_code` values `TEAM_MANAGER|TEAM_LEADER`.
- Produces: frontend `UserRole = "ADMIN" | "TEAM_MANAGER" | "TEAM_LEADER" | "VIEWER"`.
- Produces: role label `TEAM_LEADER -> 领队`, `TEAM_MANAGER -> 授权管理员`.

- [ ] **Step 1: Write migration assertions**

Extend `SchemaMigrationTest` to verify `app_user.role_code` accepts `TEAM_LEADER`, `team_manager.role_code` exists, defaults to `TEAM_MANAGER`, and rejects an invalid assignment role.

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `.\mvnw.cmd -q -Dtest=SchemaMigrationTest test`  
Expected: FAIL because `TEAM_LEADER` and `team_manager.role_code` do not exist.

- [ ] **Step 3: Add V6 migration**

Create `V6__add_team_leader_role.sql` to drop/recreate the user-role check and add `team_manager.role_code` with its check constraint. Existing rows must become `TEAM_MANAGER`.

- [ ] **Step 4: Update enums and frontend types**

Add `TEAM_LEADER` to `UserRole`, the mini-program union, and `ROLE_LABELS`. Update assignment Mapper methods to accept and return `role_code`.

- [ ] **Step 5: Run focused tests**

Run: `.\mvnw.cmd -q -Dtest=SchemaMigrationTest test`  
Run: `npm --prefix miniprogram run typecheck`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V6__add_team_leader_role.sql src/main/java/com/campusfootball/user/UserRole.java src/main/java/com/campusfootball/team/TeamManagerMapper.java src/main/resources/mappers/TeamManagerMapper.xml miniprogram/types/domain.ts miniprogram/utils/status.ts src/test/java/com/campusfootball/SchemaMigrationTest.java miniprogram/tests/role.test.ts
git commit -m "feat: add team leader role model"
```

### Task 2: Backend authorization primitives and route matrix

**Files:**
- Modify: `src/main/java/com/campusfootball/team/AuthorizationService.java`
- Modify: `src/main/java/com/campusfootball/config/SecurityConfig.java`
- Modify: `src/main/java/com/campusfootball/audit/AuditQueryService.java`
- Test: `src/test/java/com/campusfootball/SecurityIntegrationTest.java`

**Interfaces:**
- Produces: `requireManagementAccess(long userId): void`.
- Produces: `isManagementAdmin(long userId): boolean`.
- Produces: `isTeamLeader(long userId): boolean`.
- Produces: `requireCanManageTeam` accepts `TEAM_LEADER` only with a matching assignment.

- [ ] **Step 1: Write authorization route tests**

Add tests proving `TEAM_MANAGER` can call `/api/v1/admin/teams`, `TEAM_LEADER` cannot call `/api/v1/admin/teams`, both non-admin roles receive `403` on audit/rating/sensitive-term endpoints, and `VIEWER` is denied management APIs.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\mvnw.cmd -q -Dtest=SecurityIntegrationTest test`  
Expected: FAIL on the new matrix.

- [ ] **Step 3: Implement authorization methods and matcher order**

Add the authorization service methods and update `SecurityConfig` so admin-only rating, sensitive-term, and audit matchers precede general admin/management matchers. Make audit service ADMIN-only.

- [ ] **Step 4: Re-run the focused test**

Run: `.\mvnw.cmd -q -Dtest=SecurityIntegrationTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/campusfootball/team/AuthorizationService.java src/main/java/com/campusfootball/config/SecurityConfig.java src/main/java/com/campusfootball/audit/AuditQueryService.java src/test/java/com/campusfootball/SecurityIntegrationTest.java
git commit -m "feat: add tiered management authorization"
```

### Task 3: Team assignment roles and authorization API

**Files:**
- Modify: `src/main/java/com/campusfootball/team/TeamManagerService.java`
- Modify: `src/main/java/com/campusfootball/team/AdminTeamController.java`
- Modify: `src/main/java/com/campusfootball/team/TeamManagerMapper.java`
- Modify: `src/main/resources/mappers/TeamManagerMapper.xml`
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/services/manage.ts`
- Modify: `miniprogram/pages/admin-access/index.ts`
- Modify: `miniprogram/pages/admin-access/index.wxml`
- Test: `src/test/java/com/campusfootball/TeamManagerGrantIntegrationTest.java`
- Test: `miniprogram/tests/admin.test.ts`

**Interfaces:**
- Produces: `TeamManagerService.grant(actorUserId, teamId, targetUserId, UserRole assignmentRole)`.
- Produces: `GET /api/v1/admin/teams/{teamId}/managers` returning `{ userId, role }[]`.
- Produces: `POST /api/v1/admin/teams/{teamId}/managers` accepting `{ userId, role }`.
- Produces: global user role recomputed as the highest remaining assignment after revoke.

- [ ] **Step 1: Write backend assignment tests**

Cover granting a leader, granting an authorized admin, upgrading a leader to authorized admin, rejecting `ADMIN`/`VIEWER` assignment roles, and reverting to `TEAM_LEADER` or `VIEWER` after revoke.

- [ ] **Step 2: Run backend tests and confirm failure**

Run: `.\mvnw.cmd -q -Dtest=TeamManagerGrantIntegrationTest test`  
Expected: FAIL because assignment roles are not supported.

- [ ] **Step 3: Implement assignment role storage and recomputation**

Update mapper queries, grant/revoke service logic, controller requests/responses, and audit snapshots. Only `ADMIN` and `TEAM_MANAGER` may grant or revoke.

- [ ] **Step 4: Update authorization page**

Add a role selector with “授权管理员” and “领队”, render each assignment role, and send it in the grant request. Page access requires `ADMIN` or `TEAM_MANAGER`.

- [ ] **Step 5: Run focused tests**

Run: `.\mvnw.cmd -q -Dtest=TeamManagerGrantIntegrationTest test`  
Run: `npm --prefix miniprogram run verify`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/campusfootball/team/TeamManagerService.java src/main/java/com/campusfootball/team/AdminTeamController.java src/main/java/com/campusfootball/team/TeamManagerMapper.java src/main/resources/mappers/TeamManagerMapper.xml miniprogram/types/domain.ts miniprogram/services/manage.ts miniprogram/pages/admin-access/index.ts miniprogram/pages/admin-access/index.wxml src/test/java/com/campusfootball/TeamManagerGrantIntegrationTest.java miniprogram/tests/admin.test.ts
git commit -m "feat: support team manager and leader assignments"
```

### Task 4: Authorized-admin global management

**Files:**
- Modify: `src/main/java/com/campusfootball/team/TeamService.java`
- Modify: `src/main/java/com/campusfootball/competition/CompetitionService.java`
- Modify: `src/main/java/com/campusfootball/competition/SeasonService.java`
- Modify: `src/main/java/com/campusfootball/competition/StageService.java`
- Modify: `src/main/java/com/campusfootball/competition/GroupService.java`
- Modify: `src/main/java/com/campusfootball/competition/FixtureService.java`
- Modify: `src/main/java/com/campusfootball/competition/BracketService.java`
- Test: `src/test/java/com/campusfootball/AuthorizedAdminIntegrationTest.java`

**Interfaces:**
- Consumes: `requireManagementAccess`.
- Produces: `TEAM_MANAGER` may perform every global management operation except audit and rating moderation.

- [ ] **Step 1: Write global-management tests**

Test `TEAM_MANAGER` creating/updating a team, creating a competition/season/stage/group, creating a fixture, updating score/status/lock, and managing a bracket.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\mvnw.cmd -q -Dtest=AuthorizedAdminIntegrationTest test`  
Expected: FAIL because services still call `requireAdmin`.

- [ ] **Step 3: Replace global management guards**

Replace `requireAdmin` with `requireManagementAccess` in the listed services. Do not change audit, rating moderation, or sensitive-term guards.

- [ ] **Step 4: Re-run focused and permission tests**

Run: `.\mvnw.cmd -q -Dtest=AuthorizedAdminIntegrationTest,SecurityIntegrationTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/campusfootball/team/TeamService.java src/main/java/com/campusfootball/competition/CompetitionService.java src/main/java/com/campusfootball/competition/SeasonService.java src/main/java/com/campusfootball/competition/StageService.java src/main/java/com/campusfootball/competition/GroupService.java src/main/java/com/campusfootball/competition/FixtureService.java src/main/java/com/campusfootball/competition/BracketService.java src/test/java/com/campusfootball/AuthorizedAdminIntegrationTest.java
git commit -m "feat: grant authorized admins global management"
```

### Task 5: Team-leader scoped team and lineup access

**Files:**
- Modify: `src/main/java/com/campusfootball/team/TeamService.java`
- Modify: `src/main/java/com/campusfootball/competition/AppearanceService.java`
- Modify: `src/main/java/com/campusfootball/competition/EventService.java`
- Test: `src/test/java/com/campusfootball/TeamLeaderIntegrationTest.java`

**Interfaces:**
- Produces: `TEAM_LEADER` can edit assigned team profile and players but cannot change team status.
- Produces: `TEAM_LEADER` can manage appearances for assigned team in scheduled/live/unlocked finished fixtures.
- Produces: `TEAM_LEADER` receives `403` for event writes and other-team appearances.

- [ ] **Step 1: Write leader boundary tests**

Cover own-team profile/player update, rejected team status change, own-team scheduled lineup save, rejected opponent lineup save, event write rejection, and locked-fixture rejection.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\mvnw.cmd -q -Dtest=TeamLeaderIntegrationTest test`  
Expected: FAIL because leader role is not supported.

- [ ] **Step 3: Implement leader rules**

Update team status authorization, appearance write windows and team ownership checks, and explicit event-write rejection for `TEAM_LEADER`.

- [ ] **Step 4: Run focused tests**

Run: `.\mvnw.cmd -q -Dtest=TeamLeaderIntegrationTest,AppearanceIntegrationTest,EventIntegrationTest test`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/campusfootball/team/TeamService.java src/main/java/com/campusfootball/competition/AppearanceService.java src/main/java/com/campusfootball/competition/EventService.java src/test/java/com/campusfootball/TeamLeaderIntegrationTest.java
git commit -m "feat: add team leader scoped permissions"
```

### Task 6: Mini-program role helpers and management navigation

**Files:**
- Modify: `miniprogram/types/domain.ts`
- Modify: `miniprogram/state/session.ts`
- Modify: `miniprogram/pages/profile/index.ts`
- Modify: `miniprogram/pages/manage/index.ts`
- Modify: `miniprogram/pages/manage/index.wxml`
- Modify: `miniprogram/pages/admin-season/index.ts`
- Modify: `miniprogram/pages/admin-fixtures/index.ts`
- Modify: `miniprogram/pages/admin-access/index.ts`
- Modify: `miniprogram/pages/admin-competition/index.ts`
- Modify: `miniprogram/pages/admin-bracket/index.ts`
- Modify: `miniprogram/pages/admin-audit/index.ts`
- Test: `miniprogram/tests/session.test.ts`
- Test: `miniprogram/tests/role.test.ts`
- Test: `miniprogram/tests/project-config.test.ts`

**Interfaces:**
- Produces: `isManagementAdmin(): boolean`.
- Produces: `isTeamLeader(): boolean`.
- Produces: management roles `ADMIN`, `TEAM_MANAGER`, `TEAM_LEADER`.
- Produces: admin-only navigation remains limited to `ADMIN`.

- [ ] **Step 1: Write frontend gate tests**

Test that authorized admins receive all non-admin-only entries, leaders receive only team and lineup entries, admins receive all entries, and viewers receive none. Test role labels.

- [ ] **Step 2: Run tests and confirm failure**

Run: `npm --prefix miniprogram run test`  
Expected: FAIL on the new role gates.

- [ ] **Step 3: Implement helpers and page gates**

Add role helpers, profile management entry, management-home variations, and update admin page checks so only audit and rating remain ADMIN-only.

- [ ] **Step 4: Run frontend verification**

Run: `npm --prefix miniprogram run verify`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add miniprogram/types/domain.ts miniprogram/state/session.ts miniprogram/pages/profile/index.ts miniprogram/pages/manage/index.ts miniprogram/pages/manage/index.wxml miniprogram/pages/admin-season/index.ts miniprogram/pages/admin-fixtures/index.ts miniprogram/pages/admin-access/index.ts miniprogram/pages/admin-competition/index.ts miniprogram/pages/admin-bracket/index.ts miniprogram/pages/admin-audit/index.ts miniprogram/tests/session.test.ts miniprogram/tests/role.test.ts miniprogram/tests/project-config.test.ts
git commit -m "feat: add tiered mini program navigation"
```

### Task 7: Team-leader team and fixture-data pages

**Files:**
- Modify: `miniprogram/pages/manage-team/index.ts`
- Modify: `miniprogram/pages/manage-team/index.wxml`
- Modify: `miniprogram/pages/manage-fixture-data/index.ts`
- Modify: `miniprogram/pages/manage-fixture-data/index.wxml`
- Test: `miniprogram/tests/fixture-management.test.ts`
- Test: `miniprogram/tests/teams.test.ts`

**Interfaces:**
- Consumes: role helpers from Task 6.
- Produces: leader team page uses scoped management APIs and hides team status.
- Produces: leader fixture page loads assigned-team fixtures and hides event forms/actions while retaining lineup editing.

- [ ] **Step 1: Write page behavior tests**

Test the role-to-data-source mapper, leader event-edit capability false, authorized-admin event-edit capability true, and leader lineup capability for scheduled fixtures.

- [ ] **Step 2: Run tests and confirm failure**

Run: `npm --prefix miniprogram run test`  
Expected: FAIL on leader page behavior.

- [ ] **Step 3: Implement team page changes**

Allow leaders to access assigned teams, use managed player APIs, and hide team creation/status controls.

- [ ] **Step 4: Implement fixture-data page changes**

Add `canWriteEvents` and role-aware loading. Leaders load assigned fixtures, show only their team's lineup editor, and render events as read-only.

- [ ] **Step 5: Run verification**

Run: `npm --prefix miniprogram run verify`  
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add miniprogram/pages/manage-team/index.ts miniprogram/pages/manage-team/index.wxml miniprogram/pages/manage-fixture-data/index.ts miniprogram/pages/manage-fixture-data/index.wxml miniprogram/tests/fixture-management.test.ts miniprogram/tests/teams.test.ts
git commit -m "feat: add team leader management pages"
```

### Task 8: Full verification and production rollout

**Files:**
- Modify: `README.md`
- Modify: `docs/acceptance/wechat-miniprogram-acceptance.md`
- Modify: `docs/deployment/aliyun.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: tested JAR and updated deployment package.

- [ ] **Step 1: Run full backend verification**

Run: `$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'; $env:TEST_DB_USERNAME='campus'; $env:TEST_DB_PASSWORD='campus'; .\mvnw.cmd -q verify`  
Expected: PASS, zero failures.

- [ ] **Step 2: Run full mini-program verification**

Run: `npm --prefix miniprogram run verify`  
Expected: PASS, zero failures.

- [ ] **Step 3: Build the production JAR**

Run: `.\mvnw.cmd -q -DskipTests package`  
Expected: `target/campus-football-api-0.0.1-SNAPSHOT.jar` updated.

- [ ] **Step 4: Update role documentation**

Document the three management tiers and the fact that authorized admins can grant both roles while leaders cannot grant access.

- [ ] **Step 5: Commit**

```bash
git add README.md docs/acceptance/wechat-miniprogram-acceptance.md docs/deployment/aliyun.md
git commit -m "docs: document tiered management roles"
```

- [ ] **Step 6: Deploy and smoke-test**

Upload the JAR and updated deployment files, run `docker compose up -d --build`, verify Flyway reaches version 6, then check public API `200`, authorized-admin allowed endpoint, leader forbidden endpoint, and admin-only endpoint `403` for both non-admin roles.
