# 用户球员评分与内容审核 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为已完赛比赛增加 5 星制球员评分、10 分制展示、审核制短评、敏感词过滤、举报和管理员审核后台。

**Architecture:** 后端新增独立的 `rating` 模块，使用 MySQL 保存评分、举报和敏感词；本地 DFA 与微信 `msgSecCheck` 双层审核；公开 API 只聚合审核通过的数据。小程序新增自定义星级组件、比赛详情评价区、球员赛季评分和管理审核页。

**Tech Stack:** Java 17、Spring Boot 3.4、MyBatis、MySQL 8、Flyway、JUnit 5、微信小程序 TypeScript、TDesign MiniProgram、`houbb/sensitive-word:0.29.5`。

**Spec:** `docs/superpowers/specs/2026-10-27-player-ratings-moderation-design.md`

## Global Constraints

- 用户输入必须为整数 `1–5` 星。
- API 返回 `score = stars × 2`，总分 10 分。
- 每条评分必须绑定 `fixture_id + player_id + user_id`，且该组合唯一。
- 只有 `FINISHED` 比赛、且有 `fixture_appearance` 的球员可以评分。
- 短评可为空；非空时长度为 1–100 个字符。
- 首次提交后 24 小时内允许修改一次，之后锁定。
- `PENDING`、`REJECTED`、`HIDDEN` 评分不得进入公开均分、评分人数和公开短评。
- 本地敏感词策略优先级为 `BLOCK > REVIEW > PASS`。
- 生产环境微信内容安全接口失败时必须进入 `PENDING`，禁止自动公开。
- 公开接口不得返回用户 OpenID。
- 每个用户 10 分钟内最多提交 20 次评分、最多举报 10 次。
- 所有管理员审核和敏感词变更必须写审计日志。
- 不得在客户端或仓库中写入 AppSecret、access_token。
- 加入本功能后，小程序基本信息必须声明包含经审核的用户评价；不能继续声明“不提供用户发布内容服务”。
- 集成测试统一使用 `campus_football_test`，禁止指向日常开发库。

## Review Focus

- 待审核、拒绝或隐藏的评分是否可能进入公开均分和评论列表。
- 微信内容安全接口超时或返回风险时，短评是否仍可能直接公开。
- 并发提交是否可能绕过“一人一评分”或修改一次限制。
- 公开 DTO、日志和错误响应是否可能泄露 OpenID 或审核原因。
- 增加 UGC 后，基础信息、隐私说明、举报入口和包体积是否同步达标。

---

### Task 1: 数据库模型与领域类型

**Files:**
- Create: `src/main/resources/db/migration/V4__add_player_ratings_and_moderation.sql`
- Create: `src/main/java/com/campusfootball/rating/RatingStatus.java`
- Create: `src/main/java/com/campusfootball/rating/RatingReportStatus.java`
- Create: `src/main/java/com/campusfootball/rating/SensitiveTermAction.java`
- Create: `src/main/java/com/campusfootball/rating/PlayerMatchRating.java`
- Create: `src/main/java/com/campusfootball/rating/RatingReport.java`
- Create: `src/main/java/com/campusfootball/rating/SensitiveTerm.java`
- Test: `src/test/java/com/campusfootball/rating/RatingSchemaIntegrationTest.java`

**Interfaces:**
- Produces: `PlayerMatchRating` with `id`, `fixtureId`, `playerId`, `teamId`, `userId`, `stars`, `comment`, `status`, `moderationReason`, `editsUsed`, timestamps.
- Produces: `RatingReport` with `id`, `ratingId`, `reporterUserId`, `reason`, `status`, timestamps.
- Produces: `SensitiveTerm` with `id`, `term`, `category`, `action`, `enabled`, `createdBy`, timestamps.
- Produces enums: `RatingStatus { VISIBLE, PENDING, REJECTED, HIDDEN }`, `RatingReportStatus { OPEN, REVIEWED, DISMISSED }`, `SensitiveTermAction { BLOCK, REVIEW }`.

- [ ] **Step 1: Write failing schema tests**

`RatingSchemaIntegrationTest` asserts:
- `player_match_rating` has unique key on `(fixture_id, player_id, user_id)`.
- `rating_report` has unique key on `(rating_id, reporter_user_id)`.
- `sensitive_term` has unique `term`.
- Stars outside 1–5 fail.
- `edits_used` outside 0–1 fails.
- All enum-like columns reject invalid values.

- [ ] **Step 2: Run schema test and verify failure**

Run:

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:TEST_DB_USERNAME='campus'
$env:TEST_DB_PASSWORD='campus'
.\mvnw.cmd -q '-Dtest=RatingSchemaIntegrationTest' test
```

Expected: FAIL because migration and tables do not exist.

- [ ] **Step 3: Implement migration and entity classes**

Migration creates the three tables with foreign keys to `fixture`, `player`, `team`, `app_user`, and indexes needed by aggregation and moderation queries.

- [ ] **Step 4: Re-run schema test**

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/resources/db/migration/V4__add_player_ratings_and_moderation.sql src/main/java/com/campusfootball/rating src/test/java/com/campusfootball/rating/RatingSchemaIntegrationTest.java
git commit -m "feat: add rating and moderation schema"
```

---

### Task 2: 敏感词引擎与管理接口

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/campusfootball/rating/ModerationDecision.java`
- Create: `src/main/java/com/campusfootball/rating/ModerationResult.java`
- Create: `src/main/java/com/campusfootball/rating/SensitiveTermMapper.java`
- Create: `src/main/resources/mappers/SensitiveTermMapper.xml`
- Create: `src/main/java/com/campusfootball/rating/SensitiveTermService.java`
- Create: `src/main/java/com/campusfootball/rating/SensitiveTermAdminController.java`
- Modify: `src/main/java/com/campusfootball/config/SecurityConfig.java`
- Modify: `src/main/java/com/campusfootball/audit/AuditAction.java`
- Test: `src/test/java/com/campusfootball/rating/SensitiveTermServiceTest.java`
- Test: `src/test/java/com/campusfootball/rating/SensitiveTermAdminIntegrationTest.java`

**Interfaces:**
- Produces: `SensitiveTermService.classify(String text): ModerationResult`.
- Produces: `SensitiveTermService.refreshTerms(): void`.
- Produces admin CRUD/import methods:
  - `create(long actorUserId, String term, String category, SensitiveTermAction action)`
  - `update(long actorUserId, long id, String category, SensitiveTermAction action, boolean enabled)`
  - `delete(long actorUserId, long id)`
  - `importTerms(long actorUserId, List<String> terms, SensitiveTermAction action)`
- `ModerationResult` contains `decision`, `matchedTerm`, and user-safe `reason`.

- [ ] **Step 1: Write failing service tests**

Assertions:
- Built-in library detects common prohibited terms.
- A DB `REVIEW` term produces `REVIEW`.
- A DB `BLOCK` term produces `BLOCK`.
- If both block and review terms match, `BLOCK` wins.
- Full-width, uppercase, extra spaces, and repeated-character variants are normalized.
- Empty text returns `PASS`.

- [ ] **Step 2: Run service tests and verify failure**

Run:

```powershell
.\mvnw.cmd -q '-Dtest=SensitiveTermServiceTest' test
```

Expected: FAIL because service and dependency do not exist.

- [ ] **Step 3: Implement sensitive-term engine and admin API**

Add `com.github.houbb:sensitive-word:0.29.5`. Build an in-memory matcher from enabled DB terms and refresh it after every CRUD/import operation. Use `AuditAction.CREATE`, `UPDATE`, and `DELETE` for term changes.

- [ ] **Step 4: Write and run admin integration tests**

`SensitiveTermAdminIntegrationTest` asserts:
- Non-admin gets 403.
- Admin can create, update, disable, delete, and batch import.
- Duplicate term returns 409.
- Import over 5,000 terms returns 400.
- Disabled `BLOCK` term stops blocking.
- Audit records exist for changes.

- [ ] **Step 5: Run tests and commit**

```powershell
.\mvnw.cmd -q '-Dtest=SensitiveTermServiceTest,SensitiveTermAdminIntegrationTest' test
git add pom.xml src/main/java/com/campusfootball/rating src/main/resources/mappers/SensitiveTermMapper.xml src/main/java/com/campusfootball/config/SecurityConfig.java src/main/java/com/campusfootball/audit/AuditAction.java src/test/java/com/campusfootball/rating
git commit -m "feat: add sensitive term moderation"
```

---

### Task 3: 微信内容安全客户端

**Files:**
- Create: `src/main/java/com/campusfootball/rating/WechatContentSecurityClient.java`
- Create: `src/main/java/com/campusfootball/rating/WechatContentSecurityResult.java`
- Create: `src/main/java/com/campusfootball/rating/WechatAccessTokenService.java`
- Create: `src/main/java/com/campusfootball/rating/ContentModerationService.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/campusfootball/rating/WechatContentSecurityClientTest.java`

**Interfaces:**
- Produces: `WechatContentSecurityClient.check(String openid, String content): ModerationDecision`.
- Produces: `WechatAccessTokenService.getAccessToken(): String`.
- Produces: `ContentModerationService.evaluate(long userId, String content): ModerationResult`.
- `WechatContentSecurityResult` maps `suggest=pass/review/risky`, `errcode`, and `errmsg`.

- [ ] **Step 1: Write failing mocked HTTP tests**

Use `MockRestServiceServer` to assert:
- Access token is fetched once and reused until near expiry.
- `text/plain` JSON responses are parsed correctly.
- `suggest=pass` maps to `PASS`.
- `suggest=review` maps to `REVIEW`.
- `suggest=risky` maps to `BLOCK`.
- Transport error maps to `REVIEW` when fail-closed is enabled.
- Missing AppID/AppSecret maps to `REVIEW`.
- Local `BLOCK` short-circuits without a WeChat call.
- Local `REVIEW` short-circuits without a WeChat call.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\mvnw.cmd -q '-Dtest=WechatContentSecurityClientTest' test
```

Expected: FAIL because client and token cache do not exist.

- [ ] **Step 3: Implement token cache and msgSecCheck client**

Read WeChat responses as `String`, then parse with Jackson. Cache token with expiry and a five-minute safety margin. Never log AppSecret, access_token, openid, or full comment text.

- [ ] **Step 4: Run tests and commit**

```powershell
.\mvnw.cmd -q '-Dtest=WechatContentSecurityClientTest' test
git add src/main/java/com/campusfootball/rating/WechatContentSecurityClient.java src/main/java/com/campusfootball/rating/WechatContentSecurityResult.java src/main/java/com/campusfootball/rating/WechatAccessTokenService.java src/main/java/com/campusfootball/rating/ContentModerationService.java src/main/resources/application.yml src/test/java/com/campusfootball/rating/WechatContentSecurityClientTest.java
git commit -m "feat: add WeChat content security client"
```

---

### Task 4: 评分提交、修改与审核决策

**Files:**
- Create: `src/main/java/com/campusfootball/rating/RatingMapper.java`
- Create: `src/main/resources/mappers/RatingMapper.xml`
- Create: `src/main/java/com/campusfootball/rating/RatingService.java`
- Create: `src/main/java/com/campusfootball/rating/RatingSubmissionResult.java`
- Create: `src/main/java/com/campusfootball/config/ClockConfig.java`
- Modify: `src/main/java/com/campusfootball/audit/AuditAction.java`
- Test: `src/test/java/com/campusfootball/rating/RatingServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `ContentModerationService.evaluate(long userId, String content): ModerationResult`.
- Produces: `RatingService.submit(long userId, long fixtureId, long playerId, int stars, String comment): RatingSubmissionResult`.
- Produces: `RatingService.findMine(long userId, long fixtureId, long playerId): Optional<PlayerMatchRating>`.
- `RatingSubmissionResult` contains `id`, `stars`, `score`, `status`, `moderationReason`, `canEdit`.

- [ ] **Step 1: Write failing rating-domain tests**

Assertions:
- Unauthenticated calls fail at security layer.
- Non-finished fixture returns 409.
- Player without appearance returns 409.
- `stars=0` and `stars=6` return 400.
- First submission creates one row and returns `score=stars×2`.
- Empty comment returns `VISIBLE`.
- `REVIEW` comment returns `PENDING`.
- `BLOCK` comment returns `REJECTED`.
- Duplicate target updates within 24 hours and increments `editsUsed`.
- Second edit returns 409.
- Edit after 24 hours returns 409.
- Concurrent duplicate submissions still produce one row.
- Counts over 20 submissions in ten minutes return 429.
- Public aggregate excludes all non-`VISIBLE` rows.

- [ ] **Step 2: Run tests and verify failure**

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:TEST_DB_USERNAME='campus'
$env:TEST_DB_PASSWORD='campus'
.\mvnw.cmd -q '-Dtest=RatingServiceIntegrationTest' test
```

Expected: FAIL because service and mapper do not exist.

- [ ] **Step 3: Implement rating service**

Use a transaction. Validate fixture status, appearance membership, player/team consistency, stars, comment length, rate limit, edit window, and unique target. Run local moderation first; only call WeChat for a non-empty `PASS` comment. Map failures to `REVIEW`.

- [ ] **Step 4: Run tests and commit**

```powershell
.\mvnw.cmd -q '-Dtest=RatingServiceIntegrationTest' test
git add src/main/java/com/campusfootball/rating/RatingMapper.java src/main/resources/mappers/RatingMapper.xml src/main/java/com/campusfootball/rating/RatingService.java src/main/java/com/campusfootball/rating/RatingSubmissionResult.java src/main/java/com/campusfootball/config/ClockConfig.java src/main/java/com/campusfootball/audit/AuditAction.java src/test/java/com/campusfootball/rating/RatingServiceIntegrationTest.java
git commit -m "feat: add player rating workflow"
```

---

### Task 5: 公开评分、个人评分与举报 API

**Files:**
- Create: `src/main/java/com/campusfootball/rating/RatingController.java`
- Create: `src/main/java/com/campusfootball/rating/RatingViewMapper.java`
- Create: `src/main/resources/mappers/RatingViewMapper.xml`
- Create: `src/main/java/com/campusfootball/rating/RatingSummary.java`
- Create: `src/main/java/com/campusfootball/rating/RatingCommentView.java`
- Modify: `src/main/java/com/campusfootball/config/SecurityConfig.java`
- Test: `src/test/java/com/campusfootball/rating/RatingPublicApiIntegrationTest.java`

**Interfaces:**
- Produces: `RatingService.report(long reporterUserId, long ratingId, String reason)`.
- Produces:
  - `GET /api/v1/fixtures/{fixtureId}/player-ratings`
  - `GET /api/v1/fixtures/{fixtureId}/players/{playerId}/ratings`
  - `GET /api/v1/players/{playerId}/rating-summary`
  - `GET /api/v1/fixtures/{fixtureId}/players/{playerId}/rating/me`
  - `PUT /api/v1/fixtures/{fixtureId}/players/{playerId}/rating`
  - `POST /api/v1/ratings/{ratingId}/report`

- [ ] **Step 1: Write failing public API tests**

Assertions:
- Public summaries include only `VISIBLE` ratings.
- Average score is `AVG(stars)×2` with one decimal.
- Star distribution counts match test data.
- Public comments are paginated, newest first, and contain no user ID or OpenID.
- `rating/me` requires authentication.
- Upsert requires authentication.
- Report requires authentication and rejects self-report.
- Duplicate report returns 409.
- Report over 10 per ten minutes returns 429.

- [ ] **Step 2: Run API tests and verify failure**

```powershell
.\mvnw.cmd -q '-Dtest=RatingPublicApiIntegrationTest' test
```

Expected: FAIL because endpoints do not exist.

- [ ] **Step 3: Implement API and aggregate queries**

Implement one fixture-level aggregate query and one paginated comments query. Do not expose OpenID or raw user IDs. Add DTOs with only nickname, stars, score, comment, and timestamps. Put authenticated rating routes before the public `GET /api/v1/fixtures/**` matcher in `SecurityConfig` so `rating/me` is not accidentally public.

- [ ] **Step 4: Run tests and commit**

```powershell
.\mvnw.cmd -q '-Dtest=RatingPublicApiIntegrationTest' test
git add src/main/java/com/campusfootball/rating/RatingController.java src/main/java/com/campusfootball/rating/RatingViewMapper.java src/main/resources/mappers/RatingViewMapper.xml src/main/java/com/campusfootball/rating/RatingSummary.java src/main/java/com/campusfootball/rating/RatingCommentView.java src/main/java/com/campusfootball/config/SecurityConfig.java src/test/java/com/campusfootball/rating/RatingPublicApiIntegrationTest.java
git commit -m "feat: expose player rating APIs"
```

---

### Task 6: 管理员审核后台 API

**Files:**
- Create: `src/main/java/com/campusfootball/rating/RatingAdminController.java`
- Create: `src/main/java/com/campusfootball/rating/RatingModerationService.java`
- Modify: `src/main/java/com/campusfootball/rating/RatingMapper.java`
- Modify: `src/main/resources/mappers/RatingMapper.xml`
- Modify: `src/main/java/com/campusfootball/audit/AuditAction.java`
- Test: `src/test/java/com/campusfootball/rating/RatingModerationIntegrationTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/admin/ratings?status=PENDING|REPORTED|HIDDEN&page=0&size=20`
  - `PUT /api/v1/admin/ratings/{ratingId}/status`
- Rating status request uses `VISIBLE`, `REJECTED`, or `HIDDEN` with optional reason.

- [ ] **Step 1: Write failing moderation tests**

Assertions:
- Non-admin receives 403.
- Admin sees pending and reported records.
- Approving pending rating makes it public and updates avg.
- Rejecting pending rating keeps it excluded.
- Hiding visible rating removes it from avg/comments.
- Restoring hidden rating adds it back.
- Invalid transition returns 409.
- Moderation writes audit log with old and new status.
- Report is marked `REVIEWED` when admin resolves it.

- [ ] **Step 2: Run tests and verify failure**

```powershell
.\mvnw.cmd -q '-Dtest=RatingModerationIntegrationTest' test
```

Expected: FAIL because moderation endpoints do not exist.

- [ ] **Step 3: Implement moderation service and admin API**

Use a transaction for rating status, report resolution, and audit write. Do not hard-delete user ratings in V1.

- [ ] **Step 4: Run tests and commit**

```powershell
.\mvnw.cmd -q '-Dtest=RatingModerationIntegrationTest' test
git add src/main/java/com/campusfootball/rating/RatingAdminController.java src/main/java/com/campusfootball/rating/RatingModerationService.java src/main/java/com/campusfootball/rating/RatingMapper.java src/main/resources/mappers/RatingMapper.xml src/main/java/com/campusfootball/audit/AuditAction.java src/test/java/com/campusfootball/rating/RatingModerationIntegrationTest.java
git commit -m "feat: add rating moderation APIs"
```

---

### Task 7: 小程序评分服务、类型与星级组件

**Files:**
- Create: `miniprogram/services/ratings.ts`
- Create: `miniprogram/utils/rating.ts`
- Create: `miniprogram/components/star-rating/index.ts`
- Create: `miniprogram/components/star-rating/index.wxml`
- Create: `miniprogram/components/star-rating/index.wxss`
- Create: `miniprogram/components/star-rating/index.json`
- Modify: `miniprogram/types/domain.ts`
- Test: `miniprogram/tests/ratings.test.ts`
- Test: `miniprogram/tests/rating.test.ts`

**Interfaces:**
- Produces:
  - `starsToScore(stars: number): number` returning `stars * 2`.
  - `formatRatingScore(score: number | null): string` returning `-` or one decimal plus `/10`.
  - `starLabel(stars: number): string` returning five-character filled/empty stars.
- Star component emits `change` with `{ stars: 1..5 }`, supports `value`, `readonly`, and `disabled`.

- [ ] **Step 1: Write failing frontend tests**

Assertions:
- `1..5` stars map to `2,4,6,8,10`.
- Invalid star values are clamped or rejected.
- Average score formatting is one decimal and includes `/10`.
- Star label uses five glyphs.
- Service requests match the API paths and payloads.
- Star component does not emit changes in readonly mode.

- [ ] **Step 2: Run tests and verify failure**

```powershell
npm --prefix miniprogram run verify
```

Expected: FAIL because files do not exist.

- [ ] **Step 3: Implement services, utilities, types, and component**

Do not add a large UI dependency. Use a local five-star component with five tap targets. Keep text accessible with labels for each star.

- [ ] **Step 4: Run tests and commit**

```powershell
npm --prefix miniprogram run verify
git add miniprogram/services/ratings.ts miniprogram/utils/rating.ts miniprogram/components/star-rating miniprogram/types/domain.ts miniprogram/tests/ratings.test.ts miniprogram/tests/rating.test.ts
git commit -m "feat: add mini program rating primitives"
```

---

### Task 8: 比赛详情评分区、评分弹层与举报

**Files:**
- Modify: `miniprogram/pages/fixture-detail/index.ts`
- Modify: `miniprogram/pages/fixture-detail/index.wxml`
- Modify: `miniprogram/pages/fixture-detail/index.wxss`
- Modify: `miniprogram/pages/fixture-detail/index.json`
- Modify: `miniprogram/services/ratings.ts`
- Test: `miniprogram/tests/ratings.test.ts`

**Interfaces:**
- Consumes: rating APIs and `star-rating` component.
- Produces `FixtureRatingView` and methods `openRating`, `submitRating`, `openRatingComments`, `openReport`.

- [ ] **Step 1: Write failing view-model tests**

Extract pure helpers where practical and assert:
- Finished fixture rating area is enabled.
- Non-finished fixture shows “比赛结束后开放评分”.
- Existing own rating pre-fills stars and comment.
- `PENDING`, `REJECTED`, and `HIDDEN` status messages are distinct.
- Average and star distribution map to display values.

- [ ] **Step 2: Run tests and verify failure**

```powershell
npm --prefix miniprogram run verify
```

Expected: FAIL because rating UI helpers and service methods do not exist.

- [ ] **Step 3: Implement fixture rating UI**

Add a rating section below the scoreboard, a bottom-sheet input with five stars and 100-character textarea, own-rating state, comment list, and report action. Do not show pending/rejected content publicly.

- [ ] **Step 4: Run tests and commit**

```powershell
npm --prefix miniprogram run verify
git add miniprogram/pages/fixture-detail miniprogram/services/ratings.ts miniprogram/tests/ratings.test.ts
git commit -m "feat: add match player rating UI"
```

---

### Task 9: 球员赛季评分与管理审核页面

**Files:**
- Modify: `miniprogram/pages/player-detail/index.ts`
- Modify: `miniprogram/pages/player-detail/index.wxml`
- Modify: `miniprogram/pages/player-detail/index.wxss`
- Create: `miniprogram/pages/admin-ratings/index.ts`
- Create: `miniprogram/pages/admin-ratings/index.wxml`
- Create: `miniprogram/pages/admin-ratings/index.wxss`
- Create: `miniprogram/pages/admin-ratings/index.json`
- Modify: `miniprogram/pages/manage/index.ts`
- Modify: `miniprogram/pages/manage/index.wxml`
- Modify: `miniprogram/app.json`
- Test: `miniprogram/tests/ratings.test.ts`

**Interfaces:**
- Player detail consumes `GET /api/v1/players/{playerId}/rating-summary`.
- Admin ratings page consumes moderation and sensitive-term APIs.

- [ ] **Step 1: Write failing tests**

Assertions:
- Player summary displays average and count.
- Admin page requests are unavailable to non-admin via role gate.
- Status filters map to `PENDING`, `REPORTED`, `HIDDEN`.
- Admin actions send the correct status payload.

- [ ] **Step 2: Run tests and verify failure**

```powershell
npm --prefix miniprogram run verify
```

Expected: FAIL because pages and service functions do not exist.

- [ ] **Step 3: Implement player summary and admin page**

Add an “评分审核” card to the management home. Use local segmented controls rather than adding a new large component. Add sensitive-word import text entry and action selection.

- [ ] **Step 4: Run tests and package check**

```powershell
npm --prefix miniprogram run verify
```

Expected: all tests pass and projected package remains <= 1.5 MB.

- [ ] **Step 5: Commit**

```powershell
git add miniprogram/pages/player-detail miniprogram/pages/admin-ratings miniprogram/pages/manage miniprogram/app.json miniprogram/tests/ratings.test.ts
git commit -m "feat: add player summaries and rating moderation UI"
```

---

### Task 10: 合规文案、验收与整体验证

**Files:**
- Modify: `docs/acceptance/wechat-miniprogram-acceptance.md`
- Create: `docs/acceptance/ratings-moderation-acceptance.md`
- Modify: `README.md`
- Test: all backend and mini program tests

**Interfaces:**
- Produces final operator checklist for UGC compliance and content security.

- [ ] **Step 1: Add acceptance checklist**

Checklist must include:
- 5-star/10-point mapping
- Logged-in rating flow
- 100-character limit
- Sensitive word block/review/pending states
- WeChat content security pass/review/error states
- Admin approve/reject/hide/restore
- Report flow
- Hidden ratings excluded from averages
- No OpenID in public responses
- Updated mini program description and privacy guide
- Report entry present

- [ ] **Step 2: Update operator-facing description**

Document that the mini program now provides moderated post-match player ratings and comments, and that the prior “no user-generated content” statement must be removed.

- [ ] **Step 3: Run complete backend verification**

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
$env:TEST_DB_USERNAME='campus'
$env:TEST_DB_PASSWORD='campus'
.\mvnw.cmd -q verify
```

Expected: PASS with all existing and new backend tests.

- [ ] **Step 4: Run complete frontend verification**

```powershell
npm --prefix miniprogram run verify
```

Expected: PASS with all existing and new mini program tests.

- [ ] **Step 5: Manual acceptance**

Verify in WeChat Developer Tools:
- Finished match shows rating section.
- Five stars submit and display as 10-point score.
- Clean comment becomes visible.
- REVIEW comment becomes pending.
- Admin can approve it.
- Hidden/rejected comments disappear from public view.
- Report flow works.
- Package size remains under 1.5 MB and media under 200 KB.

- [ ] **Step 6: Commit**

```powershell
git add README.md docs/acceptance
git commit -m "docs: add rating moderation acceptance checklist"
```
