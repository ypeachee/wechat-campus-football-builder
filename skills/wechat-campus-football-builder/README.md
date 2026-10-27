# WeChat Campus Football Builder

一个面向 AI 编程助手（Codex Skill）的领域工作流，用来构建、扩展、调试和发布校园足球微信小程序。

它不是页面模板，也不是可以直接复制的业务仓库，而是一套经过真实项目验证的决策约束：怎么设计赛事层级、怎么保证权限边界、怎么把比赛事件和出场记录分开、怎么处理小组赛/淘汰赛、怎么接入评分与内容审核，以及怎么把本地能跑的 Demo 变成可以上线的小程序。

## 适合什么场景

- 从零规划一个校园足球、校园联赛或杯赛小程序。
- 在已有 Spring Boot + MySQL 后端上增加比赛数据、阵容、统计或杯赛能力。
- 用原生微信小程序 + TypeScript 开发管理端和公开端。
- 给比赛增加评分、短评、敏感词过滤、审核和举报。
- 排查微信登录 `401`、合法域名、体验版接口地址、页面注册或包体积问题。

## 包含内容

```text
wechat-campus-football-builder/
├── SKILL.md
├── README.md
├── agents/
│   └── openai.yaml
└── references/
    ├── domain-model-and-invariants.md
    ├── engineering-playbook.md
    └── wechat-integration-and-release.md
```

- `SKILL.md`：AI 执行入口，包含产品不变量、实现顺序和验收要求。
- `domain-model-and-invariants.md`：赛事层级、比赛事件、阵容、积分榜、淘汰赛、评分审核。
- `engineering-playbook.md`：垂直切片、测试策略、常见故障和调试顺序。
- `wechat-integration-and-release.md`：登录、AppSecret、合法域名、UGC 合规和上线清单。

## 安装到 Codex

把整个 `wechat-campus-football-builder` 文件夹放到 Codex 的 skills 目录，例如：

```text
~/.codex/skills/wechat-campus-football-builder/
```

或者放在项目内的：

```text
<repo>/.codex/skills/wechat-campus-football-builder/
```

然后在新会话中直接提出相关任务，Codex 会根据 frontmatter 中的 description 决定是否加载。

## 使用示例

```text
使用 $wechat-campus-football-builder，
为现有校园足球小程序增加“按球队批量维护首发和替补”的功能。
要求首发从完整球队名单中勾选，替补区仍然显示首发但锁定，
并有后端事务、权限、锁定状态和集成测试。
```

```text
使用 $wechat-campus-football-builder，
检查当前小程序的微信登录 401 和体验版接口不可用问题，
按配置、域名、HTTPS、AppSecret 和日志顺序定位，不要先改业务代码。
```

```text
使用 $wechat-campus-football-builder，
设计一个杯赛的小组赛 + 单场淘汰赛数据模型和验收测试。
```

## 核心原则

- 积分榜从已完赛比赛动态计算，不维护第二份真相。
- 事件和出场记录分开；比分、事件、阵容各自有明确职责。
- 权限必须由后端按实时数据库状态判断。
- 锁定比赛的所有写操作都必须被统一拦截。
- 评分短评属于 UGC，未审核内容不能公开计入统计。
- AppSecret 只放后端，前端永远不保存密钥。
- 先完成垂直切片，再做完整回归。

## 验证

Skill 本身可使用 Codex `skill-creator` 提供的校验器检查：

```bash
python quick_validate.py skills/wechat-campus-football-builder
```

真实项目使用时应继续运行目标仓库自己的：

```bash
npm --prefix miniprogram run verify
./mvnw verify
```

以及至少一次后端启动和公开 API smoke test。

## 说明

示例中的 CUGB 校园足球、TDesign、Spring Boot 和微信开发者工具是具体项目的技术背景。复用到其他学校或赛事时，保留领域不变量和验证方法，按实际产品替换名称、样式、赛季规则和部署环境。
