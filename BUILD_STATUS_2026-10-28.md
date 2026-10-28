# Vortex TV — estado técnico de 28/10/2026 (Build 25 Cloud)

## Backend
- Supabase **Vortex TV Cloud** em São Paulo, project_ref: `nozbyjbtxbgchvkdpnrp`.
- 3 tabelas instaladas com RLS habilitado: `vortex_media`, `vortex_sources` e `vortex_source_checks`.
- Acesso anônimo direto às tabelas explicitamente revogado.
- Edge Function read-only `vortex-catalog` publicada; JWT verification desativada para o endpoint público GET (somente fontes autorizadas, verificadas e marcadas playback_ok).
- Sem retransmissão de vídeo, sem chaves secretas dentro do APK, sem anúncios e sem login.
- O catálogo remoto permanece vazio até a homologação de fontes. NÃO afirmar reprodução de canais premium.

## Aplicativo Android
- Java, Media3/ExoPlayer, minSdk 25 (Android 7.1), D-pad e cache local.
- Novidade: sincronização de catálogo Cloud em segundo plano na inicialização e a cada 6 horas após sucesso; atualização manual no menu Fontes.
- Fontes locais e favoritos permanecem isolados do cache Cloud.
- Configuração em `app/src/main/assets/cloud_config.json`, apontada para a Edge Function Supabase.
- Build 0.2.7 Cloud: APK de teste Android via Render, executa 30 testes Python e verifica estrutura APK.
- **Testes reais na placa RK3229 ainda pendentes**: Ethernet, vídeo, áudio, controles, memória e estabilidade.
- Não há qualquer teste de reprodução para ESPN, AXN, Universal ou outros canais pagos.

## Operação
- Repositório: https://github.com/rhashiki/Vortex-TV
- Build Android: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk
- Documentação Cloud: docs/VORTEX_TV_CLOUD.md
- Nunca usar GitHub Actions. Render realiza build independente.
