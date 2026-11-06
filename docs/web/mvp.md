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
| 球员赛季统计 | `GET /api/v1/players/{playerId}/stats?seasonId=` |

## 新增接口

### 球员赛季统计

```http
GET /api/v1/players/{playerId}/stats?seasonId=<seasonId>
```

返回该球员在指定赛季的汇总数据与逐场明细，包括出场、首发、替补、上场分钟、进球、助攻、黄牌、红牌和乌龙球。只统计已完赛比赛。

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
/#/tactics          战术板
/#/admin            管理台
/#/admin/fixtures   比赛管理（新增比赛、赛程、比分、事件、阵容）
/#/admin/fixtures?new=1  新增比赛
/#/admin/teams      球队与球员编辑
```

## 管理台

- 管理员、球队管理员和领队登录后进入 `#/admin`。
- 管理员和球队管理员可从管理台首屏或比赛管理页顶部直接新增比赛。
- 管理员和球队管理员可编辑赛程、状态、比分、点球比分、比赛事件和双方出场阵容。
- 领队只显示涉及授权球队的比赛，并且只能维护本队出场阵容。
- 球队资料、球员资料和战术板也在同一管理台内。

## 战术板

- 管理员、球队管理员和领队可以按球队创建多个战术方案。
- 球员可从名单拖入球场，位置保存为 0-100 的自由坐标，不限制固定阵型。
- 战术方案保存到 MySQL，并通过球队授权在不同设备之间共享。
- 战术板独立于正式比赛出场阵容。

## 当前边界

- Web 端已覆盖新增比赛、赛程、比分、事件、出场阵容、球队球员和战术板。
- 比赛事件和阵容受比赛状态及数据锁控制：草稿或已锁定比赛只能查看，需先调整状态或解锁。
- 领队不能修改比分、比赛状态或比赛事件，这些操作继续由管理员/球队管理员完成。
- 网站账号中心支持超级管理员为工作人员配置网站凭据。
