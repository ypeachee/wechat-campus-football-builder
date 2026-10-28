# 阿里云部署 CUGB 校园足球后端

目标：

```text
微信小程序
    ↓ HTTPS
https://api.cugbsoccer.xyz
    ↓
Caddy（自动申请证书）
    ↓
Spring Boot API
    ↓
MySQL 8
```

生产配置使用 Docker Compose，MySQL 不暴露公网，只有 Caddy 开放 `80/443`。

## 1. 先准备一台公网服务器

域名本身不能运行后端。需要一台带公网 IP 的服务器：

- 阿里云 ECS 或轻量应用服务器。
- Ubuntu 22.04/24.04。
- 最低 2 核 2 GB 内存 + 2 GB Swap；预算允许时选 2 核 4 GB。
- 安全组开放 `22`、`80`、`443`。
- 不要向公网开放 `3306` 和 `8080`。

记下服务器公网 IP，例如：

```text
47.100.xxx.xxx
```

## 2. 在阿里云 DNS 添加解析

进入：阿里云控制台 -> 云解析 DNS -> `cugbsoccer.xyz` -> 解析设置。

添加：

| 记录类型 | 主机记录 | 记录值 | TTL |
|---|---|---|---|
| A | `api` | 服务器公网 IP | 600 |

最终应解析为：

```text
api.cugbsoccer.xyz -> 47.100.xxx.xxx
```

如果还要放官网，可以另外添加：

```text
@ -> 服务器公网 IP
www -> 服务器公网 IP
```

但微信小程序后端只使用 `api.cugbsoccer.xyz`。

## 3. 登录服务器并安装 Docker

```bash
ssh root@47.100.xxx.xxx
apt update
apt install -y curl ca-certificates
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker --version
docker compose version
```

2 GB 内存的服务器建议增加 Swap：

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h
```

## 4. 上传项目所需文件

在服务器创建目录：

```bash
mkdir -p /opt/campus-football
cd /opt/campus-football
```

从 Windows 上传时，至少需要这些内容：

```text
pom.xml
src/
Dockerfile
.dockerignore
compose.prod.yaml
deploy/Caddyfile
.env.production.example
```

可以在本机 PowerShell 中执行：

```powershell
scp -r .\pom.xml .\src .\Dockerfile .\.dockerignore .\compose.prod.yaml .\deploy .\.env.production.example root@47.100.xxx.xxx:/opt/campus-football/
```

## 5. 创建生产环境变量

在服务器执行：

```bash
cd /opt/campus-football
cp .env.production.example .env.production
nano .env.production
```

必须替换：

```text
MYSQL_PASSWORD
MYSQL_ROOT_PASSWORD
JWT_SECRET
WECHAT_APP_ID
WECHAT_APP_SECRET
ADMIN_WECHAT_OPENID
```

生成随机密钥：

```bash
openssl rand -base64 48
```

注意：

- `WECHAT_APP_ID` 和 `WECHAT_APP_SECRET` 必须是同一个微信小程序。
- `AppSecret` 只能放在服务器，不要写进小程序。
- 首次不知道 `ADMIN_WECHAT_OPENID` 时可以先留空，登录一次后再从数据库查出并回填。

## 6. 启动服务

```bash
cd /opt/campus-football
docker compose --env-file .env.production -f compose.prod.yaml up -d --build
docker compose --env-file .env.production -f compose.prod.yaml ps
docker compose --env-file .env.production -f compose.prod.yaml logs -f api
```

首次启动时 Caddy 会自动向 Let's Encrypt 申请 HTTPS 证书。确保：

- DNS 已生效。
- 服务器 `80/443` 已放行。
- 域名没有备案拦截问题。

## 7. 验证 API

在本机浏览器或 PowerShell：

```powershell
Invoke-RestMethod 'https://api.cugbsoccer.xyz/api/v1/seasons'
```

返回 `200` 和赛季数组即成功。

再检查受保护接口应当返回 `401/403`：

```powershell
Invoke-WebRequest 'https://api.cugbsoccer.xyz/api/v1/manage/audit-logs'
```

## 8. 设置第一位管理员

小程序登录一次后，在服务器查询用户：

```bash
docker compose --env-file .env.production -f compose.prod.yaml exec mysql \
  mysql -ucampus -p campus_football \
  -e "SELECT id, wechat_openid, nickname, role_code FROM app_user;"
```

把目标账号的 `wechat_openid` 填入 `.env.production` 的：

```text
ADMIN_WECHAT_OPENID=对应的-openid
```

重启 API：

```bash
docker compose --env-file .env.production -f compose.prod.yaml up -d api
```

## 9. 三级管理角色

V6 数据库迁移会自动为历史球队授权补充 `TEAM_MANAGER` 角色，不需要手工改库。

- 超级管理员：全部权限
- 授权管理员：除审计、评分审核和敏感词外的全部管理权限
- 领队：本队人员资料和本队出场阵容

球队授权页面可选择授权管理员或领队。授权管理员也可以继续授权，但不能审核评分或读取审计。

## 10. 配置微信小程序合法域名

微信公众平台 -> 开发管理 -> 开发设置 -> 服务器域名 -> request 合法域名，添加：

```text
https://api.cugbsoccer.xyz
```

不要添加路径，不要使用 `http://` 或 `localhost`。

当前小程序已经配置：

```ts
const TRIAL_API_BASE_URL = "https://api.cugbsoccer.xyz";
const RELEASE_API_BASE_URL = "https://api.cugbsoccer.xyz";
```

之后重新编译、上传体验版，用真机测试登录和公开接口。

## 11. 日常运维

查看状态：

```bash
docker compose --env-file .env.production -f compose.prod.yaml ps
```

查看日志：

```bash
docker compose --env-file .env.production -f compose.prod.yaml logs -f api
docker compose --env-file .env.production -f compose.prod.yaml logs -f caddy
```

更新代码后：

```bash
docker compose --env-file .env.production -f compose.prod.yaml up -d --build
```

备份数据库：

```bash
docker compose --env-file .env.production -f compose.prod.yaml exec mysql \
  mysqldump -uroot -p campus_football > backup-$(date +%F).sql
```

## 常见问题

### 域名打不开

依次检查：

1. DNS 的 A 记录是否指向正确公网 IP。
2. 阿里云安全组是否开放 80/443。
3. `docker compose ps` 中服务是否运行。
4. `caddy` 日志是否有证书申请错误。
5. 域名是否完成实名认证和备案要求。

### 小程序仍然请求 localhost

检查 `miniprogram/config/env.ts`，并在微信开发者工具重新编译。

### 登录 401

检查：

- AppID/AppSecret 是否匹配。
- 修改环境变量后是否重启 API。
- 微信服务器是否可访问。
- `WECHAT_APP_SECRET` 是否误填 AppID。

