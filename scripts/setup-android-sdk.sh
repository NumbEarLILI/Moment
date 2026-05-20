#!/usr/bin/env bash
# Idempotent Android SDK setup for CI and Cursor cloud agents.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$ROOT_DIR/.android-sdk}}"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

CMDLINE_TOOLS_BUILD_ID="${ANDROID_CMDLINE_TOOLS_BUILD_ID:-14742923}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_BUILD_ID}_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

if [[ ! -x "$SDKMANAGER" ]]; then
  echo "Installing Android SDK command-line tools (${CMDLINE_TOOLS_BUILD_ID}) into ${ANDROID_SDK_ROOT}"
  tmp_zip="$(mktemp)"
  trap 'rm -f "$tmp_zip"' EXIT
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp_zip"
  tmp_dir="$(mktemp -d)"
  unzip -q "$tmp_zip" -d "$tmp_dir"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  # Zip layout: cmdline-tools/{bin,lib,...}
  cp -a "$tmp_dir/cmdline-tools/." "$ANDROID_SDK_ROOT/cmdline-tools/latest/"
  rm -rf "$tmp_dir" "$tmp_zip"
  trap - EXIT
fi

echo "Accepting Android SDK licenses"
# sdkmanager closes stdin early; SIGPIPE from yes must not fail the script (pipefail).
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true

echo "Installing platform-tools, Android 36 platform, and build-tools 36.0.0"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "platforms;android-36" \
  "build-tools;36.0.0"

# AGP still reads sdk.dir from local.properties; write it when missing or sdk.dir differs.
sdk_dir_escaped="${ANDROID_SDK_ROOT//\\/\\\\}"
if [[ ! -f "$LOCAL_PROPERTIES" ]] || ! grep -q "^sdk\\.dir=" "$LOCAL_PROPERTIES" 2>/dev/null; then
  {
    if [[ -f "$LOCAL_PROPERTIES" ]]; then
      grep -v '^sdk\.dir=' "$LOCAL_PROPERTIES" || true
    fi
    echo "sdk.dir=$sdk_dir_escaped"
  } >"${LOCAL_PROPERTIES}.tmp"
  mv "${LOCAL_PROPERTIES}.tmp" "$LOCAL_PROPERTIES"
  echo "Wrote sdk.dir to ${LOCAL_PROPERTIES}"
fi

echo "Android SDK ready at ${ANDROID_SDK_ROOT}"
"$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --list_installed | grep -E 'platform-tools|platforms;android-36|build-tools;36.0.0' || true
