# beidou

Command-line management tool for BeiDou-Server. Lets an AI agent remotely start/stop the service, query data, change configuration, grant items, and more through the HTTP API.

> A Chinese version of this document is available at [zh/README.md](zh/README.md).

## Installation

> **Users in mainland China** should prefer the `install-cn` script, which uses the `ghproxy.net` mirror to speed up GitHub downloads.

### macOS / Linux

```bash
# Recommended in mainland China (ghproxy mirror acceleration)
curl -fsSL https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install-cn.sh | bash

# Direct GitHub connection
curl -fsSL https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.sh | bash
```

Installs to `~/.local/bin/beidou` and automatically updates `~/.bashrc` / `~/.zshrc`.

### Windows

```powershell
# Recommended in mainland China (ghproxy mirror acceleration)
irm https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install-cn.ps1 | iex

# Direct GitHub connection
irm https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/install.ps1 | iex
```

Installs and automatically adds itself to the user PATH; restart the terminal to start using it.

### Update

```bash
beidou update    # automatically downloads the latest version and overwrites the current binary
```

To uninstall:

```bash
beidou uninstall  # removes the binary and cleans up PATH
```

### Manual download

Download the binary for your platform from the [Releases](https://github.com/BeiDouMS/beidou-cli/releases/latest) page.

## Quick Start

```bash
# 1. Configure the server connection (only needed once)
beidou config --server http://localhost:8686 --username admin --password 123456

# 2. List available APIs
beidou apis

# 3. Test connectivity
beidou call GET /server/v1/online
```

## AI Agent Guide

### Installing the Skill

beidou-cli ships a skill file. An AI agent should download and register it as a skill/tool rather than treating it as reference documentation only.

**Skill file URL:**

```
https://raw.githubusercontent.com/BeiDouMS/beidou-cli/master/beidou-cli.md
```

**Generic installation:**

1. Download the file at the URL above.
2. Register it as a skill according to your agent platform's requirements (e.g., place it in the skills directory, register it as a custom command, etc.).

**Claude Code users:**

Put the file in `~/.claude/commands/` (global, recommended) or `<project>/.claude/commands/` (current project only), and you can invoke it directly with the `/beidou-cli` command.

### Installing the beidou CLI binary

If you have already installed the beidou CLI binary, proceed in this order:

```bash
# Step 1: Confirm the CLI is available (binary on PATH)
beidou version

# Step 2: Have the user provide configuration, or run
beidou config --server <url> --username <user> --password <pass>

# Step 3: List all available endpoints
beidou apis

# Step 4: Call an API (single call)
beidou call GET /server/v1/online

# Batch query (all results returned in one shell invocation, fewer round trips)
echo '
GET /server/v1/online
GET /server/v1/version
POST /character/v1/online/list {"pageNum":1,"pageSize":10}
' | beidou batch
```

**Notes:**
- Under Git Bash (Windows), path arguments are auto-converted by MSYS; beidou fixes this automatically. If you still hit problems, add `MSYS_NO_PATHCONV=1`.
- Sensitive operations (shutdown, delete, modify data) are blocked by default and require `--force`.
- The success code is `20000` (not HTTP 200) — don't mistake it for a failure.
- The first `call` logs in automatically to obtain a token; no manual `beidou login` is needed.
- In a non-interactive environment, `beidou config` errors out and tells you to use command-line arguments.

## Build

To build from source:

```bash
# JAR
mvn package -DskipTests

# Native binary (requires GraalVM JDK 21 + native-image)
# Linux/macOS: requires GCC/Xcode
# Windows: requires Visual Studio 2022+ with C++ desktop development
./build-native.sh  # macOS/Linux
build-native.bat   # Windows
```
