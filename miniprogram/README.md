# 微信小程序

CUGB 校园足球原生微信小程序，使用 TypeScript、TDesign MiniProgram 和现有校园足球 API。

## 环境

- Node.js 20+
- npm
- 微信开发者工具

## 安装

```powershell
npm install
npm run verify
```

## 配置

1. 用微信开发者工具打开当前 `miniprogram/` 目录。
2. 将 `project.config.json` 中的 `touristappid` 替换为真实小程序 AppID。
3. 按需修改 `config/env.ts`：
   - `develop`：本地或测试 API
   - `trial`：体验版 API
   - `release`：生产 API
4. 在微信后台配置 HTTPS request 合法域名。
5. 在开发者工具执行“工具 -> 构建 npm”。

开发环境默认请求 `http://127.0.0.1:8080`，开发者工具需要关闭合法域名校验。

## 登录

小程序使用 `wx.login` 获取 code，并调用后端：

```text
POST /api/v1/auth/wechat-login
```

真实登录需要：

- 小程序 AppID 与后端 `WECHAT_APP_ID` 一致
- 后端配置对应的 `WECHAT_APP_SECRET`
- 后端可通过公网或开发者工具代理访问

普通浏览页不需要登录。

## 验证

```powershell
npm run typecheck
npm test
npm run verify
```

微信开发者工具检查：

1. 构建 npm。
2. 编译项目。
3. 检查五个 Tab 和详情页。
4. 用真实 AppID 验证登录与角色。
5. 按 `docs/acceptance/wechat-miniprogram-acceptance.md` 执行管理端验收。

## 常见问题

### 无法构建 npm

确认在 `miniprogram/` 下执行过 `npm install`，并在开发者工具中使用“工具 -> 构建 npm”。

### CLI 调用提示服务端口关闭

打开微信开发者工具：设置 -> 安全设置 -> 开启服务端口。CLI 调用不是运行小程序的必要条件。

### 登录失败

优先检查：

- AppID 是否匹配
- AppSecret 是否配置在后端
- 后端微信登录接口是否可访问
- 开发者工具是否使用正确环境
