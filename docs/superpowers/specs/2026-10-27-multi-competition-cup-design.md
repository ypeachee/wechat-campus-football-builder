# 多赛事、小组赛与单场淘汰赛设计

**日期：** 2026-10-27  
**状态：** 方向已确认，等待书面 spec 审阅  
**影响范围：** 数据库、后端 API、管理端、小程序公开端  
**依赖：** 现有 CUGB 校园足球后端与微信小程序 V1

## 1. 目标

在现有单赛事、单积分榜模型之上同时支持：

1. 多个赛事，例如校园联赛和校园杯赛。
2. 一个赛事下的多个赛季。
3. 小组赛阶段，并分别计算各小组积分榜。
4. 杯赛淘汰赛对阵、晋级关系、点球大战和决赛树。
5. 球员榜与积分榜按赛事、赛季、阶段和小组筛选。

## 2. 核心层级

```text
competition（赛事）
└── season（赛季）
    └── stage（阶段）
        └── group（小组，可选）
```

示例：

```text
CUGB 校园足球联赛（LEAGUE）
└── 2026 春季赛
    └── 联赛阶段（LEAGUE）

CUGB 校园足球杯（CUP）
└── 2026 杯赛
    ├── 小组赛阶段（GROUP）
    │   ├── A组
    │   └── B组
    └── 淘汰赛阶段（KNOCKOUT）
        ├── 半决赛
        └── 决赛
```

## 3. 关键业务规则

### 3.1 赛事类型

- `LEAGUE`：联赛，以单循环或双循环积分赛为主。
- `CUP`：杯赛，通常包含小组赛和淘汰赛。

赛事类型决定管理端推荐流程，但阶段表仍允许灵活组合。

### 3.2 阶段类型

- `LEAGUE`：联赛积分阶段。
- `GROUP`：小组赛阶段，包含至少一个小组。
- `KNOCKOUT`：单场淘汰阶段。

同一赛季可以包含多个阶段，但 V1 杯赛默认一个小组赛阶段和一个淘汰赛阶段。

### 3.3 小组

- 小组必须属于 `GROUP` 阶段。
- 一个球队在同一阶段只能属于一个小组。
- 小组积分榜只统计该小组球队之间的比赛。
- 小组赛阶段可以有“阶段总榜”，但默认展示各小组榜。

### 3.4 淘汰赛

- V1 采用单场淘汰。
- 常规时间打平必须通过点球大战决出胜者。
- 每场淘汰赛必须有唯一胜者，不支持平局结束。
- 决赛胜者为赛事冠军。
- 可配置三四名决赛。
- 首轮晋级队伍数量必须是 4、8 或 16，V1 不支持轮空。
- 后续版本可增加轮空和 32 队以上规模。

### 3.5 晋级

- 小组赛按小组积分榜排名产生晋级球队。
- 管理员通过“晋级规则”把 `小组名次` 映射到淘汰赛首轮的 `对阵位置`。
- 默认支持每组前两名晋级，但具体名额和映射由管理员确认。
- 名次使用现有积分榜排序；完全相同的排序结果允许管理员在生成首轮前手动调整种子球队。
- 淘汰赛胜者自动填入下一轮配置的位置。
- 如果下一轮对阵已经被修改或锁定，修改上一轮结果必须被拒绝，避免破坏后续树。

### 3.6 比赛锁定

沿用现有规则：

- 淘汰赛完赛同样设置 `data_locked=1`。
- 解锁、修改比分、点球比分或胜者后，必须重新校验后续对阵树。
- 已产生下游比赛数据时，不允许直接覆盖晋级结果。

## 4. 数据模型

### 4.1 competition

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(100) | NOT NULL |
| type | VARCHAR(20) | `LEAGUE` / `CUP` |
| description | VARCHAR(255) | NULL |
| status | VARCHAR(20) | `ACTIVE` / `INACTIVE` |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

索引：`(type, status)`。

### 4.2 season 扩展

新增：

| 字段 | 类型 | 约束 |
|---|---|---|
| competition_id | BIGINT | FK -> competition.id，NOT NULL |

现有 `season` 字段保持不变。

### 4.3 stage

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| season_id | BIGINT | FK -> season.id，NOT NULL |
| name | VARCHAR(64) | NOT NULL |
| type | VARCHAR(20) | `LEAGUE` / `GROUP` / `KNOCKOUT` |
| sort_order | INT | NOT NULL，默认 0 |
| status | VARCHAR(20) | `DRAFT` / `ACTIVE` / `FINISHED` |
| created_at | DATETIME | NOT NULL |
| updated_at | DATETIME | NOT NULL |

约束：

- `UNIQUE(season_id, name)`
- `sort_order >= 0`

### 4.4 competition_group

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| stage_id | BIGINT | FK -> stage.id，NOT NULL |
| name | VARCHAR(32) | NOT NULL |
| sort_order | INT | NOT NULL，默认 0 |

约束：

- `UNIQUE(stage_id, name)`
- 服务层必须验证所属阶段为 `GROUP`

### 4.5 group_team

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| group_id | BIGINT | FK -> competition_group.id，NOT NULL |
| team_id | BIGINT | FK -> team.id，NOT NULL |
| seed_no | INT | NULL |

约束：

- `UNIQUE(group_id, team_id)`
- 同一 stage 下同一球队最多属于一个 group；服务层校验
- 球队必须已经注册到该赛季的 `season_team`

### 4.6 fixture 扩展

新增：

| 字段 | 类型 | 约束 |
|---|---|---|
| stage_id | BIGINT | FK -> stage.id，NOT NULL |
| group_id | BIGINT | FK -> competition_group.id，NULL |
| knockout_tie_id | BIGINT | FK -> knockout_tie.id，NULL |
| home_penalty_score | SMALLINT | NULL |
| away_penalty_score | SMALLINT | NULL |

规则：

- `GROUP` 阶段比赛必须有 `group_id`
- `LEAGUE` 阶段比赛 `group_id` 和 `knockout_tie_id` 为空
- `KNOCKOUT` 阶段比赛必须有 `knockout_tie_id`，`group_id` 为空
- 点球比分必须同时为空或同时非空
- 非淘汰赛不允许点球比分
- 常规时间比分打平时，淘汰赛完赛必须提供不同的点球比分

### 4.7 knockout_round

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| stage_id | BIGINT | FK -> stage.id，NOT NULL |
| round_no | INT | NOT NULL |
| name | VARCHAR(64) | NOT NULL |
| sort_order | INT | NOT NULL |

约束：

- `UNIQUE(stage_id, round_no)`
- 只允许属于 `KNOCKOUT` 阶段

### 4.8 knockout_tie

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| stage_id | BIGINT | FK -> stage.id，NOT NULL |
| round_id | BIGINT | FK -> knockout_round.id，NOT NULL |
| bracket_position | INT | NOT NULL |
| home_team_id | BIGINT | FK -> team.id，NULL |
| away_team_id | BIGINT | FK -> team.id，NULL |
| winner_team_id | BIGINT | FK -> team.id，NULL |
| next_tie_id | BIGINT | FK -> knockout_tie.id，NULL |
| next_slot | VARCHAR(10) | `HOME` / `AWAY`，NULL |
| is_third_place | TINYINT(1) | NOT NULL，默认 0 |
| status | VARCHAR(20) | `PENDING` / `READY` / `FINISHED` / `CANCELLED` |

约束：

- `UNIQUE(round_id, bracket_position)`
- 决赛 `next_tie_id` 为空
- 非决赛 `next_tie_id + next_slot` 必填
- 三四名决赛不进入决赛树晋级链路
- 一个 `knockout_tie` 最多对应一场 `fixture`，`fixture.knockout_tie_id` 需要唯一约束

### 4.9 knockout_seed

用于将小组名次映射到淘汰赛首轮位置。

| 字段 | 类型 | 约束 |
|---|---|---|
| id | BIGINT | PK |
| stage_id | BIGINT | FK -> stage.id，NOT NULL |
| source_group_id | BIGINT | FK -> competition_group.id，NOT NULL |
| source_rank | INT | NOT NULL |
| target_tie_id | BIGINT | FK -> knockout_tie.id，NOT NULL |
| target_slot | VARCHAR(10) | `HOME` / `AWAY` |

约束：

- `UNIQUE(stage_id, source_group_id, source_rank)`
- `UNIQUE(target_tie_id, target_slot)`
- `source_rank >= 1`
- 目标必须是首轮对阵

## 5. 晋级传播

### 5.1 小组赛到首轮

生成首轮对阵时：

1. 读取晋级规则。
2. 计算对应小组的最终积分榜。
3. 按 `source_rank` 取出球队。
4. 写入首轮 `knockout_tie` 的 `home_team_id` 或 `away_team_id`。
5. 缺少球队、名次不存在或目标位置已占用时，拒绝生成并指出具体规则。

### 5.2 淘汰赛胜者到下一轮

淘汰赛完赛时：

1. 计算胜者。
2. 写入 `knockout_tie.winner_team_id`。
3. 如果该对阵有 `next_tie_id`，根据 `next_slot` 填入下一轮。
4. 如果下一轮该位置已有不同球队或下游比赛已有数据，拒绝更新。
5. 如果下一轮两方均已填充，状态从 `PENDING` 变为 `READY`。

### 5.3 修改淘汰赛结果

- 上游结果只能在下一轮尚未产生比赛数据时修改。
- 修改时必须撤销旧胜者的下游传播。
- 如果下游已经开赛或完赛，必须按从后往前的顺序删除或重置。

## 6. API 变更

### 6.1 公共接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/v1/competitions | 赛事列表 |
| GET | /api/v1/seasons?competitionId= | 按赛事查询赛季 |
| GET | /api/v1/stages?seasonId= | 阶段列表 |
| GET | /api/v1/groups?stageId= | 小组列表 |
| GET | /api/v1/fixtures?seasonId=&stageId=&groupId= | 分阶段/小组查询赛程 |
| GET | /api/v1/standings?stageId=&groupId= | 小组或联赛积分榜 |
| GET | /api/v1/stats/scorers?seasonId=&stageId=&groupId= | 射手榜筛选 |
| GET | /api/v1/stats/assists?seasonId=&stageId=&groupId= | 助攻榜筛选 |
| GET | /api/v1/stats/appearances?seasonId=&stageId=&groupId= | 出场榜筛选 |
| GET | /api/v1/brackets?stageId= | 淘汰赛决赛树 |

兼容策略：

- `seasonId` 继续保留。
- 只有一个阶段时，可省略 `stageId`，后端自动选择。
- 杯赛等多阶段赛事必须显式传入 `stageId`。
- 旧的按赛季总榜在只有一个联赛阶段时保持可用。

### 6.2 管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST/PUT | /api/v1/admin/competitions | 创建/修改赛事 |
| POST | /api/v1/admin/seasons | 创建赛季，必须指定 competitionId |
| POST/PUT | /api/v1/admin/stages | 创建/修改阶段 |
| POST/PUT | /api/v1/admin/stages/{stageId}/groups | 创建/修改小组 |
| POST | /api/v1/admin/groups/{groupId}/teams | 添加球队到小组 |
| DELETE | /api/v1/admin/groups/{groupId}/teams/{teamId} | 移除小组球队 |
| POST | /api/v1/admin/stages/{stageId}/rounds | 创建淘汰轮次 |
| POST | /api/v1/admin/stages/{stageId}/ties | 创建对阵节点 |
| PUT | /api/v1/admin/ties/{tieId} | 配置对阵和晋级链路 |
| POST | /api/v1/admin/stages/{stageId}/seeds | 配置小组晋级映射 |
| POST | /api/v1/admin/stages/{stageId}/generate-bracket | 从小组榜生成首轮 |
| PUT | /api/v1/admin/fixtures/{fixtureId}/finish | 完赛，淘汰赛可带点球比分 |

## 7. 前端信息架构

### 7.1 全局选择器

首页、赛程、榜单使用：

```text
赛事 → 赛季 → 阶段 → 小组（视阶段决定是否显示）
```

选择结果持久化，切换赛事时重置下游选择。

### 7.2 联赛视图

- 阶段选择通常只有一个“联赛阶段”。
- 积分榜显示一张总榜。
- 球员榜按赛事/赛季筛选。

### 7.3 杯赛视图

- 小组赛：显示 A/B 组积分榜和小组赛程。
- 淘汰赛：显示横向决赛树。
- 对阵卡片显示双方、比分、点球比分、胜者和比赛时间。
- 决赛高亮，三四名决赛单独展示。

### 7.4 管理端

新增：

- 赛事管理
- 阶段管理
- 小组与球队分配
- 淘汰轮次和对阵节点
- 晋级规则
- 生成首轮对阵
- 决赛树预览

## 8. 权限

沿用现有角色：

- `ADMIN`：管理赛事、阶段、小组、淘汰树、比分和晋级。
- `TEAM_MANAGER`：只能维护授权球队相关的出场和事件。
- `VIEWER`：只读。

淘汰赛胜者、点球比分、晋级规则和决赛树只允许 `ADMIN` 修改。

## 9. 迁移方案

V2 数据库迁移：

1. 创建 `competition`。
2. 插入默认赛事 `CUGB校园足球联赛`，类型为 `LEAGUE`。
3. 为 `season` 增加 `competition_id` 并回填默认赛事。
4. 为每个现有赛季创建默认 `LEAGUE` 阶段。
5. 为 `fixture` 增加 `stage_id` 并回填对应阶段。
6. 增加可空的 `group_id`、`knockout_tie_id` 和点球比分字段。
7. 创建小组、淘汰轮次、对阵、晋级规则等新表。
8. 迁移后旧积分榜与球员榜结果保持不变。

## 10. 非目标

V1 不包含：

- 主客场两回合淘汰赛
- 客场进球规则
- 轮空签位
- 32 队以上自动树
- 自动抽签算法
- 升降级
- 跨赛事累计积分
- 赛事版权/媒体管理

## 11. 验收标准

### 11.1 数据与迁移

- 旧赛季、比赛和统计在 V2 迁移后保持可见。
- 默认联赛赛事和阶段自动创建。
- 非赛事管理角色不能修改赛事、阶段、小组和淘汰树。

### 11.2 联赛

- 联赛赛事可以创建多个赛季。
- 联赛积分榜只统计对应赛季/阶段的比赛。
- 球员榜按赛事、赛季和阶段筛选正确。

### 11.3 小组赛

- 一个阶段可创建 A/B 等多个小组。
- 同一球队不能同时进入同一阶段的两个小组。
- 小组积分榜只统计该组比赛。
- 小组赛比赛必须带 `group_id`。

### 11.4 淘汰赛

- 可以配置 4/8/16 队单场淘汰树。
- 小组晋级规则可以把指定名次填入首轮对阵。
- 淘汰赛完赛必须产生唯一胜者。
- 常规时间平局且没有有效点球比分时，完赛失败。
- 胜者自动进入下一轮指定位置。
- 决赛树正确展示所有轮次和最终冠军。
- 下游已有数据时，不能覆盖上游胜者。

### 11.5 前端

- 首页、赛程和榜单可以切换赛事、赛季、阶段和小组。
- 杯赛有小组赛与淘汰赛两个视图。
- 决赛树在手机横向滚动可用。
- 管理端可以完成创建赛事到产生冠军的完整流程。
