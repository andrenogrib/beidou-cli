$InstallDir = "$env:USERPROFILE\bin"
# 国内镜像：通过 ghproxy.net 代理加速 GitHub 下载
$Url = "https://ghproxy.net/https://github.com/BeiDouMS/beidou-cli/releases/latest/download/beidou-windows.exe"

Write-Host "============================================================"
Write-Host "  beidou installer (CN mirror)"
Write-Host "============================================================"

Write-Host "Downloading $Url ..."
$Tmp = "$env:TEMP\beidou.exe"
Invoke-WebRequest -Uri $Url -OutFile $Tmp

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
