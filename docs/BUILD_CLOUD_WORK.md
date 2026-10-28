# Compilação Android para ambiente Work/cloud com rede

1. Instalar JDK 17, Android command-line tools, SDK Platform 35, Android Build Tools e Gradle 8.9+ (AGP 8.7.3).
2. Abrir a pasta `VortexTV_App_Source_UI01` extraída do ZIP, sem usar o empacotador WebView antigo.
3. Executar `gradle --no-daemon :app:assembleDebug` e guardar logs de erro se falhar.
4. Executar `python3 tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`. Nunca substituir o resultado por um APK WebView sem Media3.
5. Homologar em Android 7.1 ARMv7 e smartphone: escolha de modo, reprodução, D-pad, EPG, fonte alternativa, login ausente e ausência de anúncios próprios. Não afirmar 100% de estabilidade das fontes externas.
6. Se homologado, criar um repositório **separado** `Vortex-TV` (não o firmware `VORTEX-TV-OS`), publicar fonte e APK assinados em GitHub Releases, confirmar URL permanente de download.
7. Nunca embutir credenciais recuperadas de APKs terceiros, nem contornar DRM/autenticação, nem declarar canais pagos como testados sem sinal real.
