# 今天也在米哈打工 · 游戏活动看板

从 [torikushiii/hoyoverse-api](https://github.com/torikushiii/hoyoverse-api) 同步原神、绝区零、崩坏：星穹铁道三个游戏的活动、挑战、卡池和兑换码，写入数据库；前端只读数据库渲染活动看板、倒计时和完成状态。

支持注册登录。游客可以浏览全部公开数据，并把游戏订阅保存在本机；登录后游戏订阅和「已完成」标记按用户隔离。

## 核心链路

```text
HoYoverse API（starrail / genshin / zenless）
  → ActivitySyncService 定时同步（默认 30 分钟）
  → activities / game_banners / game_codes 按稳定 ID upsert
  → 前端读取 /api/activities、/api/banners、/api/codes
  → 活动叠加当前用户的 user_activity_states
  → 活动看板、挑战看板，以及独立的兑换码页与卡池页
```

默认使用公开实例 `https://api.ennead.cc/mihoyo`，也可以通过 `HOYOVERSE_BASE_URL` 指向自建实例。

## 功能

- 三个游戏可单独订阅，看板只显示订阅范围内的数据；游客订阅保存在 `localStorage`，登录后改用账号订阅
- 游客无需登录即可查看全部活动、挑战、兑换码和卡池，也可以调整订阅；完成勾选与「仅看未完成」仅登录后可用
- 活动按「进行中 / 即将结束 / 未开始 / 已结束」筛选
- 活动封面是卡片右侧的背景层，不占布局列，移动端宽度 104–132px 并与卡片等高
- 倒计时按北京时间计算，48 小时内结束的活动标红
- 星铁挑战按虚构叙事、混沌回忆、异相仲裁、末日幻影汇总，原神幽境危战也按周期归类
- 完成状态按「用户 × 活动期次」保存，下一期开始后自动回到未完成
- 兑换码与卡池分别是独立页面，按当前订阅游戏分组
- 活动封面走 `/api/images` 同源代理并缓存到 `backend/data/images`，Fandom 封面按 640px 宽取缩略图，避免跨域拦截并减少移动端首屏流量
- 兑换码只展示 API 标记为可用的条目，支持一键复制
- 设置页展示每个游戏的数据同步状态，可手动触发同步
- 设置页支持修改密码；Chrome 的密码泄露提醒由浏览器触发，换用未泄露的密码即可
- 注册时生成一次性恢复码，忘记密码可以用「用户名 + 恢复码」重置；设置页可重新生成
- 设置页支持跟随系统 / 浅色 / 深色三种外观，切换时带平滑过渡
- 设置页支持永久删除账号，订阅、恢复码与完成记录会一起清除
- 修改密码或重置密码后，旧登录令牌立即失效
- 支持 PWA 添加到主屏幕；HTTPS 下注册 service worker，离线时仍可打开应用外壳
- 服务端每天自动备份数据库、`app.env` 与密钥，默认保留 30 天
- 登录、注册、重置密码和图片代理在 Nginx 层按 IP 限速
- 桌面滚轮和移动端触摸都使用 Lenis 阻尼；移动端拖动 1:1 跟手，松手惯性由本地速度采样控制，动效规范见 `docs/motion-system.md`

## 数据模型

| 表 | 说明 |
| --- | --- |
| `users` | 账号、PBKDF2 密码哈希、注册与最后登录时间 |
| `user_games` | 用户订阅的游戏 key |
| `activities` | 活动与挑战，含奖励 JSON、挑战类型和期次时间 |
| `game_banners` | 卡池，含版本、类型、起止时间和角色 / 光锥 / 音擎 JSON |
| `game_codes` | 兑换码，含可用状态和奖励 JSON |
| `user_activity_states` | 用户 × 活动的完成状态，唯一约束 `(user_id, activity_id)` |
| `game_sync_states` | 每个游戏的最近同步时间、状态、错误、活动数与版本 |

活动主键由 `game + event/challenge + API id（或名称）+ 开始时间` 生成稳定哈希。挑战活动使用 `challenge_stage` 标记所属周期类别，完成状态只作用于当前这一期。

## API

公开 GET 接口允许游客访问。保存订阅、标记完成、手动同步和 `/api/auth/me` 需要 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册，返回 token 和用户信息 |
| `POST` | `/api/auth/login` | 登录 |
| `POST` | `/api/auth/reset-password` | 用用户名和恢复码重置密码，无需登录 |
| `PUT` | `/api/auth/password` | 修改密码（需登录），body：`{"current_password_ciphertext":"…","new_password_ciphertext":"…"}` |
| `POST` | `/api/auth/recovery-code` | 生成新的恢复码（需登录 + 当前密码） |
| `DELETE` | `/api/auth/account` | 删除账号（需登录 + 当前密码） |
| `GET` | `/api/auth/me` | 当前用户（需登录） |
| `GET` | `/api/games` | 三个游戏、订阅状态、活动数与同步状态；游客全部可选 |
| `PUT` | `/api/games` | 保存订阅（需登录），body：`{"games":["ys","zzz"]}` |
| `GET` | `/api/activities?game=sr` | 活动列表；游客返回全部游戏，登录后按订阅 |
| `PATCH` | `/api/activities/{id}` | 标记完成（需登录），body：`{"completed":true}` |
| `GET` | `/api/banners?game=ys` | 卡池列表；游客返回全部游戏，登录后按订阅 |
| `GET` | `/api/codes?game=sr&active=true` | 兑换码列表；游客返回全部游戏，默认只返回可用码 |
| `GET` | `/api/images?url=…` | 图片同源代理，仅允许 Fandom / HoYoverse 图床，本地磁盘缓存 |
| `POST` | `/api/sync` | 立即同步一次并返回最新游戏状态（需登录） |
| `GET` | `/api/health` | 健康检查，无需登录 |

活动响应示例：

```json
{
  "id": "db3fc6ffd2f462bc10f61de75623aa9a",
  "game": "sr",
  "game_label": "崩坏：星穹铁道",
  "name": "虚构叙事•取象为文",
  "kind": "虚构叙事",
  "description": null,
  "start_at": "2026-10-19T11:00:00+08:00",
  "end_at": "2026-11-30T11:00:00+08:00",
  "cover": null,
  "rewards": [
    { "name": "星琼", "amount": 900, "icon": "https://…", "rarity": "5" }
  ],
  "version": "4.7",
  "version_name": null,
  "challenge_stage": "虚构叙事",
  "status": "active",
  "completed": false,
  "completed_at": null
}
```

## 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `HOYOVERSE_SYNC_ENABLED` | `true` | 是否启用定时同步 |
| `HOYOVERSE_BASE_URL` | `https://api.ennead.cc/mihoyo` | HoYoverse API 基础地址 |
| `HOYOVERSE_GAMES` | `sr,ys,zzz` | 需要同步的游戏 |
| `HOYOVERSE_LOCALE` | `zh-cn` | 日历接口语言 |
| `HOYOVERSE_SYNC_INTERVAL_MS` | `1800000` | 同步间隔，默认 30 分钟 |
| `HOYOVERSE_SYNC_INITIAL_DELAY_MS` | `15000` | 启动后首次定时同步延迟 |
| `HOYOVERSE_TIMEOUT_SECONDS` | `30` | 单次请求超时 |
| `HOYOVERSE_MANUAL_SYNC_COOLDOWN_MS` | `120000` | 手动同步的最小间隔，避免重复触发上游 |
| `AUTH_SECRET` | 空 | token 签名密钥；为空时自动生成到 `backend/data/auth-secret.key` |
| `AUTH_TOKEN_TTL_HOURS` | `168` | 登录有效期，默认 7 天 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | 允许的前端来源，逗号分隔 |
| `IMAGE_CACHE_DIR` | `./data/images` | 活动封面、奖励与卡池图标的本地缓存目录 |
| `IMAGE_CACHE_MAX_BYTES` | `2147483648` | 图片缓存总容量上限，超过后按最旧文件清理到 80% |
| `IMAGE_CACHE_MAX_AGE_DAYS` | `30` | 图片缓存最长保留天数 |
| `H2_CONSOLE_ENABLED` | `false` | 是否开启 H2 控制台；生产保持关闭 |
| `BACKUP_ENABLED` | `true` | 是否启用每日数据备份 |
| `BACKUP_DIR` | `./backups/data` | 备份目录；生产指向 `/www/wwwroot/ai-tracking/backups/data` |
| `BACKUP_RETENTION_DAYS` | `30` | 备份保留天数 |
| `ALERT_WEBHOOK_URL` | 空 | 可选，监控脚本发现异常时向该地址 POST `{"text":"..."}` |

## 本地运行

```powershell
cd backend
mvn spring-boot:run

cd ..\frontend
npm install
npm run dev
```

前端开发服务器运行在 `http://localhost:5173`，`/api` 由 Vite 代理到 `http://localhost:8080`。

生产构建预览使用 `npm run preview`（端口 `4173`）。如需在预览环境登录，把 `http://localhost:4173` 加入 `CORS_ALLOWED_ORIGINS`。

数据库是 H2 文件库 `backend/data/tracker.mv.db`。`ddl-auto: update` 会自动新增表与字段。旧 SRA 活动会在第一次新同步时按新哈希清理。

## 测试与构建

```powershell
cd backend
mvn -q test
mvn -q -DskipTests package

cd ..\frontend
npm run build
```

## 动效

入场、按钮、Tab、卡片与面板共用 `frontend/src/motion.css` 的时长和曲线令牌；滚动阻尼、入场变体和降级策略记录在 `docs/motion-system.md`。`prefers-reduced-motion` 下关闭平滑滚动与全部入场动画。

## 数据备份与恢复

后端启动时和每天 03:20（Asia/Shanghai）执行一次备份，生成 `data-<时间>/tracker.zip`，并复制 `app.env`、RSA 私钥和自动生成的 `auth-secret.key`。备份目录权限为 `700`，文件权限为 `600`。

恢复步骤：

1. `systemctl stop ai-tracking-backend`
2. 解压 `tracker.zip` 到 `backend/data`
3. 恢复同目录下的 `app.env` 和密钥文件
4. `systemctl start ai-tracking-backend`

`BACKUP_DIR` 可以指向挂载的异地磁盘或同步目录；只放在同一块云硬盘上无法防止整机故障。

## 已知限制

- 同步是单进程内的互斥开关，多实例部署需要分布式锁。
- 暂不支持邮箱找回密码和管理员后台；恢复码需要用户自己保存。
- HoYoverse API 的 `codes` 接口奖励文本以英文为主，本服务原样保存。
- 图片代理仅放行 `static.wikia.nocookie.net` 与 `*.hoyoverse.com` / `*.hoyolab.com`，其他来源仍由浏览器直连。
- 版本号从同游戏最新卡池推导，API 日历本身不直接提供版本。

## 部署

生产部署脚本见 `deploy/ai-deploy.ps1`，脚本头部包含服务器路径、systemd 服务名、宝塔站点名和备案限制说明。

```powershell
pwsh -NoProfile -File .\deploy\ai-deploy.ps1
```

脚本会完成前端和后端构建、上传、备份、替换、重启、健康检查，并在失败时回滚上一版 jar 和前端文件。部署记录写入服务器 `/www/wwwroot/ai-tracking/RELEASES.log`。同时会写入 Nginx 限速与安全响应头、配置应用日志轮转，并安装每 5 分钟执行一次的本地健康检查。

服务器 SSH 已关闭密码登录和 root 登录，只允许密钥登录；日常运维请使用 `ubuntu` 用户和 `~/.ssh/id_ai_tracking2`。如果需要在 `app.env` 增加 `ALERT_WEBHOOK_URL`，监控脚本会在健康检查、磁盘或证书异常时向该地址发送 `{"text":"..."}`。

生产入口使用 HTTPS 高位端口，不再在 80 端口提供页面：

```text
https://106.55.36.254:48317/
```

- 腾讯云安全组需要放行 `TCP 48317`；80 端口只保留 ACME 证书验证路径。
- 80 的验证目录是 `/var/www/acme`，宝塔站点里的应用 include 已停用，避免应用再次出现在 80。
- 应用的 Nginx 片段独立放在 `/www/server/panel/vhost/nginx/snippets/ai-tracking-app.conf`，不再放回宝塔扩展目录，防止面板重新把应用路由注入 80 站点。
- TLS 使用 Let's Encrypt 的 IP 证书（`shortlived` profile，6 天有效期），由服务器上的 snap certbot 自动续期并重载 Nginx。
- 换端口或换域名时，必须把新地址加入 `backend/app.env` 的 `CORS_ALLOWED_ORIGINS` 并重启后端，否则注册/登录会返回 403。
- `metahubgo.cn` 完成备案前不能作为公开入口，备案后可以改为域名证书并换回 443。

