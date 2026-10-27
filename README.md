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
├── common       统一错误响应、安全上下文
├── competition  赛季、比赛、出场、事件
├── config       Spring Security 和配置属性
├── stats        射手榜、助攻榜、出场榜、积分榜
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
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/campus_football?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
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

## 测试

默认使用 Testcontainers MySQL：

```powershell
.\mvnw.cmd test
```

如果本机暂时无法运行 Docker Linux 引擎，可以让集成测试连接外部 MySQL 8：

```powershell
$env:TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/campus_football?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
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

- `/api/v1/manage/**`：联赛管理员或球队管理员
- `/api/v1/admin/**`：仅联赛管理员
- `GET /api/v1/manage/audit-logs`：按角色返回可见审计记录

## 权限模型

- `ADMIN`：全量管理与比赛冻结/解锁
- `TEAM_MANAGER`：仅授权球队和参赛比赛
- `VIEWER`：公开只读

球队管理员授权保存在 `team_manager` 表中。角色与球队范围在每个请求中重新从数据库加载，撤销授权后旧 token 会立即失去对应权限。

## 比赛数据规则

- 助攻通过进球事件的 `related_player_id` 保存，不单独创建 `ASSIST` 事件
- 官方比分只由管理员维护，事件不会自动修改比分
- 完赛会同时设置 `FINISHED` 和 `data_locked=1`
- 锁定后管理员也必须先解锁才能修改事件、出场或比分
- 积分榜不建表，由已完赛比赛动态计算
- 球员榜只统计已完赛比赛

## 下一阶段

当前仓库只包含后端 API。微信小程序客户端将在下一阶段基于现有 API 实现，优先完成：

1. 球队与球员列表
2. 比赛详情与射手榜/助攻榜
3. 管理员和球队管理员登录后的编辑界面

## 微信小程序

前端子工程位于 `miniprogram/`。

```powershell
cd miniprogram
npm install
npm run verify
```

使用微信开发者工具打开 `miniprogram/`，将 `project.config.json` 中的 `touristappid` 替换为你的小程序 AppID，然后执行“工具 -> 构建 npm”。

开发环境默认请求 `http://127.0.0.1:8080`。生产或体验环境 API 地址在 `miniprogram/config/env.ts` 中配置，并需要加入微信后台 request 合法域名。
