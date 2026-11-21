# 泥地soccer Android 壳

这是现有校园足球网站的 Android WebView 客户端。

## 下载

- [泥地soccer-v1.1.0.apk](dist/muddy-soccer-v1.1.0.apk)
- [SHA-256 校验值](dist/SHA256SUMS.txt)

## 当前配置

- 应用名：`泥地soccer`
- 包名：`xyz.cugbsoccer.muddy`
- 最低系统：Android 8.0（API 26）
- 默认站点：`https://api2.cugbsoccer.xyz/`
- 版本：`1.1.0`

站点地址位于 `app/src/main/res/values/strings.xml` 的 `app_url`。如果生产域名变化，只需要修改这里并重新打包。

## 已实现

- WebView 加载现有网站，页面和 API 同域
- 系统返回键与网站路由历史
- 下拉刷新
- 加载进度条
- 断网、服务器错误与证书错误提示页
- 网页文件选择（CSV/XLSX 等）
- 网页下载保存到系统 `Download/泥地soccer/`
- 外部网站使用系统浏览器打开
- Android 12 启动页与自适应图标

## 本地构建

首次创建签名：

```powershell
.\android\create-keystore.ps1
```

构建正式 APK：

```powershell
.\android\build-apk.ps1
```

构建调试 APK：

```powershell
.\android\build-apk.ps1 -DebugBuild
```

APK 会复制到：

- `output/android/泥地soccer-v1.1.0.apk`
- `android/dist/muddy-soccer-v1.1.0.apk`

## 签名备份

`android/keystore/` 和 `android/keystore.properties` 已被 Git 忽略。必须单独备份这两个位置；以后覆盖安装升级时必须使用同一签名。

## 说明

该 APK 是网站客户端，不是离线应用。赛事数据和管理功能仍然依赖服务器在线可用。