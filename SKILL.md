---
name: wechat-campus-football-builder
description: Build, review, debug, or ship a WeChat campus football mini program with a Spring Boot/MySQL backend. Use for league or cup competition models, role-scoped editing, fixtures, lineups, match events, player/referee ratings, moderation, or WeChat login and release troubleshooting.
metadata:
  short-description: Build a campus football mini program end to end
---

# WeChat Campus Football Builder

Use this skill to produce a working native WeChat mini program and a trustworthy competition backend, not a static page mockup or a copied demo. Preserve the host repository's existing stack and conventions; the reference implementation uses native TypeScript mini-program pages, TDesign, Spring Boot, MyBatis, MySQL, Flyway, JWT, and Vitest/JUnit tests.

## Read the relevant reference

- For entities, competition hierarchy, stats, lineups, or knockout rules, read `references/domain-model-and-invariants.md`.
- For login, domains, UGC review, environment configuration, and release, read `references/wechat-integration-and-release.md`.
- For implementation order, test coverage, debugging, and acceptance, read `references/engineering-playbook.md`.

Read only the references needed for the current slice. Inspect the actual repository before assuming any file, endpoint, or migration exists.

## Non-negotiable product model

- Model competition structure as `competition -> season -> stage -> group/knockout`, not as one hard-coded league table.
- Calculate standings from finished fixtures. Do not persist a second source of truth for points or goal difference.
- Keep official scores separate from match events. Recording a goal must not silently rewrite the score.
- Keep `fixture_appearance` separate from `fixture_event`. Appearance answers who played and for how long; events answer what happened.
- Store assists as a relationship on a goal event when that matches the chosen model; do not create a second unrelated assist record that can disagree with the goal.
- On completion, support an explicit data lock. Every write path must reject edits while locked, including admin and team-manager paths.
- Enforce authorization on the server from fresh database state. `ADMIN` manages globally, `TEAM_MANAGER` is restricted to assigned teams and fixtures, and `VIEWER` is read-only.
- Validate player-team, fixture-team, event-type, minute, duplicate, and score invariants in the service layer as well as with database constraints.
- Public list and detail endpoints must exclude draft or otherwise unpublished content.

## Lineup and match-data UX

- Let a manager select starters from the active team roster.
- Show the same full roster in the substitute area. Starters remain visible there, checked and locked, rather than disappearing.
- Save one team's lineup with one transactional bulk request. Do not require one-by-one appearance entry unless the user explicitly asks for it.
- A starter defaults to 90 minutes; a substitute carries an entered appearance duration. Reject duplicate starter/substitute selections and players from another team.
- If a player already has an event, require that player to remain in the saved lineup so event and appearance data do not diverge.
- Sort roster choices by jersey number, then name. In this product, label players as `number name` such as `10 姆巴佩`; do not append the Chinese word `号` unless the host project already uses that convention.
- Provide a clear selected, unselected, and locked visual state. Prevent a locked starter row from being toggled as a substitute.
- Add a single audit record for the full lineup replacement, including before/after state.

## Competition and knockout rules

- Support group-specific standings and filter fixtures by season, stage, and group.
- For single-match knockout ties, require a unique winner; if regular time is drawn, require a valid penalty result.
- Propagate winners through a configured bracket, and refuse to overwrite an upstream result when downstream fixture data already depends on it.
- Keep brackets, penalty scores, seeding rules, and champion state under `ADMIN` control.
- Make tie-break rules explicit and deterministic. If the rules cannot resolve a seed, require administrator confirmation instead of silently inventing an order.

## Ratings and user-generated content

- Treat ratings with short comments as UGC. A comment must pass local sensitive-term policy and WeChat content security before appearing publicly.
- A 5-star input may map to a 10-point display as `stars * 2`, but store the canonical value and conversion rule once.
- Enforce one rating per user and target, the allowed edit window, public-visibility rules, reporting, rate limits, and moderation audits.
- Referee ratings may use the same score scale without comments when requested; do not run comment moderation for a rating that has no text.
- Never auto-publish content when the content-security service fails in production. Fail closed and mark it for review.
- Remove any old product copy claiming the service has no UGC after adding ratings or comments.

## Implement in vertical slices

For each slice, define the observable outcome and acceptance criteria first. Then implement in this order:

1. Inspect the current schema, domain types, API services, page registration, tests, and build commands.
2. Add or revise the database migration and constraints.
3. Implement transactional service logic, authorization, validation, and audit behavior.
4. Expose the smallest API contract needed by the UI.
5. Add typed mini-program service calls and page state/view updates.
6. Add integration tests for security and data invariants plus focused frontend tests for request payloads and pure mappings.
7. Run the repository's real typecheck, tests, compile/build, and runtime smoke check.
8. Restart the local backend when endpoint code changed; do not assume hot reload occurred.

Do not ship a UI control until the backend route, permission check, request payload, and failure state all exist. Do not add a duplicate page or form when an existing workflow can be extended cleanly.

## Testing expectations

Cover at least:

- Successful create/update/read path.
- Unauthorized role and wrong-team access.
- Locked fixture writes.
- Invalid cross-entity references.
- Duplicate and conflict cases.
- Transaction rollback or preserved prior state on failure.
- Audit entries for privileged writes.
- Leaderboard or standings calculations from known fixture data.
- Mini-program typecheck and request-payload tests.

Use a dedicated test database. Never point integration tests at the daily development database.

## Working style

- Prefer direct implementation over abstract architecture prose.
- Preserve existing user-facing language and visual conventions unless the requested change requires otherwise.
- Use real debug logs as evidence. Distinguish WeChat developer-tool warnings from actual HTTP or compile failures.
- Never place `AppSecret`, tokens, personal OpenIDs, or production credentials in the mini program, repository, screenshots, or articles.
- If a release check cannot be run, state exactly which command or environment blocked it rather than claiming success.

## Deliverable checklist

- Database migration is repeatable and backward-compatible where required.
- Backend and mini-program tests pass.
- All new pages are registered and all used components are packaged.
- Public and management roles see the correct navigation and actions.
- Backend logs show a clean startup and a smoke request succeeds.
- WeChat request domains, HTTPS, privacy disclosure, moderation, and UGC product descriptions are ready for the target environment.
