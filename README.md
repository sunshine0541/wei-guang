# 微光 · 社区互助服务系统

> 面向社区老年居民与热心邻居的互助小程序：老人一键发布小事求助，邻居接单远程/上门/外出帮忙，聊天协作、双确认完成，配微光值·荣誉积分·光语情感化体系、双向防诈与社区 UGC。
> 毕业设计 MVP（tag `v1.0-mvp`）；2026-10 体验改进（批 1–4F）+ v1.3 社区 2.0 / AI 审核 / 看板 / 分页（T21–T28）已完成；T31 新增 AI 助手（问答 + 确认后自动发布求助）。设计依据：《微光-详细设计文档v1.2》《微光-执行文档v1.2》。

## 一、仓库结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 3.5（Java 17）后端：REST + WebSocket + 定时任务；MySQL 8.4 / Redis 7.4；含 `docker-compose.yml`（MySQL + Redis + 后端容器一键起） |
| `front/miniapp/` | uni-app + Vue3 微信小程序端（唯一用户端形态） |
| `admin-web/` | Vue3 + Element Plus 管理后台（概览 / 订单 / 协商取消裁决 / 上报处置 / 用户 / 配置 / 社区审核 / 申诉反馈） |
| `sql/` | `schema.sql` 全量建表（26 张表）；`seed.sql` 拟真演示数据（自包含一键重置）；`reset.sql` ≡ seed.sql |
| `scripts/` | `seed-check`（24 条不变量）、`demo-reset`、`smoke`、`run-demo` 四个 PowerShell 脚本 |
| `docs/` | 《实现偏差清单》《验收走查记录》《seed契约》《论文研究边界与前提假设》 |
| 根目录两篇 v1.2 文档 | 详细设计 / 执行文档（任务卡与验收判据） |

## 二、环境要求

- JDK 17+、Maven 3.8+
- Node 18+（小程序端 / 管理端）
- Docker Desktop（MySQL 8.4、Redis 7.4）
- 微信开发者工具（稳定版）

## 三、快速开始

### 1. 中间件（MySQL + Redis）

```powershell
cd backend
copy .env.example .env      # 按注释填入密钥与连接（.env 不进仓库）
docker compose up -d mysql redis
```

### 2. 建表与演示数据

```powershell
# 方式 A（推荐，规避 Windows 管道中文编码问题）
docker cp sql\schema.sql weiguang-mysql:/tmp/schema.sql
docker exec -i weiguang-mysql mysql --default-character-set=utf8mb4 -uroot -p weiguang -e "source /tmp/schema.sql"
# 依次 source seed.sql（首次）或 reset.sql（随时一键重置，会清空业务数据）
```

> 也可在仓库根目录用本地 mysql 客户端：`Get-Content sql\schema.sql | mysql -u root -p weiguang`。
> 数据库口令取自 `backend/.env` 的 `MYSQL_PASSWORD`（compose 缺省 `weiguang123`，仅演示环境使用；正式环境请改用专用低权限账号）。
> 一键复位 + 自检：`powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1`（执行 `reset.sql` 后自动跑 24 条 seed 不变量断言）。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run          # 开发方式
# 或打包后走容器：mvn -DskipTests package && docker compose up -d backend
```

- 健康检查：http://localhost:8080/actuator/health （只返回 `UP/DOWN` 状态，DB/Redis 明细已关闭）
- 集成测试（Testcontainers，需 Docker）：`mvn "-Dtest=AcceptConcurrencyTest,PointConcurrencyTest,ConfirmRejectAppealTest,OrderFullFlowTest,CancelNegotiationFlowTest,CommunityReportFlowTest,AssistantFlowTest" test`
- 接口文档：http://localhost:8080/swagger-ui.html （springdoc）
- 首次启动自动按 `.env` 初始化管理员账号（`ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD`）
- 后端默认监听 `0.0.0.0:8080`，真机可通过电脑局域网 IP 访问

> **注意**：`mvn spring-boot:run` **不会自动读取 `.env`**。开发时请使用 IDE 的 EnvFile 插件，或在 PowerShell 中先读取 `backend/.env` 并把变量注入当前进程；容器方式（`docker compose up -d backend`）由 compose 注入，不受影响。

### 4. 小程序端

```powershell
cd front/miniapp
npm install
npm run dev:mp-weixin        # 产物：dist/dev/mp-weixin
```

微信开发者工具 → **导入项目** → 目录选择 `front/miniapp/dist/dev/mp-weixin`：

- **编译模式：普通编译；启动页面：`pages/index/index`；无启动参数**
- 右上角「详情 → 本地设置」勾选 **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**
- 后端地址取自构建期环境变量 `VITE_API_HOST`（模板 `front/miniapp/.env.example`）：未配置时默认 `http://localhost:8080`（仅开发者工具模拟器可用）；真机 / 换网络见「常见问题」

### 5. 管理后台

```powershell
cd admin-web
npm install
npm run dev                  # http://localhost:5174（/admin 由 Vite 代理到 :8080）
```

> 生产部署到静态站点时，可用 `VITE_API_BASE` 指定后端基址（如 `https://api.example.com`）；不配置则沿用同源 `/admin` 反代。

## 四、演示账号（账密登录，密码统一 `wg123456`，账号即本人手机号）

| 账号 | 昵称 | 用途 |
| --- | --- | --- |
| `13867429153` | 李秀英 | 受助主线（68 岁年长模式，画像/地址/紧急联系人完整） |
| `15867132409` | 陈志远 | 助人主线（接单、聊天、结算侧） |
| `13957068214` | 王德明 | 年龄未填（默认策略与补填提示） |
| `18652970318` | 赵晓琳 | 微光值 50（低于门槛，仅可接远程） |
| 背景邻居 | 张桂花 / 刘建军 / 周慧珍 / 吴国强 / 孙丽华 | `13385649271` / `13745286109` / `15162384756` / `18245037619` / `13072849563` |

管理员账号见 `backend/.env`（`ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD`）。

> 旧登录态失效：数据库重置后请在小程序内退出登录或开发者工具「清缓存 → 清除数据缓存」后再登录。

## 五、演示路径（详设第 10 章；登录方式按账密更新）

演示前执行 `sql/reset.sql` 回到干净状态（会清空全部业务数据，属预期）。

1. **李秀英**登录 → 首页：通知横幅（"有人来接您的求助啦"，3 条聚合）、紧急电话卡、求助列表；底部「社区」：荣耀榜（刘建军 4 次居首）、善意回流计数、今日新求助数、邻里动态（最近完成的互助）
2. 待响应列表：急标"厨房水管一直渗水"置顶；查看详情 → 地址分级（远程无地址 / 外出仅集合点 / 上门门牌接单后才解锁）
3. **陈志远**接"卫生间的灯不亮了" → 聊天页：照护提示卡"腿脚不便，需要扶一下"、快捷短语、图片
4. 陈志远「开始帮忙」→ 李秀英收到"邻居已出发" → 陈志远标记完成 → 李秀英确认 → 结算：微光值 +2、积分 +10、故事卡生成
5. 李秀英写评价；用 AI「起个头」写感谢信并发送；查看故事卡
6. 分支·体验补充：
   - **发布三页**：首页「我需要帮助」→ 模板选择 → 自由描述（AI 建议）→ 表单；中途退出再进入会自动恢复草稿；标签可选「跑腿 / 教学」，也可填一个 ≤10 字的自定义标签。
   - **通知中心**：确认完成或收到消息后，通知按「今天 / 更早」分组；聊天消息聚合为「有 N 位邻居给您发来 M 条消息」，点卡片只展开，点联系人才进入聊天；左滑可删除，底部「消息」有未读红点。
   - **申诉凭证**：完成或取消后 7 天内可发起申诉并上传一张凭证；处理中的申诉/反馈可撤回。
   - **社区页**：底部第 3 个 tab；顶部「助人之星 / 今日新求助 / 已成为微光」三块，下方三 Tab（推荐 / 最新 / 互助故事）；支持发帖、点赞/取消点赞、评论、举报；自己的帖子可删除；非 PUBLISHED 状态仅作者可见。发帖/评论走「本地敏感词 + AI 审核 + 人工队列」。
   - **助人者视角**：求助详情会显示「受助者给了你 X 星」；确认页与故事卡显示「本次用时」（按助人者标记完成时间计算，不含等待确认/申诉）。
   - **AI 助手**：首页右下角「AI 小助手」→ 可直接问「怎么发布求助」，也可描述一件小事，助手补齐类型/时间/地址后弹确认卡，确认后自动发布。
7. 分支·释放重派：赵晓琳接远程单演示恢复通道；陈志远演示"实在帮不上"免罚释放 → 李秀英收到"别干等"通知
8. 分支·防诈：陈志远在聊天中发"验证码"相关索要话术 → 双方收到强提示、管理端「上报处置」出现记录可处置
9. 管理端：看板（近 7 天趋势 / 状态分布 / 防诈与社区待审）、订单列表/详情与时间线、协商取消裁决、社区审核、把配置页 `elder.age_threshold` 改为 70 → 重新发布验证排序变化，改回
10. 个人页：微光徽标（被点亮 / 常亮）+「光已传递」徽记；微光值 / 荣誉积分明细
11. 稳定性：`docker compose stop redis` 后主线仍可走通（降级）；恢复后用 RedisInsight 查看限流键与 TTL
12. AI：管理端把 `ai.mode` 切 `real` 复跑（需 `.env` 配 `DASHSCOPE_API_KEY`）；切回 `mock`
13. 结束：再跑一次 `sql/reset.sql` 复位

注意：同一用户最多 2 条待响应求助（防刷屏），超出时发布/重发返回 30003 温柔提示（seed 中李秀英、王德明各有 2 条，已达上限；实测时先成交或撤回一单再发布）。

## 六、环境变量（backend/.env，模板见 .env.example）

| 变量 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼密钥（`ai.mode=real` 时需要；mock 模式可留空） |
| `AMAP_WEB_KEY` | 高德 **Web 服务** Key（地址搜索/输入提示/地理编码用；不填则地址搜索降级为手动填写） |
| `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` | MySQL 连接（compose 内为 `mysql:3306`） |
| `REDIS_HOST/PORT` | Redis 连接（compose 内为 `redis:6379`） |
| `WECHAT_APPID` / `WECHAT_APP_SECRET` | 微信小程序凭据（账密登录为主流程；`/api/auth/wx-login` 保留未用） |
| `JWT_SECRET` | JWT 签名密钥 |
| `SERVER_PORT` | 后端端口（默认 8080） |
| `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` | 管理端初始账号（首次启动写入 admin_user） |

> AI 模型名/超时/重试、存储目录、限流阈值等**非密钥运行参数一律在管理端「配置管理」（sys_config）**，改即生效；密钥永不入库。

## 七、常见问题

- **开发者工具报"找不到 app.json"**：先执行 `npm run dev:mp-weixin` 生成 `dist/dev/mp-weixin` 后再导入该目录。
- **只跑一个前端监听**：同时开两个 `npm run dev:mp-weixin`（或 dev 与 build 混跑）会写同一个 `dist`，出现「新 wxml + 旧 JS」混编，表现为方法名 `[object Object]`、空列表或字面量 `false`。先关掉多余进程，删除 `dist` 后重新 dev 编译，并在开发者工具里清缓存。
- **改 `pages.json` / 重建产物后**：重新编译并在开发者工具清缓存，编译模式选 `pages/index/index`、无启动参数。
- **后端新增 VO 字段后**：必须重启后端；前端热更新不会让新字段出现。
- **迁移脚本已全部执行并删除**：`schema.sql` 已包含 `extra_tag`、`evidence_url`、`message_count`、`sender_nickname`、`address_book.latitude/longitude`、`help_order.address_lat/address_lng`、`complete_requested_at`、`risk_reviewed_at/risk_review_result/risk_review_note`，不要重复执行迁移；需要重建时直接用 `schema.sql` + `seed.sql` / `reset.sql`。
- **地址搜索提示不可用**：先确认 `backend/.env` 有 `AMAP_WEB_KEY`（必须是「Web服务」类型的 Key），再重启后端；Key 只在后端使用，不下发前端。
- **头像/聊天图片显示**：开发者工具不允许 `<image>` 直接加载 HTTP，前端已统一用 `WgImage` 先解析成本地路径或 base64；生产环境后端走 HTTPS 时直接显示原图。
- **换账号演示**：小程序内「设置 → 退出登录」后用另一手机号账号登录（无需 openid，任意账号可切）。
- **后端地址（自动探测）**：`VITE_API_HOST` 是**编译期**注入的，IP 会随网络变化。已内置探测脚本 `front/miniapp/scripts/detect-lan-host.mjs`，并挂为 `predev:mp-weixin` / `prebuild:mp-weixin` **前置钩子**——每次 `npm run dev:mp-weixin` / `build:mp-weixin` 会**自动探测本机局域网 IPv4 并重写 `.env.local`**（自动排除 WSL / Hyper-V / VPN 等虚拟网卡，按 192.168 > 172.16–31 > 10 优先级取用）。想先看探测结果不写文件：`npm run host:dry`。
- **真机预览 / 换网络**：手机与电脑同一局域网；`npm run dev:mp-weixin` 会自动写入电脑局域网 IP，无需手改。若需固定地址（例如模拟器只想走 `localhost`）：`$env:WGU_HOST='localhost:8080'; npm run dev:mp-weixin`（PowerShell）即可覆盖自动探测。开发者工具开启「开发版调试」，并勾选「不校验合法域名」；防火墙需放行 8080。
- **重置数据**：优先执行 `powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1`（内部用 `docker cp` + `source`，可避免中文乱码），随后所有账号请重新登录。不要用 `Get-Content ... | docker exec -i mysql` 的方式灌 SQL：Windows PowerShell 5.1 会把中文重编码成 ANSI/GBK，落库变成 `?`。
- **待响应上限**：同一用户最多 2 条待响应（`order.pending_limit`），超限 30003。
- **登录限流**：连续错密码超过 5 次/分钟返回 30005，等一分钟后重试。

## 八、文档索引

- 详细设计：`微光-详细设计文档v1.2.md`（第 11 章为 15 条 MVP 验收判据全文）
- 执行文档：`微光-执行文档v1.2.md`（T01–T19 任务卡）
- 实现偏差：`docs/实现偏差清单.md`（D01–D110）
- 验收走查：`docs/验收走查记录.md`（T20 15 条判据 + 2026-10 体验批次 + T29/T30/T31 收口）
- seed 契约：`docs/seed契约.md`（数据画像、覆盖矩阵、24 条不变量、时间自洽规则；本地文档，不入代码仓库）
- 研究边界：`docs/论文研究边界与前提假设.md`（v1.0 定稿；本地文档，不入代码仓库）

> `design/`、`.screenshots/`、`docs/交接文档.md`、`docs/seed契约.md`、`docs/论文研究边界与前提假设.md` 为本地 Agent/论文资料，已在 `.gitignore` 排除，不属于代码交付物。

## 附录 A：页面清单（31 页）

| 模块 | 页面 |
| --- | --- |
| 入口与登录 | `pages/index/index`（首页）、`pages/login/index`（登录/注册） |
| 发布 | `pages/order/publish`（模板选择）、`publish-free`（自由描述 + AI 建议）、`publish-form`（求助表单） |
| 求助 | `pages/order/detail`（求助详情）、`room`（进展与操作）、`edit`（修改求助）、`mine`（我的求助）、`confirm`（确认完成） |
| 聊天 | `pages/chat/room` |
| 结算 | `pages/settle/evaluate`（评价）、`letter`（感谢信）、`story`（故事卡） |
| 个人 | `pages/user/index`、`profile`、`address`、`settings`、`credit`、`points`、`appeal`、`appeal-detail`、`notify`、`thanks` |
| 邻居 | `pages/neighbor/profile` |
| 社区 | `pages/community/index`（信息流）、`publish`（发帖）、`detail`（帖子详情/评论） |
| 引导 | `pages/guide/privacy`（隐私说明）、`pages/guide/index`（首次引导） |
| AI 助手 | `pages/assistant/chat`（问答 + 帮发布求助） |

## 附录 B：版本与改进记录

| 阶段 | 主要内容 | 状态 |
| --- | --- | --- |
| MVP（T01–T19） | 登录/发布/接单/聊天/双确认/结算/释放/申诉/通知/光语/防诈/AI/管理端/演示数据/验收 | 已交付，tag `v1.0-mvp` |
| 批 1 | 发布拆三页 + 草稿、首页图标与返回登录修复、确认死循环修复、类型文案统一 | 已完成 |
| 批 2 | 进度条、骨架重试、P10 评价/悄悄话改造、通知分组与未读、感谢独立页、故事卡数据条、通讯录选联系人、默认地址开关、头像 | 已完成 |
| 批 3A | 跑腿/教学标签改名、同区域距离粗算、申诉/反馈撤回、通知左滑删除 | 已完成 |
| 批 3B | 通知聚合与实时刷新、快捷短语分角色、对方正在输入、自定义标签、申诉凭证 | 已完成 |
| 批 3C | 今日社区求助数、确认页完成时间/本次用时、助人者星级；保底时间与地址结构化并入批 4 | 已完成 |
| 批 4A | 紧急联系人硬校验、隐私版本号配置化、输入框截断修复、手机号/长度校验 | 已完成 |
| 批 4B | 待响应求助修改 / 撤回（P04/P07 + 编辑页） | 已完成 |
| 批 4C | 8 分类保底时长、管理端存疑筛选与风险核实 | 已完成 |
| 批 4D | 地址经纬度、高德 POI/输入提示/地理编码、城市限定、距离排序与展示、地址交互收敛、图片 HTTP 兼容 | 已完成 |
| 批 4G | 社区 tab + 邻里动态；首页社区卡迁移；只读，不做感谢墙/发帖 | 已完成 |
| 批 4F | README/验收走查、全量回归、三端构建、仓库卫生 | 已完成 |
| T21–T24 | 光语 v2、社区 2.0（发帖/点赞/评论/举报/删帖）、AI 内容审核、通知 19 类 | 已完成 |
| T25–T26 | 管理端看板、通知/聊天分页、seed 重写与自检（现 24 条不变量） | 已完成 |
| T27–T28 | 实测缺陷修复（注销、释放文案、年长模式）、上传魔数校验、Dockerfile、compose 初始化 | 已完成 |
| T29 | seed 时间自洽与用时口径修正、管理端「年长」文案复验、边界/README/验收/交接收口 | 已完成 |
| T30 | 2026-10 代码审查优化（批 1–5）：状态机死角、资金一致性、迁移与索引、性能、安全限流、小程序体验、管理端上报补全、举报结果通知与 AI 提示词加固 | 已完成 |
| T31 | AI 助手（能力五）：操作问答、发布槽位补齐、地址/紧急联系人校验、确认令牌后复用发布链路 | 已完成 |

> MVP 以 tag `v1.0-mvp` 交付；2026-10 体验改进（批 1–4F）已合入 `master`（commit `b22742b`）。v1.3（T21–T29）与 T30 代码审查优化（D63–D110）实现细节见 `docs/实现偏差清单.md`。