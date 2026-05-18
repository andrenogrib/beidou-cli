package org.gms.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CliConfig {
    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".beidou-cli");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private String server;
    private String username;
    private String password;
    private String token;
    private Long tokenExpiry;

    public static CliConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            return new CliConfig();
        }
        try {
            return MAPPER.readValue(CONFIG_FILE.toFile(), CliConfig.class);
        } catch (IOException e) {
            return new CliConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            MAPPER.writeValue(CONFIG_FILE.toFile(), this);
        } catch (IOException e) {
            System.err.println("保存配置失败: " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return server != null && !server.isBlank()
                && username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }

    @JsonIgnore
    public boolean isTokenValid() {
        return token != null && !token.isBlank() && tokenExpiry != null
                && System.currentTimeMillis() < tokenExpiry - 60_000;
    }

    // display helper
    @JsonIgnore
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("server: ").append(server != null ? server : "(未设置)").append("\n");
        sb.append("username: ").append(username != null ? username : "(未设置)").append("\n");
        sb.append("password: ").append(password != null ? "****" : "(未设置)").append("\n");
        sb.append("token: ").append(token != null ? "已缓存" : "(未获取)");
        return sb.toString();
    }

    // getters / setters
    public String getServer() { return server; }
    public void setServer(String server) { this.server = stripTrailingSlash(server); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Long getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(Long tokenExpiry) { this.tokenExpiry = tokenExpiry; }

    private static String stripTrailingSlash(String s) {
        if (s != null && s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
