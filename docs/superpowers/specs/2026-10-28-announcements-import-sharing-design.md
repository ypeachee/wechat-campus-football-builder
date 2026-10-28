# 公告、定时提醒、批量导入、分享与运营看板设计

日期：2026-10-28  
状态：待确认

## 目标

在不改变现有四级权限模型、不强制首发人数、不引入默认阵型的前提下，补齐以下运营能力：

1. GitHub Actions 在远程仓库实际执行并变绿。
2. 超级管理员发布小程序内公告；授权管理员可发布指定球队公告。
3. 比赛提醒由服务端定时生成，不再依赖用户打开消息中心。
4. 支持 Excel `.xlsx` 与 CSV 批量导入球队、球员和号码。
5. 球队详情支持微信分享卡片和球队小程序码。
6. 管理台展示待审核评分、待处理举报、明日比赛和当前停赛待办。

## 约束

- 用户自行登录，不实现自动续期。
- 服务器暂维持约 1 GB 内存，新增功能不得引入高内存常驻组件。
- 单次导入限制为 2 MB、最多 1000 行；导入采用事务，校验失败不写入任何数据。
- 公告正文上限 500 字，与现有 `app_notification.content` 兼容。
- 比赛提醒复用 `app_notification` 的去重键，不新增定时消息表。
- 微信小程序码只编码公开球队页，不包含用户身份信息。
- 微信订阅消息不在本轮范围。

## 功能设计

### 1. GitHub Actions

- 将本地 `main` 推送到已知远程仓库 `ypeachee/wechat-campus-football-builder`。
- 确认工作流执行：
  - MySQL 8 服务下执行后端 `mvnw verify`
  - 构建运行时 Docker 镜像
  - 小程序安装依赖、typecheck、WXML 检查、Vitest
- 若远程推送需要交互式凭据，保留本地提交并向用户说明唯一阻塞点。

### 2. 管理员公告

新增 `announcement` 表：

- `id`
- `title`
- `content`
- `audience`：`ALL`、`ADMIN`、`TEAM_STAFF`、`TEAM`、`USER`
- `target_team_id`、`target_user_id`
- `created_by`
- `recipient_count`
- `created_at`

接口：

- `GET /api/v1/admin/announcements`
- `POST /api/v1/admin/announcements`
- `GET /api/v1/admin/announcements/{id}`，返回已读统计

权限：

- `ADMIN` 可向全部范围发送。
- `TEAM_MANAGER` 仅可向指定球队工作人员发送。
- 其他角色禁止发布。

发送机制：

- 公告写入后，立即向目标用户插入 `SYSTEM` 类型通知。
- 去重键为 `announcement:{announcementId}`。
- 公告历史显示发送人数、已读人数。

### 3. 定时比赛提醒

新增定时任务，每 60 分钟运行一次：

- 开赛前 24 小时的窗口内发送“比赛即将开始”。
- 开赛前 2 小时的窗口内发送“阵容确认提醒”。
- 只处理 `SCHEDULED` 且存在开赛时间的比赛。
- 通知对象为两队授权管理员和领队。
- 通过 `fixture-start:{fixtureId}`、`lineup-deadline:{fixtureId}` 去重。
- 删除 `NotificationService.list()` 中的懒生成逻辑。
- 定时服务使用可注入的 `Clock`，便于测试。

停赛和评分审核仍沿用现有实时通知。

### 4. Excel/CSV 批量导入

新增依赖 Apache POI，用于 `.xlsx`；CSV 使用轻量解析。

球队模板列：

```text
球队名称,简称,教练,主场,状态
```

球员模板列：

```text
球队名称,姓名,号码,位置
```

接口：

- `POST /api/v1/import/teams/preview`
- `POST /api/v1/import/teams/commit`
- `POST /api/v1/import/players/preview`
- `POST /api/v1/import/players/commit`

预览返回：

- 有效行数
- 错误行号与原因
- 新建/更新数量
- 前 20 行预览

规则：

- 提交时重新上传同一文件，服务端再次完整校验。
- 默认不覆盖；用户显式选择覆盖后才更新同名球队或同队同号码球员。
- 文件内重复、球衣号码冲突、球队不存在均报错。
- 球队导入仅 `ADMIN`、`TEAM_MANAGER`。
- 球员导入允许领队，但只能导入自己管理的球队。
- 事务失败时全部回滚并写审计日志。

小程序新增导入页，支持从微信会话选择文件、预览、确认导入和错误报告。

### 5. 球队分享卡片与小程序码

- 后端新增公开接口 `GET /api/v1/teams/{teamId}/qr`，服务端调用微信 `getwxacodeunlimit` 生成小程序码；内存缓存 24 小时，失败返回明确的 502。
- 小程序球队详情页：
  - `onShareAppMessage` 返回球队标题和分享路径。
  - 使用隐藏 2D Canvas 生成统一品牌分享卡片，包含队名、简称、教练、场馆和球队码。
  - 无卡片图时回退到球队 Logo 或小程序封面。
  - 提供“分享球队”和“球队二维码”操作。
  - 二维码支持预览、保存到相册。
- 小程序码页面为 `pages/team-detail/index?teamId={id}`，不含个人数据。

### 6. 管理台待办

新增 `GET /api/v1/manage/dashboard`：

- `pendingRatings`：仅超级管理员可见，否则为 `null`。
- `reportedRatings`：仅超级管理员可见，否则为 `null`。
- `tomorrowFixtures`：按角色球队范围统计。
- `activeSuspensions`：按角色球队范围统计。

管理台顶部显示待办卡片：

- 评分待审核 → 打开评分审核面板。
- 举报待处理 → 打开举报筛选。
- 明日比赛 → 打开比赛数据。
- 当前停赛 → 打开纪律中心。

## 验收标准

- GitHub Actions 实际运行且所有任务通过。
- 公告可按全部用户、球队工作人员、指定球队、指定用户发送，并统计已读。
- 关闭小程序 24 小时后重新打开，仍能看到服务端提前生成的比赛提醒。
- CSV 和 XLSX 均可预览并事务导入；错误文件不产生部分数据。
- 球队详情可以分享、显示二维码并保存图片。
- 管理台待办数字与数据库一致，领队只看到自己的球队范围。
- `npm run verify`、后端测试编译及相关集成测试通过。
- 生产迁移后 API、通知中心、球队详情和登录保持正常。