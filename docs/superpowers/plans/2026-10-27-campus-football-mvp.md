# Campus Football MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build the Spring Boot + MyBatis + MySQL 8 backend for browsing teams/players, viewing match statistics and standings, and safely editing league data through role-scoped authorization.

**Architecture:** A modular monolith with feature packages for auth, teams, competition, stats, and audit. Controllers expose versioned REST APIs, services enforce authorization and business invariants inside transactions, MyBatis XML handles aggregate queries, and Flyway owns the MySQL schema.

**Tech Stack:** Java 17 target, Spring Boot 3.4.5, Maven Wrapper, MyBatis, MySQL 8, Flyway, Spring Security with JWT, Jakarta Validation, JUnit 5, Testcontainers for MySQL.

**Spec:** `docs/superpowers/specs/2026-10-27-campus-football-mvp-design.md`

## Global Constraints

- Target Java 17 bytecode even when the build runs on a newer JDK.
- Use Spring Boot 3.4.5 and MyBatis Spring Boot Starter 3.0.4 so the application remains on Spring Boot 3.x.
- Use MySQL 8 for runtime and integration tests. Do not replace MySQL with H2.
- Integration tests require Docker Desktop with a running Docker engine. The current planning host has Java 21 but no Maven or Docker; use the Maven Wrapper and install/start Docker before Task 1 verification.
- Every API path starts with `/api/v1`.
- Roles are exactly `ADMIN`, `TEAM_MANAGER`, and `VIEWER`.
- Team authorization comes from `team_manager(user_id, team_id)` on every request; JWT role claims are not trusted.
- Team managers may edit only authorized teams, only participating fixtures, and only their own team records.
- `fixture.data_locked=1` blocks all event, appearance, and official-score writes. Administrators must unlock first.
- Finishing a fixture requires both scores and atomically sets `status=FINISHED` and `data_locked=1`.
- Official scores are authoritative for standings; event rows never mutate official scores.
- Assists are stored as `GOAL.related_player_id`; there is no `ASSIST` event type.
- Own goals use the beneficiary team as `team_id`, the opponent player as `player_id`, and `related_player_id=null`.
- Player statistics include only `FINISHED` fixtures.
- Standings use 3/1/0 points and sort by points, goal difference, goals for, wins, then team name.
- Do not create a `league_standing` table.
- All management writes and their audit rows commit in the same transaction.
- Do not implement the WeChat mini-program client in this plan.
- Use `.\mvnw.cmd` on Windows; do not require a system Maven installation.

## Review Focus

- Cross-team authorization bypass: attempts to edit another team's players, appearances, or events must return `403` even when the client forges a valid-looking `team_id`.
- Fixture lock bypass: both `ADMIN` and `TEAM_MANAGER` writes to appearances, events, and score must fail while `data_locked=1`.
- Event integrity: cross-team assists, self-assists, own goals with assists, cards with related players, and players outside the fixture must be rejected.
- Stats correctness: own goals must not enter the scorer table, cancelled/non-finished fixtures must be excluded, and zero-game season teams must still appear in standings.
- Audit correctness: a failing audit write must roll back the business update, and a team manager must never see another team's logs or global admin logs.

## File Structure

```text
pom.xml
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
.gitignore
compose.yaml
.env.example
README.md
src/main/resources/application.yml
src/test/resources/application-test.yml
src/main/resources/db/migration/V1__create_schema.sql
src/main/resources/mappers/AppUserMapper.xml
src/main/resources/mappers/TeamMapper.xml
src/main/resources/mappers/PlayerMapper.xml
src/main/resources/mappers/TeamManagerMapper.xml
src/main/resources/mappers/SeasonMapper.xml
src/main/resources/mappers/FixtureMapper.xml
src/main/resources/mappers/AppearanceMapper.xml
src/main/resources/mappers/EventMapper.xml
src/main/resources/mappers/AuditMapper.xml
src/main/resources/mappers/StatsMapper.xml
src/main/java/com/campusfootball/CampusFootballApplication.java
src/main/java/com/campusfootball/common/api/ApiError.java
src/main/java/com/campusfootball/common/api/ErrorCode.java
src/main/java/com/campusfootball/common/api/AppException.java
src/main/java/com/campusfootball/common/api/GlobalExceptionHandler.java
src/main/java/com/campusfootball/common/api/RequestIdFilter.java
src/main/java/com/campusfootball/common/security/UserPrincipal.java
src/main/java/com/campusfootball/common/security/CurrentUser.java
src/main/java/com/campusfootball/common/security/JwtService.java
src/main/java/com/campusfootball/common/security/JwtAuthenticationFilter.java
src/main/java/com/campusfootball/common/security/RestAuthenticationEntryPoint.java
src/main/java/com/campusfootball/common/security/RestAccessDeniedHandler.java
src/main/java/com/campusfootball/config/SecurityConfig.java
src/main/java/com/campusfootball/config/JwtProperties.java
src/main/java/com/campusfootball/auth/WechatIdentity.java
src/main/java/com/campusfootball/auth/WechatAuthClient.java
src/main/java/com/campusfootball/auth/WechatApiClient.java
src/main/java/com/campusfootball/auth/AuthService.java
src/main/java/com/campusfootball/auth/AuthController.java
src/main/java/com/campusfootball/auth/AuthResponse.java
src/main/java/com/campusfootball/auth/MeResponse.java
src/main/java/com/campusfootball/auth/AdminBootstrap.java
src/main/java/com/campusfootball/user/AppUser.java
src/main/java/com/campusfootball/user/UserRole.java
src/main/java/com/campusfootball/user/UserStatus.java
src/main/java/com/campusfootball/user/AppUserMapper.java
src/main/java/com/campusfootball/audit/AuditAction.java
src/main/java/com/campusfootball/audit/AuditLog.java
src/main/java/com/campusfootball/audit/AuditMapper.java
src/main/java/com/campusfootball/audit/AuditService.java
src/main/java/com/campusfootball/audit/AuditController.java
src/main/java/com/campusfootball/team/Team.java
src/main/java/com/campusfootball/team/TeamStatus.java
src/main/java/com/campusfootball/team/Player.java
src/main/java/com/campusfootball/team/PlayerStatus.java
src/main/java/com/campusfootball/team/TeamMapper.java
src/main/java/com/campusfootball/team/PlayerMapper.java
src/main/java/com/campusfootball/team/TeamManagerMapper.java
src/main/java/com/campusfootball/team/AuthorizationService.java
src/main/java/com/campusfootball/team/TeamService.java
src/main/java/com/campusfootball/team/PlayerService.java
src/main/java/com/campusfootball/team/TeamManagerService.java
src/main/java/com/campusfootball/team/TeamController.java
src/main/java/com/campusfootball/team/TeamManagementController.java
src/main/java/com/campusfootball/team/AdminTeamController.java
src/main/java/com/campusfootball/competition/Season.java
src/main/java/com/campusfootball/competition/SeasonStatus.java
src/main/java/com/campusfootball/competition/SeasonTeam.java
src/main/java/com/campusfootball/competition/Fixture.java
src/main/java/com/campusfootball/competition/FixtureStatus.java
src/main/java/com/campusfootball/competition/FixtureAppearance.java
src/main/java/com/campusfootball/competition/FixtureEvent.java
src/main/java/com/campusfootball/competition/EventType.java
src/main/java/com/campusfootball/competition/SeasonMapper.java
src/main/java/com/campusfootball/competition/FixtureMapper.java
src/main/java/com/campusfootball/competition/AppearanceMapper.java
src/main/java/com/campusfootball/competition/EventMapper.java
src/main/java/com/campusfootball/competition/SeasonService.java
src/main/java/com/campusfootball/competition/FixtureService.java
src/main/java/com/campusfootball/competition/FixtureWriteGuard.java
src/main/java/com/campusfootball/competition/AppearanceService.java
src/main/java/com/campusfootball/competition/EventService.java
src/main/java/com/campusfootball/competition/SeasonController.java
src/main/java/com/campusfootball/competition/SeasonAdminController.java
src/main/java/com/campusfootball/competition/FixtureController.java
src/main/java/com/campusfootball/competition/FixtureAdminController.java
src/main/java/com/campusfootball/competition/AppearanceManagementController.java
src/main/java/com/campusfootball/competition/EventManagementController.java
src/main/java/com/campusfootball/stats/ScorerRow.java
src/main/java/com/campusfootball/stats/AssistRow.java
src/main/java/com/campusfootball/stats/AppearanceRow.java
src/main/java/com/campusfootball/stats/StandingRow.java
src/main/java/com/campusfootball/stats/StatsMapper.java
src/main/java/com/campusfootball/stats/StatsService.java
src/main/java/com/campusfootball/stats/StatsController.java
src/test/java/com/campusfootball/IntegrationTestBase.java
src/test/java/com/campusfootball/TestWechatConfig.java
src/test/java/com/campusfootball/TestAuthSupport.java
src/test/java/com/campusfootball/TestDataFactory.java
src/test/java/com/campusfootball/SchemaMigrationTest.java
src/test/java/com/campusfootball/AuthIntegrationTest.java
src/test/java/com/campusfootball/SecurityIntegrationTest.java
src/test/java/com/campusfootball/TeamManagementIntegrationTest.java
src/test/java/com/campusfootball/TeamManagerGrantIntegrationTest.java
src/test/java/com/campusfootball/AuditRollbackIntegrationTest.java
src/test/java/com/campusfootball/SeasonFixtureIntegrationTest.java
src/test/java/com/campusfootball/AppearanceIntegrationTest.java
src/test/java/com/campusfootball/EventIntegrationTest.java
src/test/java/com/campusfootball/StatsIntegrationTest.java
src/test/java/com/campusfootball/AuditQueryIntegrationTest.java
src/test/java/com/campusfootball/MvpAcceptanceIntegrationTest.java
```

---

### Task 1: Bootstrap the API and MySQL Schema

**Files:**
- Create: `pom.xml`
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- Create: `.gitignore`, `compose.yaml`, `.env.example`
- Create: `src/main/java/com/campusfootball/CampusFootballApplication.java`
- Create: `src/main/resources/application.yml`
- Create: `src/test/resources/application-test.yml`
- Create: `src/main/resources/db/migration/V1__create_schema.sql`
- Test: `src/test/java/com/campusfootball/IntegrationTestBase.java`
- Test: `src/test/java/com/campusfootball/SchemaMigrationTest.java`

**Interfaces:**
- Consumes: the approved schema and workflow in the spec.
- Produces: a bootable Spring Boot application, Maven Wrapper, Testcontainers MySQL base class, and Flyway-managed schema containing `app_user`, `team_manager`, `team`, `player`, `season`, `season_team`, `fixture`, `fixture_appearance`, `fixture_event`, and `audit_log`.

- [x] **Step 1: Scaffold the project**

Create a Java 17 Maven project with Spring Boot 3.4.5 using Spring Initializr with groupId `com.campusfootball`, artifactId `campus-football-api`, and package `com.campusfootball`. Include Web, Validation, Security, JDBC, Flyway, MySQL Driver, and Testcontainers dependencies. Add MyBatis Spring Boot Starter 3.0.4, `flyway-mysql`, `spring-boot-testcontainers`, Testcontainers JUnit 5/MySQL, `spring-security-test`, and JJWT 0.12.6 API/impl/Jackson artifacts to `pom.xml`. Configure `maven-compiler-plugin` to release Java 17.

- [x] **Step 2: Write the failing schema test**

Create `IntegrationTestBase` with `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Testcontainers`, and a shared `@ServiceConnection` `MySQLContainer<>("mysql:8.0.36")`. Create `SchemaMigrationTest.migratesAllTablesAndNeverCreatesLeagueStanding()` asserting the ten required tables exist and `league_standing` does not. Add `rejectsDuplicateTeamManagerGrant()`, `rejectsDuplicateAppearanceForSameFixtureAndPlayer()`, and `rejectsSameHomeAndAwayTeam()` using `JdbcTemplate` and wrapping each invalid insert with `assertThrows(DataIntegrityViolationException.class)`.

- [x] **Step 3: Run the schema test to verify it fails**

Run: `.\mvnw.cmd -Dtest=SchemaMigrationTest test`

Expected: FAIL because the schema migration and required tables are absent.

- [x] **Step 4: Add configuration and the complete V1 migration**

Add `@MapperScan("com.campusfootball")` to the application class. Add datasource, Flyway, MyBatis with underscore-to-camel-case mapping, mapper locations, JWT secret and 2-hour TTL, WeChat credentials, and admin bootstrap to `application.yml` using environment-variable placeholders. Add a test-only JWT secret in `src/test/resources/application-test.yml`. Write `V1__create_schema.sql` with every table, foreign key, unique constraint, check constraint, and index from the spec. Use `DATETIME`, `TINYINT(1)`, `JSON`, `RESTRICT` foreign keys, and `BIGINT` identity keys. Add `.gitignore`, a MySQL 8 `compose.yaml`, and `.env.example`.

- [x] **Step 5: Run the schema test to verify it passes**

Run: `.\mvnw.cmd -Dtest=SchemaMigrationTest test`

Expected: PASS with four tests and a successful Flyway migration.

- [x] **Step 6: Commit**

```powershell
git add pom.xml mvnw mvnw.cmd .mvn .gitignore compose.yaml .env.example src/main src/test
git commit -m "chore: bootstrap Spring Boot API and schema"
```

---
### Task 2: Add WeChat Authentication, JWT Security, and Error Contracts

**Files:**
- Create: `src/main/java/com/campusfootball/common/api/ApiError.java`
- Create: `src/main/java/com/campusfootball/common/api/ErrorCode.java`
- Create: `src/main/java/com/campusfootball/common/api/AppException.java`
- Create: `src/main/java/com/campusfootball/common/api/GlobalExceptionHandler.java`
- Create: `src/main/java/com/campusfootball/common/api/RequestIdFilter.java`
- Create: `src/main/java/com/campusfootball/common/security/UserPrincipal.java`
- Create: `src/main/java/com/campusfootball/common/security/CurrentUser.java`
- Create: `src/main/java/com/campusfootball/common/security/JwtService.java`
- Create: `src/main/java/com/campusfootball/common/security/JwtAuthenticationFilter.java`
- Create: `src/main/java/com/campusfootball/common/security/RestAuthenticationEntryPoint.java`
- Create: `src/main/java/com/campusfootball/common/security/RestAccessDeniedHandler.java`
- Create: `src/main/java/com/campusfootball/config/SecurityConfig.java`
- Create: `src/main/java/com/campusfootball/config/JwtProperties.java`
- Create: `src/main/java/com/campusfootball/user/AppUser.java`
- Create: `src/main/java/com/campusfootball/user/UserRole.java`
- Create: `src/main/java/com/campusfootball/user/UserStatus.java`
- Create: `src/main/java/com/campusfootball/user/AppUserMapper.java`
- Create: `src/main/java/com/campusfootball/team/TeamManagerMapper.java`
- Create: `src/main/resources/mappers/AppUserMapper.xml`
- Create: `src/main/resources/mappers/TeamManagerMapper.xml`
- Create: `src/main/java/com/campusfootball/auth/WechatIdentity.java`
- Create: `src/main/java/com/campusfootball/auth/WechatAuthClient.java`
- Create: `src/main/java/com/campusfootball/auth/WechatApiClient.java`
- Create: `src/main/java/com/campusfootball/auth/AuthService.java`
- Create: `src/main/java/com/campusfootball/auth/AuthController.java`
- Create: `src/main/java/com/campusfootball/auth/AuthResponse.java`
- Create: `src/main/java/com/campusfootball/auth/MeResponse.java`
- Create: `src/main/java/com/campusfootball/auth/AdminBootstrap.java`
- Test: `src/test/java/com/campusfootball/TestWechatConfig.java`
- Test: `src/test/java/com/campusfootball/TestAuthSupport.java`
- Test: `src/test/java/com/campusfootball/AuthIntegrationTest.java`
- Test: `src/test/java/com/campusfootball/SecurityIntegrationTest.java`

**Interfaces:**
- Consumes: `AppUserMapper`, `TeamManagerMapper`, the V1 schema, and Spring Security.
- Produces: `WechatAuthClient.exchange(String code): WechatIdentity`, `AuthService.login(String code): AuthResponse`, `JwtService.createToken(long userId): String`, `JwtService.parseUserId(String token): long`, `CurrentUser.require(): UserPrincipal`, `AdminBootstrap.ensureAdmin(): void`, and `UserPrincipal(long userId, UserRole role)`.
- Record contracts: `WechatIdentity(String openid, String unionid)`, `AuthResponse(String token, long userId, String nickname, UserRole role, List<Long> managedTeamIds)`, `MeResponse(long userId, String nickname, UserRole role, List<Long> managedTeamIds)`, and `ApiError(String code, String message, String requestId)`.

- [x] **Step 1: Write failing authentication and security tests**

Before each test, delete `team_manager` and `app_user` rows, then call `AdminBootstrap.ensureAdmin()` so user fixtures are deterministic. Create `TestWechatConfig` with a primary `WechatAuthClient` stub mapping `manager-code` to `openid-manager`, `viewer-code` to `openid-viewer`, `admin-code` to `openid-admin`, and throwing `AppException(401, UNAUTHORIZED)` for any other code. Configure the test admin bootstrap openid as `openid-admin`. Create `TestAuthSupport.login(MockMvc, String code)` returning the bearer token value.

`AuthIntegrationTest` must assert:

- First login with `viewer-code` creates one `VIEWER`; a second login reuses the same user ID.
- `admin-code` resolves to `ADMIN` through `AdminBootstrap`.
- A token issued while the user is `VIEWER` sees `TEAM_MANAGER` after the database role is changed, proving roles are loaded per request.
- A `DISABLED` user receives `401`.
- An unknown code returns `401` with `code=WECHAT_LOGIN_FAILED`.

`SecurityIntegrationTest` must assert:

- `GET /api/v1/me` without a token returns `401` with `code=UNAUTHORIZED` and a nonblank `requestId`.
- A malformed bearer token returns `401`.
- A valid token returns the current user from `/api/v1/me`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=AuthIntegrationTest,SecurityIntegrationTest test`

Expected: FAIL because authentication, security, and error response classes are absent.

- [x] **Step 3: Implement the authentication slice**

Define `ErrorCode` constants `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `VALIDATION_FAILED`, `CONFLICT`, `FIXTURE_LOCKED`, and `WECHAT_LOGIN_FAILED`. Implement a JSON error envelope with `code`, `message`, and `requestId`. Add a highest-precedence request-ID filter that runs before Spring Security, a global exception handler, and JSON 401/403 handlers that read the request ID from that filter.

Implement `AppUserMapper.findByOpenId`, `findById`, `insert`, `updateRole`, and `updateProfile`. Implement WeChat login with `RestClient` against the WeChat `jscode2session` endpoint, store `openid`/`unionid`, and create new users as `VIEWER`. Implement `AdminBootstrap` as an optional `ApplicationRunner` driven by `app.bootstrap.admin-openid`.

Configure stateless Spring Security. Permit `POST /api/v1/auth/wechat-login` and only these public GET matchers: `/api/v1/seasons`, `/api/v1/teams/**`, `/api/v1/players/**`, `/api/v1/fixtures/**`, `/api/v1/standings`, and `/api/v1/stats/**`. Require authentication for `/api/v1/me`, `ADMIN` for `/api/v1/admin/**`, and `ADMIN` or `TEAM_MANAGER` for `/api/v1/manage/**`. Deny every unmatched request. The JWT filter must parse only the user ID, load the current user from the database, reject disabled users, and attach `ROLE_<role>`. Implement `TeamManagerMapper.findTeamIdsByUserId(long userId): List<Long>` so both auth responses can populate `managedTeamIds`.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=AuthIntegrationTest,SecurityIntegrationTest test`

Expected: PASS with all authentication, role-reload, disabled-user, and error-contract assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/common src/main/java/com/campusfootball/config src/main/java/com/campusfootball/user src/main/java/com/campusfootball/team/TeamManagerMapper.java src/main/java/com/campusfootball/auth src/main/resources/mappers/AppUserMapper.xml src/main/resources/mappers/TeamManagerMapper.xml src/test/java/com/campusfootball
git commit -m "feat: add WeChat auth and JWT security"
```

---

### Task 3: Add Team/Player Management, Team Authorization, and Audit Foundation

**Files:**
- Create: `src/main/java/com/campusfootball/audit/AuditAction.java`
- Create: `src/main/java/com/campusfootball/audit/AuditLog.java`
- Create: `src/main/java/com/campusfootball/audit/AuditMapper.java`
- Create: `src/main/java/com/campusfootball/audit/AuditService.java`
- Create: `src/main/resources/mappers/AuditMapper.xml`
- Modify: `src/test/java/com/campusfootball/IntegrationTestBase.java`
- Create: `src/main/java/com/campusfootball/team/Team.java`
- Create: `src/main/java/com/campusfootball/team/TeamStatus.java`
- Create: `src/main/java/com/campusfootball/team/Player.java`
- Create: `src/main/java/com/campusfootball/team/PlayerStatus.java`
- Create: `src/main/java/com/campusfootball/team/TeamMapper.java`
- Create: `src/main/java/com/campusfootball/team/PlayerMapper.java`
- Modify: `src/main/java/com/campusfootball/team/TeamManagerMapper.java`
- Create: `src/main/java/com/campusfootball/team/AuthorizationService.java`
- Create: `src/main/java/com/campusfootball/team/TeamService.java`
- Create: `src/main/java/com/campusfootball/team/PlayerService.java`
- Create: `src/main/java/com/campusfootball/team/TeamManagerService.java`
- Create: `src/main/java/com/campusfootball/team/TeamController.java`
- Create: `src/main/java/com/campusfootball/team/TeamManagementController.java`
- Create: `src/main/java/com/campusfootball/team/AdminTeamController.java`
- Create: `src/main/resources/mappers/TeamMapper.xml`
- Create: `src/main/resources/mappers/PlayerMapper.xml`
- Modify: `src/main/resources/mappers/TeamManagerMapper.xml`
- Test: `src/test/java/com/campusfootball/TestDataFactory.java`
- Test: `src/test/java/com/campusfootball/TeamManagementIntegrationTest.java`
- Test: `src/test/java/com/campusfootball/TeamManagerGrantIntegrationTest.java`
- Test: `src/test/java/com/campusfootball/AuditRollbackIntegrationTest.java`

**Interfaces:**
- Consumes: `UserPrincipal`, `CurrentUser`, `AppUserMapper`, `TeamManagerMapper`, and the schema.
- Produces:
  - `AuthorizationService.requireAdmin(long userId): void`
  - `AuthorizationService.requireCanManageTeam(long userId, long teamId): void`
  - `AuthorizationService.managedTeamIds(long userId): List<Long>`
  - `AuditService.record(long actorUserId, Long teamId, AuditAction action, String entityType, long entityId, Object before, Object after): void`
  - `TeamService.createTeam(long actorUserId, String name, String shortName, String logoUrl, String coachName, String homeVenue): long`
  - `TeamService.updateTeam(long actorUserId, long teamId, String name, String shortName, String logoUrl, String coachName, String homeVenue, TeamStatus status): void`
  - `PlayerService.createPlayer(long actorUserId, long teamId, String name, Integer jerseyNo, String position, LocalDate birthDate, String photoUrl): long`
  - `PlayerService.updatePlayer(long actorUserId, long playerId, String name, Integer jerseyNo, String position, LocalDate birthDate, String photoUrl, PlayerStatus status): void`
  - `TeamManagerService.grant(long actorUserId, long teamId, long targetUserId): void`
  - `TeamManagerService.revoke(long actorUserId, long teamId, long targetUserId): void`
  - Nested controller records: `CreateTeamRequest(name, shortName, logoUrl, coachName, homeVenue)`, `UpdateTeamRequest(name, shortName, logoUrl, coachName, homeVenue, status)`, `CreatePlayerRequest(name, jerseyNo, position, birthDate, photoUrl)`, `UpdatePlayerRequest(name, jerseyNo, position, birthDate, photoUrl, status)`, and `GrantManagerRequest(userId)`.

- [x] **Step 1: Write failing team, player, grant, and audit tests**

`TeamManagementIntegrationTest` must assert:

- An admin creates a team and player; public team/player endpoints return them.
- A team manager edits own team display fields and own player.
- A team manager cannot set team status or edit another team/player; both return `403`.
- A viewer cannot call `/manage/**`; returns `403`.
- A duplicate active jersey number in one team returns `409`.
- Inactive teams and players are hidden from public endpoints but visible through admin list endpoints.
- Each successful team/player/grant/revoke operation creates an audit row with the correct `team_id`.

`TeamManagerGrantIntegrationTest` must assert:

- Granting a `VIEWER` promotes the role to `TEAM_MANAGER`.
- Revoking the last grant demotes a non-admin to `VIEWER`.
- An already-issued token immediately loses access after revocation.
- Revoking an administrator's team grant does not demote the administrator.

`AuditRollbackIntegrationTest` must replace `AuditService` with `@MockitoBean`, make `record(long, Long, AuditAction, String, long, Object, Object)` throw `IllegalStateException`, call a successful player update path, and assert the player row is unchanged.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=TeamManagementIntegrationTest,TeamManagerGrantIntegrationTest,AuditRollbackIntegrationTest test`

Expected: FAIL because team, player, authorization, and audit components are absent.

- [x] **Step 3: Implement the team and audit slice**

Implement mapper methods for active/all team lists, single-team lookup, player creation/update, active jersey lookup, team-manager existence, managed-team IDs, grant insert, and grant delete.

Update `IntegrationTestBase` to call `TestDataFactory.reset()` before each test. Implement `AuthorizationService` so `ADMIN` passes directly and `TEAM_MANAGER` passes only when a `team_manager` row exists. Treat missing or disabled users as unauthorized. Implement team and player services with the spec's write rules, immutable `player.team_id`, active-jersey uniqueness, 409 conflicts, and team-scoped audit entries.

Create `TestDataFactory` for reusable user, grant, team, player, season, fixture, appearance, and event inserts used by later integration tests. Its `reset()` method must delete mutable rows in foreign-key-safe order and re-run `AdminBootstrap` so every test starts from a deterministic state. Implement public team/player GET endpoints, scoped management endpoints, and admin team/player/grant endpoints. All management writes must be `@Transactional` and call `AuditService.record(long, Long, AuditAction, String, long, Object, Object)` after the database mutation. `AuditService` serializes before/after values to JSON and inserts inside the caller's transaction. A team manager request that includes `TeamStatus.INACTIVE` must return `403`; an administrator may change it.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=TeamManagementIntegrationTest,TeamManagerGrantIntegrationTest,AuditRollbackIntegrationTest test`

Expected: PASS, including cross-team `403`, role transitions, public visibility, duplicate jersey, and rollback assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/audit src/main/java/com/campusfootball/team src/main/resources/mappers/AuditMapper.xml src/main/resources/mappers/TeamMapper.xml src/main/resources/mappers/PlayerMapper.xml src/main/resources/mappers/TeamManagerMapper.xml src/test/java/com/campusfootball/IntegrationTestBase.java src/test/java/com/campusfootball/TestDataFactory.java src/test/java/com/campusfootball/TeamManagementIntegrationTest.java src/test/java/com/campusfootball/TeamManagerGrantIntegrationTest.java src/test/java/com/campusfootball/AuditRollbackIntegrationTest.java
git commit -m "feat: add team management authorization and audit"
```

---
### Task 4: Add Seasons, Season Teams, and Fixture Lifecycle

**Files:**
- Create: `src/main/java/com/campusfootball/competition/Season.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonStatus.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonTeam.java`
- Create: `src/main/java/com/campusfootball/competition/Fixture.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureStatus.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonMapper.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureMapper.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonService.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureService.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureWriteGuard.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonController.java`
- Create: `src/main/java/com/campusfootball/competition/SeasonAdminController.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureController.java`
- Create: `src/main/java/com/campusfootball/competition/FixtureAdminController.java`
- Create: `src/main/resources/mappers/SeasonMapper.xml`
- Create: `src/main/resources/mappers/FixtureMapper.xml`
- Test: `src/test/java/com/campusfootball/SeasonFixtureIntegrationTest.java`

**Interfaces:**
- Consumes: `AuthorizationService`, `AuditService`, team/player mappers, and the schema.
- Produces:
  - `SeasonService.createSeason(long actorUserId, String name, LocalDate startDate, LocalDate endDate, SeasonStatus status): long`
  - `SeasonService.addTeam(long actorUserId, long seasonId, long teamId): void`
  - `SeasonService.removeTeam(long actorUserId, long seasonId, long teamId): void`
  - `FixtureService.createFixture(long actorUserId, long seasonId, Integer roundNo, long homeTeamId, long awayTeamId, LocalDateTime kickoffAt, String venue): long`
  - `FixtureService.updateSchedule(long actorUserId, long fixtureId, long seasonId, Integer roundNo, long homeTeamId, long awayTeamId, LocalDateTime kickoffAt, String venue): void`
  - `FixtureService.changeStatus(long actorUserId, long fixtureId, FixtureStatus target): void`
  - `FixtureService.updateScore(long actorUserId, long fixtureId, int homeScore, int awayScore): void`
  - `FixtureService.finish(long actorUserId, long fixtureId, int homeScore, int awayScore): void`
  - `FixtureService.setLocked(long actorUserId, long fixtureId, boolean locked): void`
  - `FixtureMapper.countEvents(long fixtureId): int` and `FixtureMapper.countAppearances(long fixtureId): int`
  - `FixtureWriteGuard.requireWritable(long fixtureId): Fixture`
  - Nested controller records: `CreateSeasonRequest(name, startDate, endDate, status)`, `CreateFixtureRequest(seasonId, roundNo, homeTeamId, awayTeamId, kickoffAt, venue)`, `UpdateFixtureRequest(seasonId, roundNo, homeTeamId, awayTeamId, kickoffAt, venue)`, `ChangeStatusRequest(status)`, `ScoreRequest(homeScore, awayScore)`, and `LockRequest(locked)`.

- [x] **Step 1: Write failing season and fixture tests**

`SeasonFixtureIntegrationTest` must assert:

- Admin creates an `ACTIVE` season, adds two teams, and creates a `DRAFT` fixture.
- Adding a team twice to one season returns `409`.
- Creating a fixture with a team outside the season or with identical home/away teams returns `409`.
- `DRAFT` seasons and fixtures are hidden from public endpoints but visible to admin endpoints.
- `DRAFT -> SCHEDULED -> LIVE` succeeds; illegal transitions such as `DRAFT -> LIVE` return `409`.
- `CANCELLED` is terminal.
- Removing a season team referenced by a fixture returns `409`.
- `finish` with both scores sets `FINISHED` and `data_locked=1`.
- When locked, admin score update returns `403`; after unlocking, the update succeeds.
- A team manager cannot call any `/admin/**` fixture endpoint.
- Season, fixture, score, finish, and lock operations create audit rows; all fixture-wide actions use `entity_type=FIXTURE` so team managers can scope them by participation.
- `finish` from `SCHEDULED` returns `409`, missing scores return `400`, and negative scores return `400`.
- The status endpoint rejects target `FINISHED`; finishing must use the finish endpoint.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=SeasonFixtureIntegrationTest test`

Expected: FAIL because season and fixture components are absent.

- [x] **Step 3: Implement the competition slice**

Implement season, season-team, and fixture mapper methods, including `countEvents(long)` and `countAppearances(long)` for the participant-freeze rule. Use boxed `Integer` fields with `@NotNull @Min(0)` in `ScoreRequest`, then pass primitive scores to services. Enforce season-team membership before fixture writes. Implement the allowed status graph and terminal cancellation. Implement `finish` only from `LIVE`; reject other source states with `409`. Validate non-negative scores, update score, set `FINISHED`, and set `data_locked=1` in one transaction. Implement `FixtureWriteGuard.requireWritable` so any locked write returns `AppException` with status 403 and `FIXTURE_LOCKED`.

Implement `SeasonController` public reads that exclude `DRAFT`, `FixtureController` public fixture lists that exclude `DRAFT`, admin reads that include draft data, and all admin write endpoints. `PUT /admin/fixtures/{fixtureId}` may change season or participants only while the fixture has no events or appearances; those attempts return `409` afterward. The same endpoint must always reject score, status, and lock changes; dedicated score, status, finish, and lock endpoints own those transitions.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=SeasonFixtureIntegrationTest test`

Expected: PASS with all status, visibility, membership, lock, and audit assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition src/main/resources/mappers/SeasonMapper.xml src/main/resources/mappers/FixtureMapper.xml src/test/java/com/campusfootball/SeasonFixtureIntegrationTest.java
git commit -m "feat: add season and fixture lifecycle"
```

---

### Task 5: Add Fixture Appearance Management

**Files:**
- Create: `src/main/java/com/campusfootball/competition/FixtureAppearance.java`
- Create: `src/main/java/com/campusfootball/competition/AppearanceMapper.java`
- Create: `src/main/java/com/campusfootball/competition/AppearanceService.java`
- Create: `src/main/java/com/campusfootball/competition/AppearanceManagementController.java`
- Create: `src/main/resources/mappers/AppearanceMapper.xml`
- Test: `src/test/java/com/campusfootball/AppearanceIntegrationTest.java`

**Interfaces:**
- Consumes: `FixtureWriteGuard`, `AuthorizationService`, `PlayerMapper`, `FixtureMapper`, and `AuditService`.
- Produces:
  - `AppearanceService.create(long actorUserId, long fixtureId, long teamId, long playerId, boolean starter, Integer minutesPlayed): long`
  - `AppearanceService.update(long actorUserId, long appearanceId, boolean starter, Integer minutesPlayed): void`
  - `AppearanceService.delete(long actorUserId, long appearanceId): void`
  - Nested records: `CreateAppearanceRequest(teamId, playerId, isStarter, minutesPlayed)` and `UpdateAppearanceRequest(isStarter, minutesPlayed)`.
  - `minutesPlayed` is nullable; when present it must be between 0 and 130.

- [x] **Step 1: Write failing appearance tests**

`AppearanceIntegrationTest` must assert:

- A team manager adds a starter/minutes row for an own-team player in a `LIVE` fixture.
- A team manager cannot add an appearance for the other team; returns `403`.
- A player from a different team is rejected with `409`.
- A duplicate `(fixture_id, player_id)` returns `409`.
- Negative or greater-than-130 `minutes_played` returns `400`.
- A team manager cannot write to a `SCHEDULED` fixture; returns `403`.
- A team manager may write to a `FINISHED`, unlocked fixture.
- With `data_locked=1`, both admin and team-manager appearance writes return `403`.
- Updating or deleting another team's appearance returns `403`.
- Updating a `team_id` or `player_id` is rejected; wrong records must be deleted and recreated.
- Appearance creation creates a team-scoped audit row.
- After an appearance exists, changing fixture participants or season returns `409`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=AppearanceIntegrationTest test`

Expected: FAIL because appearance components are absent.

- [x] **Step 3: Implement appearance management**

Implement mapper insert, findById, update, delete, and uniqueness checks for appearances. In `AppearanceService`, resolve the fixture, verify that `team_id` is one of the fixture teams, verify the player's current team equals `team_id`, call `FixtureWriteGuard.requireWritable`, and enforce the manager status rule. Allow administrators to write in `SCHEDULED`, `LIVE`, or `FINISHED`; allow team managers only in `LIVE` or `FINISHED`. Keep fixture, team, and player identifiers immutable on update. Wrap mutations and audit writes in one transaction.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=AppearanceIntegrationTest test`

Expected: PASS with cross-team, lock, state, duplicate, immutability, and audit assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition/FixtureAppearance.java src/main/java/com/campusfootball/competition/AppearanceMapper.java src/main/java/com/campusfootball/competition/AppearanceService.java src/main/java/com/campusfootball/competition/AppearanceManagementController.java src/main/resources/mappers/AppearanceMapper.xml src/test/java/com/campusfootball/AppearanceIntegrationTest.java
git commit -m "feat: add fixture appearance management"
```

---

### Task 6: Add Fixture Event Management and Event Invariants

**Files:**
- Create: `src/main/java/com/campusfootball/competition/FixtureEvent.java`
- Create: `src/main/java/com/campusfootball/competition/EventType.java`
- Create: `src/main/java/com/campusfootball/competition/EventMapper.java`
- Create: `src/main/java/com/campusfootball/competition/EventService.java`
- Create: `src/main/java/com/campusfootball/competition/EventManagementController.java`
- Create: `src/main/resources/mappers/EventMapper.xml`
- Test: `src/test/java/com/campusfootball/EventIntegrationTest.java`

**Interfaces:**
- Consumes: `FixtureWriteGuard`, `AuthorizationService`, `PlayerMapper`, `FixtureMapper`, and `AuditService`.
- Produces:
  - `EventService.create(long actorUserId, long fixtureId, long teamId, long playerId, EventType eventType, Long relatedPlayerId, int minute, int stoppageMinute, String note): long`
  - `EventService.update(long actorUserId, long eventId, long playerId, EventType eventType, Long relatedPlayerId, int minute, int stoppageMinute, String note): void`
  - `EventService.delete(long actorUserId, long eventId): void`
  - Nested records: `CreateEventRequest(teamId, playerId, eventType, relatedPlayerId, minute, stoppageMinute, note)` and `UpdateEventRequest(playerId, eventType, relatedPlayerId, minute, stoppageMinute, note)`.

- [x] **Step 1: Write failing event tests**

`EventIntegrationTest` must assert:

- A team manager records a `GOAL` with a same-team `related_player_id`.
- A goal without an assist stores a null related player.
- Cross-team assists, self-assists, and an assist on a non-goal event return `409`.
- An own goal stores the beneficiary as `team_id`, an opponent as `player_id`, and a null related player.
- An own goal with a related player returns `409`.
- A card with a related player returns `409`.
- A player outside either fixture team returns `409`.
- An invalid minute or stoppage minute returns `400`.
- A team manager cannot create or edit an event whose `team_id` is the other team; returns `403`.
- A team manager cannot write to a `SCHEDULED` fixture, while an administrator can.
- A team manager cannot change `team_id` on update; returns `409`.
- With `data_locked=1`, admin and team-manager event writes return `403`.
- Recording a goal does not change `fixture.home_score` or `fixture.away_score`.
- Event creation creates an audit row.
- After an event exists, changing fixture participants or season returns `409`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=EventIntegrationTest test`

Expected: FAIL because event components are absent.

- [x] **Step 3: Implement event management**

Implement mapper insert, findById, update, delete, and fixture/team/player lookups needed by validation. Implement `EventService` with immutable fixture and team identifiers on update, exact event-type semantics from the spec, team-scoped authorization, lock checks, and transactional audit writes. Administrators may write events in `SCHEDULED`, `LIVE`, or `FINISHED`; team managers only in `LIVE` or `FINISHED`. Validate that the event player belongs to one of the fixture teams; enforce same-team scorer/assist and opposite-team own-goal rules.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=EventIntegrationTest test`

Expected: PASS with all event semantics, authorization, lock, score-independence, and audit assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/competition/FixtureEvent.java src/main/java/com/campusfootball/competition/EventType.java src/main/java/com/campusfootball/competition/EventMapper.java src/main/java/com/campusfootball/competition/EventService.java src/main/java/com/campusfootball/competition/EventManagementController.java src/main/resources/mappers/EventMapper.xml src/test/java/com/campusfootball/EventIntegrationTest.java
git commit -m "feat: add fixture event management"
```

---
### Task 7: Add Public Fixture Details, Stats, and Standings

**Files:**
- Create: `src/main/java/com/campusfootball/stats/ScorerRow.java`
- Create: `src/main/java/com/campusfootball/stats/AssistRow.java`
- Create: `src/main/java/com/campusfootball/stats/AppearanceRow.java`
- Create: `src/main/java/com/campusfootball/stats/StandingRow.java`
- Create: `src/main/java/com/campusfootball/stats/StatsMapper.java`
- Create: `src/main/java/com/campusfootball/stats/StatsService.java`
- Create: `src/main/java/com/campusfootball/stats/StatsController.java`
- Create: `src/main/resources/mappers/StatsMapper.xml`
- Modify: `src/main/java/com/campusfootball/competition/FixtureController.java`
- Test: `src/test/java/com/campusfootball/StatsIntegrationTest.java`

**Interfaces:**
- Consumes: the complete fixture, appearance, event, season-team, team, and player schema.
- Produces:
  - `StatsService.scorers(long seasonId): List<ScorerRow>`
  - `StatsService.assists(long seasonId): List<AssistRow>`
  - `StatsService.appearances(long seasonId): List<AppearanceRow>`
  - `StatsService.standings(long seasonId): List<StandingRow>`
  - public `GET /fixtures/{fixtureId}` detail with events and appearances
  - `ScorerRow(long playerId, String playerName, long teamId, String teamName, long goals)`
  - `AssistRow(long playerId, String playerName, long teamId, String teamName, long assists)`
  - `AppearanceRow(long playerId, String playerName, long teamId, String teamName, long appearances)`
  - `StandingRow(long teamId, String teamName, int played, int won, int drawn, int lost, int goalsFor, int goalsAgainst, int goalDifference, int points)`

- [x] **Step 1: Write failing stats and standings tests**

`StatsIntegrationTest` must assert:

- Scorers count only `GOAL` rows from `FINISHED` fixtures; `OWN_GOAL` and cancelled fixtures are excluded.
- Assists count `GOAL.related_player_id` from finished fixtures only.
- Appearances use distinct finished fixtures and exclude cancelled fixtures.
- Standings apply 3 points for a win, 1 for a draw, and 0 for a loss.
- Standings sort by points, goal difference, goals for, wins, then team name.
- Every `season_team` row appears in standings, including a team with zero played fixtures.
- A cancelled fixture does not affect standings.
- Public fixture detail returns events and appearances and returns `404` for a `DRAFT` fixture.
- `GET /seasons` hides `DRAFT` seasons.

Use a deterministic dataset with at least four teams, finished wins/draws, one cancelled fixture, one own goal, one assisted goal, and one zero-game team.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=StatsIntegrationTest test`

Expected: FAIL because public detail and stats queries are absent.

- [x] **Step 3: Implement aggregate queries and public read models**

Write MyBatis XML aggregates that join through `fixture` and filter `fixture.status='FINISHED'` and the requested season. Use `fixture_event.team_id` for scorer/assist team identity so later player transfers do not rewrite history. Compute standings with a union of home and away perspectives, then left join `season_team` so zero-game teams remain. Apply `ORDER BY points DESC, goal_difference DESC, goals_for DESC, wins DESC, team_name ASC`.

Extend `FixtureController` to return a public detail DTO containing fixture metadata, teams, score, appearances, and events, while hiding `DRAFT` fixtures. Implement season filtering and stable response DTOs for all stats endpoints.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=StatsIntegrationTest test`

Expected: PASS with exact scorer, assist, appearance, standing, tie-break, zero-game, cancellation, and public-detail assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/stats src/main/resources/mappers/StatsMapper.xml src/main/java/com/campusfootball/competition/FixtureController.java src/test/java/com/campusfootball/StatsIntegrationTest.java
git commit -m "feat: add public stats and standings"
```

---

### Task 8: Add Scoped Audit Log Queries

**Files:**
- Create: `src/main/java/com/campusfootball/audit/AuditController.java`
- Modify: `src/main/java/com/campusfootball/audit/AuditMapper.java`
- Modify: `src/main/resources/mappers/AuditMapper.xml`
- Test: `src/test/java/com/campusfootball/AuditQueryIntegrationTest.java`

**Interfaces:**
- Consumes: `CurrentUser`, `AuthorizationService`, `AuditMapper`, and persisted audit rows.
- Produces: `GET /api/v1/manage/audit-logs` returning only the caller's permitted audit rows.

- [x] **Step 1: Write failing audit query tests**

`AuditQueryIntegrationTest` must assert:

- An admin sees audit rows for teams, fixtures, players, appearances, and events.
- A team manager sees logs whose `team_id` is one of their managed teams.
- A team manager sees fixture-level logs only when their team participates in that fixture.
- A team manager does not see a global season-creation log with `team_id=null`.
- A team manager does not see another team's player log.
- A viewer receives `403`.

- [x] **Step 2: Run the tests to verify they fail**

Run: `.\mvnw.cmd -Dtest=AuditQueryIntegrationTest test`

Expected: FAIL because the audit query endpoint and scoped mapper query are absent.

- [x] **Step 3: Implement scoped audit retrieval**

Add an audit query ordered by `created_at DESC, id DESC`. For admins, return all rows. For team managers, return rows with `team_id` in their managed team IDs plus fixture-level rows whose `entity_type='FIXTURE'` and `entity_id` belongs to a fixture containing one of their teams. Do not accept a client-supplied scope parameter that can broaden access.

- [x] **Step 4: Run the tests to verify they pass**

Run: `.\mvnw.cmd -Dtest=AuditQueryIntegrationTest test`

Expected: PASS with admin, team-scope, fixture-participation, global-log, and viewer assertions.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/campusfootball/audit/AuditController.java src/main/java/com/campusfootball/audit/AuditMapper.java src/main/resources/mappers/AuditMapper.xml src/test/java/com/campusfootball/AuditQueryIntegrationTest.java
git commit -m "feat: add scoped audit log queries"
```

---

### Task 9: Add End-to-End Acceptance Coverage and Run Instructions

**Files:**
- Create: `src/test/java/com/campusfootball/MvpAcceptanceIntegrationTest.java`
- Create: `README.md`
- Modify: `compose.yaml`
- Modify: `.env.example`

**Interfaces:**
- Consumes: every public and management endpoint produced by Tasks 1-8.
- Produces: a repeatable end-to-end acceptance test and operator instructions for local startup and verification.

- [x] **Step 1: Write the end-to-end acceptance test**

`MvpAcceptanceIntegrationTest.completeLeagueFlow()` must perform one HTTP flow:

1. Admin login, create two teams, create an active season, add both teams, create a fixture, publish it, and move it to `LIVE`.
2. Manager login, grant the manager one team, create two players, and add both players to the fixture appearances.
3. Manager records a goal with an assist and a yellow card.
4. Admin sets the official score and finishes the fixture.
5. Public statistics show the scorer, assister, and appearance counts.
6. Public standings show the winning team with 3 points.
7. A manager event write after finish returns `403` with `FIXTURE_LOCKED`.
8. `GET /api/v1/manage/audit-logs` with the admin token contains entries for the manager's writes and the finish operation.

Add a second assertion that every error response has `code`, `message`, and `requestId`.

- [x] **Step 2: Run the acceptance test**

Run: `.\mvnw.cmd -Dtest=MvpAcceptanceIntegrationTest test`

Expected: PASS if Tasks 1-8 are correctly integrated. If it fails, fix only the real integration mismatch in Step 3 and rerun; do not weaken assertions.

- [x] **Step 3: Implement only the missing integration wiring and documentation**

Resolve any mismatched DTO fields, route mappings, transaction boundaries, or security rules exposed by the acceptance flow; if the test already passes, make no production-code changes. Write `README.md` with Docker Desktop, MySQL container, environment variables, `.\mvnw.cmd spring-boot:run`, test, and build commands. Ensure `.env.example` contains placeholders but no real secrets.

- [x] **Step 4: Run the full verification suite**

Run: `.\mvnw.cmd verify`

Expected: PASS with all unit and integration tests green.

- [x] **Step 5: Commit**

```powershell
git add README.md compose.yaml .env.example src
git commit -m "test: add MVP acceptance coverage and docs"
```

---

## Spec Coverage Check

| Spec area | Covered by |
|---|---|
| Roles and team-scoped authorization | Tasks 2, 3 |
| Team and player browsing/editing | Task 3 |
| Season membership and fixture states | Task 4 |
| Appearance and event integrity | Tasks 5, 6 |
| Locking and finish behavior | Tasks 4, 5, 6 |
| Scorer, assist, appearance, standings rules | Task 7 |
| Audit atomicity and scope | Tasks 3, 8 |
| Stable error contract | Tasks 2, 9 |
| End-to-end acceptance | Task 9 |

## Execution Handoff

The plan is complete after its self-review. Implementation must not begin until the human partner approves this plan and chooses native or subagent-driven execution.






## Completion Record

- Completed: 2026-10-27
- Verification: `.\mvnw.cmd -q verify`
- Result: 61 tests, 0 failures, 0 errors, 0 skipped
- Test database: external MySQL 8.4.9 on port 3307 while the host had Docker Desktop installed but WSL2/Virtual Machine Platform not enabled
- Testcontainers path remains the default when `TEST_DB_URL` is not set
