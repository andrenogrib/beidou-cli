# beidou

BeiDou-Server 命令行工具，供 AI agent（Claude Code、OpenCode、Codex 等）通过 API 操作服务端控制台功能。

## 构建

```bash
# JAR 包
mvn package -DskipTests

# GraalVM 原生二进制（需要 GraalVM JDK 21）
mvn package -DskipTests -Pnative
```

## 使用

```bash
# 1. 配置服务端连接信息
beidou config --server http://localhost:8686 --username admin --password 123456
# 或交互式
beidou config

# 2. 查看当前配置
beidou config --show

# 3. 调用 API
beidou call GET /server/v1/online
beidou call GET /server/v1/version
beidou call POST /character/v1/online/list '{"pageNum":1,"pageSize":10}'
beidou call GET /server/v1/channel/list?worldId=1
beidou call GET /server/v1/stopServer

# 4. 强制重新登录
beidou login
```

## 认证机制

- 首次调用时自动登录，JWT token 缓存在 `~/.beidou-cli/config.json`
- token 过期前自动刷新
- 收到 401 时自动重新登录重试，最多 3 次，超过则报错退出（检查用户名/密码/地址）
