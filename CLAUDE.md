# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> A Chinese version of this document is available at [zh/CLAUDE.md](zh/CLAUDE.md).

## Project Overview

beidou-cli is the command-line tool for [BeiDou-Server](../BeiDou-Server/). It lets an AI agent operate the server console through the HTTP API. The compilation target is a GraalVM Native Image native binary.

## Build

```bash
mvn package -DskipTests          # fat JAR (the shade plugin bundles dependencies automatically)
mvn package -DskipTests -Pnative # GraalVM native binary (requires GraalVM JDK 21)
```

Output: `target/beidou-1.0-SNAPSHOT.jar` or `target/beidou` (native binary).

The native build can also use the scripts: `./build-native.sh` (macOS/Linux), `build-native.bat` (Windows, requires Visual Studio 2022+ with C++ desktop development).

## Tech Stack

- **CLI**: picocli 4.7.6 (subcommand mode)
- **HTTP**: java.net.http.HttpClient (built into the JDK, native-image compatible)
- **JSON**: Jackson 3.1.3 (`tools.jackson` namespace), with annotations mixed in from `com.fasterxml.jackson` (a transitive dependency); both must be registered in reflect-config.json
- **Build**: the Maven shade plugin builds the fat jar; `org.graalvm.buildtools:native-maven-plugin` performs native compilation under the `-Pnative` profile
- **Reflection config**: `src/main/resources/META-INF/native-image/reflect-config.json` (picocli command classes, model classes, Jackson node classes). When adding a model or command class, update this file accordingly. Note that both `tools.jackson.databind.node.ObjectNode` and `com.fasterxml.jackson.databind.node.ArrayNode` package names are mixed in and both need to be registered.

## Architecture

```
Main.java  ──>  ConfigCommand   (beidou config / beidou config --show)
           ──>  LoginCommand    (beidou login)
           ──>  CallCommand     (beidou call <METHOD> <path> [body] [--force])
           ──>  BatchCommand    (beidou batch — reads line by line from stdin, format: METHOD /path [body])
           ──>  VersionCommand  (beidou version)
           ──>  ApiListCommand  (beidou apis [keyword])
           ──>  UpdateCommand   (beidou update — downloads from GitHub Releases and overwrites the current binary)
           ──>  UninstallCommand(beidou uninstall — deletes the binary + cleans up PATH)
                     │
         CallCommand ─┴──> ApiClient ──> AuthManager ──> CliConfig
         BatchCommand ─┘        │              │
                                  │   login ─────┘ POST /auth/v1/login
                                  │   parseJwtExpiry ─  Base64-decode payload.exp
                                  │
                                  └──> sendRequest ──> java.net.http.HttpClient
```

- **CliConfig** (`config/`): singleton configuration object, JSON-serialized to `~/.beidou-cli/config.json`. Fields: server/username/password (user config), token/tokenExpiry (runtime cache). `isTokenValid()` treats the token as expired 60s early.
- **AuthManager** (`auth/`): calls `/auth/v1/login` and writes the returned token into CliConfig. The JWT signature is not verified; it only Base64-decodes the payload to read the `exp` field for local expiry checks (the server verifies the signature). `forceLogin()` calls `System.exit(1)` directly on connection failure or login failure.
- **CallCommand**: supports GET/POST/PUT/DELETE/PATCH. The body argument is automatically wrapped as `SubmitBody { requestId: null, data: <body> }`. With no body, GET/DELETE send no body while other methods send an empty body. Under Git Bash (MSYS), `/path` is auto-converted to `C:/.../path`; the built-in `fixMsysPath()` fixes this automatically.
- **BatchCommand**: reads `METHOD /path [body]` line by line from stdin; blank lines and lines starting with `#` are skipped. It calls `ApiClient.callForBatch()` (which throws an exception instead of `System.exit`, so subsequent requests keep executing).
- **ApiClient** (`http/`): the unified entry point for all API calls. Automatically attaches `Authorization: Bearer <token>`. POST bodies are automatically wrapped as `SubmitBody { requestId, data: <body> }`. On 401, it clears the token → logs in again → retries, up to 3 times; if still 401 after 3 attempts, `call()` does `System.exit(1)` while `callForBatch()` throws an exception.
- **JsonUtils** (`util/`): a static utility class that wraps the Jackson `ObjectMapper` (with `FAIL_ON_EMPTY_BEANS` and `FAIL_ON_UNKNOWN_PROPERTIES` disabled), providing `toJson`/`fromJson`/`readTree`/`toPrettyJson` and similar methods.
- **Main.java**: under Windows, `main()` runs `chcp 65001` and sets `System.out`/`err` to UTF-8 to prevent garbled Chinese characters. It then starts picocli via `CommandLine.execute()`.
- **UpdateCommand/UninstallCommand**: `findCurrentBinary()` looks up `beidou`/`beidou.exe` on PATH. Update downloads the platform-specific binary from GitHub Releases and overwrites it; Uninstall deletes the binary and cleans up PATH (Unix: `.bashrc`/`.zshrc`/`.profile`; Windows: the `HKCU:\Environment` registry key).

## Server API Conventions (BeiDou-Server)

- Addresses: `/auth/v1/login`, `/server/v1/online`, `/character/v1/online/list`, etc.
- Authentication: JWT (HS512), Authorization header: `Bearer <token>`, valid for 30min
- Request format (POST): `{ "data": { ... } }` — corresponds to the `SubmitBody` record
- Response format: `{ "code": 20000/40000/..., "message": "...(i18n)", "responseId": "...(UUID)", "data": ... }` — corresponds to the `ResultBody` record
- **Success code: 20000** (`BizExceptionEnum.SUCCESS.getResultCode()`), not HTTP 200
- Error codes: `40000`(BODY_NOT_MATCH), `40001`(REQUEST_METHOD_SUPPORT), `40002`(ILLEGAL_PARAMETERS), `40004`(NOT_FOUND), `50000`(INTERNAL_SERVER_ERROR), `50003`(SERVER_BUSY)
- The `message` field uses i18n internationalization (`I18nUtil.getExceptionMessage`); in a Chinese locale it returns Chinese, e.g. "成功!" ("Success!")
- **Exception handling**: `GlobalExceptionHandler` catches exceptions globally → `ResultBody.error()`; RuntimeException→40000, ServletException→40001, Exception→50000
- The server's JSON library is `fastjson2` while beidou-cli uses Jackson 3.x; the `ResultBody` structure must stay consistent on both sides
- `/auth/**` paths are auth-free; all others require a token

## Server Info (from ServerConstants.java)

- **BeiDou version**: 1.10
- **Build Time**: 2025-06-22 12:45:59
- **Protocol version (VERSION)**: 83
- Server code: `E:\LocalGit\GitHub\BeiDou-Server\gms-server\src\main\java\org\gms`

## API Discovery

- `beidou apis [keyword]` — view all available APIs and parameter descriptions (76 endpoints total, defined in `ApiListCommand.java`), with keyword filtering support
- The server has 15 Controllers total: auth, account, server, character, common, cashShop, drop, gachapon, give, inventory, shop, config, command, file, autoban
- The server may add new APIs; when updating beidou-cli, keep the `ApiListCommand.APIS` list in sync

## Sensitive Operation Protection

- `beidou call` blocks sensitive endpoints (shutdown, delete, modify data, etc.) by default, returning an error and prompting the use of `--force`
- The sensitivity check lives in `ApiListCommand.isSensitive()`: it first does an exact path match against the API list (priority), then falls back to a regex match when no exact match is found (replacing `{id}` with `[^/]+`)
- `/auth` paths skip login; all others authenticate automatically
- Git Bash (MSYS) converts paths like `/server/v1/online` to `D:/Program Files/Git/server/v1/online`; the built-in `fixMsysPath()` in `CallCommand` and `BatchCommand` fixes this automatically (matching the 15 controller-name prefixes)
