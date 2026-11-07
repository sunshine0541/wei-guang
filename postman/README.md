# 微光 · 接口测试工程（Postman / Newman）

本目录是「微光（邻里互助）」后端接口的自动化测试工程，可直接导入 Postman 运行，也可用 Newman 在命令行/CI 中批量执行。

## 目录结构

```
postman/
├─ 微光-API接口测试.postman_collection.json   # 主测试集合（19 个文件夹 / 145 个请求 / 523 条断言）
├─ 微光-本地.postman_environment.json         # 本地环境（baseUrl 等变量）
├─ tools/
│  ├─ build.mjs                               # 由「规格」生成集合与环境文件（唯一权威来源）
│  └─ tiny.png                                # 文件上传用例使用的测试图片
└─ README.md
```

> 集合与环境文件由 `tools/build.mjs` 生成，**不要手改生成的 JSON**；要增删用例请改 `build.mjs` 后重新生成：
> `node postman/tools/build.mjs`

## 快速开始

### 1. 启动被测服务

```powershell
# 在项目根目录，使用 docker compose 启动 mysql / redis / backend
docker compose up -d
# 健康检查应返回 {"status":"UP"}
curl http://localhost:8080/actuator/health
```

### 2. 在 Postman 中运行

1. Postman → Import → 选择 `微光-API接口测试.postman_collection.json` 与 `微光-本地.postman_environment.json`。
2. 右上角环境选择「微光-本地」。
3. 集合右键 → Run collection（Run type 选 **Functional**）。
4. 建议先单独跑 `00 初始化`，再整体运行（用例之间存在变量/顺序依赖，见下）。

### 3. 用 Newman 命令行运行（推荐 / 可进 CI）

```powershell
# 依赖已安装在本仓库 .tools 下（newman 6.x）
node .tools\node_modules\newman\bin\newman.js run `
  "postman\微光-API接口测试.postman_collection.json" `
  -e "postman\微光-本地.postman_environment.json" `
  --timeout-request 15000 `
  --reporters cli,json --reporter-json-export ".tools\newman-report.json"
```

## 关键约定

- **HTTP 状态码恒为 200，业务结果看响应体 `code`**：`0` 表示成功，其余为业务错误（如 `10001` 参数校验失败、`20001` 未登录、`30003` 待响应达上限、`40002` 状态不允许）。断言以 `body.code` 为准，而不是 HTTP 状态码。
- 统一返回体：`{ code, message, data, traceId }`。
- 鉴权：登录后取 `data.token`，后续请求以 `Authorization: Bearer {{token}}` 携带；集合通过「After-response 脚本 / save」自动把 token 写入环境变量。

## 测试数据与执行顺序

集合内文件夹按编号执行，存在**有向依赖**（前面的请求把 `orderId/token/...` 写入环境变量，后面的请求引用它们），因此请整体按顺序运行，不要跳序：

| 文件夹 | 作用 |
|---|---|
| 00 初始化 | 管理端登录、把限流/待响应上限调到测试值、登录 A/B/C/D 四个演示账号存 token |
| 01–14 | 业务模块用例（认证/用户/地址/求助单全生命周期/社区/聊天/通知/激励/申诉/助手/上传/邻居） |
| 15–16 | 管理端查询与操作 |
| 17 | 鉴权与健壮性（无 token / 错 token / 越权 / 健康检查） |
| 18 收尾 | 恢复限流配置、退出登录 |

演示账号（密码统一 `wg123456`）：李秀英 `13867429153`、陈志远 `15867132409`、王德明 `13957068214`、赵晓琳 `18652970318`；管理端 `admin / weiguang@2026`。

## 常见问题

- **Runner 报 `Empty request URL`**：Postman 不自动保存请求，Local View 下需对每个请求 `Ctrl+S` 保存后再运行。
- **登录返回 `30005`（限流）**：默认登录 5 次/分钟。集合 `00 初始化` 会先把限流调到测试值再登录；若反复调试触发限流，等待 1 分钟或清空 Redis 的限流键后再跑。
- **`{{baseUrl}}` 拼出 `//` 导致 404**：环境变量 `baseUrl` 结尾不要带 `/`。

## 覆盖面

共 **19 个文件夹 / 145 个请求 / 523 条断言**，覆盖正向、边界、异常、状态流转、鉴权与幂等场景，详见 `docs/接口测试报告.md`。
