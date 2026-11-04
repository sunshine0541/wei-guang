<div align="center">

# 微光 WeiGuang

**让不会用手机的独居老人，也能一键喊到邻居**

uni-app + Vue3 ｜ Spring Boot 3.5 ｜ Spring AI Alibaba（通义千问）｜ MySQL 8.4 · Redis 7.4 · WebSocket

[详细设计文档](微光-详细设计文档v1.2.md) · [开发记录](docs/开发记录.md)

</div>

社区互助微信小程序：老人发布一件小事，邻居接单上门 / 外出 / 远程帮忙，聊天协作、双确认后结算微光值与荣誉积分；配套 31 页小程序端、管理后台与 AI 助手。

> 个人项目：独立完成需求拆解、表设计、核心链路实现与集成测试；使用 AI 工具辅助编码，方案审查、关键逻辑与验收由本人负责。

| 接口 | 数据表 | 小程序页面 | 集成测试 | 数据自检 |
| :---: | :---: | :---: | :---: | :---: |
| 105 | 26 | 31 | 8 条 / 7 类 | 24 条不变量 |

## 四个技术点

**1. AI 助手替用户填表，而不是陪聊**
自然语言 → 槽位补齐（类型 / 时间 / 地址 / 紧急联系人校验）→ 一次性确认令牌 → 复用原发布链路。AI 全程不写库；调用前脱敏手机号、身份证、银行卡、验证码、门牌号 5 类字段；模型异常自动降级到规则兜底。

**2. AI 参与内容安全**
发帖评论走「本地敏感词 → LLM 初审 → 人工复核」；聊天防诈走「规则初筛 → AI 异步复核 → WebSocket 强提醒」，推送统一延迟到事务提交后，避免读到旧状态。

**3. 并发与钱不能错**
接单用「状态 + 版本号 + 截止时间」条件更新，多人并发只有一人成功；结算、微光值、积分账本同事务写入；账本 `FOR UPDATE` + 唯一键幂等；AI 确认令牌用 Redis `GETDEL` 原子消费。

**4. 交付可验证**
7 个 Testcontainers 集成测试类（8 条用例）覆盖核心链路；`reset.sql` + 24 条数据不变量自检；Redis 宕机时主线仍可走通；Docker Compose 一键起 MySQL + Redis + 后端。

## 界面

### 普通模式

| | | |
| :---: | :---: | :---: |
| <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-01-home.png" width="230" /><br>首页 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-02-ai-assistant.png" width="230" /><br>AI 助手 · 一句话补齐并确认发布 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-03-publish-ai.png" width="230" /><br>发布 · 小助手自动归类 |
| <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-04-chat.png" width="230" /><br>聊天 · 照护提示与门牌解锁 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-05-story-card.png" width="230" /><br>完成 · 光之故事卡 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/normal-06-community.png" width="230" /><br>社区 · 荣耀榜与邻里动态 |

### 年长模式

> 同一套业务：字号与按钮放大、非必要元素隐藏、动效默认关闭。

| | | |
| :---: | :---: | :---: |
| <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/elder-01-home.png" width="230" /><br>首页 · 单列大字卡片 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/elder-02-order-detail.png" width="230" /><br>求助详情 · 大字号与操作按钮 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/elder-03-notify.png" width="230" /><br>通知中心 · 分组与已读操作 |

### 管理端

| | |
| :---: | :---: |
| <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/admin-01-overview.png" width="360" /><br>工作台 · KPI / 趋势 / 待办 | <img src="https://gitee.com/sail-peng/wei-guang/raw/master/docs/assets/screenshots/admin-02-review.png" width="360" /><br>社区审核 · AI 初审 + 人工复核 |

## 架构

![系统架构](docs/assets/architecture.png)

模块化单体：`backend/` 按域拆包（认证、订单、聊天、社区、申诉、管理端），AI 能力以独立层内嵌；密钥只存后端 `.env`，不下发前端。

## 快速开始

**环境**：JDK 17+、Maven 3.8+、Node 18+、Docker Desktop、微信开发者工具

### 1. 中间件

```powershell
cd backend
copy .env.example .env      # 按注释填写密钥与连接（.env 不进仓库）
docker compose up -d mysql redis
```

### 2. 建表与演示数据

```powershell
# 在仓库根目录执行（规避 Windows 管道中文乱码）
docker cp sql\schema.sql weiguang-mysql:/tmp/schema.sql
docker exec -i weiguang-mysql mysql --default-character-set=utf8mb4 -uroot -p weiguang -e "source /tmp/schema.sql"
# 首次再 source seed.sql；之后随时用一键复位 + 自检
powershell -ExecutionPolicy Bypass -File scripts\demo-reset.ps1
```

> 数据库口令取自 `backend/.env` 的 `MYSQL_PASSWORD`（compose 缺省 `weiguang123`，仅演示环境使用）。

### 3. 后端

```powershell
cd backend
mvn spring-boot:run          # 开发运行（需 IDE EnvFile 或手动注入 .env）
# 或：mvn -DskipTests package && docker compose up -d backend
```

- 健康检查：http://localhost:8080/actuator/health
- 接口文档：http://localhost:8080/swagger-ui.html
- 集成测试：`mvn "-Dtest=AcceptConcurrencyTest,PointConcurrencyTest,ConfirmRejectAppealTest,OrderFullFlowTest,CancelNegotiationFlowTest,CommunityReportFlowTest,AssistantFlowTest" test`（需 Docker）

### 4. 小程序

```powershell
cd front/miniapp
npm install
npm run dev:mp-weixin        # 产物：dist/dev/mp-weixin
```

微信开发者工具导入 `front/miniapp/dist/dev/mp-weixin`，编译模式选 `pages/index/index`，勾选「不校验合法域名」。

### 5. 管理后台

```powershell
cd admin-web
npm install
npm run dev                  # http://localhost:5174（/admin 代理到 :8080）
```

## 演示账号

密码统一 `wg123456`，账号即本人手机号：

| 账号 | 昵称 | 用途 |
| --- | --- | --- |
| `13867429153` | 李秀英 | 受助主线（68 岁年长模式，画像/地址/紧急联系人完整） |
| `15867132409` | 陈志远 | 助人主线（接单、聊天、结算侧） |
| `13957068214` | 王德明 | 年龄未填（默认策略与补填提示） |
| `18652970318` | 赵晓琳 | 微光值 50（低于门槛，仅可接远程） |

管理员账号由首次启动时 `backend/.env` 的 `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` 写入。

## 演示路径

演示前执行 `sql/reset.sql` 或 `scripts\demo-reset.ps1` 回到干净状态。

1. **李秀英**登录 → 首页：通知横幅、紧急电话卡、求助列表；底部「社区」：荣耀榜、善意回流、邻里动态
2. 发布三页：模板选择 → 自由描述（AI 建议）→ 表单；中途退出再进入自动恢复草稿
3. **陈志远**接单 → 聊天页：照护提示卡、快捷短语、地址门牌接单后解锁
4. 开始帮忙 → 标记完成 → 李秀英确认 → 结算：微光值 +2、积分 +10、故事卡生成
5. 评价 / 感谢信（AI 起个头）/ 故事卡；申诉与撤回（完成或取消后 7 天内）
6. 社区：发帖、点赞、评论、举报；发帖走「敏感词 + AI 审核 + 人工队列」
7. AI 助手：首页右下角 → 问「怎么发布求助」，或描述一件小事，确认后自动发布
8. 管理端：工作台 KPI 与待办、订单详情与协商取消裁决、上报处置、社区审核、申诉仲裁
9. 稳定性：`docker compose stop redis` 后主线仍可走通（降级）；恢复后验证限流键

> 同一用户最多 2 条待响应求助（超限返回 30003）；连续错密码 5 次/分钟触发 30005。

## 环境变量（`backend/.env`）

| 变量 | 说明 |
| --- | --- |
| `DASHSCOPE_API_KEY` | 阿里云百炼密钥（`ai.mode=real` 时需要；mock 模式可留空） |
| `AMAP_WEB_KEY` | 高德 **Web 服务** Key（不填则地址搜索降级为手动填写） |
| `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` | MySQL 连接（compose 内为 `mysql:3306`） |
| `REDIS_HOST/PORT` | Redis 连接（compose 内为 `redis:6379`） |
| `WECHAT_APPID` / `WECHAT_APP_SECRET` | 微信小程序凭据（账密登录为主流程） |
| `JWT_SECRET` | JWT 签名密钥 |
| `SERVER_PORT` | 后端端口（默认 8080） |
| `ADMIN_SEED_USERNAME` / `ADMIN_SEED_PASSWORD` | 管理端初始账号 |

> AI 模型名、超时重试、限流阈值等非密钥参数在管理端「配置管理」（`sys_config`）维护，改即生效；密钥永不入库。

## 仓库结构

| 目录 | 说明 |
| --- | --- |
| `backend/` | Spring Boot 3.5（Java 17）：REST + WebSocket + 定时任务；MySQL 8.4 / Redis 7.4；含 `docker-compose.yml` |
| `front/miniapp/` | uni-app + Vue3 微信小程序端（唯一用户端形态） |
| `admin-web/` | Vue3 + Element Plus 管理后台 |
| `sql/` | `schema.sql` 全量建表（26 张）；`seed.sql` / `reset.sql` 自包含演示数据 |
| `scripts/` | 一键复位、数据自检、冒烟、启动脚本 |
| `docs/` | 实现偏差清单、验收走查记录、开发记录、截图资源 |

## 常见问题

- **开发者工具报「找不到 app.json」**：先执行 `npm run dev:mp-weixin` 生成 `dist/dev/mp-weixin` 再导入。
- **同时开两个前端监听**：会写同一个 `dist` 导致新旧混编，先关掉多余进程、删除 `dist` 重新编译并清缓存。
- **`mvn spring-boot:run` 读不到 `.env`**：用 IDE 的 EnvFile 插件注入，或改用 `docker compose up -d backend`。
- **真机预览**：手机与电脑同一局域网；`npm run dev:mp-weixin` 会自动探测本机局域网 IP 写入 `.env.local`。
- **地址搜索不可用**：确认 `backend/.env` 有「Web 服务」类型的 `AMAP_WEB_KEY`，改后重启后端。
- **重置数据乱码**：不要用 PowerShell 管道灌 SQL（会把中文重编码为 GBK），用 `scripts\demo-reset.ps1` 的 `docker cp` + `source` 方式。
- **头像/聊天图片不显示**：开发者工具不允许 `<image>` 直接加载 HTTP，前端已统一转本地路径或 base64。

## 文档

| 文档 | 内容 |
| --- | --- |
| [微光-详细设计文档v1.2.md](微光-详细设计文档v1.2.md) | 业务规则、状态机、表结构、页面与验收判据 |
| [微光-执行文档v1.2.md](微光-执行文档v1.2.md) | 任务卡与验收判据 |
| [docs/开发记录.md](docs/开发记录.md) | 31 页页面清单、版本演进记录 |
| [docs/实现偏差清单.md](docs/实现偏差清单.md) | 实现与设计的偏差及理由 |
| [docs/验收走查记录.md](docs/验收走查记录.md) | 验收走查过程与实测结果 |
