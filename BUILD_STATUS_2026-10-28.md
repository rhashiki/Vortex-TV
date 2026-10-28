# Vortex TV — Status Build 26 · 28/10/2026

- APK teste Android: 0.2.8-cloud-lab, minSdk 25, Media3.
- Build Render LIVE: 33 testes Python, Gradle assembleDebug, verificador APK aprovados.
- Supabase: Vortex TV Cloud (nozbyjbtxbgchvkdpnrp, São Paulo, Free).
- Banco: 11 fontes candidatas, 11 verificações; 5 manifestos HLS válidos, 2 HTTP 403, 4 indisponíveis. Nenhum playback_ok no dispositivo.
- Cron: quatro tarefas ativas para verificação e descoberta diária restrita.
- API: GET produção = items vazio; GET ?lab=1 = Big Buck Bunny e Tears of Steel para teste.
- Interface: Fontes → Laboratório: testar filmes livres.
- Segurança: RLS habilitado, tabelas sem grants públicos, pg_net no schema extensions, nenhuma retransmissão de vídeo.
- Política: sem GitHub Actions; não confundir aplicativo com Vortex TV OS.

**Pendências:** homologação RK3229 Android 7.1 (imagem, áudio, controle, Ethernet, memória), licenças de canais e assinatura estável do APK.

APK: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk
Mais detalhes: docs/BUILD26.md
