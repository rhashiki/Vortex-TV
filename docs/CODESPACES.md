# Compilar o Vortex TV com GitHub Codespaces

Projeto: rhashiki/Vortex-TV (aplicativo de streaming; nao confundir com VORTEX-TV-OS).

1. Acesse https://github.com/rhashiki/Vortex-TV
2. Escolha Code > Codespaces > Create codespace on main.
3. Abra o terminal do Codespaces.
4. Execute: bash tools/setup_codespaces.sh
5. Aceite as licencas do Android SDK, se concordar.
6. Execute: bash tools/build_linux.sh
7. Localize app/build/outputs/apk/debug/app-debug.apk no explorador de arquivos.
8. Faca download do APK para seu computador e instale na TV Box/celular.
9. Antes de anunciar uma Beta, valide no hardware real o player Media3, a troca de canais, TV/Smartphone e EPG.

A build falhara se o SDK nao estiver presente ou o verificador nao reconhecer o player nativo.
O APK gerado aqui e DEBUG. Para distribuicao publica, criar uma chave de assinatura e um release assinado, sem publicar a chave no repositorio.

**Nao usar GitHub Actions.** Codigo e compilacao via Codespaces ou outra maquina de desenvolvimento.
