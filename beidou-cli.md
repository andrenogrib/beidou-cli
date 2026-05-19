---
name: beidou-cli
description: Use the beidou CLI to interact with BeiDou-Server via HTTP API.
---

# beidou CLI Guide

You have the `beidou` CLI available, a tool for managing a game server (BeiDou-Server) through HTTP API.

## TL;DR

```
beidou config --server <url> --username <user> --password <pass>
beidou apis [keyword]          # discover available endpoints
beidou call GET /server/v1/online  # single API call
echo '
GET /server/v1/online
POST /drop/v1/getDropList {"pageNum":1,"pageSize":10}
' | beidou batch               # multiple calls in one shell invocation
```

## Command Reference

| Command | Description |
|---------|-------------|
| `beidou config` | Configure server connection (or `--show` to view) |
| `beidou config --server <url> --username <user> --password <pass>` | Set credentials |
| `beidou login` | Force re-login to refresh the JWT token |
| `beidou version` | Show CLI version |
| `beidou apis [keyword]` | List all 76 API endpoints, optional keyword filter |
| `beidou call <METHOD> <path> [body] [--force]` | Call a single API |
| `beidou batch` | Execute multiple API calls from stdin, one per line |
| `beidou update` | Download and install the latest version |
| `beidou uninstall` | Remove the binary and clean up PATH |

## Platform Quick Reference

| OS | Shell | Path Prefix | Batch Chaining |
|----|-------|-------------|----------------|
| Windows CMD | cmd | path as-is | `&&` |
| Windows Git Bash | bash | `MSYS_NO_PATHCONV=1 ./beidou.exe …` | `&&` |
| Linux/macOS | bash | `/path/to/beidou` | `&&` |

**Git Bash (Windows)**: Always prepend `MSYS_NO_PATHCONV=1` (or use `./beidou.exe`). Otherwise MSYS converts `/server/...` to `D:/Program Files/Git/server/...`.

**CMD (Windows)**: The CLI auto-runs `chcp 65001` to set UTF-8. Output is fine. Direct invocation, no extra flags needed.

## Workflow

### 1. Configure (once)
```bash
beidou config --server http://192.168.0.21:8686 --username admin --password admin
```
Credentials are saved to `~/.beidou-cli/config.json`. No manual login needed — the CLI auto-authenticates on the first API call.

### 2. Discover APIs
```bash
beidou apis               # all 76 endpoints
beidou apis server        # filter by keyword: only server-related APIs
beidou apis drop          # drop/rate related APIs
```
Each entry shows: HTTP method, path, description, and `[敏感]` marker if it needs `--force`.

### 3. Call APIs

**Single request:**
```bash
beidou call GET /server/v1/online
beidou call POST /character/v1/online/list '{"pageNum":1,"pageSize":10}'
```

**Sensitive operations** (shutdown, delete, modify data) require `--force`:
```bash
beidou call --force GET /server/v1/shutdown
```

**Multiple requests together** (preferred for AI agents — one shell call, one permission prompt):
```bash
# Method A: pipe to batch (cleanest)
echo '
GET /server/v1/online
GET /server/v1/version
POST /character/v1/online/list {"pageNum":1,"pageSize":5}
' | beidou batch

# Method B: && chain
beidou call GET /server/v1/online && beidou call GET /server/v1/version
```

## Maintaining the CLI

```bash
beidou update       # upgrade to the latest release
beidou uninstall    # remove beidou from the system
```

## Important Conventions

- **Success code is 20000** (NOT HTTP 200). The server uses `BizExceptionEnum.SUCCESS = 20000`.
- **`/auth/**` paths are auth-free** — no token needed. `call POST /auth/v1/login` works without prior login.
- **Auto-wrap**: POST/PUT bodies are auto-wrapped in `{"data": <body>}`. You send the inner payload directly.
- **Config first**: If `beidou call` says "未配置", run `beidou config` with server/username/password.
- **Token auto-refresh**: The CLI caches JWT tokens. Expired tokens are auto-refreshed. No manual `beidou login` needed unless forced.

## Common Pitfalls

1. **"登录失败: 成功!"** → The success code check was wrong (fixed). If you see this, update the CLI binary.
2. **`NoSuchElementException`** → You ran `beidou config` without stdin. Use `beidou config --server ... --username ... --password ...` in non-interactive mode.
3. **`Unknown option: '--help'` on subcommand** → Now fixed. All subcommands support `--help`.
4. **API 40000 "请求的数据格式不符"** → The JSON body probably uses wrong field names. Check with `beidou apis <keyword>` for the correct format.
5. **Git Bash path corruption** → The CLI auto-fixes MSYS-converted paths. If broken, add `MSYS_NO_PATHCONV=1`.
