# Campus Football API

校园足球联赛后端 MVP，提供球队/球员浏览、比赛事件、射手榜、助攻榜、出场榜、积分榜和按球队授权的数据编辑能力。

## 技术栈

- Java 17（编译目标）
- Spring Boot 3.4.5
- MyBatis
- MySQL 8
- Flyway
- Spring Security + JWT
- JUnit 5 + Testcontainers

## 目录

```text
src/main/java/com/campusfootball
├── auth         微信登录、JWT、管理员初始化
├── audit        审计日志写入与查询
├── common       统一错误响应、安全上下文、接口限流
├── competition  赛季、比赛、出场、事件
├── config       Spring Security 和配置属性
├── stats        射手榜、助攻榜、出场榜、积分榜
├── notification 小程序内消息中心
├── team         球队、球员、球队授权
└── user         用户与角色
```

## 前置环境

- JDK 17 或更高版本
- Docker Desktop（默认使用 Testcontainers / MySQL 容器）
- Docker Desktop 的 Linux 引擎需要启用 WSL2 或 Virtual Machine Platform
- 真实微信登录需要小程序 `appId` 和 `appSecret`

项目自带 Maven Wrapper，不需要单独安装 Maven。

## 启动本地 MySQL

```powershell
docker compose up -d mysql
```

默认数据库：

- 地址：`127.0.0.1:3306`
- 数据库：`campus_football`
- 用户：`campus`
- 密码：`campus`

## 配置环境变量

参考 `.env.example`。PowerShell 示例：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/campus_football?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false'
$env:DB_USERNAME='campus'
$env:DB_PASSWORD='campus'
$env:JWT_SECRET='replace-with-a-secret-at-least-32-bytes-long'
$env:WECHAT_APP_ID=''
$env:WECHAT_APP_SECRET=''
$env:ADMIN_WECHAT_OPENID=''
```

`ADMIN_WECHAT_OPENID` 用于首次启动时初始化联赛管理员。生产环境必须替换 JWT 密钥并通过受控部署流程配置管理员 openid。

## 运行

```powershell
.\mvnw.cmd spring-boot:run
```

默认端口为 `8080`，可通过 `SERVER_PORT` 修改。

生产部署（阿里云、域名、HTTPS、Docker Compose）：[docs/deployment/aliyun.md](docs/deployment/aliyun.md)

## 测试

默认使用 Testcontainers MySQL：

```powershell
.\mvnw.cmd test
```

如果本机暂时无法运行 Docker Linux 引擎，可以让集成测试连接外部 MySQL 8：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false'
$env:TEST_DB_USERNAME='campus'
$env:TEST_DB_PASSWORD='campus'
.\mvnw.cmd test
```

完整构建验证：

```powershell
.\mvnw.cmd verify
```

## 主要 API

公开接口：

- `GET /api/v1/seasons`
- `GET /api/v1/teams`
- `GET /api/v1/teams/{teamId}`
- `GET /api/v1/teams/{teamId}/players`
- `GET /api/v1/players/{playerId}`
- `GET /api/v1/fixtures?seasonId=`
- `GET /api/v1/fixtures/{fixtureId}`
- `GET /api/v1/standings?seasonId=`
- `GET /api/v1/stats/scorers?seasonId=`
- `GET /api/v1/stats/assists?seasonId=`
- `GET /api/v1/stats/appearances?seasonId=`

认证接口：

- `POST /api/v1/auth/wechat-login`
- `GET /api/v1/me`

管理接口：

- `/api/v1/manage/**`：超级管理员、授权管理员或领队
- `/api/v1/admin/**`：超级管理员或授权管理员（评分审核和敏感词除外）
- `GET /api/v1/manage/audit-logs`：仅超级管理员

## 权限模型

- `ADMIN`：超级管理员，拥有全部权限，并可查看审计、审核评分和管理敏感词
- `TEAM_MANAGER`：授权管理员，可管理全部球队、赛事、赛程、比分、比赛事件和淘汰赛，但不能查看审计或审核评分
- `TEAM_LEADER`：领队，只能维护被授权球队的人员资料和该队出场阵容
- `VIEWER`：公开只读

球队授权保存在 `team_manager` 表中，并记录 `TEAM_MANAGER` 或 `TEAM_LEADER` 授权类型。用户全局角色取所有授权中的最高角色；最后一条授权撤销后，非管理员用户降为 `VIEWER`。角色与球队范围在每个请求中重新从数据库加载，撤销授权后旧 token 会立即失去对应权限。

## 比赛数据规则

- 助攻通过进球事件的 `related_player_id` 保存，不单独创建 `ASSIST` 事件
- 官方比分只由管理员维护，事件不会自动修改比分
- 授权管理员可维护比赛事件；领队只能维护本队出场
- 完赛会同时设置 `FINISHED` 和 `data_locked=1`
- 锁定后所有管理角色都必须先由超级管理员解锁才能修改事件、出场或比分
- 积分榜不建表，由已完赛比赛动态计算
- 球员榜只统计已完赛比赛

## 红黄牌禁赛与纪律中心

- 默认同一赛事赛季累计 3 张黄牌停赛 1 场，第 6、9 张继续生成新的停赛
- 默认直接红牌停赛 1 场
- 赛事可配置黄牌阈值、红牌停赛场次，以及黄牌是否跨阶段累计
- 只有已完赛比赛中的红黄牌计入，跨赛事不累计
- 下一场本队有效比赛完赛且球员未出场时自动执行 1 场
- 停赛球员不能进入首发或替补名单
- 授权管理员和超级管理员可手动添加、延长或解除禁赛
- 领队只能查看本队禁赛情况
- 纪律中心聚合停赛状态、红黄牌历史、来源比赛与手动处罚

积分榜同分排序为：积分、净胜球、进球数、公平竞赛分、胜场、球队名称。黄牌扣 1 分、红牌扣 3 分，公平竞赛分在积分榜接口中返回。

## 接口限流

- 微信登录：按 IP 每分钟 8 次
- 评分提交：按用户每分钟 20 次
- 举报：按用户每 10 分钟 5 次
- 禁赛调整：每分钟 10 次
- 比赛数据写入：每分钟 120 次

超限返回 HTTP 429 和 `RATE_LIMITED`。阈值可通过 `RATE_LIMIT_*` 环境变量覆盖；当前为单实例内存限流，扩展为多实例时应迁移到 Redis。

## 消息中心

小程序内消息中心支持比赛开始、阵容确认、球员停赛和评分审核提醒，按用户隔离并支持未读状态。比赛提醒在用户打开消息中心时按未来 24 小时赛程生成，并通过唯一键避免重复。

## 微信小程序

前端子工程位于 `miniprogram/`，包含公开浏览、微信登录、角色门禁和管理端页面。

```powershell
cd miniprogram
npm install
npm run verify
```

详细运行说明：[miniprogram/README.md](miniprogram/README.md)

人工验收清单：[docs/acceptance/wechat-miniprogram-acceptance.md](docs/acceptance/wechat-miniprogram-acceptance.md)

使用微信开发者工具打开 `miniprogram/`，将 `project.config.json` 中的 `touristappid` 替换为你的小程序 AppID，然后执行“工具 -> 构建 npm”。

开发环境默认请求 `http://127.0.0.1:8080`。生产或体验环境 API 地址在 `miniprogram/config/env.ts` 中配置，并需要加入微信后台 request 合法域名。


## 球员评分与内容审核

- 已完赛比赛的出场球员支持 5 星评分，按 `stars × 2` 展示为 10 分制
- 评分用户每场每球员仅一条记录，24 小时内可修改一次
- 短评最多 100 字，经过本地敏感词和微信 `msgSecCheck` 审核
- 待审核、拒绝和隐藏评价不计入公开均分
- 当值主裁支持独立的 5 星评分，同样按 10 分制展示，不提供文字评价
- 管理员可审核评价、处理举报和维护敏感词
- 验收清单：[docs/acceptance/ratings-moderation-acceptance.md](docs/acceptance/ratings-moderation-acceptance.md)

> 启用评价功能后，小程序属于包含用户生成内容的服务。基础信息、服务内容、隐私保护指引和举报能力必须同步更新。

## 公告与运营待办

- `GET/POST /api/v1/admin/announcements`：公告历史与发布
- `GET /api/v1/admin/announcements/{id}`：公告送达与已读统计
- `GET /api/v1/manage/dashboard`：待审核评分、待处理举报、明日比赛、当前停赛

公告支持全部用户、管理员、球队工作人员、指定球队、指定用户。超级管理员可发送全部范围；授权管理员只能发送指定球队公告。

## 定时比赛提醒

服务端每小时检查一次未来 24 小时的已排期比赛：

- 进入 24 小时窗口时发送比赛提醒
- 进入 2 小时窗口时发送阵容确认提醒
- 通知对象为双方球队管理员和领队
- 使用唯一去重键保证重复执行不会重复发送

## CSV / XLSX 批量导入

接口位于 `/api/v1/manage/import`，支持先 `preview` 再 `commit`：

- 球队表头：`球队名称,简称,教练,主场,状态`
- 球员表头：`号码,姓名`，先选择一支球队后导入
- 单文件最大 2 MB、最多 1000 行
- 预览返回行号级错误
- 提交为事务操作，任一校验失败不会部分写入
- 领队只能选择被授权球队，并逐队导入球员

## 球队分享与小程序码

- `GET /api/v1/teams/{id}/qr`：生成球队小程序码并缓存 24 小时
- 球队详情支持微信分享、朋友圈分享、分享卡片和保存二维码
- `WECHAT_CODE_ENV_VERSION` 控制小程序码版本，可设为 `develop`、`trial` 或 `release`

当前体验阶段使用 `trial`；正式发布后改为 `release`。