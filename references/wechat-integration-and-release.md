# WeChat integration and release

## Environment model

Keep three API endpoints separate:

| Mini-program environment | API endpoint | Requirement |
|---|---|---|
| Developer tools / develop | `http://127.0.0.1:8080` or LAN test URL | Domain validation may be disabled locally |
| Trial | Deployed test API | HTTPS, valid certificate, configured request domain |
| Release | Production API | HTTPS, stable DNS, monitoring, backups |

Store environment-specific values in a small configuration module. Never hard-code production hosts across pages.

## Secrets and identity

- Put the mini-program `AppID` in the project configuration.
- Put `AppSecret` only in backend environment variables or a secrets manager.
- Never ship `AppSecret`, WeChat access tokens, JWT secrets, database passwords, or admin OpenIDs in the mini program or repository.
- Document required environment variables in `.env.example` without real values.
- `wx.login()` returns a short-lived code. Exchange it on the backend; never exchange or cache the secret on the client.

Recommended backend variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
WECHAT_APP_ID
WECHAT_APP_SECRET
ADMIN_WECHAT_OPENID
```

## Diagnosing WeChat login failures

A `401` from `/api/v1/auth/wechat-login` is usually an authentication/configuration problem, not a request-domain problem. Check:

1. The mini program uses the expected `AppID` and the backend uses the matching `AppSecret`.
2. The backend process actually inherited the environment variables; changing the terminal variable does not affect an already-running process.
3. The server can reach WeChat's code-exchange API and is not receiving a proxy or TLS error.
4. The code was not already consumed or expired.
5. The backend maps WeChat error codes to safe, useful responses while logging enough diagnostic context.
6. The local database, migrations, and user bootstrap completed successfully.

After changing backend environment variables or endpoint code, restart the backend and perform a smoke request.

## Request domains

For trial or release:

1. Deploy the API behind HTTPS.
2. Add the exact host to the WeChat admin console's request legal-domain list.
3. Use the HTTPS host in the environment configuration.
4. Verify DNS, certificate chain, TLS version, and that the domain has no redirect to an unregistered host.
5. Test on a real phone, not only in developer tools.

`127.0.0.1`, `localhost`, and LAN addresses are development conveniences. They are not substitutes for a production request domain.

## Developer-tool log triage

Classify messages before changing code:

- **Compile error / missing page / missing component:** real failure; fix registration or package path.
- **HTTP 401/403/404/409:** real backend contract, permission, routing, or configuration issue.
- **"URL check disabled" or "legal domain check skipped":** expected in local development when that setting is off.
- **Gray/trial base-library notice:** informational unless it reproduces on a stable library.
- **Preload resource warning:** usually benign unless accompanied by a load failure.
- **Navigation error naming a page:** check `app.json` registration first, then route path and cache.

Do not diagnose a page-registration error as a domain problem.

## Mini-program package structure

- Register every routed page in `app.json`.
- Verify every `usingComponents` path exists before preview.
- Prefer a mature component library such as TDesign, but localize only the components actually used if package size matters.
- Keep tests, node modules, source maps, and design source files out of the upload package when possible.
- Check the projected package size before release.
- Avoid adding a second admin page for functionality that can live inside an existing management page unless a separate route has a real product reason.

## UGC and moderation release gate

Ratings with comments are user-generated content. Before release:

- Describe the rating/comment service accurately in the mini-program basic information.
- Remove old claims that the service provides no user-generated content.
- Update the privacy policy with the purpose, storage, moderation, and retention of rating/comment data.
- Provide a visible report entry.
- Provide an administrator queue for approve, reject, hide, and restore actions.
- Use local sensitive-term policy plus WeChat content security.
- Fail closed when content security is unavailable in production.
- Rate-limit submissions and reports.
- Do not expose OpenID or unnecessary personal data in public responses.
- Keep moderation and sensitive-term changes auditable.

Referee ratings without comments are simpler but still require identity, duplicate-prevention, rate-limit, and privacy review.

## Deployment checklist

### Backend

- [ ] MySQL migration and rollback strategy reviewed.
- [ ] Strong JWT secret configured.
- [ ] Admin identity configured through a controlled bootstrap process.
- [ ] Database backups and connection limits configured.
- [ ] Reverse proxy terminates HTTPS correctly.
- [ ] Application health endpoint and logs are reachable.
- [ ] Public smoke request succeeds.
- [ ] Unauthorized write request returns `401/403`, not an accidental success.

### Mini program

- [ ] Correct AppID selected.
- [ ] Trial and release API URLs configured.
- [ ] Request legal domain added.
- [ ] Privacy guide and UGC disclosure updated.
- [ ] Moderation entry available to admins.
- [ ] Package builds and size is acceptable.
- [ ] iOS and Android real-device checks completed.
- [ ] Login, browse, manage, rate, report, and moderate flows tested end to end.

## Useful smoke checks

```powershell
# Public endpoint
Invoke-WebRequest -Uri 'https://api.example.com/api/v1/seasons' -UseBasicParsing

# Expected unauthenticated protection
Invoke-WebRequest -Uri 'https://api.example.com/api/v1/manage/audit-logs' -UseBasicParsing
```

Also verify that a team manager receives `403` for another team's fixture and that a locked fixture returns the documented lock error code.
