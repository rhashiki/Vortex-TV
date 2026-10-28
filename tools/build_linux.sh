#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# Instalacao padrao feita por tools/setup_codespaces.sh
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]; then
  echo "Android SDK 35 ausente. Execute: bash tools/setup_codespaces.sh" >&2
  exit 2
fi

if [ -x "$HOME/.local/gradle-8.9/bin/gradle" ]; then
  GRADLE_CMD="$HOME/.local/gradle-8.9/bin/gradle"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="$(command -v gradle)"
else
  echo "Gradle 8.9 ausente. Execute: bash tools/setup_codespaces.sh" >&2
  exit 2
fi

python3 -m unittest discover -s tests -p 'test_*.py'
"$GRADLE_CMD" --no-daemon :app:assembleDebug
python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

echo "APK gerado: app/build/outputs/apk/debug/app-debug.apk"
