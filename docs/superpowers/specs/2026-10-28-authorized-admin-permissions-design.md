# 三级管理员权限设计

**日期：** 2026-10-28  
**状态：** 已确认方向，准备进入实现  
**影响范围：** 数据库角色、Spring Security、后端授权服务、管理端 API、小程序管理台、角色文案、集成测试

## 1. 目标

在现有权限体系上建立三级管理角色：

1. `ADMIN`：超级管理员，拥有全部权限。
2. `TEAM_MANAGER`：授权管理员，拥有除审计日志和评分审核外的全部管理权限。
3. `TEAM_LEADER`：领队，只能维护被授权球队的人员数据和该队比赛出场记录。

`VIEWER` 仍为普通只读用户，不属于三级管理员。

## 2. 权限矩阵

| 功能 | ADMIN | TEAM_MANAGER 授权管理员 | TEAM_LEADER 领队 | VIEWER |
|---|---:|---:|---:|---:|
| 浏览公开内容 | 可以 | 可以 | 可以 | 可以 |
| 普通评分、主裁评分、举报 | 可以 | 可以 | 可以 | 需登录后评分 |
| 新建球队 | 可以 | 可以 | 不可以 | 不可以 |
| 编辑任意球队资料 | 可以 | 可以 | 不可以 | 不可以 |
| 编辑被授权球队资料 | 可以 | 可以 | 可以 | 不可以 |
| 修改球队启用状态 | 可以 | 可以 | 不可以 | 不可以 |
| 管理任意球队球员 | 可以 | 可以 | 不可以 | 不可以 |
| 管理被授权球队球员 | 可以 | 可以 | 可以 | 不可以 |
| 球队管理员/领队授权 | 可以 | 可以 | 不可以 | 不可以 |
| 赛事、赛季、阶段、小组 | 可以 | 可以 | 不可以 | 不可以 |
| 赛程、比分、锁定/解锁 | 可以 | 可以 | 不可以 | 不可以 |
| 比赛事件录入 | 可以 | 可以 | 不可以 | 不可以 |
| 任意球队出场与阵容 | 可以 | 可以 | 不可以 | 不可以 |
| 被授权球队出场与阵容 | 可以 | 可以 | 可以 | 不可以 |
| 淘汰赛、晋级、点球 | 可以 | 可以 | 不可以 | 不可以 |
| 审计日志 | 可以 | 不可以 | 不可以 | 不可以 |
| 评分审核 | 可以 | 不可以 | 不可以 | 不可以 |
| 敏感词管理 | 可以 | 不可以 | 不可以 | 不可以 |

## 3. 角色与球队授权

### 3.1 全局角色

`app_user.role_code` 保存用户的最高角色：

```text
ADMIN > TEAM_MANAGER > TEAM_LEADER > VIEWER
```

### 3.2 球队授权

`team_manager` 表增加 `role_code`：

- `TEAM_MANAGER`：该记录表示用户至少拥有一项球队授权，从而保留授权管理员角色。
- `TEAM_LEADER`：该记录把用户授权到具体球队。
- 已有历史记录迁移为 `TEAM_MANAGER`，兼容现有账号。
- 同一用户在同一球队只能有一条授权。
- 一个用户可以拥有多个球队授权。
- 用户全局角色始终取所有授权中的最高角色。

撤销授权后重新计算：

- 仍有 `TEAM_MANAGER` 授权：保持 `TEAM_MANAGER`。
- 只有 `TEAM_LEADER` 授权：降为 `TEAM_LEADER`。
- 无球队授权且非 `ADMIN`：降为 `VIEWER`。

### 3.3 数据库迁移

新增 V6 迁移：

1. 扩展 `app_user.chk_app_user_role`，允许 `TEAM_LEADER`。
2. 为 `team_manager` 增加 `role_code VARCHAR(20) NOT NULL DEFAULT 'TEAM_MANAGER'`。
3. 增加 `chk_team_manager_role`，只允许 `TEAM_MANAGER` 和 `TEAM_LEADER`。
4. 保留现有唯一键 `(user_id, team_id)`。
5. 回填历史授权为 `TEAM_MANAGER`。

## 4. 后端授权设计

### 4.1 AuthorizationService

新增方法：

```java
void requireManagementAccess(long userId)
boolean isManagementAdmin(long userId)
boolean isTeamLeader(long userId)
```

语义：

- `requireManagementAccess`：允许 `ADMIN` 和 `TEAM_MANAGER`。
- `isManagementAdmin`：`ADMIN` 或 `TEAM_MANAGER`。
- `isTeamLeader`：仅 `TEAM_LEADER`。
- `requireCanManageTeam`：允许 `ADMIN`、全局 `TEAM_MANAGER`，或授权到该球队的 `TEAM_LEADER`。
- `requireAdmin`：只用于审计、评分审核和敏感词。

### 4.2 SecurityConfig

匹配顺序：

```text
/api/v1/admin/ratings/**             -> ADMIN
/api/v1/admin/sensitive-terms/**     -> ADMIN
/api/v1/manage/audit-logs            -> ADMIN
/api/v1/admin/**                     -> ADMIN, TEAM_MANAGER
/api/v1/manage/**                    -> ADMIN, TEAM_MANAGER, TEAM_LEADER
```

`TEAM_LEADER` 无法直接访问 `/api/v1/admin/**`。

### 4.3 服务层权限

使用 `requireManagementAccess`：

- TeamService 新建球队和全局编辑
- TeamManagerService 授权与撤销
- CompetitionService
- SeasonService
- StageService
- GroupService
- FixtureService
- BracketService
- 其他赛事管理服务

继续使用 `requireAdmin`：

- AuditQueryService
- RatingModerationService
- SensitiveTermService

### 4.4 球队与球员

- 授权管理员可以新建任意球队、修改任意球队状态和人员。
- 领队只能编辑授权球队的队名、简称、教练、主场、队徽和球员数据。
- 领队不能修改球队启用状态。
- 领队不能新建球队。
- 领队不能给其他人授权。

### 4.5 出场与阵容

领队可以：

- 查看被授权球队参加的比赛。
- 在 `SCHEDULED`、`LIVE`、未锁定 `FINISHED` 比赛中维护本队出场和阵容。
- 新增、编辑、删除、批量替换本队出场记录。
- 只能选择本队球员。

领队不能：

- 维护对手阵容。
- 录入或修改进球、助攻、红黄牌。
- 修改比分、比赛状态或锁定状态。

`AppearanceService` 对领队执行球队归属检查。`EventService` 对领队返回 `403`。

### 4.6 审计

领队和授权管理员的管理操作仍写入审计日志，但都不能读取审计日志。审计读取仅 `ADMIN` 可用。

## 5. 授权 API 与小程序

### 5.1 授权记录

`GET /api/v1/admin/teams/{teamId}/managers` 改为返回：

```json
[
  { "userId": 12, "role": "TEAM_MANAGER" },
  { "userId": 18, "role": "TEAM_LEADER" }
]
```

授权请求增加角色：

```json
{ "userId": 18, "role": "TEAM_LEADER" }
```

角色只允许 `TEAM_MANAGER` 或 `TEAM_LEADER`。

### 5.2 球队授权页面

- `ADMIN` 和 `TEAM_MANAGER` 可进入。
- 授权时选择“授权管理员”或“领队”。
- 当前授权列表显示用户 ID 和角色。
- 领队不可进入球队授权页面。

### 5.3 管理首页

`ADMIN`：

- 显示全部管理入口。

`TEAM_MANAGER`：

- 显示除审计日志、评分审核外的全部管理入口。
- 球队列表使用全局管理 API。

`TEAM_LEADER`：

- 显示本队资料。
- 显示“比赛阵容”入口。
- 不显示赛事、赛程、球队授权、淘汰赛、审计和评分审核。

`VIEWER`：

- 不显示管理台入口。

### 5.4 比赛数据页面

- `ADMIN`、`TEAM_MANAGER` 显示事件录入与阵容管理。
- `TEAM_LEADER` 只显示阵容管理；事件列表可以只读展示，但不显示录入、编辑和删除按钮。
- 领队页面只加载被授权球队参加的比赛和本队阵容编辑器。

### 5.5 角色文案

- `ADMIN`：超级管理员
- `TEAM_MANAGER`：授权管理员
- `TEAM_LEADER`：领队
- `VIEWER`：普通用户

## 6. 错误处理

- 领队访问管理事件、比分、赛事、淘汰赛接口返回 `403`。
- 领队访问其他球队资料或阵容接口返回 `403`。
- 授权管理员访问审计、评分审核或敏感词接口返回 `403`。
- `VIEWER` 访问管理接口返回 `403`，未登录返回 `401`。
- 角色被撤销后重新进入管理页必须刷新角色并移除管理入口。

## 7. 测试

### 后端

1. V6 迁移保留现有授权并回填 `TEAM_MANAGER`。
2. 授权管理员可以创建和编辑任意球队。
3. 授权管理员可以管理赛事、赛程、比分、事件、淘汰赛。
4. 授权管理员不能读取审计日志。
5. 授权管理员不能访问评分审核和敏感词。
6. 授权管理员可以授予 `TEAM_MANAGER` 和 `TEAM_LEADER`。
7. 领队只能编辑授权球队资料和球员。
8. 领队不能修改球队状态。
9. 领队可以维护本队 `SCHEDULED`、`LIVE`、未锁定 `FINISHED` 比赛阵容。
10. 领队不能维护对手阵容。
11. 领队不能创建比赛事件。
12. 领队不能访问赛事、赛程、比分和淘汰赛接口。
13. 撤销最后一条授权后角色按最高剩余授权重新计算。
14. `VIEWER` 仍不能写入。

### 小程序

- 三级角色文案正确。
- 管理首页入口按角色显示。
- 授权页面可以选择授权角色并显示当前授权角色。
- 领队只显示阵容管理，不显示事件编辑。
- 比赛数据页面按角色选择全局或本队数据源。
- 类型检查、单元测试和页面注册测试通过。

## 8. 非目标

- 不新增第四种数据库权限系统。
- 不允许领队审核评分或查看审计。
- 不允许领队修改比分和比赛事件。
- 不改变普通评分、比赛锁定、积分榜和淘汰赛业务规则。

## 9. 验收标准

- 现有 `TEAM_MANAGER` 账号升级后立即成为授权管理员。
- 新领队账号可以通过球队授权页面创建。
- 领队只能看到并维护本队人员和本队阵容。
- 授权管理员拥有除审计、评分审核外的全部管理能力。
- 超级管理员权限不降低。
- 普通用户仍不能进入管理台或写接口。
- 后端全量测试、小程序全量测试和生产部署验证通过。
