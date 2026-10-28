#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME/platforms/android-35" ]]; then
  echo 'Android SDK 35 ausente. Configure ANDROID_HOME e instale platform android-35 e build-tools.' >&2
  exit 2
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle ausente. Instale Gradle 8.9+ ou abra este projeto no Android Studio.' >&2
  exit 2
fi
gradle --no-daemon :app:assembleDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
