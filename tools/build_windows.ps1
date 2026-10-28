$ErrorActionPreference = 'Stop'
if (-not $env:ANDROID_HOME -or -not (Test-Path "$env:ANDROID_HOME\platforms\android-35")) {
    throw 'Instale Android SDK Platform 35 e defina ANDROID_HOME.'
}
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {throw 'Gradle 8.9+ não encontrado. Abra no Android Studio ou instale Gradle.'}
gradle --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) {throw 'Compilação falhou'}
python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) {throw 'Verificação do APK falhou'}
