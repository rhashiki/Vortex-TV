# Build 27 — Diagnóstico e correções de reprodução (28/10/2026)

## Recebido nos prints do telefone
- O laboratório abriu um diálogo com a mensagem, mas sem lista selecionável: Android AlertDialog com setMessage() e setItems() na mesma janela.
- O player Media3 exibiu ERROR_CODE_IO_BAD_HTTP_STATUS, mas ocultava o número HTTP e a URL/recurso que falhou.
- O YouTube exibiu 152-4: embed incorporado com youtube.com como origem da página; contexto rejeitado pelo player.

## Corrigido no Android
1. Removida a mensagem da janela com setItems(): as amostras devem aparecer na lista. A confirmação individual mantém o aviso de falta de homologação.
2. Media3 agora inspeciona HttpDataSource.InvalidResponseCodeException, apresenta código HTTP real (403/404/429), host e tipo de recurso (manifesto/segmento). Não exibe parâmetros de URL.
3. WebView do YouTube passa a utilizar o domínio do serviço Vortex TV no Render como origem legítima de terceiros, mantendo iframe oficial e Referer policy; não há extração nem bypass do YouTube. Se o dono bloquear embeds, usar o botão oficial 'Abrir no YouTube'.
4. Android versão 0.2.9-player-fixes (versionCode 29); 37 testes Python OK e Gradle assembleDebug OK; verificador estrutural APK OK. Render LIVE.

## Alternativa no laboratório
Foi cadastrada uma segunda opção 'Big Buck Bunny (CDN alternativa)', servida por JW Player, com manifesto HTTP 200, para testar diferença entre provedores. Não significa que o vídeo reproduz e não é promovida para catálogo principal.

## Status e próximo teste
- Supabase Vortex TV Cloud continua com catálogo principal sem nenhuma fonte playback_ok; somente laboratório oferece amostras.
- Os prints são feitos em celular, portanto **não** confirmam execução na TV Box RK3229.
- Instalar a build 0.2.9, abrir 'Fontes → Laboratório: testar filmes livres', selecionar o filme, tentar reprodução. Fotografar a nova mensagem detalhada se persistir erro.
- Testar o YouTube; alguns vídeos ainda podem recusar incorporação por política do proprietário (abrir no YouTube é fallback legítimo).
- Se a assinatura de APK debug mudar, o Android pode exigir desinstalar a anterior, com perda dos dados locais. Não desinstalar sem checar favoritos e fontes locais.

APK: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk

Commit Android: https://github.com/rhashiki/Vortex-TV/commit/fce3c10a8db996b23220d078647cdd5b070329f5
