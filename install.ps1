$InstallDir = "$env:USERPROFILE\bin"
$Url = "https://github.com/BeiDouMS/beidou-cli/releases/latest/download/beidou-windows.exe"

Write-Host "============================================================"
Write-Host "  beidou installer"
Write-Host "============================================================"

Write-Host "Downloading $Url ..."
$Tmp = "$env:TEMP\beidou.exe"

try {
    Invoke-WebRequest -Uri $Url -OutFile $Tmp -ErrorAction Stop
} catch {
    Write-Host "[FAIL] 下载失败: $($_.Exception.Message)"
    exit 1
}

if (-not (Test-Path $Tmp)) {
    Write-Host "[FAIL] 下载的文件不存在: $Tmp"
    exit 1
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Move-Item -Force $Tmp "$InstallDir\beidou.exe"
Write-Host "[OK] Installed to $InstallDir\beidou.exe"

# Auto-add to user PATH
$Current = [Environment]::GetEnvironmentVariable("PATH", "User")
if ($Current -notlike "*$InstallDir*") {
    $NewPath = if ($Current) { "$Current;$InstallDir" } else { $InstallDir }
    [Environment]::SetEnvironmentVariable("PATH", $NewPath, "User")
    Write-Host "[OK] Added to user PATH"
}

# Refresh PATH in current session
$env:Path = [Environment]::GetEnvironmentVariable("PATH", "User") + ";" + [Environment]::GetEnvironmentVariable("PATH", "Machine")

Write-Host ""
Write-Host "Done. Run 'beidou --help' to verify."
Write-Host "============================================================"
