# Compilacao e distribuicao de teste via Render

O Dockerfile compila o **aplicativo Android nativo** (Media3) e publica somente o APK debug e seu SHA-256, sem colocar o Android SDK na imagem de execucao.

- Plano de destino: Render Web Service Free (sem GitHub Actions).
- Build: Gradle 8.9, Java 17, Android SDK 35.
- Verificacao: testes locais e `tools/verify_apk.py`.
- Saida: `/VortexTV-Android-Debug.apk` e `/SHA256.txt`.
- Nao ha scripts de publicidade, contas ou chaves de assinatura distribuídas.
- APK debug **nao** equivale a Beta homologada. E necessario testar na TV Box Android 7.1 ARM 32 bits e no celular.

O Render recompila quando o serviço é implantado; a disponibilidade de downloads depende do serviço e do plano gratuitos.
