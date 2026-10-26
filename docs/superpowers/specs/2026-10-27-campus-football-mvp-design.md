# 校园足球联赛小程序 MVP 设计

**日期：** 2026-10-27  
**状态：** 已确认设计方向，等待书面 spec 审阅  
**范围：** 后端 API 优先，微信小程序客户端后续接入

## 1. 目标

为校园足球联赛提供一个可公开浏览、可受控编辑的数据服务，解决三个核心问题：

1. 查看球队和球员名单。
2. 查看射手榜、助攻榜、出场榜和积分榜。
3. 让联赛管理员和球队管理员在各自权限范围内编辑数据。

V1 采用模块化单体后端，先保证数据模型、统计口径和权限边界正确，再接入小程序界面。

## 2. 技术栈

- Java 17
- Spring Boot 3.x
- MyBatis
- MySQL 8
- Flyway 数据库迁移
- JUnit 5 + Spring Boot Test
- Testcontainers MySQL 8 集成测试

不沿用参考项目的 Spring Boot 2 版本。若目标部署环境不支持 Java 17，需要在实施前重新评估技术栈，而不是直接降级。

## 3. 角色与权限

### 3.1 角色

- `ADMIN`：联赛管理员，拥有全量读写权限。
- `TEAM_MANAGER`：球队管理员，只能读写被授权球队的数据。
- `VIEWER`：只读用户；未登录访问者也可查看公开数据。

新用户默认角色为 `VIEWER`。角色变更只在服务端完成。

### 3.2 权限矩阵

| 操作 | ADMIN | TEAM_MANAGER | VIEWER |
|---|---|---|---|
| 查看公开球队、球员、赛程、榜单 | 是 | 是 | 是 |
| 修改球队资料 | 全部球队 | 仅授权球队 | 否 |
| 新增或修改球员 | 全部球队 | 仅授权球队 | 否 |
| 录入本队进球、助攻、红黄牌 | 全部球队 | 仅授权球队且比赛未锁定 | 否 |
| 维护本队出场记录 | 全部球队 | 仅授权球队且比赛未锁定 | 否 |
| 修改官方比分 | 是 | 否 | 否 |
| 设置比赛完赛、锁定或解锁 | 是 | 否 | 否 |
| 授权或撤销球队管理员 | 是 | 否 | 否 |
| 查看审计日志 | 全部 | 仅本队相关记录 | 否 |

### 3.3 授权规则

- `app_user.role_code` 决定基础角色。
- `team_manager(user_id, team_id)` 决定球队级数据范围。
- `TEAM_MANAGER` 必须至少拥有一条有效球队授权。
- 一名用户可以管理多支球队，一支球队可以有多个管理员。
- 服务端必须在业务层再次校验球队归属，不得只依赖前端隐藏按钮。
- 客户端提交的 `team_id` 必须与目标事件、球员或出场记录的归属一致。
- 角色撤销或球队授权撤销在后续请求中立即生效。
- 授权球队管理员时，如果用户当前是 `VIEWER`，同时把 `role_code` 更新为 `TEAM_MANAGER`。
- 撤销某用户的最后一条球队授权时，若用户不是 `ADMIN`，把 `role_code` 降回 `VIEWER`。
- `ADMIN` 可以保留管理员角色并额外拥有球队授权；撤销球队授权不会降级管理员。

## 4. 数据模型

### 4.1 实体关系

```text
app_user 1---N team_manager N---1 team
team 1---N player
season 1---N season_team N---1 team
season 1---N fixture
team 1---N fixture (home/away)
fixture 1---N fixture_appearance
fixture 1---N fixture_event
app_user 1---N audit_log
```

### 4.2 app_user

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| wechat_openid | VARCHAR(64) | NOT NULL，UNIQUE |
| unionid | VARCHAR(64) | NULL |
| nickname | VARCHAR(64) | NULL |
| avatar_url | VARCHAR(512) | NULL |
| role_code | VARCHAR(20) | NOT NULL |
| status | VARCHAR(20) | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

允许值：

- `role_code`: `ADMIN`、`TEAM_MANAGER`、`VIEWER`
- `status`: `ACTIVE`、`DISABLED`

### 4.3 team_manager

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| user_id | BIGINT | FK -> app_user.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| granted_by | BIGINT | FK -> app_user.id，NOT NULL |
| granted_at | DATETIME | NOT NULL |

约束：`UNIQUE(user_id, team_id)`。

### 4.4 team

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| name | VARCHAR(100) | NOT NULL |
| short_name | VARCHAR(32) | NULL |
| logo_url | VARCHAR(512) | NULL |
| coach_name | VARCHAR(64) | NULL |
| home_venue | VARCHAR(128) | NULL |
| status | VARCHAR(20) | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

允许值：`status` 为 `ACTIVE` 或 `INACTIVE`。

### 4.5 player

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| name | VARCHAR(64) | NOT NULL |
| jersey_no | SMALLINT | NULL |
| position | VARCHAR(20) | NULL |
| birth_date | DATE | NULL |
| photo_url | VARCHAR(512) | NULL |
| status | VARCHAR(20) | NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

允许值：`status` 为 `ACTIVE` 或 `INACTIVE`。

V1 使用 `player.team_id` 表示当前球队。历史比赛的事件和出场记录同时保存当时的 `team_id`，因此球员转队不会改变历史统计归属。

### 4.6 season

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| name | VARCHAR(64) | NOT NULL |
| start_date | DATE | NULL |
| end_date | DATE | NULL |
| status | VARCHAR(20) | NOT NULL |

允许值：`status` 为 `DRAFT`、`ACTIVE` 或 `FINISHED`。

### 4.7 season_team

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| season_id | BIGINT | FK -> season.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| created_at | DATETIME | NOT NULL |

约束：`UNIQUE(season_id, team_id)`。

`season_team` 定义某个赛季的参赛球队。赛程中的主客队必须都属于该赛季；积分榜也以该表为基础，因此零场比赛的球队仍会出现在积分榜中。

已有比赛引用某支球队时，不得从该赛季的参赛球队中移除该队。

### 4.8 fixture

表名使用 `fixture`，避免使用 MySQL 中容易与关键字冲突的 `match`。

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| season_id | BIGINT | FK -> season.id，NOT NULL |
| round_no | INT | NULL |
| home_team_id | BIGINT | FK -> team.id，NOT NULL |
| away_team_id | BIGINT | FK -> team.id，NOT NULL |
| kickoff_at | DATETIME | NULL |
| venue | VARCHAR(128) | NULL |
| status | VARCHAR(20) | NOT NULL |
| home_score | SMALLINT | NULL |
| away_score | SMALLINT | NULL |
| data_locked | TINYINT(1) | NOT NULL，默认 0 |
| created_by | BIGINT | FK -> app_user.id，NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

允许值：`status` 为 `DRAFT`、`SCHEDULED`、`LIVE`、`FINISHED` 或 `CANCELLED`。

数据约束：

- `home_team_id <> away_team_id`
- `home_score` 和 `away_score` 必须同时为 NULL 或同时非 NULL
- `home_score >= 0`，`away_score >= 0`
- `status='FINISHED'` 时比分不得为 NULL
- `data_locked=1` 时，该场比赛的事件、出场和比分均不得写入

建议索引：`(season_id, status, kickoff_at)`。

### 4.9 fixture_appearance

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| fixture_id | BIGINT | FK -> fixture.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| player_id | BIGINT | FK -> player.id，NOT NULL |
| is_starter | TINYINT(1) | NOT NULL，默认 0 |
| minutes_played | SMALLINT | NULL |
| created_by | BIGINT | FK -> app_user.id，NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

约束：

- `UNIQUE(fixture_id, player_id)`
- `team_id` 必须是该场比赛的主队或客队
- `minutes_played` 若存在，范围为 0 到 130
- 同一球员在一场比赛中最多一条出场记录
- 录入时球员必须属于对应的 `team_id`；历史记录保留当时的球队快照

建议索引：`(player_id, fixture_id)`、`(fixture_id, team_id)`。

### 4.10 fixture_event

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| fixture_id | BIGINT | FK -> fixture.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| player_id | BIGINT | FK -> player.id，NOT NULL |
| event_type | VARCHAR(32) | NOT NULL |
| related_player_id | BIGINT | FK -> player.id，NULL |
| minute | SMALLINT | NOT NULL |
| stoppage_minute | SMALLINT | NOT NULL，默认 0 |
| note | VARCHAR(255) | NULL |
| created_by | BIGINT | FK -> app_user.id，NOT NULL |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

V1 支持的事件类型：

- `GOAL`
- `OWN_GOAL`
- `YELLOW_CARD`
- `RED_CARD`

事件口径：

- `team_id` 表示事件记在哪支球队名下。
- 普通进球：`team_id` 为进球球员所属球队，`player_id` 为进球球员。
- 助攻：不单独创建 `ASSIST` 事件；在 `GOAL` 事件的 `related_player_id` 中保存助攻球员。
- 无助攻的进球：`related_player_id=NULL`。
- 乌龙球：`team_id` 为受益球队，`player_id` 为乌龙球员，`related_player_id` 必须为 NULL。
- 普通进球的助攻球员必须与进球球员属于同一球队。
- 助攻球员不得与进球球员是同一人。
- 非 `GOAL` 事件的 `related_player_id` 必须为 NULL。
- `team_id` 必须是该场比赛的主队或客队。
- 主客队必须已登记在该场比赛所属赛季的 `season_team` 中。
- `player_id` 必须属于该场比赛的参赛球队之一。
- `GOAL`：球员和助攻球员都必须属于 `event.team_id`。
- `OWN_GOAL`：球员属于对方球队，`event.team_id` 是受益球队。
- `YELLOW_CARD` 和 `RED_CARD`：球员必须属于 `event.team_id`。
- `minute` 范围为 1 到 130，`stoppage_minute` 范围为 0 到 30。

建议索引：

- `(fixture_id, team_id)`
- `(player_id, event_type)`
- `(related_player_id, event_type)`

### 4.11 audit_log

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK，自增 |
| actor_user_id | BIGINT | FK -> app_user.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NULL |
| action | VARCHAR(32) | NOT NULL |
| entity_type | VARCHAR(32) | NOT NULL |
| entity_id | BIGINT | NOT NULL |
| before_json | JSON | NULL |
| after_json | JSON | NULL |
| created_at | DATETIME | NOT NULL |

`team_id` 表示日志关联的球队范围；管理员级的全局操作可以为空。球队管理员只能查询与自己被授权球队匹配的日志。

所有会改变比赛、事件、出场、球队、球员、角色或授权的管理操作，都必须与业务变更处于同一事务并写入审计日志。

建议索引：`(team_id, created_at)`、`(entity_type, entity_id)`。

## 5. 认证与会话

- 小程序通过微信登录 code 调用 `POST /api/v1/auth/wechat-login`。
- 后端向微信换取 `openid`，按 `openid` 查找或创建 `VIEWER` 用户。
- 后端签发 Bearer JWT。
- JWT 只保存用户 ID 等最小身份信息；角色和球队范围每次请求从数据库加载，避免授权撤销后旧 token 继续生效。
- V1 不实现刷新 token；访问 token 默认有效 2 小时，小程序在收到 401 后重新执行微信登录。
- 公开接口不要求登录。
- 管理接口要求有效 token，并在服务层执行权限校验。

初始 `ADMIN` 通过受控的初始化脚本或运维配置创建，不开放公开注册管理员接口。

## 6. 业务规则

### 6.1 官方比分与事件数据

- `fixture.home_score` 和 `fixture.away_score` 是积分榜的唯一官方比分来源。
- `fixture_event` 是射手榜、助攻榜等球员统计来源。
- 球员事件不自动修改官方比分，避免部分事件未录入时破坏比赛结果。
- 官方比分只能由 `ADMIN` 修改。
- 球队管理员可以录入本队事件，但比赛最终比分与锁定状态由联赛管理员确认。

### 6.2 比赛状态

- `DRAFT`：仅管理员可见，可编辑。
- `SCHEDULED`：公开可见，管理员可维护。
- `LIVE`：公开可见，管理员可维护，球队管理员可维护本队事件和出场记录。
- `FINISHED`：公开可见，积分榜和榜单纳入统计。
- `CANCELLED`：公开可见但不纳入积分榜和榜单。

允许的状态流转：

- `DRAFT -> SCHEDULED | CANCELLED`
- `SCHEDULED -> LIVE | CANCELLED`
- `LIVE -> FINISHED | CANCELLED`
- `CANCELLED` 为终态，不允许直接恢复

`POST /admin/fixtures/{fixtureId}/finish` 必须同时保存官方比分、把状态改为 `FINISHED`，并设置 `data_locked=1`。管理员需要继续修改时，必须先调用解锁接口。

`data_locked=1` 时阻止该场比赛的事件、出场和官方比分写入，包括管理员写入。管理员只能先显式解锁，解锁操作必须写入审计日志。球队资料和球员资料编辑不受比赛锁定影响。

### 6.3 统计口径

- 射手榜：`fixture_event.event_type='GOAL'`，按 `player_id` 计数；乌龙球不计入射手榜。
- 助攻榜：`fixture_event.event_type='GOAL' AND related_player_id IS NOT NULL`，按 `related_player_id` 计数。
- 出场榜：`fixture_appearance` 按 `player_id` 统计 `COUNT(DISTINCT fixture_id)`。
- 球员统计只纳入 `fixture.status='FINISHED'` 的比赛。
- 榜单默认按统计值降序；同值时按球员姓名升序。
- 榜单返回赛季过滤参数，V1 必须指定 `seasonId`。
- 射手榜、助攻榜和出场榜可以只返回统计值大于 0 的球员。

### 6.4 积分榜

不建立 `league_standing` 表，积分榜由已完赛比赛动态计算。

- 胜：3 分
- 平：1 分
- 负：0 分
- 排序：积分、净胜球、进球数、胜场数、球队名称
- 只统计 `status='FINISHED'` 且比分非空的比赛
- 取消比赛不纳入积分榜
- 以 `season_team` 为基础返回全部参赛球队，未赛球队显示 0 场 0 分

V1 不处理相互战绩、扣分、加时赛和点球大战。出现这些规则时再扩展积分计算策略。

## 7. API 契约

所有接口统一前缀 `/api/v1`，返回 JSON。

### 7.1 公开接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /seasons | 赛季列表 |
| GET | /teams | 球队列表 |
| GET | /teams/{teamId} | 球队详情 |
| GET | /teams/{teamId}/players | 球队球员列表 |
| GET | /players/{playerId} | 球员详情 |
| GET | /fixtures?seasonId= | 赛程列表 |
| GET | /fixtures/{fixtureId} | 比赛详情，包含事件和出场 |
| GET | /standings?seasonId= | 积分榜 |
| GET | /stats/scorers?seasonId= | 射手榜 |
| GET | /stats/assists?seasonId= | 助攻榜 |
| GET | /stats/appearances?seasonId= | 出场榜 |

公开接口默认只返回 `ACTIVE` 球队和 `ACTIVE` 球员。管理员接口可以查询停用数据。

公开接口不得返回 `DRAFT` 赛季或 `DRAFT` 比赛。`CANCELLED` 比赛可以返回，但不进入统计。

### 7.2 认证接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /auth/wechat-login | 使用微信登录 code 换取 token |
| GET | /me | 当前用户、角色和可管理球队 |

### 7.3 管理接口

| 方法 | 路径 | 权限 |
|---|---|---|
| PUT | /manage/teams/{teamId} | ADMIN 或该队 TEAM_MANAGER；球队状态仅 ADMIN 可改 |
| POST | /manage/teams/{teamId}/players | ADMIN 或该队 TEAM_MANAGER |
| PUT | /manage/players/{playerId} | ADMIN 或球员所属球队的 TEAM_MANAGER |
| POST | /manage/fixtures/{fixtureId}/appearances | ADMIN 或该场参赛队的 TEAM_MANAGER |
| PUT | /manage/appearances/{appearanceId} | ADMIN 或对应球队的 TEAM_MANAGER |
| DELETE | /manage/appearances/{appearanceId} | ADMIN 或对应球队的 TEAM_MANAGER |
| POST | /manage/fixtures/{fixtureId}/events | ADMIN 或事件归属球队的 TEAM_MANAGER |
| PUT | /manage/events/{eventId} | ADMIN 或事件归属球队的 TEAM_MANAGER |
| DELETE | /manage/events/{eventId} | ADMIN 或事件归属球队的 TEAM_MANAGER |
| GET | /manage/audit-logs | ADMIN 查询全部；TEAM_MANAGER 查询本队范围 |

所有比赛数据写接口都要求 `fixture.data_locked=0`；`TEAM_MANAGER` 还必须拥有事件或出场记录归属球队的授权。事件和出场写接口要求比赛状态为 `LIVE` 或 `FINISHED`；赛前名单维护可后续单独设计。

球队管理员修改球员时不能改变 `player.team_id`；V1 不提供转队操作。同一球队内有效球员的球衣号码若不为空，必须唯一。

修改出场或事件时不能改变其归属球队；若需要更正归属，必须由管理员删除后重建。`PUT /admin/fixtures/{fixtureId}` 只修改赛程信息，不得修改比分、状态或锁定标记；这些字段必须通过各自的专用接口变更。

比赛一旦存在事件或出场记录，不得修改主客队、所属赛季或比赛双方；需要重建比赛。`TEAM_MANAGER` 可以编辑本队展示资料，但不能把球队状态改为 `INACTIVE`。

`GET /manage/audit-logs` 允许 `ADMIN` 查询全部日志。`TEAM_MANAGER` 可以查询 `team_id` 属于自己球队的日志，以及自己球队参与比赛的比赛级日志。

### 7.4 管理员接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /admin/teams | 创建球队 |
| PUT | /admin/teams/{teamId} | 修改球队 |
| POST | /admin/seasons | 创建赛季 |
| POST | /admin/seasons/{seasonId}/teams/{teamId} | 添加赛季参赛球队 |
| DELETE | /admin/seasons/{seasonId}/teams/{teamId} | 移除赛季参赛球队 |
| GET | /admin/fixtures?seasonId= | 管理员赛程列表，包含 DRAFT |
| GET | /admin/fixtures/{fixtureId} | 管理员比赛详情，包含 DRAFT |
| GET | /admin/teams | 管理员球队列表，可包含停用球队 |
| GET | /admin/teams/{teamId}/players | 管理员球员列表，可包含停用球员 |
| GET | /admin/players/{playerId} | 管理员球员详情 |
| POST | /admin/fixtures | 创建比赛 |
| PUT | /admin/fixtures/{fixtureId} | 修改赛程信息 |
| PUT | /admin/fixtures/{fixtureId}/status | 变更非完赛状态 |
| PUT | /admin/fixtures/{fixtureId}/score | 修改官方比分 |
| POST | /admin/fixtures/{fixtureId}/finish | 完赛并保存比分 |
| PUT | /admin/fixtures/{fixtureId}/lock | 锁定或解锁数据录入 |
| POST | /admin/teams/{teamId}/managers | 授权球队管理员 |
| DELETE | /admin/teams/{teamId}/managers/{userId} | 撤销球队管理员 |

## 8. 错误处理

- `400 Bad Request`：字段缺失、格式错误、比分不合法、事件分钟越界。
- `401 Unauthorized`：未登录或 token 无效。
- `403 Forbidden`：角色不足、球队范围不匹配、比赛已锁定。
- `404 Not Found`：资源不存在，或对当前用户不可见。
- `409 Conflict`：重复出场、球队相同、同一资源状态冲突。
- `500 Internal Server Error`：未预期错误。

错误响应包含稳定错误码和用户可读信息，例如：

```json
{
  "code": "FIXTURE_LOCKED",
  "message": "该场比赛的数据已锁定",
  "requestId": "..."
}
```

不得将数据库异常、SQL 或微信接口密钥返回给客户端。

## 9. 事务与一致性

- 创建、修改、删除事件时，业务变更与审计日志在同一事务中提交。
- 创建、修改、删除出场记录时同样记录审计日志。
- 授权或撤销球队管理员时同样记录审计日志。
- 事件中的球员、球队和比赛关系在服务层完整校验。
- 官方比分、比赛完赛和锁定操作由管理员接口串行处理。
- V1 不做乐观锁；并发写采用最后提交生效，但保留审计记录。若多人同时编辑成为常态，再增加版本号或 `If-Match`。

## 10. 非目标

V1 不包含：

- 微信小程序前端实现
- 多联赛运营
- 杯赛淘汰制
- 加时赛和点球大战
- 转会系统
- 自动生成赛程
- 图片上传与对象存储
- 推送通知
- 评论、点赞和社交功能
- 高级技术统计（射门、关键传球、抢断等）
- 积分榜物化表或缓存层

## 11. 验收标准

### 11.1 数据模型

- Flyway 可以在空 MySQL 8 数据库上完成迁移。
- 不存在 `league_standing` 表。
- 外键、唯一约束和检查约束能阻止明显的非法数据。
- 删除被比赛引用的球队或球员会被拒绝。
- 同一赛季中不能重复登记同一支参赛球队。
- 同一球队内有效球员的球衣号码不能重复。
- 已有比赛时不能移除对应的赛季参赛球队。

### 11.2 浏览功能

- 未登录用户可以查看球队、球员、公开赛程和榜单。
- `DRAFT` 比赛不会出现在公开赛程和比赛详情中。
- 球队详情可以返回球员列表。
- 比赛详情可以返回双方事件和出场记录。

### 11.3 统计功能

- 普通进球计入射手榜，乌龙球不计入射手榜。
- 有 `related_player_id` 的进球计入助攻榜。
- 出场次数来自 `fixture_appearance`。
- 非 `FINISHED` 比赛不进入球员统计。
- 积分榜按 3/1/0 计分，并按积分、净胜球、进球数、胜场数、球队名称排序。
- 取消比赛不进入积分榜。

### 11.4 权限功能

- `TEAM_MANAGER` 可以编辑自己球队的球员、出场和事件。
- `TEAM_MANAGER` 无法编辑其他球队的数据。
- `TEAM_MANAGER` 无法修改官方比分或锁定状态。
- `data_locked=1` 后，`TEAM_MANAGER` 的写请求返回 `403`。
- `data_locked=1` 时，管理员业务写请求同样被拒绝，必须先解锁。
- `ADMIN` 可以解锁、修改比分和撤销球队管理员。
- 完赛操作会同时设置 `data_locked=1`。
- 撤销授权后，用户下一次请求立即失去对应球队权限。
- 撤销最后一条球队授权后，非管理员用户的角色降为 `VIEWER`。
- 球队管理员不能把球队状态改为 `INACTIVE`。
- 比赛已有事件或出场记录后，不能修改主客队或所属赛季。

### 11.5 数据完整性

- 同一球员在同一场比赛中不能有两条出场记录。
- 主队和客队不能相同，且必须都是对应赛季的参赛球队。
- 事件球队必须是本场参赛球队。
- 普通进球的助攻球员必须与进球球员同队，且不能是同一人。
- 乌龙球的 `related_player_id` 必须为空。
- 所有管理写操作都能在 `audit_log` 中追溯。

## 12. 后续扩展路径

- 新增射门、关键传球、抢断等统计时，扩展 `fixture_event.event_type`，不改变核心表结构。
- 引入多联赛时，在 `season` 之上增加 `competition`。
- 支持淘汰赛时，为 `fixture` 增加阶段和晋级关系。
- 出现性能瓶颈时，先增加统计缓存，再考虑物化积分榜。
- 需要比赛级临时数据录入员时，增加 `fixture_editor(fixture_id, user_id, team_id)`，不修改现有角色定义。
