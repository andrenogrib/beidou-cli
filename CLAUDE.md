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

## 技术栈

- **CLI**: picocli 4.7.6（子命令模式）
- **HTTP**: java.net.http.HttpClient（JDK 内置，native-image 兼容）
- **JSON**: Jackson 2.17（`ObjectMapper` + Java record 作为 DTO）
- **构建**: Maven shade 插件打 fat jar，`org.graalvm.buildtools:native-maven-plugin` 按 `-Pnative` profile 进行原生编译
- **反射配置**: `src/main/resources/META-INF/native-image/reflect-config.json`（picocli 命令类、model 类、Jackson 节点类），新增 model 或命令类需同步更新该文件

## 架构

```
Main.java  ──>  ConfigCommand   (beidou config / beidou config --show)
           ──>  LoginCommand    (beidou login)
           ──>  CallCommand     (beidou call <method> <path> [body])
                     │
                     └──> ApiClient ──> AuthManager ──> CliConfig
                               │              │
                               │   login ─────┘ POST /auth/v1/login
                               │   parseJwtExpiry ─  Base64 解码 payload.exp
                               │
                               └──> sendRequest ──> java.net.http.HttpClient
```

- **CliConfig** (`config/`): 单例配置对象，JSON 序列化到 `~/.beidou-cli/config.json`。字段：server/username/password（用户配置）、token/tokenExpiry（运行时缓存）。`isTokenValid()` 提前 60s 将 token 视为过期。
- **AuthManager** (`auth/`): 调用 `/auth/v1/login`，返回 token 写入 CliConfig。JWT 不验签，仅 Base64 解码 payload 取 `exp` 字段用于本地过期判断（服务端会验签）。`forceLogin()` 在连接失败或登录失败时直接 `System.exit(1)`。
- **ApiClient** (`http/`): 所有 API 调用的统一入口。自动附加 `Authorization: Bearer <token>`。POST body 自动包装为 `SubmitBody { requestId, data: <body> }`。401 时清空 token → 重新 login → 重试，最多 3 次，3 次仍 401 则输出错误并 `System.exit(1)`。

## 服务端 API 约定（BeiDou-Server）

- 地址: `/auth/v1/login`、`/server/v1/online`、`/character/v1/online/list` 等
- 认证: JWT (HS512)，Authorization header: `Bearer <token>`，有效期 30min
- 请求格式（POST）: `{ "data": { ... } }` — 对应 `SubmitBody` record
- 响应格式: `{ "code": 200/..., "message": "...", "responseId": "...", "data": ... }` — 对应 `ResultBody` record
- `/auth/**` 路径免认证，其余需要 token
