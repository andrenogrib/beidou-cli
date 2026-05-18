package org.gms;

import org.gms.auth.AuthManager;
import org.gms.config.CliConfig;
import org.gms.http.ApiClient;
import picocli.CommandLine;
import picocli.CommandLine.*;

import java.util.concurrent.Callable;

@Command(
        name = "beidou",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        description = "BeiDou-Server 命令行工具",
        subcommands = {
                Main.ConfigCommand.class,
                Main.LoginCommand.class,
                Main.CallCommand.class
        }
)
public class Main implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "config", description = "配置或查看服务端连接信息")
    static class ConfigCommand implements Callable<Integer> {

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

    @Command(name = "call", description = "调用服务端 API")
    static class CallCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "HTTP 方法: GET, POST, PUT, DELETE")
        String method;

        @Parameters(index = "1", description = "API 路径, 如 /server/v1/online")
        String path;

        @Parameters(index = "2", arity = "0..1", description = "可选的 JSON body")
        String body;

        @Override
        public Integer call() {
            method = method.toUpperCase();
            if (!method.matches("GET|POST|PUT|DELETE|PATCH")) {
                System.err.println("不支持的方法: " + method + "，支持: GET, POST, PUT, DELETE, PATCH");
                return 1;
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

    public static void main(String[] args) {
        var exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
