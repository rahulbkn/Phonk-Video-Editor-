#!/data/data/com.termux/files/usr/bin/env bash
# Fetches a static arm64 ffmpeg for the *host* (desktop/test) analysis tool.
#
# The Android app itself does NOT bundle ffmpeg (keeps the APK small). At
# runtime the app looks for a drop-in binary at:
#     $filesDir/ffmpeg/ffmpeg
# (see FfmpegRenderer / ExportRunner) and shows a clear message when absent.
#
# This script is for developers/tests that need an ffmpeg CLI for the Python
# analysis bridge (decode mp4 -> wav). Prebuilt static builds:
#   - https://johnvansickle.com/ffmpeg/  (glibc, for real Linux hosts)
#   - Termux: pkg install ffmpeg         (native, on-device)
#
# Usage: scripts/fetch-ffmpeg.sh [host|termux]
set -euo pipefail

case "${1:-host}" in
  termux)
    echo "[ffmpeg] installing Termux ffmpeg package..."
    pkg install -y ffmpeg
    ffmpeg -version | head -1
    ;;
  host)
    DEST="$HOME/bin/ffmpeg"
    echo "[ffmpeg] downloading johnvansickle static build..."
    mkdir -p "$DEST.tmp" "$HOME/bin"
    curl -L -o "$HOME/bin/ffmpeg-static.tar.xz" \
      https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz
    tar -xJf "$HOME/bin/ffmpeg-static.tar.xz" -C "$DEST.tmp"
    find "$DEST.tmp" -type f -name ffmpeg -exec cp {} "$DEST" \;
    rm -rf "$DEST.tmp" "$HOME/bin/ffmpeg-static.tar.xz"
    chmod +x "$DEST"
    "$DEST" -version | head -1
    ;;
  *)
    echo "usage: $0 [host|termux]" >&2
    exit 2
    ;;
esac
