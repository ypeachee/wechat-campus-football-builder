# WeChat Mini Program Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native TypeScript WeChat Mini Program that consumes the existing campus football API and supports public browsing, statistics, role-aware login, and scoped management forms.

**Architecture:** A standalone `miniprogram/` subproject using native pages/components, TDesign MiniProgram, a typed request layer, local session and season stores, and role-gated management routes. The Spring Boot API remains the only authority for permissions, locking, event rules, standings, and audit data.

**Tech Stack:** Native WeChat Mini Program, TypeScript 5.9.3, TDesign MiniProgram 1.17.0, miniprogram-api-typings 5.2.3, Vitest 5.0.5, WeChat DevTools.

**Spec:** `docs/superpowers/specs/2026-10-27-wechat-miniprogram-design.md`

## Global Constraints

- The mini-program project lives at `miniprogram/` and is opened directly as the WeChat DevTools project.
- Use native mini-program pages/components. Do not add uni-app, Taro, React, or Vue.
- Use TypeScript 5.9.3 and enable `setting.useCompilerPlugins: ["typescript"]` in `project.config.json`.
- Pin `tdesign-miniprogram` to `1.17.0`.
- Use `npm run typecheck` and `npm test` as mandatory checks for every code task.
- Never call `wx.request` outside `miniprogram/services/http.ts`.
- Never store the WeChat `appSecret` in the mini program.
- Roles are exactly `ADMIN`, `TEAM_MANAGER`, and `VIEWER`.
- `ADMIN` can access all management pages.
- `TEAM_MANAGER` can access management pages only for IDs in `managedTeamIds`.
- `VIEWER` and unauthenticated users can use public pages only.
- API base URL selection must support `develop`, `trial`, and `release`.
- The public API starts at `/api/v1`; the dev default is `http://127.0.0.1:8080`.
- Development builds may disable request-domain checking in DevTools; production must use an HTTPS legal domain.
- Use the app color tokens from the spec: `#0B6B4B`, `#B7F34A`, `#14201D`, `#F4F1E8`, `#C7392F`, and `#2463A8`.
- V1 is light-theme only.
- No image upload, payments, comments, live video, or push subscriptions.
- The WeChat AppID is a local developer setting; commit only a placeholder.
- The current machine has Node 24 and npm 11. Install WeChat DevTools with `winget install --id Tencent.WeixinDevTools -e` before visual verification.

## Review Focus

- Stale or forged role state exposes a management route: role checks must use the latest `/me` response and team-manager routes must verify `managedTeamIds` before rendering forms.
- A 401 response causes a repeated login loop or leaves stale data onscreen: the HTTP layer must clear the session once and redirect to login exactly once.
- Season selection diverges between fixtures and rankings: both pages must read and update the same persisted season store.
- Forms submit invalid or unauthorized payloads and hide backend lock errors: local validation must block empty required fields and `FIXTURE_LOCKED` must map to an explicit user-facing message.
- Double submission or slow-network state leaves the UI ambiguous: submit handlers must use a page-level lock and restore it on every success or failure path.

## File Structure

```text
miniprogram/
├── app.ts
├── app.json
├── app.wxss
├── project.config.json
├── package.json
├── tsconfig.json
├── vitest.config.ts
├── sitemap.json
├── typings/index.d.ts
├── config/env.ts
├── services/http.ts
├── services/auth.ts
├── services/teams.ts
├── services/competition.ts
├── services/stats.ts
├── services/audit.ts
├── services/manage.ts
├── state/session.ts
├── state/season.ts
├── state/submit-lock.ts
├── types/api.ts
├── types/domain.ts
├── utils/date.ts
├── utils/status.ts
├── utils/validation.ts
├── components/app-header/index.ts
├── components/app-header/index.json
├── components/app-header/index.wxml
├── components/app-header/index.wxss
├── components/empty-state/index.ts
├── components/empty-state/index.json
├── components/empty-state/index.wxml
├── components/empty-state/index.wxss
├── components/match-card/index.ts
├── components/match-card/index.json
├── components/match-card/index.wxml
├── components/match-card/index.wxss
├── components/ranking-list/index.ts
├── components/ranking-list/index.json
├── components/ranking-list/index.wxml
├── components/ranking-list/index.wxss
├── components/role-gate/index.ts
├── components/role-gate/index.json
├── components/role-gate/index.wxml
├── components/role-gate/index.wxss
├── components/season-picker/index.ts
├── components/season-picker/index.json
├── components/season-picker/index.wxml
├── components/season-picker/index.wxss
├── components/team-card/index.ts
├── components/team-card/index.json
├── components/team-card/index.wxml
├── components/team-card/index.wxss
├── pages/home/index.ts
├── pages/home/index.json
├── pages/home/index.wxml
├── pages/home/index.wxss
├── pages/fixtures/index.ts
├── pages/fixtures/index.json
├── pages/fixtures/index.wxml
├── pages/fixtures/index.wxss
├── pages/fixture-detail/index.ts
├── pages/fixture-detail/index.json
├── pages/fixture-detail/index.wxml
├── pages/fixture-detail/index.wxss
├── pages/teams/index.ts
├── pages/teams/index.json
├── pages/teams/index.wxml
├── pages/teams/index.wxss
├── pages/team-detail/index.ts
├── pages/team-detail/index.json
├── pages/team-detail/index.wxml
├── pages/team-detail/index.wxss
├── pages/player-detail/index.ts
├── pages/player-detail/index.json
├── pages/player-detail/index.wxml
├── pages/player-detail/index.wxss
├── pages/stats/index.ts
├── pages/stats/index.json
├── pages/stats/index.wxml
├── pages/stats/index.wxss
├── pages/profile/index.ts
├── pages/profile/index.json
├── pages/profile/index.wxml
├── pages/profile/index.wxss
├── pages/manage/index.ts
├── pages/manage/index.json
├── pages/manage/index.wxml
├── pages/manage/index.wxss
├── pages/manage-team/index.ts
├── pages/manage-team/index.json
├── pages/manage-team/index.wxml
├── pages/manage-team/index.wxss
├── pages/manage-fixture-data/index.ts
├── pages/manage-fixture-data/index.json
├── pages/manage-fixture-data/index.wxml
├── pages/manage-fixture-data/index.wxss
├── pages/admin-season/index.ts
├── pages/admin-season/index.json
├── pages/admin-season/index.wxml
├── pages/admin-season/index.wxss
├── pages/admin-fixtures/index.ts
├── pages/admin-fixtures/index.json
├── pages/admin-fixtures/index.wxml
├── pages/admin-fixtures/index.wxss
├── pages/admin-access/index.ts
├── pages/admin-access/index.json
├── pages/admin-access/index.wxml
├── pages/admin-access/index.wxss
├── pages/admin-audit/index.ts
├── pages/admin-audit/index.json
├── pages/admin-audit/index.wxml
├── pages/admin-audit/index.wxss
└── tests/
    ├── setup.ts
    ├── env.test.ts
    ├── http.test.ts
    ├── session.test.ts
    ├── season.test.ts
    ├── submit-lock.test.ts
    ├── role.test.ts
    ├── validation.test.ts
    ├── status.test.ts
    ├── date.test.ts
    ├── teams.test.ts
    ├── competition.test.ts
    ├── stats.test.ts
    ├── admin.test.ts
    ├── fixture-management.test.ts
    └── acceptance.test.ts
```

---

### Task 1: Scaffold the Native TypeScript Mini Program

**Files:**
- Create: `miniprogram/package.json`
- Create: `miniprogram/project.config.json`
- Create: `miniprogram/tsconfig.json`
- Create: `miniprogram/vitest.config.ts`
- Create: `miniprogram/sitemap.json`
- Create: `miniprogram/typings/index.d.ts`
- Create: `miniprogram/app.ts`
- Create: `miniprogram/app.json`
- Create: `miniprogram/app.wxss`
- Create: five placeholder pages under `miniprogram/pages/{home,fixtures,teams,stats,profile}/`
- Create: `miniprogram/config/env.ts`
- Create: `miniprogram/types/api.ts`
- Create: `miniprogram/types/domain.ts`
- Create: `miniprogram/services/http.ts`
- Create: `miniprogram/state/session.ts`
- Create: `miniprogram/state/season.ts`
- Create: `miniprogram/state/submit-lock.ts`
- Create: `miniprogram/tests/setup.ts`
- Create: `miniprogram/tests/env.test.ts`
- Create: `miniprogram/tests/http.test.ts`
- Create: `miniprogram/tests/session.test.ts`
- Create: `miniprogram/tests/season.test.ts`
- Create: `miniprogram/tests/submit-lock.test.ts`
- Modify: `.gitignore`
- Modify: `README.md`

**Interfaces:**
- Produces `resolveApiBaseUrl(envVersion: string): string`.
- Produces `request<T>(path: string, options?: RequestOptions): Promise<T>`.
- Produces `ApiError` with `status`, `code`, `message`, and `requestId`.
- Produces `setUnauthorizedHandler(handler: () => void): void`, invoked once per 401 after local session cleanup.
- Produces session functions `getSession()`, `saveSession(session)`, `clearSession()`, `isAdmin()`, and `canManageTeam(teamId)`.
- Produces season functions `getSelectedSeasonId()`, `setSelectedSeasonId(seasonId)`, and `selectDefaultSeason(seasons)`.
- Produces `SubmitLock` with `tryAcquire()` and `release()`.
- Produces shared domain types for roles, statuses, seasons, teams, players, fixtures, events, appearances, rankings, standings, and audit logs.

- [ ] **Step 1: Install prerequisites and create failing runtime tests**

Install WeChat DevTools with `winget install --id Tencent.WeixinDevTools -e --accept-package-agreements --accept-source-agreements`. Create `package.json` with scripts `typecheck`, `test`, and `verify`, and pin `tdesign-miniprogram@1.17.0`, `miniprogram-api-typings@5.2.3`, `typescript@5.9.3`, `vitest@5.0.5`, and `@types/node@26.6.5`. Create tests asserting:

- `resolveApiBaseUrl("develop")` returns the local API URL.
- `resolveApiBaseUrl("release")` returns the configured HTTPS URL.
- A successful `request<T>` attaches the bearer token.
- A 401 response clears session storage and invokes the registered unauthorized handler exactly once.
- `ApiError` preserves `code`, `message`, and `requestId`.
- Session helpers persist and clear a role and `managedTeamIds`.
- `canManageTeam` returns true only for listed teams or `ADMIN`.
- Season selection persists and falls back to the first `ACTIVE` season.
- `SubmitLock.tryAcquire()` returns false until `release()` is called.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test`

Expected: FAIL because the runtime modules and project files do not exist.

- [ ] **Step 3: Implement the scaffold and shared runtime**

Create `project.config.json` with `miniprogramRoot: "./"`, `compileType: "miniprogram"`, placeholder AppID `touristappid`, `setting.useCompilerPlugins: ["typescript"]`, and `urlCheck: false` for local development. Configure `app.json` with the five tab pages and a native tab bar. Add global TDesign components needed by early pages. Add brand CSS variables in `app.wxss`.

Implement the typed HTTP wrapper with dependency-injectable request/storage adapters so Vitest can mock `wx`. Use `wx.request`, a 10-second timeout, bearer auth, JSON errors, and one-time 401 handling. Implement session/season storage and submit lock. Keep the five tab pages as compileable placeholders.

Add `miniprogram/node_modules/`, `miniprogram/miniprogram_npm/`, and `miniprogram/project.private.config.json` to `.gitignore`. Add a README section describing `cd miniprogram`, `npm install`, DevTools npm build, and the API base URL.

- [ ] **Step 4: Run typecheck and tests to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS with all runtime tests green and no TypeScript errors.

- [ ] **Step 5: Open the project in WeChat DevTools once**

Open `miniprogram/` as the project root, run “工具 -> 构建 npm”, and verify the five placeholder tabs compile without errors. Do not require real login for this step.

- [ ] **Step 6: Commit**

```powershell
git add .gitignore README.md miniprogram
git commit -m "feat: scaffold native WeChat mini program"
```

---
### Task 2: Build Shared Components and the Public Home Page

**Files:**
- Create: `miniprogram/services/competition.ts`
- Create: `miniprogram/services/stats.ts`
- Create: `miniprogram/utils/date.ts`
- Create: `miniprogram/utils/status.ts`
- Create: `miniprogram/components/app-header/*`
- Create: `miniprogram/components/empty-state/*`
- Create: `miniprogram/components/match-card/*`
- Create: `miniprogram/components/ranking-list/*`
- Create: `miniprogram/components/season-picker/*`
- Modify: `miniprogram/pages/home/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/date.test.ts`
- Test: `miniprogram/tests/status.test.ts`

**Interfaces:**
- Produces `fetchSeasons(): Promise<Season[]>`.
- Produces `fetchPublicFixtures(seasonId: number): Promise<FixtureSummary[]>`.
- Produces `fetchStandings(seasonId: number): Promise<StandingRow[]>`.
- Produces `fetchScorers(seasonId: number): Promise<ScorerRow[]>`.
- Produces date helpers `formatMatchTime(iso: string): string` and `formatDate(iso: string): string`.
- Produces label helpers `fixtureStatusLabel(status)`, `eventTypeLabel(type)`, and `roleLabel(role)`.
- Components expose properties for display data and events for taps/season changes.

- [ ] **Step 1: Write failing formatting and status tests**

Add tests asserting:

- `formatMatchTime("2026-10-27T19:30:00")` returns `10月27日 19:30`.
- `formatDate("2026-10-27")` returns `2026-10-27`.
- Fixture status labels cover `DRAFT`, `SCHEDULED`, `LIVE`, `FINISHED`, and `CANCELLED`.
- Event labels cover `GOAL`, `OWN_GOAL`, `YELLOW_CARD`, and `RED_CARD`.
- Role labels cover all three roles.
- Unknown status values fall back to the raw value rather than throwing.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/date.test.ts tests/status.test.ts`

Expected: FAIL because the helpers are absent.

- [ ] **Step 3: Implement shared display utilities, services, and components**

Implement the API service functions using the typed `request` wrapper. Build reusable TDesign-based components:

- `app-header`: season title and page subtitle.
- `season-picker`: TDesign picker bound to the season store.
- `match-card`: teams, score, kickoff, and status badge.
- `ranking-list`: rank, name, team, and metric.
- `empty-state`: TDesign empty state with optional action.

Implement the home page to fetch seasons first, resolve the selected season, then load standings, upcoming/recent fixtures, and top scorers in parallel. Add loading, error retry, and empty states. Tapping cards navigates to fixture, team, or stats pages.

- [ ] **Step 4: Run checks to verify they pass**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Open DevTools and verify the home page renders loading, empty, error, and populated states using the running backend.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add home page and shared mini program components"
```

---

### Task 3: Add Teams, Team Detail, and Player Detail

**Files:**
- Create: `miniprogram/services/teams.ts`
- Create: `miniprogram/components/team-card/*`
- Modify: `miniprogram/pages/teams/*`
- Create: `miniprogram/pages/team-detail/*`
- Create: `miniprogram/pages/player-detail/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/teams.test.ts`

**Interfaces:**
- Produces `fetchTeams(): Promise<TeamSummary[]>`.
- Produces `fetchTeam(teamId: number): Promise<TeamSummary>`.
- Produces `fetchTeamPlayers(teamId: number): Promise<PlayerSummary[]>`.
- Produces `fetchPlayer(playerId: number): Promise<PlayerSummary>`.
- Pages consume public team endpoints and do not require login.

- [ ] **Step 1: Write failing team mapping tests**

Mock the request adapter and assert:

- `fetchTeams()` requests `GET /api/v1/teams`.
- `fetchTeamPlayers(12)` requests `GET /api/v1/teams/12/players`.
- Player mapping preserves jersey number, position, and status.
- A missing player rethrows the original `ApiError`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/teams.test.ts`

Expected: FAIL because the service is absent.

- [ ] **Step 3: Implement the team pages**

Build a searchable team list, team detail with coach/home venue, player list sorted by jersey number, and a basic player detail page. Show loading, empty, and retry states. Navigate from team cards and player rows with encoded IDs. Register the new pages in `app.json`.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. In DevTools verify public team browsing without login and all navigation paths.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add team and player browsing pages"
```

---

### Task 4: Add Fixture List and Fixture Detail

**Files:**
- Modify: `miniprogram/services/competition.ts`
- Modify: `miniprogram/components/match-card/*`
- Modify: `miniprogram/pages/fixtures/*`
- Create: `miniprogram/pages/fixture-detail/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/competition.test.ts`

**Interfaces:**
- Produces `fetchFixtureDetail(fixtureId: number): Promise<FixtureDetail>`.
- Fixture detail type includes home/away teams, score, status, events, and appearances.
- The fixture list reads and writes the shared selected season.

- [ ] **Step 1: Write failing competition tests**

Mock `request` and assert:

- `fetchPublicFixtures(5)` requests `GET /api/v1/fixtures?seasonId=5`.
- `fetchFixtureDetail(9)` requests `GET /api/v1/fixtures/9`.
- A `404` response surfaces `ApiError.code === "NOT_FOUND"`.
- Event mapping preserves `relatedPlayerId` for goals.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/competition.test.ts`

Expected: FAIL because the detail function and mapping are absent.

- [ ] **Step 3: Implement fixture pages**

Add status filtering, pull-to-refresh, and season selection to the fixture list. Build a detail page with scoreboard, timeline, and appearance sections. Fetch both team rosters to map player IDs to names for events and appearances; use `球员 #ID` as a fallback when a historical player is not in the current active roster. Render goal assists as a secondary line under the scorer. Add clear empty/error states and navigation from home cards.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify all status badges, score layouts, and event timelines with real API data.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add fixture list and detail pages"
```

---

### Task 5: Add Standings, Scorers, Assists, and Appearances

**Files:**
- Modify: `miniprogram/services/stats.ts`
- Modify: `miniprogram/components/ranking-list/*`
- Modify: `miniprogram/pages/stats/*`
- Test: `miniprogram/tests/stats.test.ts`

**Interfaces:**
- Produces `fetchAssists(seasonId: number): Promise<AssistRow[]>`.
- Produces `fetchAppearances(seasonId: number): Promise<AppearanceRow[]>`.
- Stats page uses TDesign tabs for standings, scorers, assists, and appearances.
- All tabs share the selected season store.

- [ ] **Step 1: Write failing stats tests**

Mock `request` and assert:

- Each stats function calls the correct endpoint with the `seasonId` query parameter.
- Ranking order from the API is preserved.
- Zero values render as `0`, not blank.
- A season change causes one request per active tab and does not reuse old data.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/stats.test.ts`

Expected: FAIL because the assist/appearance services and tab data flow are absent.

- [ ] **Step 3: Implement the stats page**

Build four TDesign tabs with consistent ranking rows and a standings table. Keep each tab's loading/error state independent while sharing the season picker. Refresh when the season changes and when the page is shown after management edits.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify points, goal difference, scorer, assist, and appearance ordering against the API.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add stats and standings pages"
```

---
### Task 6: Add WeChat Login, Session Refresh, Profile, and Role Gate

**Files:**
- Create: `miniprogram/services/auth.ts`
- Create: `miniprogram/components/role-gate/*`
- Modify: `miniprogram/pages/profile/*`
- Modify: `miniprogram/app.ts`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/role.test.ts`

**Interfaces:**
- Produces `loginWithWechat(code: string): Promise<AuthResponse>`.
- Produces `fetchMe(): Promise<MeResponse>`.
- Produces `refreshSession(): Promise<Session | null>`.
- Produces `requireRole(roles: UserRole[]): boolean` for page-level rendering.
- `role-gate` component accepts `roles` and `teamId`, and renders its slot only when the current session passes the check.

- [ ] **Step 1: Write failing role tests**

Assert:

- Unauthenticated users cannot pass an `ADMIN` or `TEAM_MANAGER` gate.
- `VIEWER` cannot pass a management gate.
- `TEAM_MANAGER` passes only for a team in `managedTeamIds`.
- `ADMIN` passes every team gate.
- A stale local role is replaced by `refreshSession()` data from `/me`.
- A 401 during refresh clears the session and returns null.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/role.test.ts`

Expected: FAIL because the auth and role-gate logic are absent.

- [ ] **Step 3: Implement login and profile flows**

Register the app-level unauthorized handler to switch to the profile tab once. Implement `wx.login` integration and call the backend login endpoint. Persist the returned token and user data, then refresh with `/me`. Profile page shows login state, role, managed teams, management entry, and logout. On launch, restore the cached session without blocking public pages. Add a management entry only through `role-gate`.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. With a configured AppID and backend WeChat credentials, verify viewer, team manager, and admin login states. Public pages must remain accessible when login fails.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add WeChat login and role-aware profile"
```

---

### Task 7: Add Management Home, Team Profile, and Player Forms

**Files:**
- Create: `miniprogram/services/manage.ts`
- Modify: `miniprogram/pages/manage/*`
- Create: `miniprogram/pages/manage-team/*`
- Modify: `miniprogram/pages/team-detail/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/validation.test.ts`

**Interfaces:**
- Produces `updateManagedTeam(teamId, payload)`.
- Produces `createManagedPlayer(teamId, payload)`.
- Produces `updateManagedPlayer(playerId, payload)`.
- Produces `fetchAdminTeams()` and `fetchAdminTeamPlayers(teamId)`.
- Produces `validatePlayerForm(form)` and `validateTeamForm(form)`.
- Management page lists only actions allowed by the current role and managed teams.

- [ ] **Step 1: Write failing validation tests**

Assert:

- Team name is required and trimmed.
- Player name is required.
- Jersey number must be an integer from 0 to 999.
- A team manager payload cannot include `TeamStatus.INACTIVE`.
- A submit handler with an empty form does not call the API.
- A successful submit releases the submit lock, and a rejected submit also releases it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/validation.test.ts`

Expected: FAIL because validation and submit-lock integration are absent.

- [ ] **Step 3: Implement team/player management**

Build management home cards, a team editor, and a player list with create/edit popups or forms. Team managers can only select their managed teams. Administrators may select all teams and change team status. Show backend `CONFLICT` messages near the jersey field. Use the submit lock to disable buttons during requests.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify edit success, duplicate jersey error, role denial, and refresh after save.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add team and player management pages"
```

---

### Task 8: Add Admin Season and Team-Access Management

**Files:**
- Modify: `miniprogram/services/competition.ts`
- Modify: `miniprogram/services/manage.ts`
- Create: `miniprogram/pages/admin-season/*`
- Create: `miniprogram/pages/admin-access/*`
- Modify: `miniprogram/pages/manage/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/admin.test.ts`

**Interfaces:**
- Produces `fetchAdminSeasons(): Promise<Season[]>`.
- Produces `createSeason(payload)`, `addSeasonTeam(seasonId, teamId)`, and `removeSeasonTeam(seasonId, teamId)`.
- Produces `grantTeamManager(teamId, userId)` and `revokeTeamManager(teamId, userId)`.
- Admin pages call `/api/v1/admin/**` and hide all forms from non-admin users.

- [ ] **Step 1: Write failing admin service tests**

Assert:

- `fetchAdminSeasons()` calls `GET /api/v1/admin/seasons`.
- `createSeason` rejects an empty name before calling the API.
- Adding a team calls the exact nested admin path.
- `grantTeamManager(7, 21)` calls the exact manager endpoint with `userId=21`.
- A `409` conflict is preserved for display to the administrator.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/admin.test.ts`

Expected: FAIL because the admin service and page validation are absent.

- [ ] **Step 3: Implement admin season/access pages**

Add season create/list, team membership management, and team-manager grant/revoke forms. Because the backend has no user-search endpoint, the grant form accepts a numeric user ID and explains that requirement. Show audit-friendly confirmations before revocation. Gate every page with the admin role.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify DRAFT season visibility for admins, duplicate team error, and grant/revoke refresh.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add admin season and access management"
```

---
### Task 9: Add Admin Fixtures, Match Data Entry, and Audit Logs

**Files:**
- Modify: `miniprogram/services/competition.ts`
- Modify: `miniprogram/services/manage.ts`
- Create: `miniprogram/services/audit.ts`
- Create: `miniprogram/pages/admin-fixtures/*`
- Create: `miniprogram/pages/manage-fixture-data/*`
- Create: `miniprogram/pages/admin-audit/*`
- Modify: `miniprogram/pages/manage/*`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/fixture-management.test.ts`

**Interfaces:**
- Produces admin functions `createFixture`, `updateFixture`, `changeFixtureStatus`, `updateOfficialScore`, `finishFixture`, and `setFixtureLocked`.
- Produces management functions `createAppearance`, `updateAppearance`, `deleteAppearance`, `createEvent`, `updateEvent`, and `deleteEvent`.
- Produces `fetchAuditLogs(): Promise<AuditLogRow[]>`.
- Fixture-data page chooses admin or public fixture list based on role and filters manager-visible fixtures by participation.

- [ ] **Step 1: Write failing fixture-management tests**

Assert:

- Fixture creation sends `seasonId`, `homeTeamId`, and `awayTeamId`.
- `finishFixture` calls the finish endpoint with both scores.
- `FIXTURE_LOCKED` maps to “比赛已锁定，请管理员先解锁”.
- Event creation preserves nullable `relatedPlayerId`.
- Appearance/event forms do not call APIs when required IDs are missing.
- Audit log fetch calls `/api/v1/manage/audit-logs`.
- A non-admin cannot render score/finish/lock controls.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd miniprogram; npm test -- tests/fixture-management.test.ts`

Expected: FAIL because the fixture management services and page-level role rules are absent.

- [ ] **Step 3: Implement competition management and audit views**

Build the admin fixture editor with schedule, status, official score, finish, and lock actions. Build a shared match-data page with appearance and event forms. For goals, offer an optional same-team assist picker. Use TDesign pickers for teams, players, event type, and minute values. Show locked, unauthorized, and validation errors clearly. Add an audit log list with expandable JSON details.

- [ ] **Step 4: Run checks and verify in DevTools**

Run: `cd miniprogram; npm run typecheck; npm test`

Expected: PASS. Verify the full admin fixture lifecycle, manager event entry, lock rejection, and audit visibility.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram
git commit -m "feat: add fixture management and audit pages"
```

---

### Task 10: Run End-to-End Acceptance and Write Mini Program Run Guide

**Files:**
- Create: `miniprogram/README.md`
- Create: `docs/acceptance/wechat-miniprogram-acceptance.md`
- Modify: root `README.md`
- Modify: `miniprogram/package.json`
- Test: `miniprogram/tests/acceptance.test.ts`

**Interfaces:**
- Produces a repeatable `npm run verify` command that runs TypeScript checking and Vitest.
- Produces an acceptance checklist for DevTools and real-device testing.
- Documents AppID, API domains, backend credentials, npm build, and preview steps.

- [ ] **Step 1: Write the final acceptance test**

`acceptance.test.ts` must simulate the cross-cutting client flows with mocked services:

1. Unauthenticated public navigation uses the selected season.
2. A `VIEWER` login does not expose management.
3. A `TEAM_MANAGER` sees only the granted team.
4. An `ADMIN` sees all management entries.
5. A locked-fixture API error becomes the expected user message.
6. A failed submission releases the submit lock.

- [ ] **Step 2: Run the acceptance test to verify it fails**

Run: `cd miniprogram; npm test -- tests/acceptance.test.ts`

Expected: FAIL because the final acceptance harness and page-level role mapping are absent.

- [ ] **Step 3: Add final wiring and documentation**

Resolve only real cross-page integration mismatches. Write the mini-program README with:

- Node/npm requirements
- WeChat DevTools installation
- AppID placeholder replacement
- Backend API URL configuration
- TDesign npm build
- Compile/preview/test commands
- Real login prerequisites
- Production domain whitelist checklist

Document manual acceptance in `docs/acceptance/wechat-miniprogram-acceptance.md`.

- [ ] **Step 4: Run full verification**

Run:

```powershell
cd miniprogram
npm run verify
```

Expected: TypeScript check passes and all Vitest suites pass.

In WeChat DevTools:

1. Build npm.
2. Compile the project.
3. Run the public browse flow.
4. Run viewer, team-manager, and admin login flows.
5. Run create fixture -> record events -> finish -> unlock -> edit.
6. Verify management actions from a real device.

Expected: no compile errors and every checklist item passes or is recorded as a blocked environment prerequisite.

- [ ] **Step 5: Commit**

```powershell
git add README.md miniprogram docs/acceptance
git commit -m "test: add mini program acceptance and run guide"
```

---

## Spec Coverage Check

| Spec area | Covered by |
|---|---|
| Native TypeScript scaffold and TDesign | Task 1 |
| Home and shared visual components | Task 2 |
| Team and player browsing | Task 3 |
| Fixture list/detail | Task 4 |
| Standings and player rankings | Task 5 |
| WeChat login, session, roles | Task 6 |
| Team/player management | Task 7 |
| Admin season and access management | Task 8 |
| Fixture data, locking, audit | Task 9 |
| End-to-end acceptance and docs | Task 10 |
| 401 handling and request layer | Tasks 1, 6, 10 |
| Season state consistency | Tasks 1, 2, 4, 5, 10 |
| Form validation and submit lock | Tasks 1, 7, 9, 10 |

## Execution Handoff

The plan is complete after self-review. Implementation must not begin until the human partner approves this plan and chooses native or subagent-driven execution.
