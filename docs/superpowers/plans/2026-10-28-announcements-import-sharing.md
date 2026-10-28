# 公告、定时提醒、批量导入、分享与运营看板实施计划

## 交付顺序

1. GitHub 远程与 CI
2. V10 公告表
3. 公告后端与小程序页面
4. 定时比赛提醒
5. Excel/CSV 导入后端
6. 导入小程序页面
7. 分享卡片与球队小程序码
8. 管理台待办
9. 全量验证与生产部署

## Task 1: GitHub Actions

- 添加远程 `origin=https://github.com/ypeachee/wechat-campus-football-builder.git`。
- 推送 `main`。
- 若认证可用，确认 `CI` 工作流结果；若不可用，保留可推送状态并输出明确错误。

验证：`git ls-remote origin HEAD` 成功，GitHub Actions 页面出现运行记录。

## Task 2: V10 公告表

新增 `V10__add_announcements.sql`：

```sql
CREATE TABLE announcement (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(120) NOT NULL,
  content VARCHAR(500) NOT NULL,
  audience VARCHAR(20) NOT NULL,
  target_team_id BIGINT NULL,
  target_user_id BIGINT NULL,
  created_by BIGINT NOT NULL,
  recipient_count INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ...
);
```

测试：Flyway 在 MySQL 8 成功迁移，约束覆盖 audience 和外键。

## Task 3: 公告后端

接口：

```java
AnnouncementPage list(long actorUserId, int page, int size);
AnnouncementDetail create(long actorUserId, AnnouncementCommand command);
AnnouncementDetail detail(long actorUserId, long announcementId);
```

`AnnouncementCommand` 字段：`title`、`content`、`audience`、`targetTeamId`、`targetUserId`。

新增 `AnnouncementService`、`AnnouncementMapper`、`AnnouncementAdminController`、`AnnouncementView`。

通知写入复用 `NotificationService.notifyUsers`，新增批量用户选择方法：

```java
void notifyUsers(Collection<Long> userIds, NotificationType type,
                 String title, String content, String linkUrl, String dedupeKey);
```

已读统计查询：

```sql
SELECT COUNT(*), SUM(read_at IS NOT NULL)
FROM app_notification
WHERE dedupe_key = CONCAT('announcement:', #{announcementId});
```

测试：ADMIN 全范围、TEAM_MANAGER 只能发指定球队、目标用户去重、审计和已读统计。

## Task 4: 定时比赛提醒

新增 `ScheduledNotificationService`：

```java
ReminderRunResult runAt(LocalDateTime now);
```

- 24 小时提醒：`kickoffAt > now && kickoffAt <= now.plusHours(24)`
- 2 小时阵容提醒：`kickoffAt > now && kickoffAt <= now.plusHours(2)`
- 使用 `fixture-start:{fixtureId}` 和 `lineup-deadline:{fixtureId}` 去重。

新增 `@Scheduled(cron = "0 */5 * * * *")` 包装器，并在应用入口启用 scheduling。

从 `NotificationService.list()` 删除懒生成调用。

测试：窗口边界、重复执行不重复、变更开赛时间后新去重键、非 SCHEDULED 不发送。

## Task 5: 批量导入后端

新增依赖：

```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.4.1</version>
</dependency>
```

新增：

- `TabularFileReader`：解析 CSV/XLSX 为 `List<List<String>>`
- `TeamImportService`
- `PlayerImportService`
- `ImportController`

预览响应：

```java
record ImportPreview(int totalRows, int validRows, List<ImportError> errors,
                     int createCount, int updateCount, List<Map<String, String>> sample);
```

提交响应：

```java
record ImportResult(int created, int updated, int skipped);
```

规则：

- 2 MB、1000 行上限。
- CSV 第一行必须是表头；XLSX 读取首个工作表。
- 全文件先校验，再事务写入。
- 覆盖时必须显式传 `overwrite=true`。
- 领队只能导入授权球队。

测试：合法 CSV、合法 XLSX、坏行、号码重复、球队不存在、全部回滚、角色越权。

## Task 6: 导入小程序页面

新增 `pages/import-data/index`：

- 文件选择：`wx.chooseMessageFile`
- 类型选择：球队/球员
- 覆盖开关
- 预览结果和错误明细
- 确认导入
- CSV 模板说明

服务层新增 `uploadImportFile`，使用 `wx.uploadFile` 并携带 Bearer token。

测试：URL、表单字段、401 清理、预览错误渲染。

## Task 7: 球队分享卡片与小程序码

后端：

- `WechatMiniProgramCodeService`
- `GET /api/v1/teams/{id}/qr`
- access token 内存缓存，提前 5 分钟刷新。
- 小程序码缓存键为 `team:{id}`，TTL 24 小时。
- 微信 API 失败返回 502，不泄露 access token。

前端球队详情：

- `onShareAppMessage` 和 `onShareTimeline`
- 隐藏 2D Canvas 生成分享卡片
- `share-card` 组件
- 二维码预览层
- 保存二维码到相册

测试：公开接口返回 `image/png`、未知球队 404、分享路径正确、二维码失败回退。

## Task 8: 管理台待办

新增 `ManagementDashboardService` 和 `GET /api/v1/manage/dashboard`：

```java
record DashboardView(Long pendingRatings, Long reportedRatings,
                     long tomorrowFixtures, long activeSuspensions);
```

SQL 按角色过滤球队：

- ADMIN/TEAM_MANAGER：全部。
- TEAM_LEADER：`team_manager` 中的球队。
- 评分和举报统计仅 ADMIN 返回数值，其他角色返回 `null`。

管理台增加四个待办卡片及跳转。

测试：超级管理员、球队管理员、领队、无球队领队的统计范围。

## Task 9: 验证与部署

- `npm run verify`
- `mvnw test-compile`
- 可运行的 MySQL 集成测试或 CI
- 生产备份
- 上传 JAR 和 Compose 配置
- Flyway V10
- 验证公告、消息中心、定时任务、导入预览、球队二维码、管理台待办
- 确认 API 无 OOM、健康检查正常

## 回滚策略

- 代码回滚到部署前提交。
- 数据库 V10 只新增表，不删除旧数据；回滚代码不回滚迁移。
- 微信小程序码和分享卡片失败时不影响球队页其他功能。
- 导入失败不写入部分数据。