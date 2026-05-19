# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

beidou-cli 是 [BeiDou-Server](../BeiDou-Server/) 的命令行工具，供 AI agent 通过 HTTP API 操作服务端控制台。编译目标为 GraalVM Native Image 原生二进制。

## 构建

```bash
mvn package -DskipTests          # fat JAR（shade 插件自动打包依赖）
mvn package -DskipTests -Pnative # GraalVM 原生二进制（需要 GraalVM JDK 21）
```

输出：`target/beidou-1.0-SNAPSHOT.jar` 或 `target/beidou`（原生二进制）。

原生构建也可用脚本：`./build-native.sh`（macOS/Linux）、`build-native.bat`（Windows，需 Visual Studio 2022+ C++ 桌面开发）。

## 技术栈

- **CLI**: picocli 4.7.6（子命令模式）
- **HTTP**: java.net.http.HttpClient（JDK 内置，native-image 兼容）
- **JSON**: Jackson 3.1.3（`tools.jackson` 命名空间），annotation 混用 `com.fasterxml.jackson`（来自传递依赖），两者需同时在 reflect-config.json 中注册
- **构建**: Maven shade 插件打 fat jar，`org.graalvm.buildtools:native-maven-plugin` 按 `-Pnative` profile 进行原生编译
- **反射配置**: `src/main/resources/META-INF/native-image/reflect-config.json`（picocli 命令类、model 类、Jackson 节点类），新增 model 或命令类需同步更新该文件。注意 `tools.jackson.databind.node.ObjectNode` 和 `com.fasterxml.jackson.databind.node.ArrayNode` 两个包名混用，都需要注册

## 架构

```
Main.java  ──>  ConfigCommand   (beidou config / beidou config --show)
           ──>  LoginCommand    (beidou login)
           ──>  CallCommand     (beidou call <METHOD> <path> [body] [--force])
           ──>  BatchCommand    (beidou batch — 从 stdin 逐行读取，格式: METHOD /path [body])
           ──>  VersionCommand  (beidou version)
           ──>  ApiListCommand  (beidou apis [keyword])
           ──>  UpdateCommand   (beidou update — 从 GitHub Releases 下载覆盖当前二进制)
           ──>  UninstallCommand(beidou uninstall — 删除二进制 + 清理 PATH)
                     │
         CallCommand ─┴──> ApiClient ──> AuthManager ──> CliConfig
         BatchCommand ─┘        │              │
                                  │   login ─────┘ POST /auth/v1/login
                                  │   parseJwtExpiry ─  Base64 解码 payload.exp
                                  │
                                  └──> sendRequest ──> java.net.http.HttpClient
```

- **CliConfig** (`config/`): 单例配置对象，JSON 序列化到 `~/.beidou-cli/config.json`。字段：server/username/password（用户配置）、token/tokenExpiry（运行时缓存）。`isTokenValid()` 提前 60s 将 token 视为过期。
- **AuthManager** (`auth/`): 调用 `/auth/v1/login`，返回 token 写入 CliConfig。JWT 不验签，仅 Base64 解码 payload 取 `exp` 字段用于本地过期判断（服务端会验签）。`forceLogin()` 在连接失败或登录失败时直接 `System.exit(1)`。
- **CallCommand**: 支持 GET/POST/PUT/DELETE/PATCH。body 参数自动包装为 `SubmitBody { requestId: null, data: <body> }`。无 body 时 GET/DELETE 不发 body，其他方法发空 body。Git Bash (MSYS) 下 `/path` 会被自动转为 `C:/.../path`，内置 `fixMsysPath()` 自动修复。
- **BatchCommand**: 从 stdin 逐行读取 `METHOD /path [body]`，空行和 `#` 开头的行跳过。调用 `ApiClient.callForBatch()`（失败抛异常而非 `System.exit`，保证后续请求继续执行）。
- **ApiClient** (`http/`): 所有 API 调用的统一入口。自动附加 `Authorization: Bearer <token>`。POST body 自动包装为 `SubmitBody { requestId, data: <body> }`。401 时清空 token → 重新 login → 重试，最多 3 次，3 次仍 401 则 `call()` 中 `System.exit(1)`，`callForBatch()` 中抛异常。
- **JsonUtils** (`util/`): 静态工具类，封装 Jackson `ObjectMapper`（关闭 `FAIL_ON_EMPTY_BEANS` 和 `FAIL_ON_UNKNOWN_PROPERTIES`），提供 `toJson`/`fromJson`/`readTree`/`toPrettyJson` 等方法。
- **Main.java**: `main()` 入口在 Windows 下执行 `chcp 65001` + 设置 `System.out`/`err` 为 UTF-8，防止中文乱码。然后通过 `CommandLine.execute()` 启动 picocli。
- **UpdateCommand/UninstallCommand**: `findCurrentBinary()` 在 PATH 中查找 `beidou`/`beidou.exe`。Update 从 GitHub Releases 下载对应平台二进制覆盖；Uninstall 删除二进制并清理 PATH（Unix: `.bashrc`/`.zshrc`/`.profile`，Windows: 注册表 `HKCU:\Environment`）。

## 服务端 API 约定（BeiDou-Server）

- 地址: `/auth/v1/login`、`/server/v1/online`、`/character/v1/online/list` 等
- 认证: JWT (HS512)，Authorization header: `Bearer <token>`，有效期 30min
- 请求格式（POST）: `{ "data": { ... } }` — 对应 `SubmitBody` record
- 响应格式: `{ "code": 20000/40000/..., "message": "...(i18n)", "responseId": "...(UUID)", "data": ... }` — 对应 `ResultBody` record
- **成功码: 20000**（`BizExceptionEnum.SUCCESS.getResultCode()`），不是 HTTP 200
- 错误码: `40000`(BODY_NOT_MATCH)、`40001`(REQUEST_METHOD_SUPPORT)、`40002`(ILLEGAL_PARAMETERS)、`40004`(NOT_FOUND)、`50000`(INTERNAL_SERVER_ERROR)、`50003`(SERVER_BUSY)
- `message` 字段使用 i18n 国际化（`I18nUtil.getExceptionMessage`），中文环境返回中文如"成功!"
- **异常处理**: `GlobalExceptionHandler` 全局捕获异常 → `ResultBody.error()`，RuntimeException→40000，ServletException→40001，Exception→50000
- 服务端 JSON 库是 `fastjson2`，beidou-cli 是 Jackson 3.x，两边的 `ResultBody` 结构需保持一致
- `/auth/**` 路径免认证，其余需要 token

## 服务端信息（来自 ServerConstants.java）

- **BeiDou 版本**: 1.10
- **Build Time**: 2025-06-22 12:45:59
- **协议版本 (VERSION)**: 83
- 服务端代码: `E:\LocalGit\GitHub\BeiDou-Server\gms-server\src\main\java\org\gms`

## API 发现

- `beidou apis [keyword]` — 查看所有可用 API 及参数说明（共 76 个接口，定义在 `ApiListCommand.java`），支持关键词过滤
- 服务端共 15 个 Controller: auth, account, server, character, common, cashShop, drop, gachapon, give, inventory, shop, config, command, file, autoban
- 服务端可能新增 API，更新 beidou-cli 时需同步更新 `ApiListCommand.APIS` 列表

## 敏感操作保护

- `beidou call` 对关服、删除、修改数据等敏感接口默认阻止，返回错误并提示使用 `--force`
- 敏感判定逻辑在 `ApiListCommand.isSensitive()`：先按 API 列表中精确路径匹配（优先），未匹配时用正则回退匹配（将 `{id}` 替换为 `[^/]+`）
- `/auth` 路径免登录，其余自动认证
- Git Bash (MSYS) 会将 `/server/v1/online` 等路径转为 `D:/Program Files/Git/server/v1/online`，`CallCommand` 和 `BatchCommand` 内置 `fixMsysPath()` 自动修复（匹配 15 个 controller 名前缀）
