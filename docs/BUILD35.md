# Vortex TV — Build 35 · Catálogo autônomo e recuperação de fontes

## Escopo
Este repositório desenvolve **somente o aplicativo Vortex TV**, não o firmware Vortex TV OS. Não utilizar GitHub Actions.

## Regra implementada
1. O Supabase busca novas playlists M3U e HLS nos diretórios cadastrados diariamente.
2. O parser separa títulos e grupos, aplica deduplicação e classifica infantil, animação, filmes, canais ao vivo, séries e outros grupos.
3. A capa vem do logo do canal, da playlist ou de metadados confiáveis correspondentes exatamente ao título. Na ausência de imagem válida, o Android mostra um cartão de reserva.
4. Fonte inédita é **candidata**, não "reproduzível" por presunção. O verificador busca manifesto HLS e pelo menos um segmento de mídia com limites de tempo e tamanho.
5. Só após a checagem positiva a fonte é publicada no `vortex_family.playlist_sources` como `online`. O player tenta links do mesmo título, com as fontes saudáveis primeiro.
6. Se o monitor ou player observar falhas, atualiza o estado. O monitor exige falhas repetidas antes de classificar a fonte como offline; o player comunica o resultado real da tentativa. Quando não há alternativa funcional, o título recebe o estado `searching`.
7. A Home do Android apresenta a tag exata **Buscando novo servidor**. Assim que outro servidor é confirmado, o estado volta a `available`.
8. As mudanças podem ser sincronizadas por um feed incremental pequeno a cada 10 minutos, evitando baixar todas as 33 mil URLs para atualizar apenas a tag.

## Operação verificada
- `vortex_family.playlist_sources`: **33.664 URLs** no retrato de 29/10/2026, **64** com status `online` (checagem em servidor, não homologação de reprodução na TV Box).
- `vortex_internal.media_bot_candidates`: **5.031** candidatos catalogados; não são todos válidos.
- `public.vortex_animation_catalog`: **4.258** metadados de animações/episódios — títulos catalogados não equivalem a filmes disponíveis.
- `vortex_family.title_status`: registros de títulos por identidade e status, com capa/categoria.
- Jobs cron de descoberta diária, processamento de listas, monitor de fontes e busca de recuperação estão ativos.
- Backend de saúde privado autenticado: GET HTTP 200, feed incremental HTTP 200 (168 atualizações no teste), POST autenticado com hash inexistente HTTP 200 `accepted:false`. O aparelho não envia URLs ou credenciais nas notificações de reprodução.
- Transição comprovada em transação SQL sintética e revertida: `source offline → searching → source online → available`. A API de catálogo retornou os estados correspondentes.
- Não foram usadas funções de bypass de DRM, autenticação, extração de DEX ou proxy de vídeo.

## Android e compilação
- Package release: `os.vortex.tv.stream`.
- Versão: **0.3.5-source-repair**, `versionCode 35`, minSdk 25.
- Interface exibe `Buscando novo servidor` no catálogo; o parser lê `vortex-status` enviado pelo backend; SQLite preserva e atualiza esse estado.
- O player Media3 troca de fonte automaticamente e envia feedback de sucesso/falha com hash do endereço, somente na instalação nativa com configuração OEM.
- Build independente no Render (sem GitHub Actions): **71 testes Python**, testes Java de identidade e carga sintética com **33.601 entradas**, `assembleDebug` concluído.
- Deploy LIVE: `dep-dbh975ifngtc73b01kg0`, commit `ec8ae7508993d629e91e2ed892c2afab41d173dd`.
- APK debug: https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk
- HTTP 200, 5.927.553 bytes, SHA-256 `7a6165f1e0a48426e3adec679e5ed13ad8b9c72ceef69bcea1cae78ad434c7f5`.

## Limites e próximos testes
A infraestrutura encontra, organiza, monitora e substitui por novas alternativas aprovadas, mas não pode assegurar reprodução quando todas as fontes estão indisponíveis. Uma checagem HTTP/HLS não valida codec, áudio, georrestrição ou reprodução contínua na TV Box física.

A lista `vortex_family` exige configuração OEM do firmware; o APK debug instalado isoladamente não dispõe dessa configuração. A instalação e o flash do Vortex TV OS são responsabilidade de outro projeto (Codex), não deste desenvolvimento.

O bot usa uma coleção definida de diretórios e playlists — **não rastreia todos os sites da internet**. Fontes externas sem prova de autorização permanecem classificadas como candidatas até as validações adequadas.
