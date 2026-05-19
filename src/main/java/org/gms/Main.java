package org.gms;

import org.gms.auth.AuthManager;
import org.gms.command.ApiListCommand;
import org.gms.command.BatchCommand;
import org.gms.command.UpdateCommand;
import org.gms.command.UninstallCommand;
import org.gms.config.CliConfig;
import org.gms.http.ApiClient;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(
        name = "beidou",
        versionProvider = Main.VersionProvider.class,
        description = "BeiDou-Server 游戏服务端管理工具，通过 HTTP API 管理服务器、玩家、掉落、商城等",
        header = "通过 HTTP API 远程管理游戏服务端：启停服务、查询数据、修改配置、发放道具等。",
        footerHeading = "快速开始 / 批量查询（推荐）",
        footer = {
                "  配置: beidou config --server <url> --username <user> --password <pass>",
                "  查看: beidou apis [keyword]   # 查可用 API",
                "  多个请求务必用 batch，一次 Bash 调用全返回，避免反复确认：",
                "     echo 'GET /x  POST /y {\"k\":1}' | beidou batch",
                "  单个请求: beidou call GET /server/v1/online",
        },
        subcommands = {
                Main.ConfigCommand.class,
                Main.LoginCommand.class,
                Main.CallCommand.class,
                Main.VersionCommand.class,
                ApiListCommand.class,
                BatchCommand.class,
                UpdateCommand.class,
                UninstallCommand.class
        }
)
public class Main implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    @Option(names = {"-v", "--version"}, versionHelp = true, description = "显示版本号")
    boolean version;

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "config", description = "配置或查看服务端连接信息")
    static class ConfigCommand implements Callable<Integer> {

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
        boolean help;

        @Option(names = {"-s", "--server"}, description = "服务端地址")
        String server;

        @Option(names = {"-u", "--username"}, description = "用户名")
        String username;

        @Option(names = {"-p", "--password"}, description = "密码")
        String password;

        @Option(names = "--show", description = "显示当前配置")
        boolean show;

        @Override
        public Integer call() {
            var config = CliConfig.load();

            if (show) {
                System.out.println(config.toDisplayString());
                return 0;
            }

            if (server == null && username == null && password == null) {
                if (System.console() == null) {
                    System.err.println("非交互式环境，请使用命令行参数：");
                    System.err.println("  beidou config --server <url> --username <user> --password <pass>");
                    return 1;
                }
                // 交互式配置
                try (var scanner = new java.util.Scanner(System.in)) {
                    System.out.print("服务端地址 [" + (config.getServer() != null ? config.getServer() : "http://localhost:8686") + "]: ");
                    var input = scanner.nextLine().trim();
                    if (!input.isEmpty()) {
                        config.setServer(input);
                    } else if (config.getServer() == null) {
                        config.setServer("http://localhost:8686");
                    }

                    System.out.print("用户名: ");
                    input = scanner.nextLine().trim();
                    if (!input.isEmpty()) {
                        config.setUsername(input);
                    }

                    System.out.print("密码: ");
                    input = scanner.nextLine().trim();
                    if (!input.isEmpty()) {
                        config.setPassword(input);
                    }
                }
            } else {
                if (server != null) config.setServer(server);
                if (username != null) config.setUsername(username);
                if (password != null) config.setPassword(password);
            }

            if (!config.isConfigured()) {
                System.err.println("配置不完整，请设置 server、username、password");
                return 1;
            }

            // 清除旧 token，触发重新登录验证
            config.setToken(null);
            config.setTokenExpiry(null);
            config.save();
            System.out.println("配置已保存到 ~/.beidou-cli/config.json");
            System.out.println(config.toDisplayString());
            return 0;
        }
    }

    @Command(name = "login", description = "强制重新登录获取 token")
    static class LoginCommand implements Callable<Integer> {

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
        boolean help;

        @Override
        public Integer call() {
            var config = CliConfig.load();
            if (!config.isConfigured()) {
                System.err.println("未配置，请先执行 beidou config");
                return 1;
            }
            var authManager = new AuthManager(config);
            authManager.clearToken();
            var token = authManager.forceLogin();
            if (token != null) {
                System.out.println("登录成功");
            }
            return 0;
        }
    }

    @Command(name = "version", description = "显示版本号")
    static class VersionCommand implements Callable<Integer> {

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
        boolean help;

        @Override
        public Integer call() {
            System.out.println("beidou " + readVersion());
            return 0;
        }
    }

    @Command(name = "call", description = "调用单个 API。多个请求务必用 beidou batch 合并，避免重复权限确认！")
    static class CallCommand implements Callable<Integer> {

        @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
        boolean help;

        @Parameters(index = "0", description = "HTTP 方法: GET, POST, PUT, DELETE, PATCH")
        String method;

        @Parameters(index = "1", description = "API 路径, 如 /server/v1/online")
        String path;

        @Parameters(index = "2", arity = "0..1", description = "可选的 JSON body（POST/PUT 时用）")
        String body;

        @Option(names = {"-f", "--force"}, description = "强制执行敏感操作（-f 关服/删除/修改等）")
        boolean force;

        @Override
        public Integer call() {
            method = method.toUpperCase();

            // 修复 Git Bash (MSYS) 自动路径转换：/server/v1/online → D:/Program Files/Git/server/v1/online
            if (path.matches("^[A-Za-z]:/.+")) {
                var fixed = path.replaceFirst("^[A-Za-z]:.*?/(server|auth|character|common|cashShop|drop|gachapon|give|inventory|shop|config|command|file|autoban)/", "/$1/");
                if (!fixed.equals(path)) {
                    System.err.println("[注意] MSYS 路径已自动修复: " + path + " → " + fixed);
                    path = fixed;
                }
            }
            if (!method.matches("GET|POST|PUT|DELETE|PATCH")) {
                System.err.println("不支持的方法: " + method + "，支持: GET, POST, PUT, DELETE, PATCH");
                return 1;
            }

            if (ApiListCommand.isSensitive(method, path)) {
                System.err.println("⚠  敏感操作: " + method + " " + path);
                System.err.println("   此操作可能影响线上服务或数据，请确认。");
                if (!force) {
                    System.err.println("   使用 beidou apis 查看所有接口说明");
                    System.err.println("   如需强制执行，请添加 --force 参数");
                    return 1;
                }
                System.err.println("   --force 已启用，继续执行...");
                System.err.println();
            }

            var config = CliConfig.load();
            if (!config.isConfigured()) {
                System.err.println("未配置，请先执行 beidou config --server <url> --username <user> --password <pass>");
                return 1;
            }

            var client = new ApiClient(config);
            client.call(method, path, body);
            return 0;
        }
    }

    public static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { readVersion() };
        }
    }

    static String readVersion() {
        try (var in = Main.class.getResourceAsStream("/version.txt")) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {}
        return "dev";
    }

    public static void main(String[] args) {
        // 设置控制台为 UTF-8，避免 Windows 中文乱码
        if (System.console() != null) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001 > nul").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        var exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
