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
$RegPath = "HKCU:\Environment"
$Current = (Get-ItemProperty -Path $RegPath -Name PATH -ErrorAction SilentlyContinue).PATH
if ($Current -notlike "*$InstallDir*") {
    $NewPath = if ($Current) { "$Current;$InstallDir" } else { $InstallDir }
    Set-ItemProperty -Path $RegPath -Name PATH -Value $NewPath
    Write-Host "[OK] Added to user PATH"
}

Write-Host ""
Write-Host "Done. Restart your terminal and run 'beidou --help'."
Write-Host "============================================================"
