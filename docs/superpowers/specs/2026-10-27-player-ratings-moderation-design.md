# 用户球员评分与内容审核设计

**日期：** 2026-10-27

**状态：** 方向已确认，待书面规格审核
**目标：** 为 CUGB 校园足球小程序增加虎扑式赛后球员评分、短评、敏感内容过滤和后台审核能力

## 1. 目标

登录用户可以在已完赛比赛中为出场球员提交“5 星评分”和可选短评。用户选择的整星数按 `星数 × 2` 换算成 10 分制，例如：

| 用户选择 | 提交分数 |
|---|---:|
| 1 星 | 2 分 |
| 2 星 | 4 分 |
| 3 星 | 6 分 |
| 4 星 | 8 分 |
| 5 星 | 10 分 |

公开页面展示平均分、评分人数、星级分布和审核通过的短评。短评必须经过本地敏感词检测和微信内容安全检查；疑似违规内容进入管理员审核。

## 2. 范围

### V1 包含

- 已完赛比赛的出场球员评分
- 登录用户每人每场每球员一条评分
- 1–5 整星输入，按 10 分制展示
- 可选 100 字短评
- 本地敏感词过滤
- 微信 `msgSecCheck` 内容安全检查
- 自动通过、待审核、拒绝、隐藏状态
- 用户自行修改评分一次
- 评价举报
- 管理员审核、隐藏、恢复
- 敏感词增删、启停和批量导入
- 球员赛季平均分
- 管理操作审计

### V1 不包含

- 回复、盖楼、点赞或“亮了”功能
- 图片、视频或语音评价
- 匿名评价
- 用户主页、关注或私信
- 实时聊天
- 自动情绪分析或大模型审核
- 球员评分之外的球队评分、裁判评分

## 3. 产品规则

### 3.1 评分资格

- 用户必须微信登录
- 比赛状态必须为 `FINISHED`
- 评分对象必须在该场比赛有出场记录
- 同一用户对同一场比赛的同一球员只能有一条评分
- 用户可以分别评价双方球队的多名球员
- 评分不要求填写短评

### 3.2 星级与分数

- 输入仅允许整数 `1–5`
- 数据库保存 `stars`
- API 同时返回 `stars` 和 `score = stars × 2`
- 平均分使用 `AVG(stars) × 2`，展示一位小数，例如 `8.4/10`
- 星级分布按 1–5 星分别统计人数
- 平均分不显示到第二位小数

### 3.3 修改规则

- 首次提交后 24 小时内允许修改一次
- 修改次数记录在评分记录中
- 已完成一次修改后锁定，用户端提示“评分已修改过，不能再次修改”
- 管理员仍可隐藏或调整审核状态
- 评分记录修改前会写入审计日志

### 3.4 短评

- 可为空
- 填写时长度为 1–100 个字符
- 禁止 URL、二维码联系方式和其他明显导流内容
- 同一用户连续提交评分受频率限制
- 短评通过审核后公开显示，最新评价优先
- 每名球员每页最多展示 20 条公开短评

## 4. 数据模型

### 4.1 `player_match_rating`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| fixture_id | BIGINT | 比赛 |
| player_id | BIGINT | 被评分球员 |
| team_id | BIGINT | 球员所属球队 |
| user_id | BIGINT | 评分用户 |
| stars | TINYINT | 1–5 |
| comment | VARCHAR(100) | 可选短评 |
| status | VARCHAR(20) | `VISIBLE` / `PENDING` / `REJECTED` / `HIDDEN` |
| moderation_reason | VARCHAR(100) | 用户可见的审核原因 |
| edits_used | TINYINT | 已修改次数，最大 1 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

约束：

- `UNIQUE (fixture_id, player_id, user_id)`
- 评分用户必须为有效登录用户
- 比赛、球员和球队必须相互匹配
- 只有 `VISIBLE` 评分计入公开平均分和人数

### 4.2 `rating_report`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| rating_id | BIGINT | 被举报评分 |
| reporter_user_id | BIGINT | 举报用户 |
| reason | VARCHAR(100) | 举报原因 |
| status | VARCHAR(20) | `OPEN` / `REVIEWED` / `DISMISSED` |
| created_at | DATETIME | 举报时间 |
| reviewed_at | DATETIME | 处理时间 |

约束：

- `UNIQUE (rating_id, reporter_user_id)`
- 用户不能举报自己的评分
- 同一用户对同一评分只能举报一次

### 4.3 `sensitive_term`

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| term | VARCHAR(64) | 敏感词 |
| category | VARCHAR(32) | 分类，例如辱骂、广告、违法 |
| action | VARCHAR(10) | `BLOCK` / `REVIEW` |
| enabled | TINYINT(1) | 是否启用 |
| created_by | BIGINT | 管理员 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

约束：

- `UNIQUE (term)`
- 单次批量导入最多 5,000 个词
- 词条变更后刷新敏感词 DFA
- 管理操作写入审计日志

### 4.4 聚合策略

V1 不建立评分汇总表：

- 单场比赛的所有球员聚合一次查询完成
- 球员赛季平均分按 `player_id + season_id` 聚合
- 只统计状态为 `VISIBLE` 的评分
- 避免逐球员查询造成 N+1

## 5. 内容审核

### 5.1 本地敏感词检测

使用 GitHub 项目 [houbb/sensitive-word](https://github.com/houbb/sensitive-word)：

- Maven：`com.github.houbb:sensitive-word:0.29.5`
- 许可证：Apache-2.0
- 负责 DFA 敏感词匹配、全半角处理和常见变体处理
- 数据库 `sensitive_term` 中的词条作为运行时补充
- `BLOCK` 词命中后直接拒绝
- `REVIEW` 词命中后进入 `PENDING`

短评提交前先进行规范化：

- 去除多余空格和标点
- 全角转半角
- 统一大小写
- 合并连续重复字符
- 保留原文用于展示和审计

### 5.2 微信内容安全

非空短评还必须调用微信内容安全接口：

- 使用后端缓存的 `access_token`
- 使用当前用户 OpenID
- 请求内容为待发布短评
- 微信判定风险时进入 `PENDING`
- 微信接口不可用或超时：生产环境进入 `PENDING`，不能直接公开
- 本地开发环境允许通过测试开关模拟审核结果，但测试开关不得在生产启用

### 5.3 状态流转

```text
无短评 / 检测通过：VISIBLE
命中 REVIEW 或微信风险：PENDING
命中 BLOCK：REJECTED
管理员隐藏：HIDDEN
管理员恢复：VISIBLE
```

- `PENDING`、`REJECTED`、`HIDDEN` 不计入公开均分
- 作者本人可以查看自己的 `PENDING/REJECTED` 记录和原因
- 管理员可以把 `PENDING` 改为 `VISIBLE/REJECTED`
- 管理员可以把 `VISIBLE` 改为 `HIDDEN`
- 所有审核动作写入审计日志

## 6. API 设计

### 6.1 公共接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/fixtures/{fixtureId}/player-ratings` | 返回出场球员的平均分、人数、星级分布和公开短评 |
| GET | `/api/v1/fixtures/{fixtureId}/players/{playerId}/ratings` | 分页返回该球员公开短评 |
| GET | `/api/v1/players/{playerId}/rating-summary` | 返回球员赛季平均分 |

### 6.2 登录用户接口

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/v1/fixtures/{fixtureId}/players/{playerId}/rating` | 提交或修改评分 |
| GET | `/api/v1/fixtures/{fixtureId}/players/{playerId}/rating/me` | 查看自己的评分和审核状态 |
| POST | `/api/v1/ratings/{ratingId}/report` | 举报公开短评 |

评分请求：

```json
{
  "stars": 4,
  "comment": "防守稳健，补位及时"
}
```

### 6.3 管理员接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/ratings` | 按 `PENDING/REPORTED/HIDDEN` 筛选 |
| PUT | `/api/v1/admin/ratings/{ratingId}/status` | 通过、拒绝、隐藏或恢复 |
| GET | `/api/v1/admin/sensitive-terms` | 查询敏感词 |
| POST | `/api/v1/admin/sensitive-terms` | 新增敏感词 |
| PUT | `/api/v1/admin/sensitive-terms/{id}` | 修改分类、策略或启停 |
| DELETE | `/api/v1/admin/sensitive-terms/{id}` | 删除敏感词 |
| POST | `/api/v1/admin/sensitive-terms/import` | 批量导入敏感词 |

## 7. 小程序界面

### 7.1 比赛详情

在比分板下方新增“球员评分”区域：

- 分主客队展示有出场记录的球员
- 每名球员显示：
  - 平均分，例如 `8.4/10`
  - 五颗星视觉
  - 评分人数
  - 当前用户自己的评分
- 点击球员打开评分弹层
- 展示审核通过的公开短评摘要
- 提供“查看全部评价”入口

### 7.2 评分弹层

- 5 颗可点击的整星
- 实时显示对应分数：`星数 × 2`
- 可选 100 字短评输入框
- 显示剩余字数
- 提交中禁用重复点击
- 审核待处理时提示“评价已提交，审核通过后公开展示”
- 违规时显示明确原因
- 已修改过的评分显示锁定状态

### 7.3 球员详情

- 展示赛季平均评分
- 展示评分人数和五颗星
- 展示最近审核通过的短评
- 提供举报入口

### 7.4 管理台

新增“评分审核”页面：

- 待审核、被举报、已隐藏筛选
- 查看评分、短评、球员、比赛和作者昵称
- 通过、拒绝、隐藏、恢复
- 查看举报次数和原因
- 管理敏感词、批量导入、启停和修改处理策略

## 8. 安全与合规

用户评分和短评属于用户生成内容。上线前必须完成：

- 在小程序基本信息中如实说明提供赛后评价服务
- 更新小程序简介，不能继续声明“不提供用户发布内容服务”
- 完善隐私保护指引，说明评论、用户身份和审核数据用途
- 提供评价举报入口
- 提供管理员审核和下线能力
- 保留审核和处置记录
- 不在公开接口返回用户 OpenID
- 不展示用户头像以外的非必要个人信息
- 使用微信内容安全接口作为生产环境必需环节
- 禁止在客户端硬编码 AppSecret 或 access_token

### 8.1 频率限制

- 每个用户每 10 分钟最多提交 20 次评分
- 每个用户每 10 分钟最多举报 10 次
- 超限返回 `429 RATE_LIMITED`
- 同一目标和同一用户的唯一约束始终生效

## 9. 测试

### 后端单元与集成测试

- 只有完赛比赛可以评分
- 未登录用户不能评分
- 未出场球员不能评分
- 每人每场每球员只能有一条评分
- 1–5 星校验和 2–10 分换算
- 24 小时内只能修改一次
- 平均分只统计 `VISIBLE`
- 星级分布统计正确
- 敏感词 `BLOCK` 和 `REVIEW` 策略正确
- 微信内容安全风险会进入 `PENDING`
- 微信服务异常时生产环境不能自动公开
- 管理员通过、拒绝、隐藏、恢复流程正确
- 用户不能重复举报同一评价
- 管理审核和敏感词变更写入审计日志
- 频率限制返回 429
- 公开接口不返回 OpenID

### 小程序测试

- 五颗星映射 2–10 分
- 评分弹层校验和提交参数
- 待审核和拒绝状态文案
- 平均分与人数展示
- 举报流程
- 管理审核页面权限门禁

### 验收场景

1. 两名登录用户分别给同一名球员 4 星和 5 星
2. 公开平均分显示 `9.0/10`
3. 一名用户提交含敏感词短评，进入待审核
4. 管理员通过后短评公开，评分计入均分
5. 另一名用户举报短评，管理员隐藏后短评和评分从公开统计中移除
6. 未登录用户只能查看，不能评分或举报

## 10. 验收标准

- 比赛详情可完整完成评分、短评、修改和举报
- 5 星输入准确换算为 10 分制
- 敏感短评无法直接公开发布
- 微信内容安全异常时不会漏放内容
- 管理员可以处理待审核和举报内容
- 球员赛季评分聚合正确
- 公开接口不泄露 OpenID
- 评分功能上线后，小程序基本信息、隐私指引和审核说明同步更新

## 11. 调研参考

- [houbb/sensitive-word](https://github.com/houbb/sensitive-word)：Java DFA 敏感词引擎，Apache-2.0
- [konsheng/Sensitive-lexicon](https://github.com/konsheng/Sensitive-lexicon)：中文敏感词库，MIT
- [discourse-kuu-ratings](https://github.com/DAVVVVVVVVVVVID/discourse-kuu-ratings)：虎扑式评分的交互参考
- [KiritoXDone/Miaopu](https://github.com/KiritoXDone/Miaopu)：虎扑式体育评分产品参考
- [Tencent/tdesign-miniprogram](https://github.com/Tencent/tdesign-miniprogram)：小程序评分控件与视觉参考
