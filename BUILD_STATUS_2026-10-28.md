# Vortex TV — status de build 28/10/2026

**NÃO EXISTE APK NATIVO COMPILADO NESTA ENTREGA.**

O projeto Android UI01 foi reconciliado com a Build 22 e contém:
- Media3/ExoPlayer com HLS/DASH/arquivo progressivo; Android mínimo 7.1 (API 25).
- Na primeira abertura, escolha TV (controle remoto) ou Smartphone (toque), armazenada localmente.
- Durante TV ao vivo, ↑/↓ muda entre canais disponíveis e ←/→ abre o guia de canais.
- 23 URLs públicas candidatas para canais + 4 curtas de acervo aberto, todas **não validadas por reprodução**; 60 canais sem fonte permanecem em metadados separados (não aparecem como reproduzíveis).
- Importação de arquivo M3U, URL HTTPS de M3U, URL direta e XMLTV por arquivo ou HTTPS.
- Logos oficiais do Vortex enviados pelo usuário.
- Falha no Media3 oferece abertura da URL em player externo instalado, quando compatível.
- Sem login, servidor próprio, bibliotecas publicitárias ou DRM bypass.

**Testes executados aqui:** compilação `javac` e testes de parser M3U/XMLTV, cinco testes Python do kit, testes estruturais dos assets e verificações do projeto. **Não houve compilação Android, assinatura, teste no emulador ou na RK3229.**

**Bloqueio técnico:** este ambiente não possui Android SDK Platform/Build Tools/Gradle nem consegue acessar os servidores de download (falha de DNS). Não renomear ZIP ou shell WebView como APK.

## Como gerar o APK em ambiente Android configurado

1. Abra esta pasta no Android Studio; instale JDK 17, Android SDK Platform 35 e Build-Tools.
2. Aguarde a sincronização Gradle com acesso a google() e mavenCentral().
3. Execute `gradle :app:assembleDebug` (AGP 8.7.3, Gradle 8.9+).
4. Execute `python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk`.
5. Instale o arquivo gerado em Android 7.1 ARM 32 bits e celular; valide vídeo, EPG, DPAD e conexão Ethernet.

**Critério de liberação:** não publicar a versão como Beta 100% funcional antes de confirmação dos testes reais. Fontes externas podem falhar e não há acesso confirmado aos canais por assinatura.
