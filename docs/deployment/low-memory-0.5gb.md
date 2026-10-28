# 0.5 GB 服务器实验部署

> 结论：这是低成本的实验方案，不是推荐的生产架构。当前系统不能在 0.5 GB 内存里同时运行 Java、MySQL 和 HTTPS 反向代理。

## 整体结构

```text
微信小程序
    ↓ HTTPS
https://api.cugbsoccer.xyz
    ↓
0.5 GB 服务器
├── Caddy       约 20–40 MB
└── Spring Boot 约 320–430 MB
    ↓ 公网连接
外部 MySQL
```

约束：

- 服务器上不使用 Docker。
- 服务器上不安装 MySQL。
- 必须把 MySQL 托管到外部服务。
- 必须增加至少 2 GB Swap。
- 只能承受非常低的访问量。
- 外部数据库故障会直接导致小程序不可用。

## 1. 购买服务器

选择：

```text
2 vCPU / 0.5 GiB
Ubuntu 24.04
20 GiB 系统盘
公网 IPv4
```

购买后安全组开放：

```text
22   SSH
80   HTTP
443  HTTPS
```

不要开放 `3306` 和 `8080`。

## 2. 增加 Swap

登录服务器后：

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

Swap 只能避免瞬间 OOM，速度会比内存慢很多。

## 3. 准备外部 MySQL

需要一个外部 MySQL 8 数据库。可以使用云厂商免费或低价 MySQL，具体额度和可用性以服务商当前页面为准。

创建数据库后，准备：

```text
主机名
端口
数据库名
用户名
密码
```

JDBC URL 示例：

```text
jdbc:mysql://HOST:PORT/DB_NAME?sslMode=REQUIRED&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8
```

不要把外部数据库密码提交到 Git。

## 4. 安装 Java 和 Caddy

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version
```

安装 Caddy 时使用 Caddy 官方软件源或静态二进制。确认：

```bash
caddy version
```

## 5. 在本机构建 JAR

不要在 0.5 GB 服务器上执行 Maven 构建。先在开发电脑上执行：

```powershell
.\mvnw.cmd -DskipTests package
```

产物位于：

```text
target\campus-football-api-0.0.1-SNAPSHOT.jar
```

上传到服务器：

```powershell
scp .\target\campus-football-api-0.0.1-SNAPSHOT.jar root@服务器IP:/tmp/campus-football.jar
```

## 6. 安装 API

在服务器执行：

```bash
sudo useradd --system --home /opt/campus-football --shell /usr/sbin/nologin campusfootball
sudo mkdir -p /opt/campus-football
sudo mv /tmp/campus-football.jar /opt/campus-football/campus-football.jar
sudo chown -R campusfootball:campusfootball /opt/campus-football
```

创建环境变量：

```bash
sudo cp deploy/low-memory/env.example /opt/campus-football/.env
sudo nano /opt/campus-football/.env
sudo chmod 600 /opt/campus-football/.env
sudo chown root:root /opt/campus-football/.env
```

安装 systemd：

```bash
sudo cp deploy/low-memory/campus-football.service /etc/systemd/system/campus-football.service
sudo systemctl daemon-reload
sudo systemctl enable --now campus-football
sudo systemctl status campus-football
sudo journalctl -u campus-football -f
```

启动失败时，首先检查：

```bash
free -h
sudo journalctl -u campus-football -n 200 --no-pager
```

如果出现 `OutOfMemoryError`，说明 0.5 GB 确实无法承载当前应用。

## 7. 安装 Caddy

```bash
sudo cp deploy/low-memory/Caddyfile /etc/caddy/Caddyfile
sudo systemctl enable --now caddy
sudo systemctl status caddy
```

阿里云 DNS 添加：

```text
A  api  服务器公网IP
```

验证：

```powershell
Invoke-RestMethod 'https://api.cugbsoccer.xyz/api/v1/seasons'
```

## 8. 微信后台

在微信公众平台 -> 开发管理 -> 开发设置 -> 服务器域名，添加：

```text
https://api.cugbsoccer.xyz
```

## 9. 这套方案的风险

- Java 启动时可能直接 OOM。
- 赛事期间并发稍高就会变慢。
- Swap 使用过多会导致接口超时。
- 外部 MySQL 增加网络故障点。
- 不适合长期正式运营。

如果健康启动并且只是少量用户查看，可以用于体验版验证。正式运营仍建议升级到 2 核 2 GB 或 2 核 4 GB，并把 MySQL 放回同一内网环境。
