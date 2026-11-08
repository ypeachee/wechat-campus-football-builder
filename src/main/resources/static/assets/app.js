const API_BASE = "/api/v1";
const SESSION_KEY = "campusFootball.webSession";
const SEASON_KEY = "campusFootball.seasonId";
const ANNOUNCEMENT_SEEN_KEY = "campusFootball.lastSeenAnnouncementId";

const app = document.getElementById("app");
const seasonSelect = document.getElementById("season-select");
const authEntry = document.getElementById("auth-entry");
const authEntryLabel = document.getElementById("auth-entry-label");
const adminNavLink = document.getElementById("admin-nav-link");
const menuToggle = document.getElementById("menu-toggle");
const primaryNav = document.getElementById("primary-nav");
const toast = document.getElementById("toast");

const state = {
  competitions: [],
  seasons: [],
  teams: [],
  teamMap: new Map(),
  seasonId: null,
  announcements: [],
  newsFeed: { managed: false, items: [] },
  session: readStoredSession(),
  loading: false,
};

class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

function readStoredSession() {
  let session = null;
  try {
    session = JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null");
  } catch {
    session = null;
  }
  // Always clean up legacy localStorage key (migration + hygiene).
  try {
    if (session) {
      localStorage.removeItem(SESSION_KEY);
      return session;
    }
  } catch { /* ignore */ }
  if (session) return session;
  try {
    const legacy = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    if (legacy) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(legacy));
      try { localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
      return legacy;
    }
  } catch { /* ignore */ }
  try { localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
  return null;
}

function saveSession(session) {
  state.session = session;
  try {
    if (session) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(SESSION_KEY);
    }
  } catch { /* ignore */ }
  try { localStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
  updateAuthHeader();
}

function updateAuthHeader() {
  const role = state.session?.role;
  const canManage = Boolean(role && role !== "VIEWER");
  if (adminNavLink) adminNavLink.hidden = !canManage;
  if (role) {
    authEntry.classList.add("is-logged-in");
    authEntry.href = canManage ? "#/admin" : "#/account";
    authEntryLabel.textContent = canManage ? "管理台" : "账号";
  } else {
    authEntry.classList.remove("is-logged-in");
    authEntry.href = "#/login";
    authEntryLabel.textContent = "管理入口";
  }
}

function roleLabel(role) {
  return ({ ADMIN: "超级管理员", TEAM_MANAGER: "球队管理员", TEAM_LEADER: "领队", VIEWER: "浏览用户" })[role] || role;
}

function roleBadge(role) {
  const cls = ({ ADMIN: "badge-admin", TEAM_MANAGER: "badge-manager", TEAM_LEADER: "badge-leader" })[role] || "badge-scheduled";
  return `<span class="badge no-dot ${cls}">${esc(roleLabel(role))}</span>`;
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (state.session?.token) headers.set("Authorization", `Bearer ${state.session.token}`);
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  } catch {
    throw new ApiError("无法连接服务器，请检查网络后重试", 0, "NETWORK_ERROR");
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get("content-type") || "";
  const payload = contentType.includes("application/json") ? await response.json().catch(() => null) : null;
  if (!response.ok) {
    if (response.status === 401 && state.session) {
      saveSession(null);
    }
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload?.code);
  }
  return payload;
}

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function param(name) {
  return new URLSearchParams(location.hash.split("?")[1] || "").get(name);
}

function routeInfo() {
  const raw = location.hash.slice(1) || "/";
  const path = raw.split("?")[0] || "/";
  return { raw, path, parts: path.split("/").filter(Boolean) };
}

function setActiveNav(key) {
  document.querySelectorAll("[data-nav]").forEach((link) => {
    link.classList.toggle("active", link.dataset.nav === key);
  });
}

function closeMobileNav() {
  primaryNav.classList.remove("open");
  menuToggle.setAttribute("aria-expanded", "false");
}

function showLoading() {
  app.innerHTML = `<section class="page-loading" aria-live="polite"><div class="loader-mark"></div><p>正在加载赛事数据…</p></section>`;
}

function emptyState(title, description, mark = "○") {
  return `<div class="empty-state"><div class="empty-mark">${esc(mark)}</div><h3>${esc(title)}</h3><p>${esc(description)}</p></div>`;
}

function errorState(error, title = "数据加载失败") {
  return `<div class="empty-state error-state">
    <div class="empty-mark">!</div>
    <h3>${esc(title)}</h3>
    <p>${esc(error?.message || "暂时无法加载数据，请稍后再试。")}</p>
    <button class="button button-ghost button-small" type="button" data-retry style="margin-top:18px">重新加载</button>
  </div>`;
}

function notify(message) {
  toast.textContent = message;
  toast.classList.add("visible");
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => toast.classList.remove("visible"), 2400);
}

function initials(name) {
  const chars = Array.from(String(name || "球队").trim());
  if (!chars.length) return "FC";
  if (/[\u4e00-\u9fa5]/.test(chars[0])) return chars.slice(0, 2).join("");
  return chars.filter((char) => /[A-Za-z0-9]/.test(char)).slice(0, 2).join("").toUpperCase() || "FC";
}

function crestInitials(name) {
  const first = Array.from(String(name || "FC").trim())[0] || "F";
  return /[\u4e00-\u9fa5]/.test(first) ? first : initials(name);
}

function teamCrest(team, size = "") {
  const name = team?.name || team?.shortName || "球队";
  const image = team?.logoUrl
    ? `<img src="${esc(team.logoUrl)}" alt="${esc(name)}队徽" loading="lazy">`
    : esc(initials(team?.shortName || name));
  return `<span class="team-crest ${size}">${image}</span>`;
}

function teamById(id) {
  return state.teamMap.get(Number(id)) || { id: Number(id), name: `球队 #${id}`, shortName: null };
}

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function dateParts(value) {
  const date = parseDate(value);
  if (!date) return { date: "待定", time: "时间待定", weekday: "", full: "时间待定" };
  return {
    date: new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(date).replace("/", "."),
    time: new Intl.DateTimeFormat("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false }).format(date),
    weekday: new Intl.DateTimeFormat("zh-CN", { weekday: "short" }).format(date),
    full: new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "short" }).format(date),
  };
}

function statusLabel(status) {
  return ({ DRAFT: "草稿", SCHEDULED: "未开始", LIVE: "进行中", FINISHED: "已结束", CANCELLED: "已取消" })[status] || status;
}

function statusBadge(status) {
  const cls = ({ DRAFT: "badge-draft", SCHEDULED: "badge-scheduled", LIVE: "badge-live", FINISHED: "badge-finished", CANCELLED: "badge-cancelled" })[status] || "badge-scheduled";
  return `<span class="badge ${cls}">${esc(statusLabel(status))}</span>`;
}

function announcementDate(value) {
  const date = parseDate(value);
  if (!date) return "刚刚发布";
  return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" })
    .format(date).replaceAll("/", ".");
}

function maybeShowLatestAnnouncement() {
  const latest = state.announcements[0];
  if (!latest || document.querySelector(".announcement-dialog")) return;
  let seenId = 0;
  try { seenId = Number(localStorage.getItem(ANNOUNCEMENT_SEEN_KEY) || 0); } catch { /* ignore */ }
  if (Number(latest.id) <= seenId) return;
  const dialog = document.createElement("dialog");
  dialog.className = "announcement-dialog";
  dialog.setAttribute("aria-labelledby", "announcement-dialog-title");
  dialog.innerHTML = `<div class="announcement-dialog-top"><span class="badge no-dot badge-admin">最新公告</span><button class="dialog-close" type="button" data-dialog-close aria-label="关闭公告">×</button></div>
    <span class="eyebrow">NOTICE BOARD</span><h2 id="announcement-dialog-title">${esc(latest.title)}</h2><time>${esc(announcementDate(latest.createdAt))}</time><p>${esc(latest.content)}</p>
    <div class="announcement-dialog-actions"><button class="button button-ghost button-small" type="button" data-dialog-board>查看公告板</button><button class="button button-primary button-small" type="button" data-dialog-close>我知道了</button></div>`;
  dialog.addEventListener("click", (event) => { if (event.target === dialog) dialog.close(); });
  dialog.addEventListener("close", () => {
    try { localStorage.setItem(ANNOUNCEMENT_SEEN_KEY, String(latest.id)); } catch { /* ignore */ }
    dialog.remove();
  });
  dialog.querySelectorAll("[data-dialog-close]").forEach((button) => button.addEventListener("click", () => dialog.close()));
  dialog.querySelector("[data-dialog-board]")?.addEventListener("click", () => {
    dialog.close();
    setTimeout(() => document.getElementById("announcement-board")?.scrollIntoView({ behavior: "smooth", block: "start" }), 50);
  });
  document.body.appendChild(dialog);
  dialog.showModal();
}

function fixtureScore(fixture) {
  if (fixture.homeScore == null || fixture.awayScore == null) return "VS";
  return `${fixture.homeScore}<span>:</span>${fixture.awayScore}`;
}

function fixtureRow(fixture, compact = false) {
  const home = teamById(fixture.homeTeamId);
  const away = teamById(fixture.awayTeamId);
  const when = dateParts(fixture.kickoffAt);
  return `<a class="fixture-row ${compact ? "compact" : ""}" href="#/fixtures/${fixture.id}">
    <span class="fixture-date"><strong>${esc(when.date)}</strong>${esc(when.weekday)} ${esc(when.time)}</span>
    <span class="fixture-team">${teamCrest(home, "small")}<span>${esc(home.shortName || home.name)}</span></span>
    <span class="fixture-score ${fixture.status === "LIVE" ? "live" : ""}">${fixture.status === "SCHEDULED" || fixture.status === "DRAFT" ? `<time>${esc(when.time)}</time>` : fixtureScore(fixture)}</span>
    <span class="fixture-team away"><span>${esc(away.shortName || away.name)}</span>${teamCrest(away, "small")}</span>
    <span class="fixture-venue"><span>${statusLabel(fixture.status)}</span><span>${esc(fixture.venue || "场地待定")}</span></span>
  </a>`;
}

function seasonName() {
  return state.seasons.find((season) => season.id === state.seasonId)?.name || "当前赛季";
}

function seasonQuery() {
  return state.seasonId ? `seasonId=${encodeURIComponent(state.seasonId)}` : "";
}

function pageHeader(eyebrow, title, subtitle, actions = "") {
  return `<header class="page-head">
    <div class="page-head-copy"><span class="eyebrow">${esc(eyebrow)}</span><h1 class="page-title">${esc(title)}</h1><p class="page-subtitle">${esc(subtitle)}</p></div>
    ${actions ? `<div class="page-actions">${actions}</div>` : ""}
  </header>`;
}

function sectionHead(title, subtitle, link = "") {
  return `<div class="section-head"><div><h2 class="section-title">${esc(title)}</h2>${subtitle ? `<p class="section-subtitle">${esc(subtitle)}</p>` : ""}</div>${link || ""}</div>`;
}

function setTitle(title) {
  document.title = `${title} | 泥地soccer`;
}

window.addEventListener("scroll", () => {
  document.getElementById("site-header").classList.toggle("scrolled", window.scrollY > 8);
}, { passive: true });

menuToggle?.addEventListener("click", () => {
  const open = !primaryNav.classList.contains("open");
  primaryNav.classList.toggle("open", open);
  menuToggle.setAttribute("aria-expanded", String(open));
});

document.addEventListener("click", (event) => {
  if (!primaryNav.contains(event.target) && !menuToggle.contains(event.target)) closeMobileNav();
  const retry = event.target.closest("[data-retry]");
  if (retry) renderRoute();
});

window.addEventListener("hashchange", () => {
  closeMobileNav();
  renderRoute();
  window.scrollTo({ top: 0, behavior: "smooth" });
});

async function loadCore() {
  const [competitions, seasons, teams, announcements, newsFeed] = await Promise.all([
    api("/competitions"),
    api("/seasons"),
    api("/teams"),
    api("/announcements?limit=10").catch(() => []),
    api("/news?limit=8").catch(() => ({ managed: false, items: [] })),
  ]);
  state.competitions = competitions || [];
  state.seasons = seasons || [];
  state.teams = teams || [];
  state.announcements = announcements || [];
  state.newsFeed = newsFeed || { managed: false, items: [] };
  state.teamMap = new Map(state.teams.map((team) => [Number(team.id), team]));
  const saved = Number(localStorage.getItem(SEASON_KEY));
  const savedValid = state.seasons.some((season) => season.id === saved);
  const preferred = state.seasons.find((season) => season.status === "ACTIVE") || state.seasons[0];
  state.seasonId = savedValid ? saved : preferred?.id || null;
  populateSeasonSelect();
}

function populateSeasonSelect() {
  if (!state.seasons.length) {
    seasonSelect.innerHTML = `<option value="">暂无赛季</option>`;
    seasonSelect.disabled = true;
    return;
  }
  seasonSelect.disabled = false;
  seasonSelect.innerHTML = state.seasons.map((season) => {
    const competition = state.competitions.find((item) => item.id === season.competitionId);
    const suffix = competition ? ` · ${competition.name}` : "";
    return `<option value="${season.id}" ${season.id === state.seasonId ? "selected" : ""}>${esc(season.name + suffix)}</option>`;
  }).join("");
}

seasonSelect?.addEventListener("change", () => {
  const value = Number(seasonSelect.value);
  state.seasonId = Number.isFinite(value) && value > 0 ? value : null;
  if (state.seasonId) localStorage.setItem(SEASON_KEY, String(state.seasonId));
  else localStorage.removeItem(SEASON_KEY);
  renderRoute();
});

async function renderRoute() {
  if (state.loading) return;
  const { path, parts } = routeInfo();
  updateAuthHeader();
  closeMobileNav();
  try {
    if (parts.length === 0) {
      setActiveNav("home");
      await renderHome();
      return;
    }
    if (path === "/fixtures" && parts.length === 1) {
      setActiveNav("fixtures");
      await renderFixturesPage();
      return;
    }
    if (path.startsWith("/fixtures/") && parts.length === 2) {
      setActiveNav("fixtures");
      await renderFixtureDetail(parts[1]);
      return;
    }
    if (path === "/standings") {
      setActiveNav("standings");
      await renderStandingsPage();
      return;
    }
    if (path === "/stats") {
      setActiveNav("stats");
      await renderStatsPage();
      return;
    }
    if (path === "/teams") {
      setActiveNav("teams");
      await renderTeamsPage();
      return;
    }
    if (path.startsWith("/teams/") && parts.length === 2) {
      setActiveNav("teams");
      await renderTeamDetail(parts[1]);
      return;
    }
    if (path.startsWith("/players/") && parts.length === 2) {
      setActiveNav("teams");
      await renderPlayerDetail(parts[1]);
      return;
    }
    if (path === "/admin") {
      setActiveNav("admin");
      await renderAdminHub();
      return;
    }
    if (path === "/admin/fixtures") {
      setActiveNav("admin");
      await renderAdminFixturesPage();
      return;
    }
    if (path === "/admin/announcements") {
      setActiveNav("admin");
      await renderAdminAnnouncementsPage();
      return;
    }
    if (path === "/admin/news") {
      setActiveNav("admin");
      await renderAdminNewsPage();
      return;
    }
    if (path === "/admin/teams") {
      setActiveNav("admin");
      await renderAdminTeamsPage();
      return;
    }
    if (path === "/tactics") {
      setActiveNav("tactics");
      await renderTacticsPage();
      return;
    }
    if (path === "/login") {
      setActiveNav("login");
      await renderLoginPage();
      return;
    }
    if (path === "/account") {
      setActiveNav("login");
      await renderAccountPage();
      return;
    }
    setActiveNav("");
    setTitle("页面不存在");
    app.innerHTML = `<div class="page" style="padding-top:60px">${emptyState("没有找到这个页面", "页面可能已移动，请从导航重新进入。", "404")}</div>`;
  } catch (error) {
    console.error(error);
    setTitle("加载失败");
    app.innerHTML = `<div class="page" style="padding-top:40px">${errorState(error)}</div>`;
  }
}

async function renderHome() {
  setTitle("首页");
  showLoading();
  const query = seasonQuery();
  const results = await Promise.allSettled([
    query ? api(`/fixtures?${query}`) : Promise.resolve([]),
    query ? api(`/standings?${query}`) : Promise.resolve([]),
    query ? api(`/stats/scorers?${query}`) : Promise.resolve([]),
    query ? api(`/stats/assists?${query}`) : Promise.resolve([]),
  ]);
  const fixtures = results[0].status === "fulfilled" ? results[0].value : [];
  const standings = results[1].status === "fulfilled" ? results[1].value : [];
  const scorers = results[2].status === "fulfilled" ? results[2].value : [];
  const assists = results[3].status === "fulfilled" ? results[3].value : [];
  const season = state.seasons.find((item) => item.id === state.seasonId);
  const upcoming = fixtures.filter((item) => item.status === "SCHEDULED" || item.status === "DRAFT");
  const live = fixtures.filter((item) => item.status === "LIVE");
  const finished = fixtures.filter((item) => item.status === "FINISHED").reverse();
  const spotlight = live[0] || upcoming[0] || finished[0];
  const goalCount = fixtures.reduce((sum, item) => sum + (item.homeScore || 0) + (item.awayScore || 0), 0);
  const safeResults = [fixtures, standings, scorers, assists];

  const spotlightHtml = spotlight ? spotlightCard(spotlight) : `<div class="spotlight">${emptyState("赛程准备中", "当前赛季还没有可展示的比赛。", "↗")}</div>`;
  const recentHtml = finished.length
    ? finished.slice(0, 4).map((fixture) => fixtureRow(fixture, true)).join("")
    : emptyState("暂无完赛记录", "比赛结束后，比分和事件会出现在这里。", "○");
  const standingsHtml = standings.length
    ? standings.slice(0, 5).map((row, index) => `<a class="rank-item" href="#/teams/${row.teamId}">
        <span class="rank-person"><strong>${esc(row.teamName)}</strong><span>${row.played} 场 · 净胜球 ${row.goalDifference > 0 ? "+" : ""}${row.goalDifference}</span></span>
        <span class="rank-number">${row.points}<small>分</small></span>
      </a>`).join("")
    : emptyState("积分榜尚未生成", "录入并完成比赛后会自动计算积分。", "≡");
  const scorersHtml = scorers.length
    ? scorers.slice(0, 5).map((row) => `<a class="rank-item" href="#/players/${row.playerId}">
        <span class="rank-person"><strong>${esc(row.playerName)}</strong><span>${esc(row.teamName)}</span></span>
        <span class="rank-number">${row.goals}<small>球</small></span>
      </a>`).join("")
    : emptyState("暂无射手数据", "已完赛比赛中的进球会进入榜单。", "⚽");

  app.innerHTML = `<div class="page">
    <section class="hero">
      <div class="hero-grid">
        <article class="hero-main">
          <div class="hero-copy">
            <span class="hero-kicker"><span class="pulse"></span>${esc(seasonName())} · 正在进行</span>
            <h1 class="hero-title">把每一场<br><span class="outline">比赛</span>写进赛季。</h1>
            <p>赛程、比分、积分与球员数据，在一处完整呈现。为每一次奔跑留下可被看见的记录。</p>
          </div>
          <div class="hero-actions">
            <a class="button button-lime" href="#/fixtures">查看完整赛程</a>
            <a class="button button-ghost" href="#/standings" style="color:white;border-color:rgba(255,255,255,.22)">查看积分榜</a>
            <span class="hero-note">${results.some((item) => item.status === "fulfilled") ? "数据实时同步自赛事后台" : "等待赛事数据"}</span>
          </div>
        </article>
        <aside class="hero-side">
          ${spotlightHtml}
          <div class="quick-stats">
            <div class="quick-stat"><strong>${state.teams.length}</strong><span>参赛球队</span></div>
            <div class="quick-stat"><strong>${fixtures.length}</strong><span>赛季比赛</span></div>
            <div class="quick-stat"><strong>${finished.length}</strong><span>已完赛</span></div>
            <div class="quick-stat"><strong>${goalCount}</strong><span>总进球</span></div>
          </div>
        </aside>
      </div>
    </section>
    ${announcementBoardHtml()}
    <section class="section">
      ${sectionHead("最近赛果", "回看刚刚结束的比赛", `<a class="text-link" href="#/fixtures">全部赛程</a>`)}
      <div class="content-grid">
        <div class="fixture-list">${recentHtml}</div>
        <div class="stack">
          <section class="card panel">
            <div class="panel-head"><h2 class="panel-title">积分榜 · 前五</h2><a class="text-link" href="#/standings">完整</a></div>
            <div class="rank-list">${standingsHtml}</div>
          </section>
          <section class="card panel">
            <div class="panel-head"><h2 class="panel-title">射手榜 · 前五</h2><a class="text-link" href="#/stats">完整</a></div>
            <div class="rank-list">${scorersHtml}</div>
          </section>
        </div>
      </div>
    </section>
    ${newsRailHtml()}
    ${homeGroundHtml()}
  </div>`;
  const newsRail = document.getElementById("news-rail");
  document.querySelectorAll("[data-news-scroll]").forEach((button) => {
    button.addEventListener("click", () => {
      const direction = Number(button.dataset.newsScroll);
      newsRail?.scrollBy({ left: direction * Math.min(newsRail.clientWidth * .82, 430), behavior: "smooth" });
    });
  });
}

function announcementBoardHtml() {
  const items = state.announcements.slice(0, 3);
  const cards = items.length
    ? items.map((item, index) => `<article class="announcement-item"><div class="announcement-item-meta"><time>${esc(announcementDate(item.createdAt))}</time>${index === 0 ? `<span class="announcement-new">最新</span>` : ""}</div><h3>${esc(item.title)}</h3><p>${esc(item.content)}</p></article>`).join("")
    : `<div class="announcement-empty">暂无公告，管理员发布后会显示在这里。</div>`;
  return `<section class="announcement-board card" id="announcement-board"><div class="announcement-board-head"><div><span class="eyebrow">NOTICE BOARD</span><h2>公告板</h2><p>赛事通知、赛程变更和重要提醒都会在这里发布。</p></div><span class="badge no-dot badge-scheduled">${items.length} 条</span></div><div class="announcement-grid">${cards}</div></section>`;
}

function newsRailHtml() {
  const fallbackItems = [
    { title: "北地女足主场集结", description: "校园足球在学院路29号开场，绿茵与看台一起见证比赛。", tag: "主场亮相", publishedAt: "2026-10-26", imageUrl: "/assets/cugb-match-team.jpg" },
    { title: "绿茵场上的攻防转换", description: "奔跑、拼抢和传递，构成北地校园足球最鲜活的现场。", tag: "赛场实况", publishedAt: "2026-10-26", imageUrl: "/assets/cugb-match-action.jpg" },
    { title: "赛前集结，准备开场", description: "队员们在球场列队，迎接下一场校园足球较量。", tag: "赛前时刻", publishedAt: "2026-10-26", imageUrl: "/assets/cugb-match-lineup.jpg" },
    { title: "球门前的关键一刻", description: "禁区内的争抢与扑救，让每一场比赛都充满悬念。", tag: "门前瞬间", publishedAt: "2026-10-26", imageUrl: "/assets/cugb-match-goal.jpg" },
  ];
  const sourceItems = state.newsFeed?.managed ? state.newsFeed.items : fallbackItems;
  const items = Array.isArray(sourceItems) ? sourceItems : [];
  const cards = items.length ? items.map((item) => {
    const date = String(item.publishedAt || "").replaceAll("-", ".");
    return `<article class="news-card"><div class="news-card-media"><img src="${esc(item.imageUrl)}" alt="${esc(item.title)}"><span>${esc(item.tag)}</span></div><div class="news-card-copy"><time>${esc(date)}</time><h3>${esc(item.title)}</h3><p>${esc(item.description)}</p></div></article>`;
  }).join("") : `<div class="news-empty card">暂无已发布新闻，管理员可在管理台添加。</div>`;
  return `<section class="section news-section"><div class="section-head news-section-head"><div><span class="eyebrow">MATCH DAYS</span><h2 class="section-title">赛场实况</h2><p class="section-subtitle">左右滑动，看看北地足球场上的比赛瞬间。${state.newsFeed?.managed ? "由赛事管理台维护。" : "图片来自中国地质大学（北京）体育学院。"}</p></div><a class="text-link" href="http://www.dpe.cugb.edu.cn/c/2026-10-26/859866.shtml" target="_blank" rel="noopener noreferrer">查看赛事报道</a></div><div class="news-carousel"><button class="news-control news-control-prev" type="button" data-news-scroll="-1" aria-label="向左滚动新闻">‹</button><div class="news-rail" id="news-rail" tabindex="0" aria-label="比赛实况新闻，可左右滚动">${cards}</div><button class="news-control news-control-next" type="button" data-news-scroll="1" aria-label="向右滚动新闻">›</button></div></section>`;
}

function homeGroundHtml() {
  return `    <section class="home-ground card">
      <figure class="home-ground-media">
        <img src="/assets/cugb-football-field-sunset.jpg" alt="中国地质大学（北京）足球场晚霞实景" width="1820" height="1024">
        <figcaption>中国地质大学（北京）· 学院路29号</figcaption>
      </figure>
      <div class="home-ground-copy">
        <span class="eyebrow">HOME GROUND · CUGB</span>
        <h2>中国地质大学（北京）足球场</h2>
        <p>中国地质大学（北京）运动场拥有标准跑道和足球场。这里见证训练、联赛与每一场属于北地人的足球记忆。</p>
        <div class="home-ground-facts"><span>北京 · 学院路29号</span><span>标准跑道</span><span>2 个足球场</span></div>
        <a class="text-link" href="https://www.cugb.edu.cn/xyfg2/39104.jhtml" target="_blank" rel="noopener noreferrer">查看学校官网</a>
      </div>
    </section>
`;
}

function spotlightCard(fixture) {
  const home = teamById(fixture.homeTeamId);
  const away = teamById(fixture.awayTeamId);
  const when = dateParts(fixture.kickoffAt);
  const score = fixture.homeScore == null ? "VS" : `${fixture.homeScore}<span>:</span>${fixture.awayScore}`;
  return `<article class="spotlight">
    <div class="spotlight-top"><span class="spotlight-round">${fixture.roundNo ? `第 ${fixture.roundNo} 轮` : "焦点比赛"}</span>${statusBadge(fixture.status)}</div>
    <div class="matchup">
      <a class="matchup-team" href="#/teams/${home.id}">${teamCrest(home)}<strong>${esc(home.shortName || home.name)}</strong></a>
      <div class="matchup-score">${score}</div>
      <a class="matchup-team" href="#/teams/${away.id}">${teamCrest(away)}<strong>${esc(away.shortName || away.name)}</strong></a>
    </div>
    <div class="kickoff"><span>${esc(when.full)}</span><strong>${esc(fixture.venue || "场地待定")}</strong></div>
    <a class="text-link" href="#/fixtures/${fixture.id}" style="margin-top:15px">比赛详情</a>
  </article>`;
}

async function renderFixturesPage() {
  setTitle("赛程");
  showLoading();
  if (!state.seasonId) {
    app.innerHTML = `<div class="page">${pageHeader("MATCH CENTER", "赛程", "按赛季查看全部比赛安排与赛果。")}${emptyState("暂无可展示赛季", "请先在赛事后台创建赛季并录入赛程。", "◫")}</div>`;
    return;
  }
  const fixtures = await api(`/fixtures?${seasonQuery()}`);
  app.innerHTML = `<div class="page">
    ${pageHeader("MATCH CENTER", "赛程", `${seasonName()} 的全部比赛安排、比分与场地信息。`, `<a class="button button-ghost button-small" href="#/standings">积分榜</a>`)}
    <div class="filters" id="fixture-filters">
      ${[["all", "全部"], ["SCHEDULED", "未开始"], ["LIVE", "进行中"], ["FINISHED", "已结束"], ["CANCELLED", "已取消"]].map(([value, label], index) => `<button class="filter-chip ${index === 0 ? "active" : ""}" type="button" data-fixture-filter="${value}">${label}</button>`).join("")}
      <span style="margin-left:auto;color:var(--muted);font-size:11px">共 ${fixtures.length} 场</span>
    </div>
    <div id="fixture-list">${renderFixtureList(fixtures, "all")}</div>
  </div>`;
  document.getElementById("fixture-filters")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-fixture-filter]");
    if (!button) return;
    document.querySelectorAll("[data-fixture-filter]").forEach((item) => item.classList.toggle("active", item === button));
    document.getElementById("fixture-list").innerHTML = renderFixtureList(fixtures, button.dataset.fixtureFilter);
  });
}

function renderFixtureList(fixtures, filter) {
  const visible = filter === "all" ? fixtures : fixtures.filter((item) => item.status === filter);
  if (!visible.length) return emptyState("没有符合条件的比赛", "切换筛选条件或等待赛程更新。", "◫");
  return `<div class="fixture-list">${visible.map((fixture) => fixtureRow(fixture)).join("")}</div>`;
}

async function renderStandingsPage() {
  setTitle("积分榜");
  showLoading();
  if (!state.seasonId) {
    app.innerHTML = `<div class="page">${pageHeader("TABLE", "积分榜", "根据已完赛比赛动态计算排名。")}${emptyState("暂无积分数据", "当前还没有可用的赛季。", "≡")}</div>`;
    return;
  }
  let standings;
  try {
    standings = await api(`/standings?${seasonQuery()}`);
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("TABLE", "积分榜", `${seasonName()} 的联赛排名。`)}${errorState(error)}</div>`;
    return;
  }
  const rows = standings.map((row, index) => `<tr>
    <td><span class="rank ${index < 3 ? "top" : ""}">${index + 1}</span></td>
    <td class="team-cell"><a class="table-team" href="#/teams/${row.teamId}">${teamCrest(teamById(row.teamId), "small")}<span>${esc(row.teamName)}</span></a></td>
    <td>${row.played}</td><td>${row.won}</td><td>${row.drawn}</td><td>${row.lost}</td>
    <td>${row.goalsFor}</td><td>${row.goalsAgainst}</td>
    <td>${row.goalDifference > 0 ? "+" : ""}${row.goalDifference}</td>
    <td>${row.yellowCards}</td><td>${row.redCards}</td>
    <td class="points">${row.points}</td>
  </tr>`).join("");
  app.innerHTML = `<div class="page">
    ${pageHeader("TABLE", "积分榜", `${seasonName()} 实时排名。排序依次比较积分、净胜球、进球数、公平竞赛分和胜场。`, `<a class="button button-ghost button-small" href="#/stats">数据榜</a>`)}
    ${standings.length ? `<div class="table-wrap"><table class="data-table">
      <thead><tr><th>排名</th><th style="text-align:left">球队</th><th>赛</th><th>胜</th><th>平</th><th>负</th><th>进</th><th>失</th><th>净</th><th>黄</th><th>红</th><th>积分</th></tr></thead>
      <tbody>${rows}</tbody>
    </table></div>
    <p class="form-message">黄牌扣 1 分、红牌扣 3 分计入公平竞赛分；表格左右滑动可查看完整数据。</p>` : emptyState("积分榜尚未生成", "完成第一场比赛后，积分会在这里自动更新。", "≡")}
  </div>`;
}

async function renderStatsPage() {
  setTitle("数据榜");
  showLoading();
  if (!state.seasonId) {
    app.innerHTML = `<div class="page">${pageHeader("LEADERBOARD", "数据榜", "射手与助攻数据。")}${emptyState("暂无数据榜", "当前还没有可用的赛季。", "↗")}</div>`;
    return;
  }
  const [scorers, assists] = await Promise.all([
    api(`/stats/scorers?${seasonQuery()}`),
    api(`/stats/assists?${seasonQuery()}`),
  ]);
  let active = "scorers";
  const paint = () => {
    const rows = active === "scorers" ? scorers : assists;
    const valueKey = active === "scorers" ? "goals" : "assists";
    const unit = active === "scorers" ? "球" : "次";
    const top = rows.slice(0, 3);
    const rest = rows.slice(3);
    document.querySelectorAll("[data-stats-tab]").forEach((button) => button.classList.toggle("active", button.dataset.statsTab === active));
    document.getElementById("stats-content").innerHTML = rows.length ? `
      <div class="podium">${top.map((row, index) => `<a class="podium-card ${index === 0 ? "first" : ""}" href="#/players/${row.playerId}">
        <span class="podium-place">${index + 1}</span>
        <span class="podium-avatar">${esc(initials(row.playerName))}</span>
        <strong>${esc(row.playerName)}</strong>
        <span>${esc(row.teamName)}</span>
        <div class="podium-value">${row[valueKey]} <small style="font-size:11px">${unit}</small></div>
      </a>`).join("")}</div>
      <div class="card panel"><div class="rank-list" style="counter-reset:rank 3">${rest.map((row) => `<a class="rank-item" href="#/players/${row.playerId}">
        <span class="rank-person"><strong>${esc(row.playerName)}</strong><span>${esc(row.teamName)}</span></span>
        <span class="rank-number">${row[valueKey]}<small>${unit}</small></span>
      </a>`).join("")}</div></div>` : emptyState(active === "scorers" ? "暂无射手数据" : "暂无助攻数据", "已完赛比赛中的事件会汇总到对应榜单。", "↗");
  };
  app.innerHTML = `<div class="page">
    ${pageHeader("LEADERBOARD", "数据榜", `${seasonName()} 的射手与助攻排名。只统计已完赛比赛。`, `<a class="button button-ghost button-small" href="#/standings">积分榜</a>`)}
    <div class="stats-tabs"><button type="button" data-stats-tab="scorers" class="active">射手榜</button><button type="button" data-stats-tab="assists">助攻榜</button></div>
    <div id="stats-content"></div>
  </div>`;
  paint();
  document.querySelector(".stats-tabs")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-stats-tab]");
    if (!button) return;
    active = button.dataset.statsTab;
    paint();
  });
}

async function renderTeamsPage() {
  setTitle("球队");
  const teams = state.teams;
  app.innerHTML = `<div class="page">
    ${pageHeader("CLUBS", "参赛球队", `浏览 ${seasonName()} 的球队资料、教练、主场与球员名单。`)}
    <div class="filters"><label class="search-box" style="margin-left:auto"><input id="team-search" type="search" placeholder="搜索球队" autocomplete="off"></label></div>
    <div id="team-grid">${renderTeamGrid(teams)}</div>
  </div>`;
  document.getElementById("team-search")?.addEventListener("input", (event) => {
    const keyword = event.target.value.trim().toLowerCase();
    const visible = teams.filter((team) => `${team.name} ${team.shortName || ""}`.toLowerCase().includes(keyword));
    document.getElementById("team-grid").innerHTML = renderTeamGrid(visible);
  });
}

function renderTeamGrid(teams) {
  if (!teams.length) return emptyState("没有找到球队", "尝试更换搜索词，或等待赛事后台录入球队。", "◉");
  return `<div class="team-grid">${teams.map((team, index) => `<a class="team-card card" href="#/teams/${team.id}" data-index="${String(index + 1).padStart(2, "0")}">
    <div><div class="team-card-top">${teamCrest(team)}<span class="badge ${team.status === "ACTIVE" ? "badge-finished" : "badge-cancelled"}">${team.status === "ACTIVE" ? "有效" : "停用"}</span></div>
      <h3>${esc(team.name)}</h3><p>${esc(team.coachName ? `主教练 ${team.coachName}` : "教练信息待补充")}</p></div>
    <div class="team-card-footer"><span>${esc(team.homeVenue || "主场待定")}</span><span>球队主页 →</span></div>
  </a>`).join("")}</div>`;
}

async function renderTeamDetail(teamId) {
  setTitle("球队详情");
  showLoading();
  const id = Number(teamId);
  if (!Number.isFinite(id)) throw new ApiError("球队编号无效", 400, "VALIDATION_FAILED");
  const [team, players, standings, fixtures] = await Promise.all([
    api(`/teams/${id}`),
    api(`/teams/${id}/players`),
    state.seasonId ? api(`/standings?${seasonQuery()}`).catch(() => []) : Promise.resolve([]),
    state.seasonId ? api(`/fixtures?${seasonQuery()}`).catch(() => []) : Promise.resolve([]),
  ]);
  const record = standings.find((row) => row.teamId === id);
  const teamFixtures = fixtures.filter((fixture) => fixture.homeTeamId === id || fixture.awayTeamId === id).slice(-6).reverse();
  const playersHtml = players.length ? players.map((player) => `<a class="player-row" href="#/players/${player.id}">
    <span class="jersey">${esc(player.jerseyNo || "-")}</span>
    <span><strong>${esc(player.name)}</strong><span>${esc(player.position || "位置待补充")}</span></span>
    <span class="player-arrow">›</span>
  </a>`).join("") : emptyState("暂无球员资料", "领队或管理员录入球员后会显示在这里。", "○");
  app.innerHTML = `<div class="page">
    <section class="detail-hero">
      ${teamCrest(team, "large")}
      <div class="detail-copy">
        <span class="hero-kicker"><span class="pulse"></span>${esc(seasonName())}</span>
        <h1>${esc(team.name)}</h1>
        <p>${esc(team.shortName || team.name)} · ${esc(team.homeVenue || "主场待定")}</p>
        <div class="detail-facts">
          <span class="detail-fact">教练 <strong>${esc(team.coachName || "待补充")}</strong></span>
          <span class="detail-fact">球员 <strong>${players.length}</strong></span>
          ${record ? `<span class="detail-fact">积分 <strong>${record.points}</strong></span><span class="detail-fact">排名 <strong>第 ${standings.indexOf(record) + 1}</strong></span>` : ""}
        </div>
      </div>
    </section>
    <div class="detail-layout">
      <section class="card panel">
        <div class="panel-head"><h2 class="panel-title">球员名单</h2><span class="badge no-dot badge-scheduled">${players.length} 人</span></div>
        <div class="roster">${playersHtml}</div>
      </section>
      <div class="stack">
        <section class="card panel">
          <div class="panel-head"><h2 class="panel-title">球队资料</h2></div>
          <div class="info-list">
            <div class="info-row"><span>球队全称</span><strong>${esc(team.name)}</strong></div>
            <div class="info-row"><span>球队简称</span><strong>${esc(team.shortName || "待补充")}</strong></div>
            <div class="info-row"><span>主教练</span><strong>${esc(team.coachName || "待补充")}</strong></div>
            <div class="info-row"><span>主场</span><strong>${esc(team.homeVenue || "待补充")}</strong></div>
            <div class="info-row"><span>赛季战绩</span><strong>${record ? `${record.won}胜 ${record.drawn}平 ${record.lost}负` : "暂无"}</strong></div>
          </div>
        </section>
        <section class="card panel">
          <div class="panel-head"><h2 class="panel-title">近期比赛</h2><a class="text-link" href="#/fixtures">全部</a></div>
          <div class="fixture-list">${teamFixtures.length ? teamFixtures.map((fixture) => fixtureRow(fixture, true)).join("") : emptyState("暂无比赛", "该球队还没有本赛季赛程。", "◫")}</div>
        </section>
      </div>
    </div>
  </div>`;
}

async function renderPlayerDetail(playerId) {
  setTitle("球员详情");
  showLoading();
  const id = Number(playerId);
  if (!Number.isFinite(id)) throw new ApiError("球员编号无效", 400, "VALIDATION_FAILED");
  const emptyStats = {
    appearances: 0, starts: 0, substitutes: 0, minutesPlayed: 0,
    goals: 0, assists: 0, yellowCards: 0, redCards: 0, ownGoals: 0, matches: [],
  };
  const [player, stats] = await Promise.all([
    api(`/players/${id}`),
    state.seasonId ? api(`/players/${id}/stats?${seasonQuery()}`) : Promise.resolve(emptyStats),
  ]);
  const [team, players] = await Promise.all([
    api(`/teams/${player.teamId}`).catch(() => teamById(player.teamId)),
    api(`/teams/${player.teamId}/players`).catch(() => []),
  ]);
  const age = player.birthDate ? new Date().getFullYear() - new Date(player.birthDate).getFullYear() : null;
  const photo = player.photoUrl ? `<img src="${esc(player.photoUrl)}" alt="${esc(player.name)}照片" loading="lazy">` : esc(initials(player.name));
  const matches = Array.isArray(stats.matches) ? stats.matches : [];
  const seasonOptions = state.seasons.map((season) => `<option value="${season.id}" ${season.id === state.seasonId ? "selected" : ""}>${esc(season.name)}</option>`).join("");
  const statCards = [
    ["出场", stats.appearances],
    ["首发", stats.starts],
    ["替补", stats.substitutes],
    ["上场时间", stats.minutesPlayed],
    ["进球", stats.goals],
    ["助攻", stats.assists],
    ["黄牌", stats.yellowCards],
    ["红牌", stats.redCards],
    ["乌龙球", stats.ownGoals],
  ].map(([label, value]) => `<div class="card player-stat"><strong>${esc(value)}${label === "上场时间" ? "<small>′</small>" : ""}</strong><span>${esc(label)}</span></div>`).join("");
  const matchRows = matches.map((match) => {
    const when = dateParts(match.kickoffAt);
    const result = match.scoreFor > match.scoreAgainst ? "胜" : match.scoreFor < match.scoreAgainst ? "负" : "平";
    const resultClass = result === "胜" ? "match-result-win" : result === "负" ? "match-result-loss" : "match-result-draw";
    return `<tr>
      <td><a class="player-match-date" href="#/fixtures/${match.fixtureId}">${esc(when.date)}</a><small>第 ${esc(match.roundNo || "-")} 轮</small></td>
      <td class="team-cell"><a href="#/teams/${match.opponentTeamId}">${esc(match.opponentTeamName)}</a></td>
      <td><span class="player-match-score ${resultClass}">${esc(match.scoreFor)}:${esc(match.scoreAgainst)} <small>${result}</small></span></td>
      <td>${match.starter ? "首发" : "替补"}</td>
      <td>${match.minutesPlayed != null ? `${esc(match.minutesPlayed)}′` : "—"}</td>
      <td>${match.goals || "—"}</td>
      <td>${match.assists || "—"}</td>
      <td>${match.yellowCards || "—"}</td>
      <td>${match.redCards || "—"}</td>
      <td>${match.ownGoals || "—"}</td>
    </tr>`;
  }).join("");
  app.innerHTML = `<div class="page">
    <section class="player-hero">
      <div class="player-photo">${photo}</div>
      <div>
        <span class="eyebrow">PLAYER PROFILE</span>
        <h1 class="player-name">${esc(player.name)}</h1>
        <div class="player-meta">
          <span>${esc(team.name)}</span>
          <span>${esc(player.position || "位置待补充")}</span>
          <span>${player.jerseyNo ? `#${esc(player.jerseyNo)}` : "号码待定"}</span>
          ${age ? `<span>${age} 岁</span>` : ""}
        </div>
      </div>
      <div class="player-number"><strong>${esc(player.jerseyNo || "-")}</strong><span>JERSEY NUMBER</span></div>
    </section>
    <section class="player-season-bar card">
      <div><span class="eyebrow">SEASON DATA</span><h2>赛季数据</h2><p>只统计已完赛比赛，切换赛季即可查看历史数据。</p></div>
      <label class="player-season-picker" for="player-season-select"><span>选择赛季</span><select id="player-season-select" ${state.seasons.length ? "" : "disabled"}>${seasonOptions || '<option value="">暂无赛季</option>'}</select></label>
    </section>
    <section class="player-stats">${statCards}</section>
    <section class="card panel player-match-panel">
      <div class="panel-head"><h2 class="panel-title">逐场数据</h2><span class="badge no-dot badge-scheduled">${matches.length} 场</span></div>
      ${matches.length ? `<div class="table-wrap"><table class="data-table player-match-table">
        <thead><tr><th>日期</th><th>对手</th><th>比分</th><th>身份</th><th>分钟</th><th>进球</th><th>助攻</th><th>黄牌</th><th>红牌</th><th>乌龙</th></tr></thead>
        <tbody>${matchRows}</tbody>
      </table></div>` : emptyState("本赛季暂无出场数据", "比赛完赛并录入出场阵容后，这里会显示逐场统计。", "○")}
    </section>
    <section class="section content-grid">
      <div class="card panel">
        <div class="panel-head"><h2 class="panel-title">球员资料</h2><a class="text-link" href="#/teams/${team.id}">所属球队</a></div>
        <div class="info-list">
          <div class="info-row"><span>姓名</span><strong>${esc(player.name)}</strong></div>
          <div class="info-row"><span>球队</span><strong>${esc(team.name)}</strong></div>
          <div class="info-row"><span>球衣号码</span><strong>${esc(player.jerseyNo || "待定")}</strong></div>
          <div class="info-row"><span>场上位置</span><strong>${esc(player.position || "待补充")}</strong></div>
          <div class="info-row"><span>出生日期</span><strong>${esc(player.birthDate || "待补充")}</strong></div>
          <div class="info-row"><span>注册状态</span><strong>${esc(player.status === "ACTIVE" ? "有效" : "停用")}</strong></div>
        </div>
      </div>
      <div class="card panel">
        <div class="panel-head"><h2 class="panel-title">同队球员</h2><span class="text-link">${players.length} 人</span></div>
        <div class="rank-list">${players.filter((item) => item.id !== id).slice(0, 7).map((item) => `<a class="rank-item" href="#/players/${item.id}"><span class="rank-person"><strong>${esc(item.name)}</strong><span>${esc(item.position || "位置待补充")}</span></span><span class="rank-number">${esc(item.jerseyNo || "-")}</span></a>`).join("") || emptyState("暂无队友资料", "球队名单尚未完善。", "○")}</div>
      </div>
    </section>
  </div>`;
  document.getElementById("player-season-select")?.addEventListener("change", async (event) => {
    const seasonId = Number(event.target.value);
    if (!Number.isFinite(seasonId) || seasonId <= 0) return;
    state.seasonId = seasonId;
    localStorage.setItem(SEASON_KEY, String(seasonId));
    populateSeasonSelect();
    await renderPlayerDetail(id);
  });
}

async function renderFixtureDetail(fixtureId) {
  setTitle("比赛详情");
  showLoading();
  const id = Number(fixtureId);
  if (!Number.isFinite(id)) throw new ApiError("比赛编号无效", 400, "VALIDATION_FAILED");
  const match = await api(`/fixtures/${id}`);
  const [homePlayers, awayPlayers] = await Promise.all([
    api(`/teams/${match.homeTeam.id}/players`).catch(() => []),
    api(`/teams/${match.awayTeam.id}/players`).catch(() => []),
  ]);
  const playerMap = new Map([...homePlayers, ...awayPlayers].map((player) => [Number(player.id), player]));
  const when = dateParts(match.kickoffAt);
  const hasScore = match.homeScore != null && match.awayScore != null;
  const homeEvents = match.events.filter((event) => event.teamId === match.homeTeam.id).sort((a, b) => a.minute - b.minute);
  const awayEvents = match.events.filter((event) => event.teamId === match.awayTeam.id).sort((a, b) => a.minute - b.minute);
  const eventsHtml = match.events.length
    ? `<div class="event-list">${[...match.events].sort((a, b) => a.minute - b.minute).map((event) => eventRow(event, match, playerMap)).join("")}</div>`
    : emptyState("暂无比赛事件", "进球、助攻和红黄牌事件会在比赛过程中记录。", "○");
  const homeLineup = lineupHtml(match.homeTeam, homePlayers, match.appearances, match.homeTeam.id);
  const awayLineup = lineupHtml(match.awayTeam, awayPlayers, match.appearances, match.awayTeam.id);
  app.innerHTML = `<div class="page">
    <a class="text-link" href="#/fixtures" style="margin-top:30px">返回赛程</a>
    <section class="match-hero">
      <div class="match-meta-row">${statusBadge(match.status)}<span>${match.roundNo ? `第 ${match.roundNo} 轮` : "比赛详情"}</span><span>${esc(when.full)}</span></div>
      <div class="match-scoreboard">
        <a class="match-team" href="#/teams/${match.homeTeam.id}">${teamCrest(match.homeTeam, "large")}<h2>${esc(match.homeTeam.name)}</h2><span>主队</span></a>
        <div class="match-big-score ${match.status === "LIVE" ? "is-live" : ""}">
          <strong>${hasScore ? `${match.homeScore} : ${match.awayScore}` : "VS"}</strong>
          <span>${statusLabel(match.status)}</span>
        </div>
        <a class="match-team" href="#/teams/${match.awayTeam.id}">${teamCrest(match.awayTeam, "large")}<h2>${esc(match.awayTeam.name)}</h2><span>客队</span></a>
      </div>
      <div class="match-info"><span>场地 <strong>${esc(match.venue || "待定")}</strong></span><span>主裁 <strong>${esc(match.referee || "待定")}</strong></span><span>数据状态 <strong>${match.dataLocked ? "已锁定" : "可更新"}</strong></span></div>
    </section>
    <div class="detail-layout">
      <div class="stack">
        <section class="card panel"><div class="panel-head"><h2 class="panel-title">比赛事件</h2><span class="badge no-dot badge-scheduled">${match.events.length} 条</span></div>${eventsHtml}</section>
        <section class="card panel"><div class="panel-head"><h2 class="panel-title">出场阵容</h2><span class="badge no-dot badge-scheduled">${match.appearances.length} 人</span></div>
          <div class="lineup-grid">${homeLineup}${awayLineup}</div>
        </section>
      </div>
      <div class="stack">
        <section class="card panel"><div class="panel-head"><h2 class="panel-title">比赛信息</h2></div><div class="info-list">
          <div class="info-row"><span>比赛时间</span><strong>${esc(when.full)} ${esc(when.time)}</strong></div>
          <div class="info-row"><span>比赛场地</span><strong>${esc(match.venue || "待定")}</strong></div>
          <div class="info-row"><span>裁判</span><strong>${esc(match.referee || "待定")}</strong></div>
          <div class="info-row"><span>主队事件</span><strong>${homeEvents.length}</strong></div>
          <div class="info-row"><span>客队事件</span><strong>${awayEvents.length}</strong></div>
        </div></section>
        <section class="card panel"><div class="panel-head"><h2 class="panel-title">快速导航</h2></div><div class="account-actions" style="margin-top:0"><a class="button button-ghost button-small" href="#/teams/${match.homeTeam.id}">主队资料</a><a class="button button-ghost button-small" href="#/teams/${match.awayTeam.id}">客队资料</a></div></section>
      </div>
    </div>
  </div>`;
}

function eventRow(event, match, playerMap) {
  const player = playerMap.get(Number(event.playerId));
  const playerTeam = player ? teamById(player.teamId) : null;
  const team = event.teamId === match.homeTeam.id ? match.homeTeam : match.awayTeam;
  const type = ({ GOAL: ["⚽", "进球"], OWN_GOAL: ["OG", "乌龙球"], YELLOW_CARD: ["▰", "黄牌"], RED_CARD: ["■", "红牌"] })[event.eventType] || ["•", "事件"];
  const related = event.relatedPlayerId ? playerMap.get(Number(event.relatedPlayerId)) : null;
  const detail = event.eventType === "OWN_GOAL"
    ? `${team.name}得分 · 乌龙球员来自${playerTeam?.name || "对方"}`
    : `${team.name}${related ? ` · 助攻 ${related.name}` : ""}${event.note ? ` · ${event.note}` : ""}`;
  return `<div class="event-row">
    <span class="event-minute">${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'</span>
    <span class="event-icon">${type[0]}</span>
    <span class="event-main"><strong>${esc(type[1])} · ${esc(player?.name || `球员 #${event.playerId}`)}</strong><span>${esc(detail)}</span></span>
  </div>`;
}

function lineupHtml(team, players, appearances, teamId) {
  const playerMap = new Map(players.map((player) => [Number(player.id), player]));
  const rows = appearances.filter((appearance) => appearance.teamId === teamId);
  const starters = rows.filter((row) => row.starter);
  const subs = rows.filter((row) => !row.starter);
  const renderRows = (items) => items.length ? items.map((item) => `<div class="lineup-item"><span>${esc(item.starter ? "首发" : "替补")} · ${esc(playerMap.get(Number(item.playerId))?.name || `球员 #${item.playerId}`)}</span><span>${item.minutesPlayed != null ? `${item.minutesPlayed}'` : "—"}</span></div>`).join("") : `<div class="lineup-item"><span>阵容尚未录入</span><span>—</span></div>`;
  return `<div class="lineup-team"><h3>${teamCrest(team, "small")}${esc(team.shortName || team.name)}</h3><div class="lineup-list">${renderRows(starters)}${subs.length ? renderRows(subs) : ""}</div></div>`;
}

async function renderLoginPage() {
  setTitle("管理入口");
  if (state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "管理入口", "你已登录，可进入账号页查看当前权限。")}${emptyState("当前已有登录状态", `已登录为 ${roleLabel(state.session.role)}。`, "✓")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/account">进入账号页</a><button class="button button-ghost" type="button" id="logout-inline">退出登录</button></div></div>`;
    document.getElementById("logout-inline")?.addEventListener("click", logout);
    return;
  }
  app.innerHTML = `<div class="page login-layout">
    <section class="login-story">
      <div><span class="hero-kicker"><span class="pulse"></span>MANAGEMENT ACCESS</span><h1>管理，从<br>赛场之外开始。</h1><p>网站账号与小程序共用管理员、球队管理员和领队权限。登录后可查看当前授权范围，并为球队工作人员配置网站访问凭据。</p></div>
      <div class="login-story-list"><div><span>01</span>保持现有小程序登录与业务功能不变</div><div><span>02</span>网站使用独立账号密码与 JWT 会话</div><div><span>03</span>角色与球队授权实时读取后端数据</div></div>
    </section>
    <section class="card login-card">
      <span class="eyebrow">SIGN IN</span><h2>管理员 / 领队登录</h2><p>使用由超级管理员配置的网站账号登录。公开赛程与数据无需登录。</p>
      <div class="form-error" id="login-error" role="alert"></div>
      <form id="login-form">
        <div class="form-field"><label for="login-username">网站账号</label><input id="login-username" name="username" autocomplete="username" required maxlength="64" placeholder="例如 campus-admin"></div>
        <div class="form-field"><label for="login-password">密码</label><input id="login-password" name="password" type="password" autocomplete="current-password" required maxlength="72" placeholder="至少 8 个字符"></div>
        <button class="button button-primary" type="submit" id="login-submit">登录管理入口</button>
      </form>
      <p class="form-message">首次部署请设置环境变量 <strong>WEB_ADMIN_USERNAME</strong> 与 <strong>WEB_ADMIN_PASSWORD</strong>，服务启动时会自动创建超级管理员网站账号。</p>
    </section>
  </div>`;
  document.getElementById("login-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("login-submit");
    const errorBox = document.getElementById("login-error");
    errorBox.classList.remove("visible");
    button.disabled = true;
    button.textContent = "正在登录…";
    try {
      const session = await api("/auth/web-login", {
        method: "POST",
        body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }),
      });
      saveSession(session);
      notify(`欢迎回来，${session.nickname || roleLabel(session.role)}`);
      location.hash = "#/account";
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.classList.add("visible");
    } finally {
      button.disabled = false;
      button.textContent = "登录管理入口";
    }
  });
}

async function renderAccountPage() {
  if (!state.session) {
    location.hash = "#/login";
    return;
  }
  setTitle("账号中心");
  showLoading();
  let me;
  try {
    me = await api("/me");
    state.session = { ...state.session, ...me };
    saveSession(state.session);
  } catch (error) {
    saveSession(null);
    setTitle("登录已失效");
    app.innerHTML = `<div class="page" style="padding-top:40px">${errorState(error, "登录状态已失效")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">重新登录</a></div></div>`;
    return;
  }
  const teams = me.managedTeamIds.map((teamId) => teamById(teamId));
  const teamHtml = teams.length ? teams.map((team) => `<a class="info-row" href="#/teams/${team.id}"><span>${esc(team.name)}</span><strong>查看 →</strong></a>`).join("") : `<div class="info-row"><span>授权球队</span><strong>${me.role === "ADMIN" || me.role === "TEAM_MANAGER" ? "全部球队" : "暂未关联"}</strong></div>`;
  const credentialPanel = me.role === "ADMIN" ? `<section class="card account-card">
    <div class="panel-head"><h2 class="panel-title">配置工作人员网站账号</h2><span class="badge no-dot badge-admin">仅超级管理员</span></div>
    <p class="form-message" style="margin-top:-8px">搜索用户昵称、ID 或网站账号，选择后为其配置网站登录凭据。角色必须已是球队管理员或领队。</p>
    <div class="form-error" id="credential-error" role="alert"></div>
    <div class="form-error" id="credential-success" role="status" style="background:#e9f7ef;color:#0b5b41"></div>
    <div class="form-field">
      <label for="user-search-input">搜索用户</label>
      <input id="user-search-input" type="search" autocomplete="off" placeholder="输入昵称、用户 ID 或网站账号" aria-describedby="user-search-help">
      <small id="user-search-help" class="form-help">输入至少 1 个字符后自动搜索</small>
    </div>
    <div id="user-search-results" class="user-search-results" aria-label="搜索结果"></div>
    <div id="credential-form-area" style="display:none">
      <div class="selected-user-banner" id="selected-user-banner"></div>
      <form id="credential-form">
        <input type="hidden" id="credential-user-id" name="userId">
        <div class="form-field"><label for="credential-username">网站账号</label><input id="credential-username" name="username" required minlength="3" maxlength="64" autocomplete="off" placeholder="3-64 个字符"></div>
        <div class="form-field"><label for="credential-password">初始密码</label><input id="credential-password" name="password" type="password" required minlength="8" maxlength="72" autocomplete="new-password" placeholder="至少 8 个字符"></div>
        <div class="account-actions" style="margin-top:8px">
          <button class="button button-primary" type="submit" id="credential-submit">保存网站账号</button>
          <button class="button button-ghost" type="button" id="credential-cancel">取消选择</button>
        </div>
      </form>
    </div>
  </section>` : "";
  app.innerHTML = `<div class="page">
    ${pageHeader("ACCOUNT", "账号中心", "查看当前网站登录状态与授权范围。", `<button class="button button-danger button-small" type="button" id="logout-button">退出登录</button>`)}
    <div class="account-grid">
      <div class="stack">
        <section class="card account-card">
          <div class="account-identity"><div class="account-avatar">${esc(initials(me.nickname || roleLabel(me.role)))}</div><div><h2>${esc(me.nickname || "赛事工作人员")}</h2><p>用户 ID ${me.userId} · ${esc(roleLabel(me.role))}</p></div></div>
          <div class="permission-note"><strong>当前权限</strong>${permissionDescription(me.role)}</div>
          <div class="account-actions"><a class="button button-primary button-small" href="#/fixtures">查看赛事</a><a class="button button-ghost button-small" href="#/teams">球队资料</a></div>
        </section>
        ${credentialPanel}
      </div>
      <div class="stack">
        <section class="card account-card"><div class="panel-head"><h2 class="panel-title">授权范围</h2>${roleBadge(me.role)}</div><div class="info-list">${teamHtml}</div></section>
        <section class="card account-card"><div class="panel-head"><h2 class="panel-title">业务管理说明</h2></div><p class="form-message" style="margin:0">当前 MVP 网站负责赛程、积分、球队球员和榜单浏览，以及工作人员登录入口。比分、阵容和赛事配置继续在现有微信小程序管理端完成，避免重复建设与前端权限分叉。</p></section>
      </div>
    </div>
  </div>`;
  document.getElementById("logout-button")?.addEventListener("click", logout);
  if (me.role === "ADMIN") {
    setupUserSearch();
  }
  document.getElementById("credential-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("credential-submit");
    const errorBox = document.getElementById("credential-error");
    const successBox = document.getElementById("credential-success");
    const userId = form.userId.value.trim();
    if (!userId) {
      errorBox.textContent = "请先从搜索结果中选择一个用户";
      errorBox.classList.add("visible");
      return;
    }
    errorBox.classList.remove("visible");
    successBox.classList.remove("visible");
    button.disabled = true;
    button.textContent = "正在保存…";
    try {
      const result = await api(`/admin/users/${encodeURIComponent(userId)}/web-credentials`, {
        method: "PUT",
        body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }),
      });
      successBox.textContent = `已为用户 ${result.userId} 配置账号 ${result.username}`;
      successBox.classList.add("visible");
      document.getElementById("credential-form-area").style.display = "none";
      document.getElementById("selected-user-banner").innerHTML = "";
      document.getElementById("credential-user-id").value = "";
      document.getElementById("user-search-input").value = "";
      document.getElementById("user-search-results").innerHTML = "";
      form.reset();
      notify("网站账号已保存");
    } catch (error) {
      errorBox.textContent = error.message;
      errorBox.classList.add("visible");
    } finally {
      button.disabled = false;
      button.textContent = "保存网站账号";
    }
  });
}

function setupUserSearch() {
  const searchInput = document.getElementById("user-search-input");
  const resultsContainer = document.getElementById("user-search-results");
  const formArea = document.getElementById("credential-form-area");
  const banner = document.getElementById("selected-user-banner");
  const userIdField = document.getElementById("credential-user-id");
  const cancelBtn = document.getElementById("credential-cancel");
  let searchTimer = null;
  let requestId = 0;

  if (!searchInput || !resultsContainer) return;

  function resetFormArea() {
    if (userIdField) userIdField.value = "";
    if (formArea) formArea.style.display = "none";
    if (banner) banner.innerHTML = "";
    const form = document.getElementById("credential-form");
    if (form) form.reset();
    const username = document.getElementById("credential-username");
    const password = document.getElementById("credential-password");
    if (username) username.value = "";
    if (password) password.value = "";
    const errorBox = document.getElementById("credential-error");
    const successBox = document.getElementById("credential-success");
    if (errorBox) { errorBox.textContent = ""; errorBox.classList.remove("visible"); }
    if (successBox) { successBox.textContent = ""; successBox.classList.remove("visible"); }
  }

  searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    const query = searchInput.value.trim();
    if (query.length < 1) {
      resultsContainer.innerHTML = "";
      requestId += 1;
      return;
    }
    searchTimer = setTimeout(() => searchUsers(query), 300);
  });

  async function searchUsers(query) {
    const currentRequestId = ++requestId;
    resultsContainer.innerHTML = `<div class="user-search-loading">搜索中…</div>`;
    try {
      const users = await api(`/admin/users?query=${encodeURIComponent(query)}`);
      if (currentRequestId !== requestId) return;
      if (!users || !users.length) {
        resultsContainer.innerHTML = `<div class="user-search-empty">未找到匹配用户</div>`;
        return;
      }
      resultsContainer.innerHTML = users.map((user) => {
        const isViewer = user.role === "VIEWER";
        if (isViewer) {
          return `<div class="user-search-item disabled" aria-disabled="true" data-user-id="${user.id}">
            <span class="user-search-id">#${user.id}</span>
            <span class="user-search-name">${esc(user.nickname || "未命名")}</span>
            <span class="user-search-role">${esc(roleLabel(user.role))}</span>
            ${user.webUsername ? `<span class="user-search-web">${esc(user.webUsername)}</span>` : ""}
            <span class="user-search-hint">只读用户不可配置</span>
          </div>`;
        }
        return `<button type="button" class="user-search-item" data-user-id="${user.id}">
          <span class="user-search-id">#${user.id}</span>
          <span class="user-search-name">${esc(user.nickname || "未命名")}</span>
          <span class="user-search-role">${esc(roleLabel(user.role))}</span
          >${user.webUsername ? `<span class="user-search-web">${esc(user.webUsername)}</span>` : ""}
        </button>`;
      }).join("");
      resultsContainer.querySelectorAll("button.user-search-item").forEach((btn) => {
        btn.addEventListener("click", () => selectUser(btn));
      });
    } catch (error) {
      if (currentRequestId !== requestId) return;
      resultsContainer.innerHTML = `<div class="user-search-empty">搜索失败：${esc(error.message)}</div>`;
    }
  }

  function selectUser(item) {
    const userId = item.dataset.userId;
    const name = item.querySelector(".user-search-name")?.textContent || "";
    const role = item.querySelector(".user-search-role")?.textContent || "";
    const webUsername = item.querySelector(".user-search-web")?.textContent || "";
    userIdField.value = userId;
    banner.innerHTML = `已选择：<strong>${esc(name)}</strong>（#${esc(userId)} · ${esc(role)}${webUsername ? ` · 当前账号 ${esc(webUsername)}` : ""}）`;
    formArea.style.display = "block";
    resultsContainer.innerHTML = "";
    searchInput.value = "";
    const usernameField = document.getElementById("credential-username");
    if (usernameField) usernameField.value = webUsername || "";
    usernameField?.focus();
  }

  cancelBtn?.addEventListener("click", () => {
    resetFormArea();
    searchInput.value = "";
    resultsContainer.innerHTML = "";
    requestId += 1;
    searchInput.focus();
  });
}

async function renderAdminHub() {
  setTitle("管理台");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "管理台", "登录后进入球队、球员和战术板管理。")}${emptyState("请先登录", "管理台仅对管理员、球队管理员和领队开放。", "▦")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "管理台", "读取权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role === "VIEWER") {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "管理台", "当前账号没有编辑权限。")}${emptyState("只读账号", "请使用管理员、球队管理员或领队账号登录。", "▦")}</div>`;
    return;
  }
  const canManageAll = me.role === "ADMIN" || me.role === "TEAM_MANAGER";
  const teamScope = canManageAll ? "全部球队" : `${me.managedTeamIds.length} 支授权球队`;
  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("MANAGEMENT", "管理台", `当前身份：${roleLabel(me.role)} · 可管理范围：${teamScope}`, `<a class="button button-ghost button-small" href="#/account">账号中心</a>`)}
    <div class="admin-welcome card">
      <div><span class="eyebrow">WORKSPACE</span><h2>今天要维护哪一部分？</h2><p>新增比赛、录比分、记事件、排出场阵容、维护球队名单和战术板，都已放到 Web 管理台。</p></div>
      <span class="admin-role-pill">${roleBadge(me.role)}</span>
    </div>
    <div class="admin-hub-grid">
      ${canManageAll ? `<a class="card admin-module-card admin-module-primary" href="#/admin/fixtures?new=1"><span class="admin-module-icon">＋</span><span><strong>新增比赛</strong><small>直接创建新赛程，不用再进小程序找入口</small></span><b>立即新增 →</b></a>` : ""}
      <a class="card admin-module-card" href="#/admin/fixtures"><span class="admin-module-icon">◫</span><span><strong>比赛管理</strong><small>编辑赛程、状态、比分、事件和出场阵容</small></span><b>进入 →</b></a>
      <a class="card admin-module-card" href="#/admin/teams"><span class="admin-module-icon">◉</span><span><strong>球队与球员</strong><small>编辑球队资料、球员号码和状态</small></span><b>进入 →</b></a>
      ${me.role === "ADMIN" ? `<a class="card admin-module-card" href="#/admin/announcements"><span class="admin-module-icon">!</span><span><strong>公告板</strong><small>发布赛事通知，首页展示并在访客进入时弹出</small></span><b>发布 →</b></a>` : ""}
      ${me.role === "ADMIN" ? `<a class="card admin-module-card" href="#/admin/news"><span class="admin-module-icon">▧</span><span><strong>新闻栏</strong><small>上传比赛实况图，编辑首页横向新闻内容</small></span><b>管理 →</b></a>` : ""}
      <a class="card admin-module-card" href="#/tactics"><span class="admin-module-icon">▦</span><span><strong>战术板</strong><small>自由拖拽球员，保存多个阵型方案</small></span><b>进入 →</b></a>
      <a class="card admin-module-card" href="#/account"><span class="admin-module-icon">⌁</span><span><strong>账号与权限</strong><small>查看授权范围和管理工作人员网站账号</small></span><b>查看 →</b></a>
    </div>
    <section class="card admin-note"><strong>权限说明</strong><p>管理员和球队管理员可新增比赛并维护全部比赛数据；领队可在比赛管理中维护被授权球队的出场阵容。所有写操作都由后端再次校验。</p></section>
  </div>`;
}

async function renderAdminAnnouncementsPage() {
  setTitle("公告管理");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "公告管理", "请先登录管理台。")}${emptyState("请先登录", "登录后即可发布公告。", "!")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "公告管理", "读取权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role !== "ADMIN") {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "公告管理", "只有超级管理员可以发布全员公告。")}${emptyState("没有权限", "球队管理员和领队不能发布全员公告。", "!")}<div class="page-actions" style="margin-top:18px"><a class="button button-ghost" href="#/admin">返回管理台</a></div></div>`;
    return;
  }
  let page;
  try {
    page = await api("/admin/announcements?page=0&size=30");
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "公告管理", "读取公告失败。")}${errorState(error)}</div>`;
    return;
  }
  const audienceLabel = ({ ALL: "全员公告", ADMIN: "管理员", TEAM_STAFF: "球队工作人员", TEAM: "指定球队", USER: "指定用户" });
  const items = page.items || [];
  const listHtml = items.length ? items.map((item) => `<article class="admin-announcement-row"><div><div class="admin-announcement-meta"><time>${esc(announcementDate(item.createdAt))}</time><span class="badge no-dot badge-scheduled">${esc(audienceLabel[item.audience] || item.audience)}</span></div><h3>${esc(item.title)}</h3><p>${esc(item.content)}</p></div><div class="admin-announcement-stats"><span>触达 ${item.deliveredCount}</span><span>已读 ${item.readCount}</span></div></article>`).join("") : emptyState("暂无公告", "发布第一条公告后会显示在这里。", "!");
  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("NOTICE ADMIN", "公告管理", "发布全员公告。公告会出现在首页公告板，并在访客下次进入网站时弹出。", `<a class="button button-ghost button-small" href="#/admin">返回管理台</a>`)}
    <div class="admin-announcement-layout">
      <section class="card admin-form-card"><div class="panel-head"><h2 class="panel-title">发布新公告</h2><span class="badge no-dot badge-admin">全员可见</span></div>
        <form id="admin-announcement-form"><div class="form-field"><label for="announcement-title">公告标题</label><input id="announcement-title" name="title" maxlength="120" required placeholder="例如：本周末赛程调整通知"></div><div class="form-field"><label for="announcement-content">公告内容</label><textarea id="announcement-content" name="content" maxlength="500" rows="7" required placeholder="填写要通知所有访客的内容"></textarea><small class="form-help">最多 500 个字符。发布后会同步到首页公告板。</small></div><div class="admin-create-intro"><strong>进站弹窗</strong><span>访客看到最新公告后，浏览器会记住已读状态；后续只有新公告才会再次弹出。</span></div><div class="page-actions"><button class="button button-primary" type="submit">发布公告</button></div></form>
      </section>
      <section class="card panel"><div class="panel-head"><h2 class="panel-title">公告记录</h2><span class="badge no-dot badge-scheduled">${page.total || 0} 条</span></div><div class="admin-announcement-list">${listHtml}</div></section>
    </div>
  </div>`;
  document.getElementById("admin-announcement-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      await api("/admin/announcements", { method: "POST", body: JSON.stringify({ title: form.title.value.trim(), content: form.content.value.trim(), audience: "ALL" }) });
      state.announcements = await api("/announcements?limit=10").catch(() => state.announcements);
      notify("公告已发布");
      await renderAdminAnnouncementsPage();
    } catch (error) {
      notify(error.message);
      button.disabled = false;
    }
  });
}

async function renderAdminNewsPage() {
  setTitle("新闻栏管理");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "新闻栏管理", "请先登录管理台。")}${emptyState("请先登录", "登录后即可管理首页新闻。", "▧")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "新闻栏管理", "读取权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role !== "ADMIN") {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "新闻栏管理", "只有超级管理员可以编辑新闻栏。")}${emptyState("没有权限", "请联系超级管理员。", "▧")}<div class="page-actions" style="margin-top:18px"><a class="button button-ghost" href="#/admin">返回管理台</a></div></div>`;
    return;
  }
  let items;
  try {
    items = await api("/admin/news");
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "新闻栏管理", "读取新闻失败。")}${errorState(error)}</div>`;
    return;
  }
  const editId = Number(param("edit")) || null;
  const editing = items.find((item) => item.id === editId);
  const defaultDate = new Date().toISOString().slice(0, 10);
  const defaultOrder = items.length ? Math.max(...items.map((item) => item.displayOrder)) + 10 : 10;
  const listHtml = items.length ? items.map((item) => `<article class="admin-news-row"><img src="${esc(item.imageUrl)}" alt="${esc(item.title)}"><div class="admin-news-copy"><div class="admin-news-meta"><span class="badge no-dot ${item.active ? "badge-live" : "badge-cancelled"}">${item.active ? "首页显示" : "已隐藏"}</span><span>排序 ${item.displayOrder}</span><time>${esc(item.publishedAt)}</time></div><h3>${esc(item.title)}</h3><p>${esc(item.description)}</p></div><div class="admin-news-actions"><a class="button button-ghost button-small" href="#/admin/news?edit=${item.id}">编辑</a><button class="button button-danger button-small" type="button" data-delete-news="${item.id}">删除</button></div></article>`).join("") : emptyState("暂无管理新闻", "当前首页仍显示 4 张默认实况图。可以导入默认图后编辑，也可以直接新增。", "▧");
  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("NEWS ADMIN", editing ? "编辑新闻" : "新闻栏管理", "管理首页“赛场实况”的图片、标题、说明和排序。", `<a class="button button-ghost button-small" href="#/admin">返回管理台</a>`)}
    <div class="admin-news-layout">
      <section class="card admin-form-card"><div class="panel-head"><h2 class="panel-title">${editing ? "编辑新闻" : "新增新闻"}</h2>${editing ? `<a class="text-link" href="#/admin/news">取消编辑</a>` : ""}</div><form id="admin-news-form"><div class="form-field"><label for="news-title">标题</label><input id="news-title" name="title" maxlength="120" required value="${esc(editing?.title || "")}"></div><div class="form-field"><label for="news-description">说明</label><textarea id="news-description" name="description" maxlength="300" rows="4" required>${esc(editing?.description || "")}</textarea></div><div class="admin-form-grid"><div class="form-field"><label for="news-tag">分类标签</label><input id="news-tag" name="tag" maxlength="40" required value="${esc(editing?.tag || "赛场实况")}"></div><div class="form-field"><label for="news-date">发布日期</label><input id="news-date" name="publishedAt" type="date" required value="${esc(editing?.publishedAt || defaultDate)}"></div><div class="form-field"><label for="news-order">排序值</label><input id="news-order" name="displayOrder" type="number" min="0" max="9999" value="${editing?.displayOrder ?? defaultOrder}"></div><div class="form-field"><label for="news-image">图片${editing ? "（不选则保留原图）" : ""}</label><input id="news-image" name="image" type="file" accept="image/jpeg,image/png,image/webp" ${editing ? "" : "required"}><small class="form-help">支持 JPG、PNG、WebP，最大 5MB。</small></div></div><label class="admin-check"><input id="news-active" type="checkbox" ${editing?.active === false ? "" : "checked"}> 在首页新闻栏显示</label><div class="page-actions"><button class="button button-primary" type="submit">${editing ? "保存修改" : "发布新闻"}</button>${editing ? `<a class="button button-ghost" href="#/admin/news">取消</a>` : ""}</div></form></section>
      <section class="card panel"><div class="panel-head"><h2 class="panel-title">新闻内容</h2><span class="badge no-dot badge-scheduled">${items.length} 条</span></div>${!items.length ? `<div class="admin-news-seed"><button class="button button-lime button-small" type="button" id="seed-default-news">导入当前 4 张默认新闻</button></div>` : ""}<div class="admin-news-list">${listHtml}</div></section>
    </div>
  </div>`;
  const form = document.getElementById("admin-news-form");
  document.getElementById("seed-default-news")?.addEventListener("click", async (event) => {
    event.currentTarget.disabled = true;
    try { await api("/admin/news/seed-defaults", { method: "POST" }); state.newsFeed = await api("/news?limit=8"); notify("默认新闻已导入"); await renderAdminNewsPage(); } catch (error) { notify(error.message); event.currentTarget.disabled = false; }
  });
  document.querySelectorAll("[data-delete-news]").forEach((button) => button.addEventListener("click", async () => {
    if (!window.confirm("确认删除这条新闻？")) return;
    try { await api(`/admin/news/${button.dataset.deleteNews}`, { method: "DELETE" }); state.newsFeed = await api("/news?limit=8"); notify("新闻已删除"); await renderAdminNewsPage(); } catch (error) { notify(error.message); }
  }));
  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    const data = new FormData();
    data.append("title", document.getElementById("news-title").value.trim());
    data.append("description", document.getElementById("news-description").value.trim());
    data.append("tag", document.getElementById("news-tag").value.trim());
    data.append("publishedAt", document.getElementById("news-date").value);
    data.append("displayOrder", document.getElementById("news-order").value || "0");
    data.append("active", String(document.getElementById("news-active").checked));
    const file = document.getElementById("news-image").files[0];
    if (file) data.append("image", file);
    try {
      await api(editing ? `/admin/news/${editing.id}` : "/admin/news", { method: editing ? "PUT" : "POST", body: data });
      state.newsFeed = await api("/news?limit=8");
      notify(editing ? "新闻已更新" : "新闻已发布");
      location.hash = "#/admin/news";
      await renderAdminNewsPage();
    } catch (error) { notify(error.message); button.disabled = false; }
  });
}

async function renderAdminTeamsPage() {
  setTitle("球队与球员管理");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "请先登录管理台。")}${emptyState("请先登录", "登录后即可编辑球队和球员资料。", "◉")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "读取权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role === "VIEWER") {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "当前账号没有编辑权限。")}${emptyState("只读账号", "请使用管理员、球队管理员或领队账号。", "◉")}</div>`;
    return;
  }
  const canManageAll = me.role === "ADMIN" || me.role === "TEAM_MANAGER";
  let teams;
  try {
    teams = canManageAll ? await api("/admin/teams") : state.teams.filter((team) => me.managedTeamIds.includes(team.id));
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "读取球队失败。")}${errorState(error)}</div>`;
    return;
  }
  if (!teams.length) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "当前账号没有可管理球队。")}${emptyState("暂无可编辑球队", "请联系超级管理员配置球队授权。", "◉")}<div class="page-actions" style="margin-top:18px"><a class="button button-ghost" href="#/admin">返回管理台</a></div></div>`;
    return;
  }
  let selectedTeamId = Number(param("teamId"));
  if (!teams.some((team) => team.id === selectedTeamId)) selectedTeamId = teams[0].id;
  let selectedTeam = teams.find((team) => team.id === selectedTeamId);
  let creatingTeam = false;
  let players = [];
  let selectedPlayer = null;
  try {
    players = await api(canManageAll ? `/admin/teams/${selectedTeamId}/players` : `/manage/teams/${selectedTeamId}/players`);
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "球队与球员", "读取球员名单失败。")}${errorState(error)}<div class="page-actions" style="margin-top:18px"><a class="button button-ghost" href="#/admin">返回管理台</a></div></div>`;
    return;
  }

  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("MANAGEMENT", "球队与球员", "编辑球队资料和球员名单；所有修改会写入现有赛事数据库并记录审计。", `<a class="button button-ghost button-small" href="#/admin">返回管理台</a>`)}
    <div class="admin-teams-layout">
      <aside class="card admin-team-panel">
        <div class="panel-head"><h2 class="panel-title">球队</h2>${canManageAll ? `<button class="button button-ghost button-small" type="button" id="admin-new-team">新建</button>` : ""}</div>
        <div class="admin-team-list" id="admin-team-list">${teams.map((team) => `<button type="button" class="admin-team-button ${team.id === selectedTeamId ? "active" : ""}" data-team-id="${team.id}"><span>${esc(team.name)}</span><small>${esc(team.shortName || "未设置简称")}</small></button>`).join("")}</div>
        <div class="admin-team-meta"><span>${teams.length} 支球队</span><span>${canManageAll ? "全部球队" : "仅授权球队"}</span></div>
      </aside>
      <div class="stack admin-editor-stack">
        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title" id="admin-team-form-title">球队资料</h2><span class="badge no-dot ${canManageAll ? "badge-admin" : "badge-leader"}">${canManageAll ? "可编辑" : "领队权限"}</span></div>
          <form id="admin-team-form">
            <div class="admin-form-grid">
              <div class="form-field"><label for="admin-team-name">球队名称</label><input id="admin-team-name" name="name" maxlength="100" required></div>
              <div class="form-field"><label for="admin-team-short">简称</label><input id="admin-team-short" name="shortName" maxlength="32"></div>
              <div class="form-field"><label for="admin-team-coach">主教练</label><input id="admin-team-coach" name="coachName" maxlength="64"></div>
              <div class="form-field"><label for="admin-team-venue">主场</label><input id="admin-team-venue" name="homeVenue" maxlength="128"></div>
              <div class="form-field admin-form-wide"><label for="admin-team-logo">队徽 URL</label><input id="admin-team-logo" name="logoUrl" maxlength="512" placeholder="可留空"></div>
              <div class="form-field" ${canManageAll ? "" : "hidden"}><label for="admin-team-status">球队状态</label><select id="admin-team-status" name="status"><option value="ACTIVE">有效</option><option value="INACTIVE">停用</option></select></div>
            </div>
            <div class="page-actions"><button class="button button-primary button-small" type="submit" id="admin-team-save">保存球队资料</button></div>
          </form>
        </section>
        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title" id="admin-player-form-title">新增球员</h2><button class="button button-ghost button-small" type="button" id="admin-new-player">新增球员</button></div>
          <form id="admin-player-form">
            <div class="admin-form-grid">
              <div class="form-field"><label for="admin-player-name">姓名</label><input id="admin-player-name" name="name" maxlength="64" required></div>
              <div class="form-field"><label for="admin-player-jersey">球衣号码</label><input id="admin-player-jersey" name="jerseyNo" maxlength="20"></div>
              <div class="form-field"><label for="admin-player-position">位置</label><input id="admin-player-position" name="position" maxlength="20" placeholder="如 前锋、中场"></div>
              <div class="form-field"><label for="admin-player-birth">出生日期</label><input id="admin-player-birth" name="birthDate" type="date"></div>
              <div class="form-field"><label for="admin-player-photo">照片 URL（可选）</label><input id="admin-player-photo" name="photoUrl" maxlength="512" placeholder="外链图片，上传图片优先"></div>
              <div class="form-field"><label for="admin-player-status">球员状态</label><select id="admin-player-status" name="status"><option value="ACTIVE">有效</option><option value="INACTIVE">停用</option></select></div>
              <div class="form-field admin-form-wide"><label for="admin-player-photo-file">直接上传照片</label><div class="admin-player-photo-editor"><div class="admin-player-photo-preview" id="admin-player-photo-preview"></div><div class="admin-player-photo-controls"><input id="admin-player-photo-file" type="file" accept="image/jpeg,image/png,image/webp"><small class="form-help">支持 JPG、PNG、WebP，最大 5MB。上传图片会优先于照片 URL。</small><button class="button button-danger button-small" type="button" id="admin-player-photo-remove" hidden>移除已上传照片</button></div></div></div>
            </div>
            <div class="page-actions"><button class="button button-primary button-small" type="submit" id="admin-player-save">保存球员</button></div>
          </form>
        </section>
        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title">球员名单</h2><span class="badge no-dot badge-scheduled">${players.length} 人</span></div>
          <div class="admin-player-list" id="admin-player-list">${players.length ? players.map((player) => `<button type="button" class="admin-player-row" data-player-id="${player.id}"><span class="jersey">${esc(player.jerseyNo || "-")}</span><span><strong>${esc(player.name)}</strong><small>${esc(player.position || "位置待补")} · ${esc(player.status === "ACTIVE" ? "有效" : "停用")}</small></span><span>编辑 →</span></button>`).join("") : `<div class="tactic-plan-empty">暂无球员，先新增一名球员。</div>`}</div>
        </section>
      </div>
    </div>
  </div>`;

  const teamForm = document.getElementById("admin-team-form");
  const playerForm = document.getElementById("admin-player-form");
  const teamTitle = document.getElementById("admin-team-form-title");
  const playerTitle = document.getElementById("admin-player-form-title");
  const teamList = document.getElementById("admin-team-list");
  const playerList = document.getElementById("admin-player-list");

  function fillTeamForm(team) {
    creatingTeam = !team;
    selectedTeam = team || selectedTeam;
    teamTitle.textContent = team ? "球队资料" : "新建球队";
    document.getElementById("admin-team-name").value = team?.name || "";
    document.getElementById("admin-team-short").value = team?.shortName || "";
    document.getElementById("admin-team-coach").value = team?.coachName || "";
    document.getElementById("admin-team-venue").value = team?.homeVenue || "";
    document.getElementById("admin-team-logo").value = team?.logoUrl || "";
    document.getElementById("admin-team-status").value = team?.status || "ACTIVE";
    document.getElementById("admin-team-save").textContent = team ? "保存球队资料" : "创建球队";
  }

  function hasManagedPhoto(player) {
    return Boolean(player?.photoUrl && player.photoUrl.includes(`/api/v1/players/${player.id}/photo`));
  }

  function renderPlayerPhotoPreview(player) {
    const preview = document.getElementById("admin-player-photo-preview");
    const removeButton = document.getElementById("admin-player-photo-remove");
    const fileInput = document.getElementById("admin-player-photo-file");
    fileInput.value = "";
    if (player?.photoUrl) {
      preview.innerHTML = `<img src="${esc(player.photoUrl)}" alt="${esc(player.name)}照片">`;
    } else {
      preview.textContent = initials(player?.name || "球员");
    }
    removeButton.hidden = !hasManagedPhoto(player);
  }

  function fillPlayerForm(player) {
    selectedPlayer = player || null;
    playerTitle.textContent = player ? `编辑球员 · ${player.name}` : "新增球员";
    document.getElementById("admin-player-name").value = player?.name || "";
    document.getElementById("admin-player-jersey").value = player?.jerseyNo || "";
    document.getElementById("admin-player-position").value = player?.position || "";
    document.getElementById("admin-player-birth").value = player?.birthDate || "";
    document.getElementById("admin-player-photo").value = hasManagedPhoto(player) ? "" : (player?.photoUrl || "");
    document.getElementById("admin-player-status").value = player?.status || "ACTIVE";
    document.getElementById("admin-player-save").textContent = player ? "保存球员" : "新增球员";
    renderPlayerPhotoPreview(player);
  }

  function selectTeam(team) {
    location.hash = `#/admin/teams?teamId=${team.id}`;
    renderAdminTeamsPage();
  }

  teamList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-team-id]");
    if (!button) return;
    const team = teams.find((item) => item.id === Number(button.dataset.teamId));
    if (team) selectTeam(team);
  });

  document.getElementById("admin-new-team")?.addEventListener("click", () => fillTeamForm(null));

  playerList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-player-id]");
    if (!button) return;
    const player = players.find((item) => item.id === Number(button.dataset.playerId));
    if (player) fillPlayerForm(player);
  });

  document.getElementById("admin-new-player").addEventListener("click", () => fillPlayerForm(null));
  document.getElementById("admin-player-photo-file").addEventListener("change", (event) => {
    const file = event.target.files[0];
    if (!file) { renderPlayerPhotoPreview(selectedPlayer); return; }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type) || file.size > 5 * 1024 * 1024) {
      notify("照片仅支持 JPG、PNG、WebP，且不能超过 5MB");
      event.target.value = "";
      renderPlayerPhotoPreview(selectedPlayer);
      return;
    }
    document.getElementById("admin-player-photo-preview").innerHTML = `<img src="${URL.createObjectURL(file)}" alt="待上传球员照片预览">`;
  });

  document.getElementById("admin-player-photo-remove").addEventListener("click", async () => {
    if (!selectedPlayer || !hasManagedPhoto(selectedPlayer) || !window.confirm("确认移除已上传的球员照片？")) return;
    try {
      await api(`/manage/players/${selectedPlayer.id}/photo`, { method: "DELETE" });
      notify("球员照片已移除");
      await renderAdminTeamsPage();
    } catch (error) { notify(error.message); }
  });

  teamForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("admin-team-save");
    button.disabled = true;
    try {
      const payload = {
        name: form.name.value.trim(),
        shortName: form.shortName.value.trim() || null,
        coachName: form.coachName.value.trim() || null,
        homeVenue: form.homeVenue.value.trim() || null,
        logoUrl: form.logoUrl.value.trim() || null,
        status: form.status.value || selectedTeam.status,
      };
      if (creatingTeam) {
        const created = await api("/admin/teams", { method: "POST", body: JSON.stringify(payload) });
        notify("球队已创建");
        location.hash = `#/admin/teams?teamId=${created.id}`;
        await renderAdminTeamsPage();
      } else {
        await api(`/manage/teams/${selectedTeam.id}`, { method: "PUT", body: JSON.stringify(payload) });
        notify("球队资料已保存");
        await renderAdminTeamsPage();
      }
    } catch (error) {
      notify(error.message);
    } finally {
      button.disabled = false;
    }
  });

  playerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("admin-player-save");
    button.disabled = true;
    try {
      const payload = {
        name: form.name.value.trim(),
        jerseyNo: form.jerseyNo.value.trim() || null,
        position: form.position.value.trim() || null,
        birthDate: form.birthDate.value || null,
        photoUrl: form.photoUrl.value.trim() || null,
        status: form.status.value || "ACTIVE",
      };
      let playerId = selectedPlayer?.id || null;
      if (selectedPlayer) {
        await api(`/manage/players/${selectedPlayer.id}`, { method: "PUT", body: JSON.stringify(payload) });
      } else {
        const created = await api(`/manage/teams/${selectedTeamId}/players`, { method: "POST", body: JSON.stringify(payload) });
        playerId = created.id;
      }
      const photoFile = document.getElementById("admin-player-photo-file").files[0];
      if (photoFile && playerId) {
        const imageForm = new FormData();
        imageForm.append("image", photoFile);
        await api(`/manage/players/${playerId}/photo`, { method: "POST", body: imageForm });
      }
      notify(selectedPlayer ? "球员资料已保存" : "球员已新增");
      await renderAdminTeamsPage();
    } catch (error) {
      notify(error.message);
    } finally {
      button.disabled = false;
    }
  });

  fillTeamForm(selectedTeam);
  fillPlayerForm(null);
}


async function renderAdminFixtureComposer() {
  const sortedTeams = [...state.teams]
    .sort((a, b) => String(a.name).localeCompare(String(b.name), "zh-CN"));
  const teamOptions = sortedTeams
    .map((team) => `<option value="${team.id}">${esc(team.name)}</option>`)
    .join("");
  const awayTeamOptions = sortedTeams
    .map((team, index) => `<option value="${team.id}" ${index === 1 ? "selected" : ""}>${esc(team.name)}</option>`)
    .join("");
  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("NEW FIXTURE", "新增比赛", `${seasonName()} · 新比赛会先保存为草稿，随后可在比赛管理中补时间和阵容。`, `<a class="button button-ghost button-small" href="#/admin/fixtures">返回比赛管理</a>`)}
    <section class="card admin-form-card admin-create-card">
      <div class="panel-head"><h2 class="panel-title">创建一场新比赛</h2><span class="badge no-dot badge-admin">管理员 / 球队管理员</span></div>
      <form id="admin-create-fixture-form">
        <div class="admin-create-intro"><strong>先填最必要的 4 项</strong><span>主队、客队、轮次和开球时间；场地与裁判可稍后补。</span></div>
        <div class="admin-form-grid">
          <div class="form-field"><label for="admin-create-home">主队</label><select id="admin-create-home" name="homeTeamId" required>${teamOptions}</select></div>
          <div class="form-field"><label for="admin-create-away">客队</label><select id="admin-create-away" name="awayTeamId" required>${awayTeamOptions}</select></div>
          <div class="form-field"><label for="admin-create-round">轮次</label><input id="admin-create-round" name="roundNo" type="number" min="1" placeholder="例如 1"></div>
          <div class="form-field"><label for="admin-create-kickoff">开球时间</label><input id="admin-create-kickoff" name="kickoffAt" type="datetime-local"></div>
          <div class="form-field"><label for="admin-create-venue">场地</label><input id="admin-create-venue" name="venue" maxlength="128" placeholder="例如 东操场"></div>
          <div class="form-field"><label for="admin-create-referee">裁判</label><input id="admin-create-referee" name="referee" maxlength="64" placeholder="可稍后指定"></div>
        </div>
        <p class="admin-create-hint">创建后会自动打开这场比赛。若比赛暂不公开，可保持草稿；准备发布时把状态改为“未开始”。</p>
        <div class="page-actions"><button class="button button-primary" type="submit">创建比赛</button><a class="button button-ghost" href="#/admin/fixtures">取消</a></div>
      </form>
    </section>
  </div>`;
  const form = document.getElementById("admin-create-fixture-form");
  const homeSelect = form?.homeTeamId;
  const awaySelect = form?.awayTeamId;
  function avoidSameTeams(changed) {
    if (!homeSelect || !awaySelect || homeSelect.value !== awaySelect.value) return;
    const other = [...awaySelect.options].find((option) => option.value !== changed.value);
    if (other) changed.value = other.value;
  }
  homeSelect?.addEventListener("change", () => avoidSameTeams(awaySelect));
  awaySelect?.addEventListener("change", () => avoidSameTeams(homeSelect));
  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const homeTeamId = Number(form.homeTeamId.value);
    const awayTeamId = Number(form.awayTeamId.value);
    if (homeTeamId === awayTeamId) {
      notify("主队和客队不能相同");
      return;
    }
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const created = await api("/admin/fixtures", { method: "POST", body: JSON.stringify({
        seasonId: state.seasonId,
        stageId: null,
        groupId: null,
        roundNo: form.roundNo.value ? Number(form.roundNo.value) : null,
        homeTeamId,
        awayTeamId,
        kickoffAt: form.kickoffAt.value || null,
        venue: form.venue.value.trim() || null,
        referee: form.referee.value.trim() || null,
      })});
      notify("比赛已创建");
      if (created?.id) {
        location.hash = `#/admin/fixtures?fixtureId=${created.id}`;
        await renderAdminFixturesPage();
      }
    } catch (error) {
      notify(error.message);
    } finally {
      button.disabled = false;
    }
  });
}

async function renderAdminFixturesPage() {
  setTitle("比赛管理");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "请先登录管理台。")}${emptyState("请先登录", "登录后即可管理赛程、比分和阵容。", "◫")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "读取权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role === "VIEWER") {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "当前账号没有编辑权限。")}${emptyState("只读账号", "请使用管理员、球队管理员或领队账号。", "◫")}</div>`;
    return;
  }
  if (!state.seasonId) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "没有可管理赛季。")}${emptyState("暂无赛季", "请先创建赛季。", "◫")}</div>`;
    return;
  }
  const canManageAll = me.role === "ADMIN" || me.role === "TEAM_MANAGER";
  if (canManageAll && param("new") === "1") {
    await renderAdminFixtureComposer();
    return;
  }
  let fixtures;
  try {
    const all = canManageAll
      ? await api(`/admin/fixtures?seasonId=${encodeURIComponent(state.seasonId)}`)
      : await api(`/fixtures?seasonId=${encodeURIComponent(state.seasonId)}`);
    fixtures = canManageAll ? all : all.filter((fixture) => me.managedTeamIds.includes(fixture.homeTeamId) || me.managedTeamIds.includes(fixture.awayTeamId));
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "读取赛程失败。")}${errorState(error)}</div>`;
    return;
  }
  if (!fixtures.length) {
    const actions = canManageAll
      ? `<a class="button button-primary" href="#/admin/fixtures?new=1">＋ 新增第一场比赛</a><a class="button button-ghost" href="#/admin">返回管理台</a>`
      : `<a class="button button-ghost" href="#/admin">返回管理台</a>`;
    app.innerHTML = `<div class="page">${pageHeader("MANAGEMENT", "比赛管理", "当前没有可管理的比赛。", actions)}${emptyState("暂无比赛", canManageAll ? "直接在这里新增比赛，不用再进小程序。" : "等待管理员排定赛程。", "◫")}</div>`;
    return;
  }
  let fixtureId = Number(param("fixtureId"));
  if (!fixtures.some((item) => item.id === fixtureId)) fixtureId = fixtures[0].id;
  let fixture = canManageAll ? await api(`/admin/fixtures/${fixtureId}`) : fixtures.find((item) => item.id === fixtureId);
  let detail = null;
  try { detail = await api(`/fixtures/${fixtureId}`); } catch { detail = null; }
  const [homePlayers, awayPlayers] = await Promise.all([
    api(`/teams/${fixture.homeTeamId}/players`).catch(() => []),
    api(`/teams/${fixture.awayTeamId}/players`).catch(() => []),
  ]);
  const home = teamById(fixture.homeTeamId);
  const away = teamById(fixture.awayTeamId);
  const allPlayers = [...homePlayers, ...awayPlayers];
  const playerMap = new Map(allPlayers.map((player) => [Number(player.id), player]));
  const events = detail?.events || [];
  const appearances = detail?.appearances || [];
  const editableTeams = [
    { team: home, players: homePlayers, canEdit: canManageAll || me.managedTeamIds.includes(home.id) },
    { team: away, players: awayPlayers, canEdit: canManageAll || me.managedTeamIds.includes(away.id) },
  ].filter((item) => item.canEdit);
  const scoreText = fixture.homeScore != null && fixture.awayScore != null ? `${fixture.homeScore} : ${fixture.awayScore}` : "VS";
  const kickoffValue = fixture.kickoffAt ? String(fixture.kickoffAt).slice(0, 16) : "";
  const statusOptions = ["DRAFT", "SCHEDULED", "LIVE", "CANCELLED"];
  const lineupEditable = !fixture.dataLocked && ["SCHEDULED", "LIVE", "FINISHED"].includes(fixture.status);
  const eventEditable = !fixture.dataLocked && ["SCHEDULED", "LIVE", "FINISHED"].includes(fixture.status);

  function fixtureName(team) { return team.shortName || team.name; }
  function playerOptions(team) {
    return (team.id === home.id ? homePlayers : awayPlayers).map((player) => `<option value="${player.id}">${esc(player.jerseyNo ? `${player.jerseyNo} ` : "")}${esc(player.name)}</option>`).join("");
  }
  function relatedOptions(team) {
    return (team.id === home.id ? homePlayers : awayPlayers).map((player) => `<option value="${player.id}">${esc(player.name)} · ${esc(teamById(player.teamId).name)}</option>`).join("");
  }
  function eventLabel(type) {
    return ({ GOAL: "进球", OWN_GOAL: "乌龙球", YELLOW_CARD: "黄牌", RED_CARD: "红牌" })[type] || type;
  }
  function eventIcon(type) {
    return ({ GOAL: "⚽", OWN_GOAL: "OG", YELLOW_CARD: "▰", RED_CARD: "■" })[type] || "•";
  }

  app.innerHTML = `<div class="page admin-page">
    ${pageHeader("MATCHROOM", "比赛管理", `${seasonName()} · 编辑赛程、比分、事件和球队阵容。`, `${canManageAll ? `<a class="button button-primary button-small" href="#/admin/fixtures?new=1">＋ 新增比赛</a>` : ""}<a class="button button-ghost button-small" href="#/admin">返回管理台</a>`)}
    ${canManageAll ? `<section class="card admin-quick-create"><div><span class="eyebrow">QUICK ACTION</span><h2>要新增一场比赛？</h2><p>入口就在比赛管理顶部；创建后直接进入新比赛继续补信息。</p></div><a class="button button-lime" href="#/admin/fixtures?new=1">＋ 立即新增比赛</a></section>` : ""}
    <div class="admin-fixture-layout">
      <aside class="card admin-team-panel">
        <div class="panel-head"><h2 class="panel-title">比赛列表</h2><span class="badge no-dot badge-scheduled">${fixtures.length} 场</span></div>
        <div class="admin-fixture-list">${fixtures.map((item) => `<button type="button" class="admin-fixture-button ${item.id === fixtureId ? "active" : ""}" data-fixture-id="${item.id}"><span><strong>${esc(fixtureName(teamById(item.homeTeamId)))}</strong><small>${esc(dateParts(item.kickoffAt).date)}</small></span><span class="admin-fixture-mini-score">${item.homeScore != null ? `${item.homeScore}:${item.awayScore}` : "VS"}</span><span><strong>${esc(fixtureName(teamById(item.awayTeamId)))}</strong><small>${esc(statusLabel(item.status))}</small></span></button>`).join("")}</div>
      </aside>
      <div class="stack">
        <section class="card admin-match-summary">
          <div class="admin-match-meta">${statusBadge(fixture.status)}<span>${fixture.roundNo ? `第 ${fixture.roundNo} 轮` : "比赛详情"}</span><span>${esc(dateParts(fixture.kickoffAt).full)}</span></div>
          <div class="admin-match-scoreline"><a href="#/teams/${home.id}">${teamCrest(home, "small")}<strong>${esc(home.name)}</strong></a><b>${scoreText}</b><a href="#/teams/${away.id}"><strong>${esc(away.name)}</strong>${teamCrest(away, "small")}</a></div>
          <div class="admin-match-meta-bottom"><span>场地 <strong>${esc(fixture.venue || "待定")}</strong></span><span>主裁 <strong>${esc(fixture.referee || "待定")}</strong></span><span>数据 <strong>${fixture.dataLocked ? "已锁定" : "可编辑"}</strong></span></div>
        </section>

        ${canManageAll ? `<section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title">赛程信息</h2><span class="badge no-dot badge-admin">管理员编辑</span></div>
          <form id="admin-fixture-schedule-form">
            <div class="admin-form-grid">
              <div class="form-field"><label for="admin-fixture-home">主队</label><select id="admin-fixture-home" name="homeTeamId">${state.teams.map((team) => `<option value="${team.id}" ${team.id === fixture.homeTeamId ? "selected" : ""}>${esc(team.name)}</option>`).join("")}</select></div>
              <div class="form-field"><label for="admin-fixture-away">客队</label><select id="admin-fixture-away" name="awayTeamId">${state.teams.map((team) => `<option value="${team.id}" ${team.id === fixture.awayTeamId ? "selected" : ""}>${esc(team.name)}</option>`).join("")}</select></div>
              <div class="form-field"><label for="admin-fixture-round">轮次</label><input id="admin-fixture-round" name="roundNo" type="number" min="1" value="${fixture.roundNo || ""}"></div>
              <div class="form-field"><label for="admin-fixture-kickoff">开球时间</label><input id="admin-fixture-kickoff" name="kickoffAt" type="datetime-local" value="${esc(kickoffValue)}"></div>
              <div class="form-field"><label for="admin-fixture-venue">场地</label><input id="admin-fixture-venue" name="venue" maxlength="128" value="${esc(fixture.venue || "")}"></div>
              <div class="form-field"><label for="admin-fixture-referee">裁判</label><input id="admin-fixture-referee" name="referee" maxlength="64" value="${esc(fixture.referee || "")}"></div>
            </div>
            <div class="page-actions"><button class="button button-primary button-small" type="submit">保存赛程</button></div>
          </form>
        </section>
        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title">比分与状态</h2><span class="badge no-dot ${fixture.dataLocked ? "badge-cancelled" : "badge-live"}">${fixture.dataLocked ? "数据已锁定" : "可编辑"}</span></div>
          <form id="admin-fixture-score-form">
            <div class="admin-form-grid admin-score-grid">
              <div class="form-field"><label for="admin-score-home">主队比分</label><input id="admin-score-home" type="number" min="0" value="${fixture.homeScore ?? ""}" ${!lineupEditable ? "disabled" : ""}></div>
              <div class="form-field"><label for="admin-score-away">客队比分</label><input id="admin-score-away" type="number" min="0" value="${fixture.awayScore ?? ""}" ${!lineupEditable ? "disabled" : ""}></div>
              <div class="form-field"><label for="admin-penalty-home">主队点球</label><input id="admin-penalty-home" type="number" min="0" value="${fixture.homePenaltyScore ?? ""}" ${!lineupEditable ? "disabled" : ""}></div>
              <div class="form-field"><label for="admin-penalty-away">客队点球</label><input id="admin-penalty-away" type="number" min="0" value="${fixture.awayPenaltyScore ?? ""}" ${!lineupEditable ? "disabled" : ""}></div>
            </div>
            <div class="page-actions"><button class="button button-primary button-small" type="submit" ${!lineupEditable ? "disabled" : ""}>保存比分</button><button class="button button-ghost button-small" type="button" id="admin-finish-fixture" ${fixture.status !== "LIVE" || fixture.dataLocked ? "disabled" : ""}>完赛并锁定</button>${fixture.dataLocked ? `<button class="button button-danger button-small" type="button" id="admin-unlock-fixture">解锁数据</button>` : ""}</div>
          </form>
          ${fixture.status !== "FINISHED" ? `<form id="admin-status-form" style="margin-top:18px"><div class="admin-inline-form"><label for="admin-fixture-status">比赛状态</label><select id="admin-fixture-status">${statusOptions.map((status) => `<option value="${status}" ${fixture.status === status ? "selected" : ""}>${statusLabel(status)}</option>`).join("")}</select><button class="button button-ghost button-small" type="submit">更新状态</button></div></form>` : ""}
        </section>` : `<section class="card panel"><div class="admin-locked-note"><strong>领队模式</strong><p>你可以查看比赛并编辑自己球队的出场阵容。比分、状态和比赛事件仍由管理员或球队管理员维护。</p></div></section>`}

        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title">比赛事件</h2><span class="badge no-dot badge-scheduled">${events.length} 条</span></div>
          ${events.length ? `<div class="admin-event-list">${events.slice().sort((a, b) => a.minute - b.minute).map((event) => `<div class="admin-event-row"><span class="event-minute">${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'</span><span class="event-icon">${eventIcon(event.eventType)}</span><span class="admin-event-copy"><strong>${esc(eventLabel(event.eventType))} · ${esc(playerMap.get(Number(event.playerId))?.name || `球员 #${event.playerId}`)}</strong><small>${esc(teamById(event.teamId).name)}${event.relatedPlayerId ? ` · 助攻 ${esc(playerMap.get(Number(event.relatedPlayerId))?.name || `球员 #${event.relatedPlayerId}`)}` : ""}${event.note ? ` · ${esc(event.note)}` : ""}</small></span>${canManageAll && eventEditable ? `<button class="button button-danger button-small" type="button" data-delete-event="${event.id}">删除</button>` : ""}</div>`).join("")}</div>` : emptyState("暂无比赛事件", "比赛开始后可以录入进球、助攻和红黄牌。", "○")}
          ${canManageAll && !eventEditable ? `<p class="admin-locked-note">${fixture.dataLocked ? "比赛数据已锁定，解锁后才能维护事件。" : "比赛仍是草稿，先把状态改为“未开始”后才能录入事件。"}</p>` : ""}
          ${canManageAll && eventEditable ? `<form id="admin-event-form" class="admin-event-form"><div class="admin-form-grid"><div class="form-field"><label for="admin-event-team">事件归属球队</label><select id="admin-event-team"><option value="${home.id}">${esc(home.name)}</option><option value="${away.id}">${esc(away.name)}</option></select><small class="form-help" id="admin-event-team-help">进球、红黄牌归属该球队</small></div><div class="form-field"><label for="admin-event-player">球员</label><select id="admin-event-player">${playerOptions(home)}</select></div><div class="form-field"><label for="admin-event-type">事件类型</label><select id="admin-event-type"><option value="GOAL">进球</option><option value="OWN_GOAL">乌龙球</option><option value="YELLOW_CARD">黄牌</option><option value="RED_CARD">红牌</option></select></div><div class="form-field"><label for="admin-event-related">助攻球员</label><select id="admin-event-related"><option value="">无</option>${relatedOptions(home)}</select></div><div class="form-field"><label for="admin-event-minute">分钟</label><input id="admin-event-minute" type="number" min="1" max="130" required></div><div class="form-field"><label for="admin-event-stoppage">补时</label><input id="admin-event-stoppage" type="number" min="0" max="30" value="0"></div><div class="form-field admin-form-wide"><label for="admin-event-note">备注</label><input id="admin-event-note" maxlength="255" placeholder="可选"></div></div><div class="page-actions"><button class="button button-primary button-small" type="submit">添加事件</button></div></form>` : ""}
        </section>

        <section class="card panel admin-form-card">
          <div class="panel-head"><h2 class="panel-title">出场阵容</h2><span class="badge no-dot badge-scheduled">${appearances.length} 人</span></div>
          ${!lineupEditable ? `<p class="admin-locked-note">${fixture.dataLocked ? "比赛数据已锁定，解锁后才能修改阵容。" : "比赛仍是草稿，先把状态改为“未开始”后才能维护阵容。"}</p>` : ""}
          <div class="admin-lineup-grid">${editableTeams.map((entry) => `<div class="admin-lineup-team" data-lineup-team="${entry.team.id}"><div class="admin-lineup-team-head"><strong>${esc(entry.team.name)}</strong><button class="button button-primary button-small" type="button" data-save-lineup="${entry.team.id}" ${!lineupEditable ? "disabled" : ""}>保存${esc(entry.team.shortName || entry.team.name)}阵容</button></div><div class="admin-lineup-list">${entry.players.length ? entry.players.map((player) => { const appearance = appearances.find((item) => Number(item.teamId) === entry.team.id && Number(item.playerId) === Number(player.id)); const status = appearance?.starter ? "starter" : appearance ? "substitute" : "none"; return `<div class="admin-lineup-row"><span class="jersey">${esc(player.jerseyNo || "-")}</span><span class="admin-lineup-player"><strong>${esc(player.name)}</strong><small>${esc(player.position || "位置待补")}</small></span><select data-lineup-status data-player-id="${player.id}" ${!lineupEditable ? "disabled" : ""}><option value="none" ${status === "none" ? "selected" : ""}>未选</option><option value="starter" ${status === "starter" ? "selected" : ""}>首发</option><option value="substitute" ${status === "substitute" ? "selected" : ""}>替补</option></select><input data-lineup-minutes data-player-id="${player.id}" type="number" min="0" max="130" value="${appearance?.minutesPlayed ?? ""}" placeholder="分钟" ${!lineupEditable ? "disabled" : ""}></div>`; }).join("") : `<div class="tactic-plan-empty">暂无球员，请先在球队与球员中维护名单</div>`}</div></div>`).join("")}</div>
          ${editableTeams.length === 0 ? emptyState("没有可编辑阵容", "你当前没有该比赛双方球队的带队权限。", "○") : ""}
        </section>
      </div>
    </div>
  </div>`;

  const scheduleForm = document.getElementById("admin-fixture-schedule-form");
  const scoreForm = document.getElementById("admin-fixture-score-form");
  const statusForm = document.getElementById("admin-status-form");
  const eventForm = document.getElementById("admin-event-form");
  const eventTeam = document.getElementById("admin-event-team");
  const eventPlayer = document.getElementById("admin-event-player");
  const eventType = document.getElementById("admin-event-type");
  const eventRelated = document.getElementById("admin-event-related");
  const eventTeamHelp = document.getElementById("admin-event-team-help");

  document.querySelector(".admin-fixture-list")?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-fixture-id]");
    if (!button) return;
    location.hash = `#/admin/fixtures?fixtureId=${button.dataset.fixtureId}`;
    renderAdminFixturesPage();
  });

  scheduleForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
      await api(`/admin/fixtures/${fixtureId}`, {
        method: "PUT",
        body: JSON.stringify({
          seasonId: fixture.seasonId,
          stageId: fixture.stageId,
          groupId: fixture.groupId,
          roundNo: form.roundNo.value ? Number(form.roundNo.value) : null,
          homeTeamId: Number(form.homeTeamId.value),
          awayTeamId: Number(form.awayTeamId.value),
          kickoffAt: form.kickoffAt.value || null,
          venue: form.venue.value.trim() || null,
          referee: form.referee.value.trim() || null,
        }),
      });
      notify("赛程已保存");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  scoreForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const homeScore = Number(document.getElementById("admin-score-home").value);
    const awayScore = Number(document.getElementById("admin-score-away").value);
    if (!Number.isInteger(homeScore) || !Number.isInteger(awayScore)) { notify("请填写完整比分"); return; }
    try {
      await api(`/admin/fixtures/${fixtureId}/score`, { method: "PUT", body: JSON.stringify({
        homeScore, awayScore,
        homePenaltyScore: document.getElementById("admin-penalty-home").value ? Number(document.getElementById("admin-penalty-home").value) : null,
        awayPenaltyScore: document.getElementById("admin-penalty-away").value ? Number(document.getElementById("admin-penalty-away").value) : null,
      })});
      notify("比分已保存");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  document.getElementById("admin-finish-fixture")?.addEventListener("click", async () => {
    const homeScore = Number(document.getElementById("admin-score-home").value);
    const awayScore = Number(document.getElementById("admin-score-away").value);
    if (!Number.isInteger(homeScore) || !Number.isInteger(awayScore) || !window.confirm("确认完赛并锁定数据？完成后需要解锁才能修改。")) return;
    try {
      await api(`/admin/fixtures/${fixtureId}/finish`, { method: "POST", body: JSON.stringify({
        homeScore, awayScore,
        homePenaltyScore: document.getElementById("admin-penalty-home").value ? Number(document.getElementById("admin-penalty-home").value) : null,
        awayPenaltyScore: document.getElementById("admin-penalty-away").value ? Number(document.getElementById("admin-penalty-away").value) : null,
      })});
      notify("比赛已完赛并锁定");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  document.getElementById("admin-unlock-fixture")?.addEventListener("click", async () => {
    if (!window.confirm("确认解锁这场比赛的数据？")) return;
    try {
      await api(`/admin/fixtures/${fixtureId}/lock`, { method: "PUT", body: JSON.stringify({ locked: false }) });
      notify("比赛数据已解锁");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  statusForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api(`/admin/fixtures/${fixtureId}/status`, { method: "PUT", body: JSON.stringify({ status: document.getElementById("admin-fixture-status").value }) });
      notify("比赛状态已更新");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  function refreshEventFormFields() {
    if (!eventTeam || !eventPlayer || !eventType || !eventRelated) return;
    const beneficiary = eventTeam.value === String(home.id) ? home : away;
    const playerTeam = eventType.value === "OWN_GOAL" ? (beneficiary.id === home.id ? away : home) : beneficiary;
    eventPlayer.innerHTML = playerOptions(playerTeam);
    const relatedTeamPlayers = beneficiary.id === home.id ? homePlayers : awayPlayers;
    eventRelated.replaceChildren(new Option("无", ""));
    relatedTeamPlayers.forEach((player) => eventRelated.add(new Option(player.name + " · " + teamById(player.teamId).name, player.id)));
    eventRelated.disabled = eventType.value !== "GOAL";
    if (eventRelated.disabled) eventRelated.value = "";
    if (eventTeamHelp) eventTeamHelp.textContent = eventType.value === "OWN_GOAL" ? "乌龙球计入" + beneficiary.name + "，请选择" + playerTeam.name + "球员" : "进球、红黄牌归属该球队";
  }

  eventTeam?.addEventListener("change", refreshEventFormFields);
  eventType?.addEventListener("change", refreshEventFormFields);
  refreshEventFormFields();

  eventForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const teamId = Number(eventTeam.value);
      await api(`/manage/fixtures/${fixtureId}/events`, { method: "POST", body: JSON.stringify({
        teamId,
        playerId: Number(eventPlayer.value),
        eventType: eventType.value,
        relatedPlayerId: eventRelated.value ? Number(eventRelated.value) : null,
        minute: Number(document.getElementById("admin-event-minute").value),
        stoppageMinute: Number(document.getElementById("admin-event-stoppage").value || 0),
        note: document.getElementById("admin-event-note").value.trim() || null,
      })});
      notify("比赛事件已添加");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  });

  document.querySelectorAll("[data-delete-event]").forEach((button) => button.addEventListener("click", async () => {
    if (!window.confirm("确认删除这条比赛事件？")) return;
    try {
      await api(`/manage/events/${button.dataset.deleteEvent}`, { method: "DELETE" });
      notify("比赛事件已删除");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  }));

  document.querySelectorAll("[data-save-lineup]").forEach((button) => button.addEventListener("click", async () => {
    const teamId = Number(button.dataset.saveLineup);
    const container = document.querySelector(`[data-lineup-team="${teamId}"]`);
    const starters = [];
    const substitutes = [];
    container.querySelectorAll("[data-lineup-status]").forEach((select) => {
      const playerId = Number(select.dataset.playerId);
      if (select.value === "starter") starters.push(playerId);
      if (select.value === "substitute") {
        const minutes = container.querySelector(`[data-lineup-minutes][data-player-id="${playerId}"]`)?.value;
        substitutes.push({ playerId, minutesPlayed: minutes ? Number(minutes) : null });
      }
    });
    try {
      await api(`/manage/fixtures/${fixtureId}/teams/${teamId}/appearances`, { method: "PUT", body: JSON.stringify({ starters, substitutes }) });
      notify("阵容已保存");
      await renderAdminFixturesPage();
    } catch (error) { notify(error.message); }
  }));
}


let tacticDrag = null;
let tacticPageContext = null;

document.addEventListener("pointermove", (event) => {
  if (tacticPageContext) tacticPageContext.move(event);
});
document.addEventListener("pointerup", (event) => {
  if (tacticPageContext) tacticPageContext.up(event);
});
document.addEventListener("pointercancel", (event) => {
  if (tacticPageContext) tacticPageContext.up(event);
});

async function renderTacticsPage() {
  setTitle("战术板");
  if (!state.session) {
    app.innerHTML = `<div class="page">${pageHeader("TACTICS", "战术板", "登录球队管理账号后，保存并共享排兵布阵方案。")}${emptyState("请先登录", "战术板包含球队名单和位置数据，需要管理员、球队管理员或领队权限。", "▦")}<div class="page-actions" style="margin-top:18px"><a class="button button-primary" href="#/login">前往登录</a></div></div>`;
    return;
  }
  showLoading();
  let me;
  try {
    me = await api("/me");
    saveSession({ ...state.session, ...me });
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("TACTICS", "战术板", "读取账号权限失败。")}${errorState(error)}</div>`;
    return;
  }
  if (me.role === "VIEWER") {
    app.innerHTML = `<div class="page">${pageHeader("TACTICS", "战术板", "当前账号没有编辑战术板的权限。")}${emptyState("当前角色为只读用户", "请使用管理员、球队管理员或领队账号。", "▦")}</div>`;
    return;
  }
  const teams = state.teams.filter((team) =>
    me.role === "ADMIN" || me.role === "TEAM_MANAGER" || me.managedTeamIds.includes(team.id)
  );
  if (!teams.length) {
    app.innerHTML = `<div class="page">${pageHeader("TACTICS", "战术板", "当前账号还没有关联球队。")}${emptyState("暂无可管理球队", "请联系超级管理员配置球队授权。", "▦")}</div>`;
    return;
  }
  let teamId = Number(localStorage.getItem("campusFootball.tacticTeamId"));
  if (!teams.some((team) => team.id === teamId)) teamId = teams[0].id;
  const team = teams.find((item) => item.id === teamId);
  let players;
  let tactics;
  try {
    [players, tactics] = await Promise.all([
      api(`/teams/${teamId}/players`),
      api(`/manage/teams/${teamId}/tactics`),
    ]);
  } catch (error) {
    app.innerHTML = `<div class="page">${pageHeader("TACTICS", "战术板", "读取球队战术数据失败。")}${errorState(error)}</div>`;
    return;
  }
  const playerMap = new Map(players.map((player) => [Number(player.id), player]));
  const editor = {
    tacticId: null,
    name: "新战术",
    positions: [],
    tactics: tactics || [],
  };
  let rosterQuery = "";
  tacticPageContext = null;

  app.innerHTML = `<div class="page tactics-page">
    ${pageHeader("TACTICS", "战术板", `${team.name} · 自由拖拽球员，保存多个排兵布阵方案。`)}
    <div class="tactics-layout">
      <aside class="card tactics-sidebar">
        <div class="form-field">
          <label for="tactic-team-select">球队</label>
          <select id="tactic-team-select">${teams.map((item) => `<option value="${item.id}" ${item.id === teamId ? "selected" : ""}>${esc(item.name)}</option>`).join("")}</select>
        </div>
        <div class="panel-head tactics-panel-head">
          <h2 class="panel-title">战术方案</h2>
          <button class="button button-ghost button-small" type="button" id="tactic-new">新建</button>
        </div>
        <div class="tactic-plan-list" id="tactic-plan-list"></div>
        <div class="tactic-roster-head">
          <label for="tactic-player-search">球员池</label>
          <input id="tactic-player-search" type="search" placeholder="搜索姓名或号码" autocomplete="off">
        </div>
        <div class="tactic-roster" id="tactic-player-list"></div>
      </aside>
      <section class="card tactics-canvas-card">
        <div class="tactics-toolbar">
          <div class="form-field tactics-name-field"><label for="tactic-name">方案名称</label><input id="tactic-name" maxlength="80" autocomplete="off"></div>
          <div class="page-actions">
            <button class="button button-primary button-small" type="button" id="tactic-save">保存方案</button>
            <button class="button button-danger button-small" type="button" id="tactic-delete">删除方案</button>
          </div>
        </div>
        <div class="tactics-status"><span>已上场 <strong id="tactic-count">0</strong> 人</span><span>可自由拖动位置，不限制固定阵型</span><span id="tactic-save-state">未保存</span></div>
        <div class="tactics-pitch" id="tactics-pitch" aria-label="足球战术场">
          <div class="tactics-pitch-markings" aria-hidden="true">
            <span class="pitch-circle"></span><span class="pitch-halfway"></span><span class="pitch-box pitch-box-top"></span><span class="pitch-box pitch-box-bottom"></span><span class="pitch-goal pitch-goal-top"></span><span class="pitch-goal pitch-goal-bottom"></span>
          </div>
          <div class="tactics-token-layer" id="tactics-token-layer"></div>
        </div>
        <p class="form-help">从左侧拖入球员；球场上球员可拖动到任意位置。手机端点击球员可快速加入，再拖动微调。</p>
      </section>
    </div>
  </div>`;

  const pitch = document.getElementById("tactics-pitch");
  const tokenLayer = document.getElementById("tactics-token-layer");
  const planList = document.getElementById("tactic-plan-list");
  const roster = document.getElementById("tactic-player-list");
  const nameInput = document.getElementById("tactic-name");
  const count = document.getElementById("tactic-count");
  const saveState = document.getElementById("tactic-save-state");
  const deleteButton = document.getElementById("tactic-delete");
  const searchInput = document.getElementById("tactic-player-search");

  function formatSavedAt(value) {
    if (!value) return "刚刚";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? "刚刚" : new Intl.DateTimeFormat("zh-CN", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" }).format(date);
  }

  function renderPlans() {
    planList.innerHTML = editor.tactics.length ? editor.tactics.map((plan) => `<button type="button" class="tactic-plan ${plan.id === editor.tacticId ? "active" : ""}" data-tactic-id="${plan.id}"><span>${esc(plan.name)}</span><small>${formatSavedAt(plan.updatedAt)}</small></button>`).join("") : `<div class="tactic-plan-empty">还没有保存的方案</div>`;
  }

  function renderRoster() {
    const placed = new Set(editor.positions.map((position) => position.playerId));
    const query = rosterQuery.trim().toLowerCase();
    const available = players.filter((player) => !placed.has(player.id) && (!query || `${player.name} ${player.jerseyNo || ""}`.toLowerCase().includes(query)));
    roster.innerHTML = available.length ? available.map((player) => `<button type="button" class="tactic-player" data-player-id="${player.id}"><span class="tactic-player-no">${esc(player.jerseyNo || "-")}</span><span class="tactic-player-copy"><strong>${esc(player.name)}</strong><small>${esc(player.position || "位置待补")}</small></span><span class="tactic-player-add">+</span></button>`).join("") : `<div class="tactic-plan-empty">${placed.size === players.length ? "所有球员都已上场" : "没有匹配球员"}</div>`;
    roster.querySelectorAll(".tactic-player").forEach((item) => item.addEventListener("pointerdown", (event) => beginRosterDrag(event, Number(item.dataset.playerId))));
  }

  function renderTokens() {
    tokenLayer.innerHTML = editor.positions.map((position) => {
      const player = playerMap.get(Number(position.playerId));
      if (!player) return "";
      return `<div class="tactic-token" data-player-id="${player.id}" style="left:${position.x}%;top:${position.y}%">
        <span class="tactic-token-number">${esc(player.jerseyNo || "-")}</span>
        <span class="tactic-token-name">${esc(player.name)}</span>
        <button type="button" class="tactic-token-remove" data-remove-player="${player.id}" aria-label="移出 ${esc(player.name)}">×</button>
      </div>`;
    }).join("");
    tokenLayer.querySelectorAll(".tactic-token").forEach((token) => {
      token.addEventListener("pointerdown", (event) => {
        if (event.target.closest("[data-remove-player]")) return;
        beginTokenDrag(event, Number(token.dataset.playerId), token);
      });
    });
    tokenLayer.querySelectorAll("[data-remove-player]").forEach((button) => button.addEventListener("click", (event) => {
      event.stopPropagation();
      editor.positions = editor.positions.filter((position) => position.playerId !== Number(button.dataset.removePlayer));
      saveState.textContent = "有未保存修改";
      renderBoard();
    }));
    count.textContent = String(editor.positions.length);
  }

  function renderBoard() {
    renderPlans();
    renderRoster();
    renderTokens();
    deleteButton.disabled = !editor.tacticId;
  }

  function setEditor(plan) {
    editor.tacticId = plan ? plan.id : null;
    editor.name = plan ? plan.name : "新战术";
    editor.positions = plan ? plan.positions.map((position) => ({ playerId: Number(position.playerId), x: Number(position.x), y: Number(position.y) })) : [];
    nameInput.value = editor.name;
    saveState.textContent = plan ? "已加载已保存方案" : "新方案";
    renderBoard();
  }

  function pointInPitch(event) {
    const rect = pitch.getBoundingClientRect();
    return event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom;
  }

  function pointOnPitch(event) {
    const rect = pitch.getBoundingClientRect();
    return {
      x: Math.max(3, Math.min(97, ((event.clientX - rect.left) / rect.width) * 100)),
      y: Math.max(3, Math.min(97, ((event.clientY - rect.top) / rect.height) * 100)),
    };
  }

  function defaultPosition() {
    const slots = [[50, 85], [22, 72], [50, 68], [78, 72], [15, 52], [38, 50], [62, 50], [85, 52], [30, 28], [50, 22], [70, 28]];
    const slot = slots[editor.positions.length % slots.length];
    return { x: slot[0], y: slot[1] };
  }

  function addPlayer(playerId, point) {
    if (editor.positions.some((position) => position.playerId === playerId)) return;
    if (editor.positions.length >= 30) {
      notify("单个战术板最多放置 30 名球员");
      return;
    }
    editor.positions.push({ playerId, x: point.x, y: point.y });
    saveState.textContent = "有未保存修改";
    renderBoard();
  }

  function makeGhost(player, event) {
    const ghost = document.createElement("div");
    ghost.className = "tactic-drag-ghost";
    ghost.textContent = `${player.jerseyNo || ""} ${player.name}`;
    ghost.style.left = `${event.clientX}px`;
    ghost.style.top = `${event.clientY}px`;
    document.body.appendChild(ghost);
    return ghost;
  }

  function beginRosterDrag(event, playerId) {
    if (event.button !== undefined && event.button !== 0) return;
    const player = playerMap.get(playerId);
    if (!player) return;
    event.preventDefault();
    tacticDrag = { source: "roster", playerId, startX: event.clientX, startY: event.clientY, ghost: makeGhost(player, event) };
  }

  function beginTokenDrag(event, playerId, token) {
    if (event.button !== undefined && event.button !== 0) return;
    event.preventDefault();
    tacticDrag = { source: "token", playerId, token, startX: event.clientX, startY: event.clientY };
    token.classList.add("dragging");
  }

  tacticPageContext = {
    move(event) {
      if (!tacticDrag) return;
      if (tacticDrag.ghost) {
        tacticDrag.ghost.style.left = `${event.clientX}px`;
        tacticDrag.ghost.style.top = `${event.clientY}px`;
      }
      if (tacticDrag.source === "token" && pointInPitch(event)) {
        const point = pointOnPitch(event);
        tacticDrag.token.style.left = `${point.x}%`;
        tacticDrag.token.style.top = `${point.y}%`;
      }
    },
    up(event) {
      if (!tacticDrag) return;
      const drag = tacticDrag;
      tacticDrag = null;
      if (drag.ghost) drag.ghost.remove();
      if (drag.source === "roster") {
        if (pointInPitch(event)) addPlayer(drag.playerId, pointOnPitch(event));
        else if (Math.abs(event.clientX - drag.startX) < 8 && Math.abs(event.clientY - drag.startY) < 8) addPlayer(drag.playerId, defaultPosition());
        return;
      }
      const position = editor.positions.find((item) => item.playerId === drag.playerId);
      if (position && pointInPitch(event)) {
        const point = pointOnPitch(event);
        position.x = point.x;
        position.y = point.y;
        saveState.textContent = "有未保存修改";
      }
      renderBoard();
      tacticDrag = null;
    },
  };

  planList.addEventListener("click", (event) => {
    const button = event.target.closest("[data-tactic-id]");
    if (!button) return;
    const plan = editor.tactics.find((item) => item.id === Number(button.dataset.tacticId));
    if (plan) setEditor(plan);
  });

  document.getElementById("tactic-new").addEventListener("click", () => {
    setEditor(null);
    nameInput.focus();
  });

  document.getElementById("tactic-team-select").addEventListener("change", (event) => {
    localStorage.setItem("campusFootball.tacticTeamId", event.target.value);
    renderTacticsPage();
  });

  searchInput.addEventListener("input", () => {
    rosterQuery = searchInput.value;
    renderRoster();
  });

  document.getElementById("tactic-save").addEventListener("click", async (event) => {
    const button = event.currentTarget;
    const name = nameInput.value.trim();
    if (!name) {
      notify("请填写战术方案名称");
      nameInput.focus();
      return;
    }
    button.disabled = true;
    button.textContent = "保存中…";
    try {
      const payload = { name, positions: editor.positions };
      const saved = editor.tacticId
        ? await api(`/manage/tactics/${editor.tacticId}`, { method: "PUT", body: JSON.stringify(payload) })
        : await api(`/manage/teams/${teamId}/tactics`, { method: "POST", body: JSON.stringify(payload) });
      editor.tacticId = saved.id;
      editor.name = saved.name;
      editor.tactics = await api(`/manage/teams/${teamId}/tactics`);
      nameInput.value = saved.name;
      saveState.textContent = "已保存";
      renderBoard();
      notify("战术方案已保存");
    } catch (error) {
      notify(error.message);
    } finally {
      button.disabled = false;
      button.textContent = "保存方案";
    }
  });

  deleteButton.addEventListener("click", async () => {
    if (!editor.tacticId) return;
    if (!window.confirm(`确定删除“${nameInput.value.trim() || editor.name}”吗？`)) return;
    try {
      await api(`/manage/tactics/${editor.tacticId}`, { method: "DELETE" });
      editor.tactics = await api(`/manage/teams/${teamId}/tactics`);
      setEditor(editor.tactics[0] || null);
      notify("战术方案已删除");
    } catch (error) {
      notify(error.message);
    }
  });

  setEditor(editor.tactics[0] || null);
}


function permissionDescription(role) {
  return ({
    ADMIN: "可管理全部球队、赛事、赛程与比赛数据，维护用户授权，并配置其他工作人员的网站账号。",
    TEAM_MANAGER: "可管理全部球队、赛事、赛程、比分和比赛事件。网站账号由超级管理员配置。",
    TEAM_LEADER: "可管理授权球队的人员资料和比赛出场阵容。网站当前提供资料查看与登录入口。",
  })[role] || "当前账号为只读权限。";
}

function logout() {
  saveSession(null);
  notify("已退出登录");
  location.hash = "#/login";
}

async function init() {
  try {
    await loadCore();
    updateAuthHeader();
    await renderRoute();
    maybeShowLatestAnnouncement();
  } catch (error) {
    console.error(error);
    setTitle("服务暂不可用");
    app.innerHTML = `<div class="page" style="padding-top:40px">${errorState(error, "无法加载赛事数据")}</div>`;
  }
}

init();
