# Vortex TV — Build 28 (28/10/2026)

## Entrada: falhas observadas no celular
- Player incorporado oficial do YouTube reproduziu Deu a Louca nos Bichos: sucesso confirmado por captura.
- Fontes de vídeo hospedadas no storage.googleapis.com retornaram 403 em segmento MP4, enquanto o manifesto HLS separado podia ter HTTP 200. Não era falha exclusiva do decodificador.
- Cartões de filmes e canais tinham capas/miniaturas ausentes; quatro demos antigas apontavam imagens do mesmo Google Storage.
- Selecionar uma capa abria diálogo de detalhes antes de permitir assistir; usuário exige reprodução em primeiro toque.
- Variedade insuficiente; nem todas as referências YouTube foram validadas e não se deve anunciar conteúdo com DRM ou fontes não autorizadas como funcional.

## Corrigido
1. Clique em qualquer pôster chama diretamente play(item). Pressão longa abre detalhes, favoritos e correção de título. Guia TV já reproduz direto.
2. O laboratório Vortex Cloud também inicia o player assim que o item é escolhido, sem confirmação adicional.
3. CatalogStore faz migração idempotente apenas de links demo antigos exatos (Google Storage e antigo endereço TV Câmara), preservando favoritos e importações do usuário. Sementes atualizadas para HLS Mux/Unified e URLs Archive para os demais filmes; o sucesso do Archive ainda depende de teste na rede do aparelho.
4. Big Buck Bunny passa a testar fonte secundária de HLS (JW Player) automaticamente quando a primeira origem falha, sem ocultar mensagem se todas falharem.
5. Fontes HLS com grupo Filmes/VOD não são mais classificadas incorretamente como canais ao vivo.
6. Capas de Big Buck Bunny, Sintel e Tears of Steel usam referências de pôsteres Wikimedia Commons licenciados; Elephants Dream aponta para imagem de acervo do Internet Archive. Não se confirmou ainda acesso pelo Glide no dispositivo.
7. Catálogo de referências ampliado com episódios 1–3 da série livre Caminandes e Sprite Fright; total do seed YouTube passou de 49 a 53 referências, ainda sujeitas às regras de incorporação do YouTube.

## Validação
- Commit da implementação: https://github.com/rhashiki/Vortex-TV/commit/4b302d5da57cc71fbc9952237b49a4da8337c63e
- Correção de teste de versão: https://github.com/rhashiki/Vortex-TV/commit/bd2c9d8eee6c5ebd4382e70b2f51be1d62138e89
- Render dep-dbh6udmi0phs73b17at0: LIVE às 21:57 UTC; 43 testes Python aprovados, Gradle assembleDebug bem-sucedido, verificações de estrutura Android OK.
- Versão 0.3.0-direct-play, versionCode 30; Android minSdk 25 (7.1).
- APK de testes: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk

## Limitações
- APK compilado não comprova playback na TV Box MXQ PRO RK3229 nem todas as capas/carregamentos.
- URLs do Internet Archive e vídeos YouTube recém-adicionados não foram homologados; some owners may block embedding or downloads.
- Nenhum stream premium foi validado e nenhum bypass de DRM/autenticação está implementado.
- A atualização de um APK de debug pode ter assinatura diferente; não desinstalar uma versão com dados locais sem avaliar risco de perder favoritos e playlists.
- O firmware Vortex TV OS é projeto distinto.

Próximo teste: instalar no celular, tocar na capa Big Buck Bunny, testar Caminandes e Sprite Fright no player oficial, confirmar capas. Caso falhe, capturar mensagem HTTP com o host e tipo de recurso.

## Pós-build: preflight HTTPS independente, via Supabase Edge
- 28/10/2026, 21:58 UTC, 7 requisições HEAD fixas: pôsteres Big Buck Bunny, Sintel, Tears of Steel, Elephants Dream; thumbnail YouTube Deu a Louca nos Bichos; MP4 Sintel e Elephants Dream. Todas com HTTP 200 e Content-Type coerente (image/jpeg, image/png, video/mp4).
- Os pôsteres Wikimedia Commons resolveram para thumb.wikimedia.org; arquivos MP4 Archive resolveram para nós de mídia do Internet Archive. HEAD 200 NÃO comprova GET completo nem decodificação no Android.
- Atualizado o Supabase vortex_media.poster_url (quatro títulos), adicionados sources candidatos MP4 para Sintel e Elephants Dream, com validação http_ok, habilitado=false, e histórico sem playback_ok.
- Supabase após escrita: 13 mídias, 14 fontes, 14 verificações; 0 fontes playback_ok homologadas.
- A função diagnóstica vortex-media-preflight verifica exclusivamente URLs previamente fixadas; não recebe URLs arbitrárias do público e não retransmite mídia.
