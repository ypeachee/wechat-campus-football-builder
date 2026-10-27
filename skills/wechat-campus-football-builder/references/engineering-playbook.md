# Engineering playbook

## Start from the repository, not the prompt alone

Before editing:

- Read `README`, build files, schema migrations, app configuration, domain types, route registration, service wrappers, and the tests nearest the requested change.
- Find the actual verification commands. Do not substitute generic commands when the repository has wrappers or a custom test database.
- Identify whether the work is a data-model change, a workflow change, a UI-only change, or a release/configuration change.
- Search for an existing page, endpoint, or component before creating a new one.

A useful opening move is to trace one complete vertical slice, for example fixture detail: database table -> mapper -> service -> controller -> API type -> mini-program service -> page state -> WXML -> test.

## Slice template

Write a short implementation contract before coding:

```text
User outcome:
Affected roles:
Data invariants:
API request/response:
UI states:
Failure cases:
Tests:
Verification command:
```

Example for bulk lineup:

```text
Outcome: one manager saves a full team lineup in one action.
Roles: ADMIN any participating team; TEAM_MANAGER assigned team only.
Invariants: no cross-team players; no starter/substitute duplicates; event players retained.
API: PUT .../teams/{teamId}/appearances with starters and substitutes.
UI: starter roster and substitute roster both show full team; starters locked in substitute list.
Failures: 403 unauthorized, 409 data conflict, 403 locked, 400 invalid minutes.
Tests: successful save, replacement, duplicates, cross-team, event-player retention, permissions.
```

If the request is ambiguous about an irreversible product rule, make the smallest reasonable assumption and state it. Do not invent a second workflow to cover both interpretations.

## Backend implementation rules

- Add a Flyway migration instead of editing an already-applied production migration.
- Put transactional behavior in the service layer.
- Use database constraints for durable invariants, but do not rely on raw database exceptions for user-facing validation.
- Return stable error codes such as `CONFLICT`, `FORBIDDEN`, `FIXTURE_LOCKED`, and `VALIDATION_FAILED`.
- Record audit information for every privileged mutation.
- Batch replacements should validate everything before deleting the old records, so a failed request leaves the prior lineup intact.
- Avoid N+1 queries in standings, ratings, and leaderboards.
- Keep deleted/hidden moderation content out of public aggregates unless the product explicitly says otherwise.

## Mini-program implementation rules

- Keep page data serializable. Do not place class instances or `Map` objects in `setData`.
- Use page state for selection and display, and service functions for HTTP.
- Keep custom components localized and registered in page JSON.
- A custom component event still needs the correct dataset on the component if the handler reads `event.currentTarget.dataset`.
- When a tap row contains an input, stop the tap from bubbling or the input interaction will toggle the row.
- Reflect server truth after save by reloading the fixture or entity; do not assume the local optimistic state is canonical.
- Show clear states for loading, empty, locked, unauthorized, validation failure, and successful save.
- For a list of players, use the same label formatter everywhere to avoid one screen showing `10 姆巴佩` and another showing `10号 姆巴佩`.

## Suggested tests

### Backend integration

- Admin success and team-manager success.
- Team manager wrong team -> `403`.
- Viewer/non-owner -> `403`.
- Locked fixture -> documented lock error.
- Cross-entity player/team mismatch -> `409` or `400` according to contract.
- Duplicate lineup rating/target -> `409`.
- Existing event player removed from lineup -> `409` and old lineup preserved.
- Replacing a lineup removes only the selected team's previous rows.
- Audit row created once.
- Standings/statistics computed only from eligible finished fixtures.

### Mini-program

- Typecheck.
- Request path and payload for every new service function.
- Pure mapping helpers: jersey labels, score conversion, status labels, bracket mapping.
- Page registration and component-path tests.
- Session/role gate tests.
- Failure-message mapping tests.

## Debugging sequence

1. Reproduce with the smallest request or page action.
2. Read the first real error, not the longest stack trace.
3. Determine whether it is compile, route registration, auth, backend validation, database, network/domain, or UI state.
4. Inspect server logs and response body.
5. Fix the narrowest layer that owns the invariant.
6. Re-run the targeted test before the full suite.
7. Restart long-running processes after backend environment or endpoint changes.
8. Smoke-test the public endpoint and one protected endpoint.

## Common failure patterns

| Symptom | Likely cause | Correct fix |
|---|---|---|
| Page cannot be navigated to | Page missing from `app.json` | Register route; avoid duplicate ad hoc pages |
| `401` on WeChat login | AppID/secret mismatch, stale process, invalid code, WeChat API failure | Fix backend configuration and restart |
| Trial login works locally only | API URL still points to localhost | Configure HTTPS trial URL and legal domain |
| Team manager can edit another team | Authorization only in UI | Recheck server-side team/fixture ownership |
| Existing event player disappears from lineup | Bulk replacement lacks event-reference guard | Require event players in new lineup |
| Input tap toggles player selection | Event bubbles to row | Use a catch handler around the input |
| Ratings show publicly immediately | Moderation state not applied to aggregate/query | Filter to visible approved content |
| Rating form shows on unfinished match | Eligibility rule only enforced client-side | Enforce finished status in service and API |
| Build passes but feature fails at runtime | Only unit tests were run | Add integration/smoke verification |
| Package too large | Component library or assets over-included | Exclude unused folders/media and localized unnecessary components |

## Verification standard

Before saying a feature is complete:

- Run the exact frontend and backend verify commands from the repository.
- Confirm tests pass, not merely compile.
- Confirm the running server is new code when an endpoint changed.
- Confirm the working tree contains only intended changes.
- If developer-tool compilation or real-device testing is blocked, state the blocker and what remains manual.

A concise evidence-based status is better than a broad claim of success.
