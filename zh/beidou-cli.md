---
name: beidou-cli
description: Use the beidou CLI to interact with BeiDou-Server via HTTP API. ALWAYS use `beidou batch` for 2+ API calls — never make multiple individual `beidou call` commands.
---

# beidou CLI Guide

`beidou` is a CLI tool for managing BeiDou-Server through HTTP API.

**GOLDEN RULE: When you need 2 or more API calls, combine them with `echo '...' | beidou batch`. Each separate `beidou call` triggers its own permission prompt. Batch = one prompt for all.**

## If `beidou` Is Not Installed

Check by running `beidou version`. If the command is not found, install with the exact one-liner below — do NOT manually download or build from source:

**Windows (PowerShell):**
```powershell
irm https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install-cn.ps1 | iex
```
If the mirror is unavailable, fall back to:
```powershell
irm https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.ps1 | iex
```

**macOS / Linux:**
```bash
curl -fsSL https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install-cn.sh | bash
```
If the mirror is unavailable, fall back to:
```bash
curl -fsSL https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.sh | bash
```

The script handles download, PATH setup, and platform detection automatically. After install, restart the terminal or reload PATH.

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

**CRITICAL: When you need 2 or more API calls, you MUST use `beidou batch`. Each `beidou call` is a separate shell invocation that triggers its own permission prompt. Batching combines all calls into ONE shell command — one permission, one wait.**

**Batch multiple requests (default for >1 call):**
```bash
echo '
GET /server/v1/online
GET /server/v1/version
POST /character/v1/online/list {"pageNum":1,"pageSize":5}
POST /drop/v1/getDropList {"pageNum":1,"pageSize":200}
' | beidou batch
```
All results are printed together. Sensitive operations still work in batch by prefixing the line with `--force`.

**Single request (only when you truly need just ONE call):**
```bash
beidou call GET /server/v1/online
beidou call POST /character/v1/online/list '{"pageNum":1,"pageSize":10}'
```

**Sensitive operations** (shutdown, delete, modify data) require `--force`:
```bash
beidou call --force GET /server/v1/shutdown
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
