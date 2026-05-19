package org.gms.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

@Command(name = "update", description = "更新 beidou 到最新版本")
public class UpdateCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    private static final String BASE = "https://github.com/BeiDouMS/beidou-cli/releases/latest/download";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Override
    public Integer call() {
        var currentPath = findCurrentBinary();
        if (currentPath == null) {
            System.err.println("未找到 beidou 二进制文件");
            return 1;
        }
        System.out.println("当前安装: " + currentPath);

        String platform;
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) platform = "windows";
        else if (os.contains("mac")) platform = "macos";
        else platform = "linux";

        String ext = platform.equals("windows") ? ".exe" : "";
        String url = BASE + "/beidou-" + platform + ext;

        System.out.println("下载: " + url);
        try {
            var tmp = Files.createTempFile("beidou", ext);
            var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tmp);
                System.err.println("下载失败: HTTP " + response.statusCode());
                return 1;
            }

            if (platform.equals("windows")) {
                // Write new binary as <target>.new, spawn script to replace on exit
                var newExe = Path.of(currentPath.toString() + ".new");
                Files.move(tmp, newExe, StandardCopyOption.REPLACE_EXISTING);
                spawnDeferred(ProcessHandle.current().pid(),
                        "move /y \"" + newExe + "\" \"" + currentPath + "\"",
                        "del /f \"" + newExe + "\"");
                System.out.println("更新将在进程退出后生效: " + currentPath);
            } else {
                Files.move(tmp, currentPath, StandardCopyOption.REPLACE_EXISTING);
                if (!currentPath.toFile().setExecutable(true)) {
                    System.err.println("[WARN] 无法设置可执行权限");
                }
                System.out.println("已更新: " + currentPath);
            }
            return 0;
        } catch (IOException | InterruptedException e) {
            System.err.println("更新失败: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Spawns a detached batch script that waits for {@code pid} to exit,
     * then retries {@code command} up to 5 times. If all retries fail and
     * {@code cleanup} is non-empty, runs cleanup before self-deleting.
     * Used by both update (move new over old) and uninstall (delete old).
     */
    static void spawnDeferred(long pid, String command, String cleanup) throws IOException {
        var script = Files.createTempFile("beidou-defer", ".bat");
        var lines = new java.util.ArrayList<String>();
        lines.add("@echo off");
        lines.add("setlocal enabledelayedexpansion");
        lines.add("set PID=" + pid);
        lines.add(":wait");
        lines.add("tasklist /fi \"pid eq %PID%\" 2>nul | find \"%PID%\" >nul");
        lines.add("if not errorlevel 1 (ping 127.0.0.1 -n 2 >nul & goto wait)");
        lines.add("set RETRY=0");
        lines.add(":retry");
        lines.add("set /a RETRY+=1");
        lines.add(command + " >nul 2>&1");
        lines.add("if not errorlevel 1 goto done");
        lines.add("if !RETRY! lss 5 (ping 127.0.0.1 -n 2 >nul & goto retry)");
        if (cleanup != null && !cleanup.isBlank()) {
            lines.add(cleanup + " >nul 2>&1");
        }
        lines.add(":done");
        lines.add("del \"%~f0\"");
        lines.add("");
        Files.writeString(script, String.join("\r\n", lines));
        new ProcessBuilder("cmd", "/c", "start", "\"\"", "/b", script.toString())
                .inheritIO().start();
    }

    static Path findCurrentBinary() {
        var cmd = ProcessHandle.current().info().command();
        if (cmd.isPresent()) {
            var p = Path.of(cmd.get());
            if (Files.isRegularFile(p)) return p.toAbsolutePath();
        }
        var name = System.getProperty("os.name").toLowerCase().contains("win") ? "beidou.exe" : "beidou";
        var pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (var dir : pathEnv.split(System.getProperty("path.separator"))) {
                var candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
            }
        }
        return null;
    }
}