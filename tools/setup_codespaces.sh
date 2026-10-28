#!/usr/bin/env bash
set -euo pipefail

# Instalacao local no Codespaces. Nenhum GitHub Actions e utilizado.
# O SDK Android esta sujeito a licencas; o sdkmanager pedira aceite interativo.
SDK_ROOT="$HOME/android-sdk"
GRADLE_VERSION="8.9"
GRADLE_DIR="$HOME/.local/gradle-$GRADLE_VERSION"
CMDLINE_ZIP="commandlinetools-linux-15859902_latest.zip"
CMDLINE_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

command -v java >/dev/null || { echo "Java JDK 17 ausente." >&2; exit 2; }
command -v curl >/dev/null || { echo "curl ausente." >&2; exit 2; }
command -v unzip >/dev/null || { echo "unzip ausente." >&2; exit 2; }

if [ ! -x "$SDKMANAGER" ]; then
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  echo "Baixando ferramentas Android oficiais..."
  curl -fL --retry 3 "https://dl.google.com/android/repository/$CMDLINE_ZIP" -o "$tmpdir/$CMDLINE_ZIP"
  echo "$CMDLINE_SHA256  $tmpdir/$CMDLINE_ZIP" | sha256sum -c -
  unzip -q "$tmpdir/$CMDLINE_ZIP" -d "$tmpdir/extracted"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  mv "$tmpdir/extracted/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

echo "Aceite as licencas apresentadas pelo SDK Manager, se concordar."
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses
"$SDKMANAGER" --sdk_root="$SDK_ROOT" "platforms;android-35" "build-tools;35.0.0" "platform-tools"

if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  tmpdir="$(mktemp -d)"
  trap 'rm -rf "$tmpdir"' EXIT
  echo "Baixando Gradle $GRADLE_VERSION ..."
  curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$tmpdir/gradle.zip"
  mkdir -p "$HOME/.local"
  unzip -q "$tmpdir/gradle.zip" -d "$HOME/.local"
fi

echo
echo "Android SDK: $SDK_ROOT"
echo "Gradle: $GRADLE_DIR/bin/gradle"
echo "Execute agora: bash tools/build_linux.sh"
