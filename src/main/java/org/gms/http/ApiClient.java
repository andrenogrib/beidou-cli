package org.gms.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gms.auth.AuthManager;
import org.gms.config.CliConfig;
import org.gms.model.SubmitBody;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ApiClient {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final int MAX_LOGIN_ATTEMPTS = 3;

    private final CliConfig config;
    private final AuthManager authManager;

    public ApiClient(CliConfig config) {
        this.config = config;
        this.authManager = new AuthManager(config);
    }

    public void call(String method, String path, String bodyJson) {
        var token = authManager.ensureToken();
        var loginAttempts = 1;

        while (true) {
            try {
                var response = sendRequest(method, path, bodyJson, token);
                if (response.statusCode() == 401) {
                    if (loginAttempts >= MAX_LOGIN_ATTEMPTS) {
                        System.err.println("请求失败: 连续 " + MAX_LOGIN_ATTEMPTS + " 次认证失败");
                        System.err.println("请确认用户名、密码和服务地址配置正确");
                        System.err.println("当前配置: server=" + config.getServer() + " username=" + config.getUsername());
                        System.exit(1);
                    }
                    // 清空旧 token，重新登录再重试
                    authManager.clearToken();
                    token = authManager.forceLogin();
                    loginAttempts++;
                    continue;
                }
                // 正常输出结果
                var responseBody = response.body();
                var node = MAPPER.readTree(responseBody);
                System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node));
                return;
            } catch (IOException e) {
                System.err.println("请求失败: " + e.getMessage());
                System.exit(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.exit(1);
            }
        }
    }

    private HttpResponse<String> sendRequest(String method, String path, String bodyJson, String token)
            throws IOException, InterruptedException {
        var uri = URI.create(config.getServer() + path);
        var builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token);

        if (bodyJson != null && !bodyJson.isBlank()) {
            // 包装到 SubmitBody.data
            String wrapped;
            try {
                var dataNode = MAPPER.readTree(bodyJson);
                wrapped = MAPPER.writeValueAsString(new SubmitBody(dataNode));
            } catch (IOException e) {
                // 非 JSON 时原样放入 data
                wrapped = MAPPER.writeValueAsString(new SubmitBody(bodyJson));
            }
            var bodyPublisher = HttpRequest.BodyPublishers.ofString(wrapped);
            builder.header("Content-Type", "application/json");
            switch (method.toUpperCase()) {
                case "POST" -> builder.POST(bodyPublisher);
                case "PUT" -> builder.PUT(bodyPublisher);
                case "PATCH" -> builder.method("PATCH", bodyPublisher);
                case "DELETE" -> builder.method("DELETE", bodyPublisher);
                default -> builder.method(method.toUpperCase(), bodyPublisher);
            }
        } else {
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
        }

        builder.timeout(Duration.ofSeconds(30));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
