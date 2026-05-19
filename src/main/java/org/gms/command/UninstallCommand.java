package org.gms.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@Command(name = "uninstall", description = "卸载 beidou（删除二进制、配置文件和 PATH 条目）")
public class UninstallCommand implements Callable<Integer> {

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "显示帮助信息")
    boolean help;

    @Override
    public Integer call() {
        var current = UpdateCommand.findCurrentBinary();
        var configDir = Path.of(System.getProperty("user.home"), ".beidou-cli");
        var isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // 1. Delete config directory (not locked, safe to do now)
        if (Files.isDirectory(configDir)) {
            try {
                var failed = deleteRecursively(configDir);
                if (failed.isEmpty()) {
                    System.out.println("[OK] 已删除配置目录: " + configDir);
                } else {
                    System.err.println("[WARN] 部分文件删除失败:");
                    for (var f : failed) System.err.println("  " + f);
                }
            } catch (IOException e) {
                System.err.println("[WARN] 删除配置失败: " + e.getMessage());
            }
        }

        // 2. Clean PATH + delete binary
        if (current != null) {
            var dir = current.getParent().toString();
            if (isWindows) {
                cleanWindowsPath(dir);
                try {
                    // Delete binary, then remove install dir if empty
                    UpdateCommand.spawnDeferred(ProcessHandle.current().pid(),
                            "del /f \"" + current + "\" & rd \"" + dir + "\"",
                            null);
                    System.out.println("[OK] 将在进程退出后删除: " + current);
                } catch (IOException e) {
                    System.err.println("[WARN] 无法删除: " + e.getMessage());
                    System.err.println("       手动执行: del /f \"" + current + "\"");
                }
            } else {
                cleanUnixPath(dir);
                try {
                    Files.deleteIfExists(current);
                    System.out.println("[OK] 已删除: " + current);
                    // Remove install dir if empty
                    var installDir = current.getParent();
                    if (isEmptyDirectory(installDir)) {
                        Files.deleteIfExists(installDir);
                        System.out.println("[OK] 已删除空目录: " + installDir);
                    }
                } catch (IOException e) {
                    System.err.println("[WARN] 删除失败: " + e.getMessage());
                }
            }
        } else {
            // Binary not found, still try to clean PATH from default install location
            if (isWindows) {
                cleanWindowsPath(Path.of(System.getProperty("user.home"), "bin").toString());
            } else {
                cleanUnixPath(Path.of(System.getProperty("user.home"), ".local", "bin").toString());
            }
        }
        System.out.println("卸载完成");
        return 0;
    }

    /**
     * Removes dir from PATH entries in .bashrc/.zshrc/.profile.
     * Handles formats like: export PATH="/dir:$PATH", export PATH="$PATH:/dir"
     */
    private void cleanUnixPath(String dir) {
        for (var rc : new String[] {".bashrc", ".zshrc", ".profile"}) {
            var file = Path.of(System.getProperty("user.home"), rc);
            try {
                if (!Files.isRegularFile(file)) continue;
                var content = Files.readString(file);
                var lines = content.split("\n", -1);
                var modified = false;
                var newLines = new ArrayList<String>();

                for (var line : lines) {
                    var processed = removeDirFromPathLine(line, dir);
                    if (!processed.equals(line)) modified = true;
                    if (!processed.isEmpty()) newLines.add(processed);
                }

                if (modified) {
                    Files.writeString(file, String.join("\n", newLines));
                    System.out.println("[OK] 已从 " + rc + " 移除 PATH 条目");
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Removes dir from a single PATH export line.
     * Input:  export PATH="/home/user/.local/bin:$PATH"
     * Output: export PATH="$PATH"
     * Handles: "dir:$PATH", "$PATH:dir", "dir" (alone)
     */
    private String removeDirFromPathLine(String line, String dir) {
        // Match: export PATH="..."  or  export PATH=...
        var m = Pattern.compile("^(export\\s+PATH=)(\"?)(.*)\\2$").matcher(line);
        if (!m.matches()) return line;

        var prefix = m.group(1);
        var quote = m.group(2);
        var value = m.group(3);

        // Split by : and filter out the dir
        var parts = new ArrayList<String>();
        for (var part : value.split(":")) {
            if (!part.equals(dir) && !part.isEmpty()) {
                parts.add(part);
            }
        }

        if (parts.isEmpty()) {
            // Whole line was just this dir, remove entire line
            return "";
        }

        return prefix + quote + String.join(":", parts) + quote;
    }

    /**
     * Removes dir from Windows user PATH via PowerShell.
     * Escapes single quotes in dir to prevent injection.
     */
    private void cleanWindowsPath(String dir) {
        // Escape single quotes for PowerShell: ' -> ''
        var escaped = dir.replace("'", "''");
        try {
            new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "$d='" + escaped + "';" +
                    "$p=[Environment]::GetEnvironmentVariable('PATH','User');" +
                    "if($p){$p=($p.Split(';')|?{$_ -and $_ -ne $d}) -join ';';" +
                    "[Environment]::SetEnvironmentVariable('PATH',$p,'User')}"
            ).inheritIO().start().waitFor();
            System.out.println("[OK] 已从注册表移除 PATH 条目");
        } catch (Exception e) {
            System.err.println("[WARN] 清理注册表失败: " + e.getMessage());
        }
    }

    /**
     * Recursively deletes directory, returns list of files that failed to delete.
     */
    private static List<Path> deleteRecursively(Path dir) throws IOException {
        var failed = new ArrayList<Path>();
        try (var stream = Files.walk(dir)) {
            var files = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (var f : files) {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    failed.add(f);
                }
            }
        }
        return failed;
    }

    /**
     * Returns true if dir is an empty directory.
     */
    private static boolean isEmptyDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (var stream = Files.list(dir)) {
            return !stream.findFirst().isPresent();
        } catch (IOException e) {
            return false;
        }
    }
}
