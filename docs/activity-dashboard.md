# 活动看板改造说明

## 背景

原项目通过 AI 联网搜索抽取「活动结束时间」。现在数据源固定为 [torikushiii/hoyoverse-api](https://github.com/torikushiii/hoyoverse-api)，使用结构化日历、卡池和兑换码接口，不再需要搜索与抽取。

## 目标

1. 从 HoYoverse API 获取原神、绝区零、崩坏：星穹铁道活动。
2. 活动、卡池和兑换码落库，前端只读数据库。
3. 用户可以选择订阅哪些游戏；游客的订阅保存在本机浏览器，登录后跟随账号。
4. 用户注册、登录，完成状态按用户隔离。
5. 活动完成状态按单期记录，新一期开始后自动重置。

## 数据源

- `GET /mihoyo/{slug}/calendar?lang=zh-cn`
- `GET /mihoyo/{slug}/codes`
- `slug`：`starrail`、`genshin`、`zenless`

日历响应包含 `events`、`challenges` 和 `banners`。时间字段是 Unix 秒，可能为 `null` 或 `0`，入库前统一转成带 `Asia/Shanghai` 偏移的 `OffsetDateTime`，无效值置空。

## 设计决策

### 活动全局共享，完成状态按用户

活动本身对所有用户相同，只同步一份。`activities` 不存 `completed` 字段，完成状态放在 `user_activity_states`，唯一键 `(user_id, activity_id)`。

### 稳定 ID

- 活动：`SHA-256(game | event | API id | startAt)`。
- 挑战：`SHA-256(game | challenge | API id 或名称 | startAt)`。
- 卡池：`SHA-256(game | banner | id 或名称 + 首批内容 | startAt | endAt)`。
- 兑换码：`SHA-256(game | code | 兑换码文本)`。

API 原始 ID 为 `0` 或缺失时回退到名称。挑战 ID 稳定但期次会变化，因此始终把开始时间纳入哈希，保证新一期拿不到上一期的完成状态。

### 挑战分类

星铁按 `type_name` 映射：

| API 类型 | 看板分类 |
| --- | --- |
| `ChallengeTypeStory` | 虚构叙事 |
| `ChallengeTypeChasm` | 混沌回忆 |
| `ChallengeTypePeak` | 异相仲裁 |
| `ChallengeTypeBoss` | 末日幻影 |

原神使用 `ActTypeTower` 渊月螺旋、`ActTypeRoleCombat` 幻想真境剧诗；绝区零使用接口返回的挑战名称。

### 同步策略

- 启动时异步同步一次，不阻塞 Web 服务启动。
- 固定延迟 30 分钟同步一次。
- 支持 `POST /api/sync` 手动触发。
- 单进程内用 `AtomicBoolean` 防止并发重复同步。
- 日历与兑换码分开请求；任一部分失败都不会删除该部分旧数据。
- 接口返回空数组时保留旧记录，避免上游抖动清空看板。
- 成功同步后删除该游戏本次未出现的活动、卡池和兑换码，以及活动关联的用户状态。

### 字段映射

- 活动 `image_url` → `cover`。
- 封面、奖励图标和卡池图标通过 `/api/images` 同源代理，首次抓取后缓存到 `backend/data/images`，避免第三方图床的 CORP 拦截；Fandom 封面按 640px 宽请求缩略图，降低移动端首屏流量。
- 活动 / 挑战 `rewards` + `special_reward` → `rewards_json`，前端不再用正则从描述里猜奖励。
- 绝区零活动没有描述，用 `polychrome` 生成「菲林 ×N」奖励。
- 活动版本从同游戏最新卡池的 `version` 推导。
- 卡池的 `characters`、`weapons`、`light_cones`、`agents`、`w_engines` 合并为统一条目列表。

### 认证

- 密码：PBKDF2-HMAC-SHA256，210,000 次迭代，16 字节随机盐。
- 令牌：`base64url(payload).base64url(HMAC-SHA256)`，payload 包含用户 ID 和过期时间。
- 密钥：优先 `AUTH_SECRET`，为空时生成并持久化到 `data/auth-secret.key`。
- 拦截器保护 `/api/**`，放行注册、登录、健康检查。

## 接口与前端

- 登录页：用户名 + 密码，支持注册；token 存 `localStorage`。
- 看板：游戏标签、状态筛选、名称搜索、仅看未完成、完成标记乐观更新。
- 活动看板：封面是卡片最右侧的绝对定位背景层，不占栅格列；桌面宽 264px，移动端宽 104–132px，始终与卡片等高，并用渐变蒙版从卡片底色过渡到图片。封面优先加载，失败会重试、回退直连，最终显示统一占位图。
- 设置页：游戏订阅（游客写本机缓存，登录用户写账号）、同步状态、手动同步、账号信息。

## 与旧版的关系

旧版的 `trackers`、`facts`、`fact_history`、`ai_answers`、`snapshots`、`search_runs` 和 AI 搜索代码已移除。H2 文件中可能仍保留旧表，但新代码不会读写它们。
