# Vortex TV — Build 26 · 28/10/2026

## Implementado
- Supabase Vortex TV Cloud (nozbyjbtxbgchvkdpnrp, São Paulo) com esquema protegido por RLS.
- 11 fontes candidatas e 11 testes: 5 manifestos HLS válidos (Big Buck Bunny, Tears of Steel, TV Brasil, TV Câmara e Canal Gov); 2 HTTP 403 (Sintel, DW Brasil); 4 indisponíveis (France 24, TV Câmara 2, NASA TV e TV Senado).
- 4 agendamentos ativos: fila de verificações a cada 6h, processamento a cada 2min, descoberta institucional diária e processamento a cada 10min.
- Playlist brasileira IPTV-org consumida automaticamente, filtrada por tvg-id institucional e domínio HTTPS oficial. Importou Canal Gov e TV Câmara 2. URLs de IP e canais pagos não são importados.
- pg_net realocado para extensions; índice da FK de probes adicionado; RLS sem policies é intencional, pois as tabelas não são acessíveis anonimamente.
- API Supabase versão 2: GET catálogo normal retorna items vazio (zero fontes homologadas); GET ?lab=1 retorna duas fontes abertas em manifest_ok para testes, sem mudar o status para playback_ok.
- APK Android 0.2.8-cloud-lab com opção Fontes → Laboratório: testar filmes livres. Player Media3 com HLS, Android mínimo 7.1.
- Render deploy LIVE, 33 testes Python aprovados, Gradle assembleDebug e verificação estrutural do APK aprovados. Zero GitHub Actions.

## Pendências
1. Testar imagem/áudio, Ethernet, D-pad, desempenho e instalação na TV Box MXQ PRO RK3229 Android 7.1. Não há playback_ok confirmado.
2. Só publicar canais após comprovação de reprodução e direito de integração.
3. Gerar posteriormente versão release com certificado de assinatura estável. Debug pode exigir reinstalação se o certificado variar.
4. O Vortex TV (aplicativo) é separado do Vortex TV OS (firmware).

APK: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk
Cloud: https://supabase.com/dashboard/project/nozbyjbtxbgchvkdpnrp
Backend GET: https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-catalog
Laboratório GET: https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-catalog?lab=1

Arquivos: catalog/cloud/curated_sources.json, supabase/migrations/20261028_vortex_manifest_checks.sql, supabase/migrations/20261028_vortex_scheduler.sql, supabase/migrations/20261028_vortex_directory_discovery.sql, supabase/migrations/20261028_vortex_security_advisors.sql, supabase/functions/vortex-catalog/index.ts.

HTTP 200 e manifesto válido não comprovam áudio, vídeo decodificado ou autorização de redistribuição.
