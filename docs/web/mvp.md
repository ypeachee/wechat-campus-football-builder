# Web 网站 MVP 技术说明

## 目标

在不修改微信小程序现有功能的前提下，为同一足球赛事后端增加响应式网站，覆盖赛程、积分榜、球队与球员资料、射手榜/助攻榜、比赛详情，以及管理员/领队登录入口。

## 实现方式

- 网站作为 Spring Boot 静态资源随 API 一起发布，入口为 `/`。
- 前端使用原生 HTML/CSS/JavaScript 和 Hash 路由，不引入额外构建链。
- 公开页面直接复用现有 `/api/v1` 接口。
- Web 登录使用独立账号密码，成功后签发与小程序相同的 JWT。
- 密码保存为 BCrypt 哈希；JWT、角色、球队授权和接口权限继续复用现有体系。
- 小程序源码与交互保持不变。

## 复用接口

| 页面能力 | 接口 |
|---|---|
| 赛事/赛季 | `GET /api/v1/competitions`、`GET /api/v1/seasons` |
| 赛程 | `GET /api/v1/fixtures?seasonId=` |
| 比赛详情 | `GET /api/v1/fixtures/{fixtureId}` |
| 积分榜 | `GET /api/v1/standings?seasonId=` |
| 射手榜 | `GET /api/v1/stats/scorers?seasonId=` |
| 助攻榜 | `GET /api/v1/stats/assists?seasonId=` |
| 出场榜 | `GET /api/v1/stats/appearances?seasonId=` |
| 球队 | `GET /api/v1/teams`、`GET /api/v1/teams/{teamId}` |
| 球员 | `GET /api/v1/teams/{teamId}/players`、`GET /api/v1/players/{playerId}` |

## 新增接口

### Web 登录

```http
POST /api/v1/auth/web-login
Content-Type: application/json

{"username":"admin","password":"..."}
```

成功响应与小程序登录一致，包含 `token`、`userId`、`nickname`、`role`、`managedTeamIds`。

### 配置工作人员网站账号

仅超级管理员可调用：

```http
PUT /api/v1/admin/users/{userId}/web-credentials
Authorization: Bearer <token>
Content-Type: application/json

{"username":"team-leader","password":"..."}
```

目标用户角色必须是 `ADMIN`、`TEAM_MANAGER` 或 `TEAM_LEADER`。球队管理员和领队的小程序角色及球队授权保持不变。

## 初始化网站超级管理员

配置环境变量后启动 API：

```text
WEB_ADMIN_USERNAME=admin
WEB_ADMIN_PASSWORD=<strong-password>
```

服务会创建或更新对应的网站超级管理员账号。未配置时不会创建网站账号。

## 前端路由

```text
/#/                 首页
/#/fixtures         赛程
/#/fixtures/{id}    比赛详情
/#/standings        积分榜
/#/stats            射手榜/助攻榜
/#/teams            球队列表
/#/teams/{id}       球队详情
/#/players/{id}     球员详情
/#/login            管理入口
/#/account          登录后账号中心
```

## 当前边界

- 网站 MVP 以浏览和登录入口为主。
- 比分、阵容、赛事配置等写入操作继续使用现有小程序管理端。
- 网站账号中心支持超级管理员为工作人员配置网站凭据。
- 若后续需要 Web 管理后台，可在现有 JWT 和角色权限上继续增加页面，无需改造公开 API。
