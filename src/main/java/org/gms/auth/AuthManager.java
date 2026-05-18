package org.gms.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gms.config.CliConfig;
import org.gms.model.ResultBody;
import org.gms.model.SubmitBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

public class AuthManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final CliConfig config;

    public AuthManager(CliConfig config) {
        this.config = config;
    }

    public String ensureToken() {
        if (config.isTokenValid()) {
            return config.getToken();
        }
        return forceLogin();
    }

    public String forceLogin() {
        var loginUrl = URI.create(config.getServer() + "/auth/v1/login");
        var submitBody = new SubmitBody(Map.of("username", config.getUsername(), "password", config.getPassword()));

        try {
            var bodyJson = MAPPER.writeValueAsString(submitBody);
            var request = HttpRequest.newBuilder()
                    .uri(loginUrl)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var result = MAPPER.readValue(response.body(), new TypeReference<ResultBody>() {});

            if (!result.isSuccess()) {
                System.err.println("登录失败: " + result.message());
                System.err.println("请检查用户名、密码和服务地址是否正确");
                System.exit(1);
            }

            var data = (Map<String, String>) result.data();
            var token = data.get("token");
            if (token == null) {
                System.err.println("登录失败: 服务端未返回 token");
                System.exit(1);
            }

            // 解析 JWT 获取过期时间
            var exp = parseJwtExpiry(token);
            config.setToken(token);
            config.setTokenExpiry(exp);
            config.save();

            return token;
        } catch (IOException e) {
            System.err.println("连接服务端失败: " + e.getMessage());
            System.exit(1);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.exit(1);
            return null;
        }
    }

    public void clearToken() {
        config.setToken(null);
        config.setTokenExpiry(null);
        config.save();
    }

    static long parseJwtExpiry(String token) {
        var parts = token.split("\\.");
        if (parts.length < 2) {
            return System.currentTimeMillis();
        }
        try {
            var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var node = MAPPER.readTree(payload);
            var exp = node.get("exp");
            if (exp != null) {
                return exp.asLong() * 1000; // jwt exp is in seconds
            }
        } catch (Exception ignored) {
        }
        return System.currentTimeMillis();
    }
}
