#!/usr/bin/env bash
set -euo pipefail

# 国内镜像：通过 ghproxy.net 代理加速 GitHub 下载
BASE="https://ghproxy.net/https://github.com/BeiDouMS/beidou-cli/releases/latest/download"
INSTALL_DIR="${BEIDOU_INSTALL_DIR:-${HOME}/.local/bin}"

echo "============================================================"
echo "  beidou installer (CN mirror)"
echo "============================================================"

# Detect platform
case "$(uname -s)" in
    Linux)  PLATFORM="linux" ;;
    Darwin) PLATFORM="macos" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM="windows" ;;
    *)      echo "[FAIL] Unsupported OS: $(uname -s)"; exit 1 ;;
esac

if [ "$PLATFORM" = "windows" ]; then
    ARCH="x64"
    URL="${BASE}/beidou-windows.exe"
    BIN_NAME="beidou.exe"
else
    case "$(uname -m)" in
        x86_64|amd64) ARCH="x64" ;;
        aarch64|arm64) ARCH="aarch64" ;;
        *) echo "[FAIL] Unsupported arch: $(uname -m)"; exit 1 ;;
    esac
    URL="${BASE}/beidou-${PLATFORM}"
    BIN_NAME="beidou"
fi

echo "[OK] Platform: ${PLATFORM}/${ARCH}, downloading via ghproxy..."
echo "[URL] ${URL}"

# Download
TMP=$(mktemp)
if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$URL" -o "$TMP"
elif command -v wget > /dev/null 2>&1; then
    wget -q "$URL" -O "$TMP"
else
    echo "[FAIL] Need curl or wget."
    exit 1
fi

# Install binary
mkdir -p "$INSTALL_DIR"
mv "$TMP" "$INSTALL_DIR/$BIN_NAME"
chmod +x "$INSTALL_DIR/$BIN_NAME"
echo "[OK] Installed to $INSTALL_DIR/$BIN_NAME"

# Auto-add to PATH
if ! echo "$PATH" | tr ':' '\n' | grep -qxF "$INSTALL_DIR"; then
    LINE="export PATH=\"$INSTALL_DIR:\$PATH\""
    for rc in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.profile"; do
        if [ -f "$rc" ] && ! grep -qF "$INSTALL_DIR" "$rc" 2>/dev/null; then
            echo "$LINE" >> "$rc"
            echo "[OK] Added to $rc"
        fi
    done
fi

echo ""
echo "Done. Restart your terminal and run 'beidou --help'."
echo "============================================================"
