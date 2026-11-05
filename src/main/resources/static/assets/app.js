const API_BASE = "/api/v1";
const SESSION_KEY = "campusFootball.webSession";
const SEASON_KEY = "campusFootball.seasonId";

const app = document.getElementById("app");
const seasonSelect = document.getElementById("season-select");
const authEntry = document.getElementById("auth-entry");
const authEntryLabel = document.getElementById("auth-entry-label");
const menuToggle = document.getElementById("menu-toggle");
const primaryNav = document.getElementById("primary-nav");
const toast = document.getElementById("toast");

const state = {
  competitions: [],
  seasons: [],
  teams: [],
  teamMap: new Map(),
  seasonId: null,
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
  try {
    const session = JSON.parse(sessionStorage.getItem(SESSION_KEY) || "null");
    if (session) return session;
  } catch { /* ignore */ }
  // Migrate from old localStorage and clean up
  try {
    const legacy = JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    if (legacy) {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(legacy));
      localStorage.removeItem(SESSION_KEY);
      return legacy;
    }
  } catch { /* ignore */ }
  localStorage.removeItem(SESSION_KEY);
  return null;
}

function saveSession(session) {
  state.session = session;
  if (session) {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } else {
    sessionStorage.removeItem(SESSION_KEY);
  }
  // Always clean up legacy localStorage key
  localStorage.removeItem(SESSION_KEY);
  updateAuthHeader();
}

function updateAuthHeader() {
  const role = state.session?.role;
  if (role) {
    authEntry.classList.add("is-logged-in");
    authEntry.href = "#/account";
    authEntryLabel.textContent = state.session.nickname || roleLabel(role);
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
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
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
  document.title = `${title} | 校园绿茵`;
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
  const [competitions, seasons, teams] = await Promise.all([
    api("/competitions"),
    api("/seasons"),
    api("/teams"),
  ]);
  state.competitions = competitions || [];
  state.seasons = seasons || [];
  state.teams = teams || [];
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
  </div>`;
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
  const [player, scorers, assists, appearances] = await Promise.all([
    api(`/players/${id}`),
    state.seasonId ? api(`/stats/scorers?${seasonQuery()}`).catch(() => []) : Promise.resolve([]),
    state.seasonId ? api(`/stats/assists?${seasonQuery()}`).catch(() => []) : Promise.resolve([]),
    state.seasonId ? api(`/stats/appearances?${seasonQuery()}`).catch(() => []) : Promise.resolve([]),
  ]);
  const [team, players] = await Promise.all([
    api(`/teams/${player.teamId}`).catch(() => teamById(player.teamId)),
    api(`/teams/${player.teamId}/players`).catch(() => []),
  ]);
  const scoring = scorers.find((row) => row.playerId === id);
  const assisting = assists.find((row) => row.playerId === id);
  const apps = appearances.find((row) => row.playerId === id);
  const age = player.birthDate ? new Date().getFullYear() - new Date(player.birthDate).getFullYear() : null;
  const photo = player.photoUrl ? `<img src="${esc(player.photoUrl)}" alt="${esc(player.name)}照片" loading="lazy">` : esc(initials(player.name));
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
    <section class="player-stats">
      <div class="card player-stat"><strong>${scoring?.goals || 0}</strong><span>本赛季进球</span></div>
      <div class="card player-stat"><strong>${assisting?.assists || 0}</strong><span>本赛季助攻</span></div>
      <div class="card player-stat"><strong>${apps?.appearances || 0}</strong><span>本赛季出场</span></div>
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
  const team = event.teamId === match.homeTeam.id ? match.homeTeam : match.awayTeam;
  const type = ({ GOAL: ["⚽", "进球"], OWN_GOAL: ["OG", "乌龙球"], YELLOW_CARD: ["▰", "黄牌"], RED_CARD: ["■", "红牌"] })[event.eventType] || ["•", "事件"];
  const related = event.relatedPlayerId ? playerMap.get(Number(event.relatedPlayerId)) : null;
  return `<div class="event-row">
    <span class="event-minute">${event.minute}${event.stoppageMinute ? `+${event.stoppageMinute}` : ""}'</span>
    <span class="event-icon">${type[0]}</span>
    <span class="event-main"><strong>${esc(type[1])} · ${esc(player?.name || `球员 #${event.playerId}`)}</strong><span>${esc(team.name)}${related ? ` · 助攻 ${esc(related.name)}` : ""}${event.note ? ` · ${esc(event.note)}` : ""}</span></span>
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
    <div id="user-search-results" class="user-search-results" role="listbox" aria-label="搜索结果"></div>
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
    errorBox.classList.remove("visible");
    successBox.classList.remove("visible");
    button.disabled = true;
    button.textContent = "正在保存…";
    try {
      const result = await api(`/admin/users/${encodeURIComponent(form.userId.value.trim())}/web-credentials`, {
        method: "PUT",
        body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value }),
      });
      successBox.textContent = `已为用户 ${result.userId} 配置账号 ${result.username}`;
      successBox.classList.add("visible");
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

  if (!searchInput || !resultsContainer) return;

  searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    const query = searchInput.value.trim();
    if (query.length < 1) {
      resultsContainer.innerHTML = "";
      return;
    }
    searchTimer = setTimeout(() => searchUsers(query), 300);
  });

  async function searchUsers(query) {
    resultsContainer.innerHTML = `<div class="user-search-loading">搜索中…</div>`;
    try {
      const users = await api(`/admin/users?query=${encodeURIComponent(query)}`);
      if (!users || !users.length) {
        resultsContainer.innerHTML = `<div class="user-search-empty">未找到匹配用户</div>`;
        return;
      }
      resultsContainer.innerHTML = users.map((user) => {
        const isViewer = user.role === "VIEWER";
        const disabledAttr = isViewer ? `aria-disabled="true"` : "";
        const className = isViewer ? "user-search-item disabled" : "user-search-item";
        return `<div class="${className}" role="option" data-user-id="${user.id}" ${disabledAttr}>
          <span class="user-search-id">#${user.id}</span>
          <span class="user-search-name">${esc(user.nickname || "未命名")}</span>
          <span class="user-search-role">${esc(roleLabel(user.role))}</span>
          ${user.webUsername ? `<span class="user-search-web">${esc(user.webUsername)}</span>` : ""}
          ${isViewer ? `<span class="user-search-hint">只读用户不可配置</span>` : ""}
        </div>`;
      }).join("");
      resultsContainer.querySelectorAll(".user-search-item:not(.disabled)").forEach((item) => {
        item.addEventListener("click", () => selectUser(item));
      });
    } catch (error) {
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
    document.getElementById("credential-username").value = webUsername || "";
    document.getElementById("credential-username")?.focus();
  }

  cancelBtn?.addEventListener("click", () => {
    userIdField.value = "";
    formArea.style.display = "none";
    banner.innerHTML = "";
    document.getElementById("credential-form")?.reset();
  });
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
  } catch (error) {
    console.error(error);
    setTitle("服务暂不可用");
    app.innerHTML = `<div class="page" style="padding-top:40px">${errorState(error, "无法加载赛事数据")}</div>`;
  }
}

init();



