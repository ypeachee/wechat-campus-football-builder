# 数据库备份与健康监控

## 1. 安装定时任务

服务器上进入项目目录后执行：

```bash
cd /opt/campus-football
sudo bash deploy/ops/install-cron.sh
```

安装后会创建：

- 每天 03:17 执行 MySQL 压缩备份
- 每 5 分钟检查 API、磁盘、内存和容器状态
- 日志自动轮转，保留 14 天

日志位置：

```text
/var/log/campus-football-backup.log
/var/log/campus-football-health.log
```

手动测试：

```bash
sudo /opt/campus-football/deploy/ops/mysql-backup.sh
sudo /opt/campus-football/deploy/ops/health-check.sh
```

默认本地备份目录：

```text
/opt/campus-football/backups
```

本地备份保留 14 天。备份文件格式：

```text
campus-football-YYYYMMDD-HHMMSS.sql.gz
```

## 2. 配置阿里云 OSS 异地备份

仅把备份留在同一台 ECS 上不够。建议创建独立的 OSS Bucket，并使用最小权限 RAM 用户。

1. 创建 OSS Bucket，例如 `cugb-football-backup`。
2. 创建 RAM 用户，只授予目标 Bucket 的上传和列举权限。
3. 在服务器安装并配置 `ossutil`。
4. 在 `/opt/campus-football/.env.production` 增加：

```text
OSS_BACKUP_URI=oss://cugb-football-backup/campus-football/backups
```

之后每日备份会自动上传到 OSS。没有配置 `OSS_BACKUP_URI` 时，只保留本地备份。

不要把 AccessKey 写进 Git、文章或小程序。

## 3. 外部可用性监控

至少配置一个外部监控服务，例如：

- UptimeRobot
- 阿里云云监控站点监控
- Better Stack

监控地址：

```text
https://api.cugbsoccer.xyz/api/v1/seasons
```

建议设置：

- 检查间隔：5 分钟
- 超时：15 秒
- 连续 2 次失败告警
- 告警渠道：邮件、短信或钉钉/飞书 Webhook
- 同时监控 HTTPS 证书到期时间

服务器内部的 `health-check.sh` 负责磁盘、内存和容器状态；外部监控负责判断公网是否可访问，两者不能互相替代。

## 4. 恢复备份

先确认目标数据库和备份文件，再执行：

```bash
cd /opt/campus-football
gunzip -c /opt/campus-football/backups/campus-football-YYYYMMDD-HHMMSS.sql.gz \
  | docker compose --env-file .env.production -f compose.prod.yaml exec -T mysql \
      sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot campus_football'
```

恢复前应先创建额外数据库或确认当前数据可丢弃。建议每季度做一次真实恢复演练，而不是只检查备份文件是否存在。

## 5. 验收

- [ ] 手动执行备份成功
- [ ] 本地生成 `.sql.gz` 文件
- [ ] OSS 中出现当日备份
- [ ] 健康检查返回 0
- [ ] 外部监控显示 API 正常
- [ ] 模拟 API 不可访问时能收到告警
- [ ] 已成功恢复一次备份
