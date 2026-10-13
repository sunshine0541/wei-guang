# 微光 · 社区互助服务系统

> 面向社区老年居民与热心邻居的互助小程序：老人一键发布小事求助，邻居接单上门/陪同/远程帮忙，聊天协作、双确认完成，配微光值·积分·光语情感化体系与防诈保护。
> 毕业设计 MVP（tag `v1.0-mvp`）。设计依据：《微光-详细设计文档v1.2》《微光-执行文档v1.2》。

## 一、仓库结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 3.5（Java 17）后端：REST + WebSocket + 定时任务；MySQL 8.4 / Redis 7.4；含 `docker-compose.yml`（MySQL + Redis + 后端容器一键起） |
| `front/miniapp/` | uni-app + Vue3 微信小程序端（唯一用户端形态） |
| `admin-web/` | Vue3 + Element Plus 管理后台（概览 / 订单 / 协商取消裁决 / 上报处置 / 用户 / 配置） |
| `sql/` | `schema.sql` 全量建表（23 张表）；`seed.sql` 拟真演示数据（自包含一键重置）；`reset.sql` ≡ seed.sql |
| `docs/` | 《实现偏差清单》《验收走查记录》 |
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
> 数据库口令取自 `backend/.env` 的 `MYSQL_PASSWORD`（compose 缺省 `weiguang123`）。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run          # 开发方式
# 或打包后走容器：mvn -DskipTests package && docker compose up -d backend
```

- 健康检查：http://localhost:8080/actuator/health （含 `db`、`redis` 明细）
- 接口文档：http://localhost:8080/swagger-ui.html （springdoc）
- 首次启动自动按 `.env` 初始化管理员账号（`ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD`）

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

1. **李秀英**登录 → 首页：荣耀榜（刘建军 4 次居首）、善意回流计数、通知横幅（"有人来接您的求助啦"，3 条聚合）
2. 待响应列表：急标"厨房水管一直渗水"置顶；查看详情 → 地址分级（远程无地址 / 陪同仅集合点 / 上门门牌接单后才解锁）
3. **陈志远**接"卫生间的灯不亮了" → 聊天页：照护提示卡"腿脚不便，需要扶一下"、快捷短语、图片
4. 陈志远「开始帮忙」→ 李秀英收到"邻居已出发" → 陈志远标记完成 → 李秀英确认 → 结算：微光值 +2、积分 +10、故事卡生成
5. 李秀英写评价 + 悄悄话；用 AI「起个头」写感谢信并发送；查看故事卡
6. 分支·释放重派：赵晓琳接远程单演示恢复通道；陈志远演示"实在帮不上"免罚释放 → 李秀英收到"别干等"通知
7. 分支·防诈：陈志远在聊天中发"验证码"相关索要话术 → 双方收到强提示、管理端「上报处置」出现记录可处置
8. 管理端：订单列表/详情与时间线；协商取消裁决；把配置页 `elder.age_threshold` 改为 70 → 重新发布验证排序变化，改回
9. 个人页：微光徽标（被点亮/常亮/小憩）+「光已传递」徽记；微光值 / 积分明细
10. 稳定性：`docker compose stop redis` 后主线仍可走通（降级）；恢复后用 RedisInsight 查看限流键与 TTL
11. AI：管理端把 `ai.mode` 切 `real` 复跑（需 `.env` 配 `DASHSCOPE_API_KEY`）；切回 `mock`
12. 结束：再跑一次 `sql/reset.sql` 复位

注意：同一用户最多 2 条待响应求助（防刷屏），超出时发布/重发返回 30003 温柔提示（李秀英 seed 存量已达 3 条，属演示数据特例，实测该规则可先成交一单再发布）。

## 六、环境变量（backend/.env，模板见 .env.example）

| 变量 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼密钥（`ai.mode=real` 时需要；mock 模式可留空） |
| `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` | MySQL 连接（compose 内为 `mysql:3306`） |
| `REDIS_HOST/PORT` | Redis 连接（compose 内为 `redis:6379`） |
| `WECHAT_APPID` / `WECHAT_APP_SECRET` | 微信小程序凭据（账密登录为主流程；`/api/auth/wx-login` 保留未用） |
| `JWT_SECRET` | JWT 签名密钥 |
| `SERVER_PORT` | 后端端口（默认 8080） |
| `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` | 管理端初始账号（首次启动写入 admin_user） |

> AI 模型名/超时/重试、存储目录、限流阈值等**非密钥运行参数一律在管理端「配置管理」（sys_config）**，改即生效；密钥永不入库。

## 七、常见问题

- **开发者工具报"找不到 app.json"**：先执行 `npm run dev:mp-weixin` 生成 `dist/dev/mp-weixin` 后再导入该目录。
- **换账号演示**：小程序内「设置 → 退出登录」后用另一手机号账号登录（无需 openid，任意账号可切）。
- **真机预览 / 换网络**：手机与电脑同一局域网；复制 `front/miniapp/.env.example` 为 `front/miniapp/.env.local`，把 `VITE_API_HOST` 改为电脑局域网 IP（如 `172.16.78.127:8080`），再重新编译（`npm run dev:mp-weixin`）；开发者工具开启「开发版调试」，仍勾选"不校验合法域名"。**换 Wi-Fi / 换电脑后只改 `.env.local` 这一行**（该文件已被 `.gitignore` 忽略，不入库；`constants.js` 保持中性默认值）。
- **重置数据**：执行 `sql/reset.sql`（等价于清空业务数据 + 重灌演示数据），随后所有账号请重新登录。
- **待响应上限**：同一用户最多 2 条待响应（`order.pending_limit`），超限 30003。
- **登录限流**：连续错密码超过 5 次/分钟返回 30005，等一分钟后重试。

## 八、文档索引

- 详细设计：`微光-详细设计文档v1.2.md`（第 11 章为 15 条 MVP 验收判据全文）
- 执行文档：`微光-执行文档v1.2.md`（T01–T19 任务卡）
- 实现偏差：`docs/实现偏差清单.md`（D01–D34）
- 验收走查：`docs/验收走查记录.md`（15 条判据逐项证据）