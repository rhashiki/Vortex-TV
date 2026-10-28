# VORTEX TV — PLANO MESTRE DE CATÁLOGOS E BUILDS 25–34
Data: 2026-10-28 • Projeto: **aplicativo Android Vortex TV**, não firmware Vortex TV OS

## Diagnóstico factual (fonte: seed da main e relatórios anteriores)

- APK atual: **23 canais ao vivo com URL cadastrada** (ainda não homologada), **60 canais por assinatura SEM fonte**, **4 curtas** pré-carregados (Blender).
- **83 canais** no inventário local, mas **todos os 83 sem URL de logotipo preenchida** em seus arquivos JSON. Os 4 curtas têm URLs de imagens no seed.
- Engenharia reversa: 10 APKs/9 binários distintos da primeira amostra; 1.895 URLs textuais, 1.204 domínios, 357 referências de extratores NinjaTV, 371 metadados de anime. Não confundir URL/metadata com mídia.
- GitHub playlists BR: 452 itens br.m3u, 165 br_pluto.m3u, 3 br_samsung.m3u, 23 iptv-com Brazil = **643 ocorrências de entrada**; contêm repetições e alguns canais potencialmente restritos. Nenhuma reprodução foi testada nesta organização.
- As fontes são catalogadas em [fontes_registro.json](../catalog/fontes_registro.json) e os canais em [inventario/](../catalog/inventario/). **Não incluir no app sinais sem acesso comprovado.**

## Modelo único para todos os catálogos

Entidades:
`Provider(id,tipos,dominio,origem,licenca,status,atualizado_em)`
`Media(id_interno, tipo, title_pt_br, title_original, ano, tmdb_id, imdb_id, anilist_id, sinopse_pt_br, genero[], poster, backdrop, idiomas[], classificacao, provenance)`
`Season(show_id,numero,ano)`, `Episode(show_id,season,episode,title_pt_br,duracao,thumbnail)`
`Channel(id, numero, name, tvg_id, logo, group, epg_id, variants[])`
`Source(media_or_channel_id,provider_id,media_type,url_ref,auth_mode,rights_status,validation_status,last_check,codec,bitrate,reason)`
`GuideEvent(channel_id,start,end,title_pt_br,description_pt_br,artwork)`.

Tipos de mídia **estritamente separados**: `movie`, `series`, `episode`, `documentary`, `anime`, `channel`, `short`. Um canal de cinema continua `channel`, não `movie`. Não usar `format=hls` como sinônimo de `channel`: HLS também serve filmes/séries.

Estados independentes:
1. `CATALOGED` — título ou emissora identificados, não há reprodução;
2. `SOURCE_FOUND` — URL externa de mídia ou player identificada;
3. `HTTP_OK` — retorno de rede aceito;
4. `MANIFEST_OK` — conteúdo é HLS/DASH/MP4/contêiner válido;
5. `PLAYBACK_OK` — vídeo e áudio decodificados no Android-alvo;
6. `BLOCKED_AUTH` / `BLOCKED_DRM` / `BLOCKED_RIGHTS` / `UNAVAILABLE`.

**Não trocar `CATALOGED` para `PLAYBACK_OK` só com HTTP 200.** Títulos apenas indexados podem aparecer na pesquisa, mas devem mostrar claramente “Indisponível para assistir” e não fingir que há botão reproduzível.

## Categorias definitivas da interface

**TV ao vivo:** Abertos Brasil; Filmes e séries; Esportes; Documentários e ciência; Notícias; Infantil; Música; Cultura/Educação; Religiosos; Variedades; FAST; Favoritos. Guia vertical com números, logo, programa agora/próximo.

**Filmes:** Ação; Aventura; Comédia; Drama; Romance; Terror; Suspense; Ficção científica; Fantasia; Animação; Documentário longa-metragem; LGBT+; Família; Guerra; Clássicos; Cinema nacional; Domínio público / Creative Commons.

**Séries:** Drama; Comédia; Crime/policial; Suspense; Fantasia; Ficção científica; Terror; Documentais; Reality; Infantil; Doramas; LGBT+; Nacional. Temporadas e episódios são entidades próprias.

**Documentários:** Natureza/animais; Ciência/espaço; História; Política e sociedade; Biografias; Crime real; Arte/cultura; Saúde; Viagem; Tecnologia; Música.

**Anime:** Shōnen; Seinen; Shōjo; Fantasia/Isekai; Drama; Romance; Comédia; Ação; Ficção científica; Filmes de anime; Séries de anime. Metadados AniList/TMDB são referências, não episódios.

## Critérios visuais globais (bloqueadores)

- Navegação cinematográfica familiar à Netflix, **sem copiar logotipos ou elementos exclusivos**; identidade Vortex roxo/azul, base grafite, hero/backdrop, thumbnails 16:9 e pôsteres 2:3, fileiras, foco TV bem evidente; no celular, barra inferior própria.
- Dados apresentados em **português brasileiro**: TMDb com `language=pt-BR`, título original em segundo plano, sem traduzir inventando. Se metadado oficial indisponível, marcar idioma e mostrar original.
- Canal reconhecido **não pode aparecer como quadrado vazio**: logo obtido de base identificada (iptv-org logos, emissora ou metadados de provedor), cache local com expiração. Não alterar marca de emissora.
- D-pad **↑ = canal seguinte / ↓ = anterior** sem retornar à Home; **←/→ = abrir guia vertical**; OK selecionar; BACK fechar; numérico opcional. Nos filmes, ↑/↓ navegam componentes, não trocam canais.
- Sob ~1 GB RAM: carregamento incremental, cache disco com limites, thumbnails reduzidas, paginar 20–40 entradas, não manter milhares de views em memória.

## Sequência de builds — uma entrega com testes e aceite por vez

| Build | Nome | Escopo real e fontes | Critérios de aceite (sem aprovar na base de prints) | Dependências |
|---|---|---|---|---|
| **25** | Inventário e contratos | Registrar todos os APKs, sites, GitHub, 643 entradas IPTV BR, 371 animes, 83 canais locais, tabelas por mídia, IDs e estado do player | Inventários versionados, contagens reproduzíveis e zero links anunciados como ativos sem teste. **Documentação e dados, não novo APK.** | Nenhuma |
| **26** | Identidade + metadados PT-BR | TMDb, iptv-org API de logos; título/ano/idioma/pôster/sinopse, normalização e deduplicação; UI estilo Netflix | No conjunto piloto de 50 obras identificáveis, >=90% títulos corretos e sinopses pt-BR quando disponíveis, >=90% com pôster; nos 83 canais locais, logos identificados ou pendência explícita, jamais “carregados” falsos | 25, acesso e termos TMDb |
| **27** | TV aberta e FAST BR | iptv-org br/pluto/samsung, Free-TV/IPTV, iptv-org EPG; importação, teste de rede, EPG e logos | 643 entradas **processadas como referências**; agrupar duplicados; reprodução real e licença verificadas por emissora antes da promoção a “Ao vivo”; guia vertical e cache funcional | 25–26 |
| **28** | TV premium por provedor | M3U/Xtream/XMLTV de fornecedor autorizado; Zp.net, Play Cine CT e OpenTV como referências arquiteturais; 60 canais-alvo do guia | Importação de provedor legítimo, mapeamento por tvg-id, troca ↑/↓, categorias/EPG; canais sem direito/credencial continuam “Sem sinal”, nunca inventar ESPN/HBO/AXN | 26–27 |
| **29** | VOD Catalog Core | TMDb pt-BR, AniList e Simkl para filmes/séries/docs/animes; 371 metadados anime; pesquisa por ID e gêneros | Distingue filmes, séries, docs e animes; temporadas/episódios; imagens e nomes por ID confiável, sem resultado duplicado; diferenciar metadata-only de assistível | 25–26 |
| **30** | VOD aberto e Dailymotion | Internet Archive, vídeos Dailymotion com integração permitida por SDK/embed e direitos; catálogo de curtas e documentários | Filme realmente reproduzível, não análise/trailer rotulado “filme completo”; sinopse pt-BR quando existir; links mortos invisíveis em “assistir” | 29 |
| **31** | Playback Engine/Resiliência | Media3 HLS/DASH/MP4, legendas/áudios, fallback legal, memória de posição; dimensões do controle remoto | Testar play/pause/seek/retomada/EPG e múltiplos codecs na TV Box ARM32 Android 7.1 e celular; travamentos e controles monitorados | 27–30 |
| **32** | Adaptadores externos catalogados | Estudo separado por provider: Vizer/Vizr, RedeCanais, Recanais, filmesonlinehd1, Mega Filmes HD, Pobreflix, FilmesGays, TV Online HD; referências GitHub VizerTV, Stretor, KKMovies, Play-Infinity, Mox, Kodi resolvers | Para CADA provider: `manifest` com metadados, esquema de episódios, documentação, permissões e estado; somente fontes que autorizem integração são habilitadas no player; proteção/DRM/contas não são contornados. Não vender estudo como acervo disponível | 25,29,31 |
| **33** | Qualidade e testes reais | Navegação no controle IR, teste de catálogos, reprodução real, logs, baixo uso de RAM, assinatura estável | Matriz TV Box RK3229/Android 7.1 + celular; gravação de falhas; dados persistem em atualização; verificador reprovando fontes não testadas | 25–32 |
| **34** | Release e monitoramento | OTA de listas/metadados licenciados, catálogo diferenciado, QA final; GitHub Releases + Render para build | APK assinado com chave estável, compatibilidade e versão explícitas; sem links expirados anunciados; zero GitHub Actions | 33 |

## Matriz de provedores atribuídos às builds

- **B27:** iptv-org br, iptv-org Pluto BR, iptv-org Samsung BR, iptv-com BR (referência), Free-TV/IPTV, guias XMLTV idôneos.
- **B28:** canais premium HBO, Telecine, ESPN, AXN, Discovery, SporTV, Premiere, etc. **somente** por conta/lista com acesso válido; Zp.net/Play Cine como estudo de esquema.
- **B29:** TMDb, AniList (371 registros NinjaTV), Simkl; OpenSubtitles na B31.
- **B30:** quatro curtas Blender, Archive e Dailymotion. No Dailymotion, o título de um upload não garante obra completa nem autorização.
- **B32:** `filmesonlinehd1.org`, `megafilmeshd.press` e variantes, `vizer.autos`/Vizr, RedeCanais/Recanais/redecanaistv, Pobreflix, `filmesgays.net`, `tvonlinehd.app.br`; GitHub `viniciusgdr/vizertv`, `BrunoDelfim/stretor`, `Dante2526/Play-Infinity`, `kauankelvin7/KKMovies`, `KayohanCosta/Mox-Streaming`, `cleitonleonel/redecanais`, `script.module.rcresolver`, `Gerador-M3U-RedeCanais`, `rede-canais-tv-scraper`, `rede-canais-scraper`, `mflix-back-rede-canais`, `Addon-stremion-redecanais`.
- **APKs estudados sem incorporação direta de DEX:** NinjaTV, YouCine, Zp.net, MetaX, Rede Canais TV, TV Digital, Next+, Cinegato, Veora, AnimeTV, Play Animes, VeloPlay.

## Extensão de YouTube — implementada parcialmente em 28/10/2026

- Branch main contém `catalog/youtube/search_plan.json`: **53 buscas configuradas**, com 20 consultas de live, 25 de filmes e 8 de distribuidores oficiais.
- `catalog/youtube/seed_referencias.json`: **49 referências únicas de filmes** com vídeo YouTube, canais identificados e miniaturas geradas a partir do ID. **Não são 49 reproduções homologadas**.
- `catalog/youtube/index.json`: **índice remoto vazio por ausência da chave da API oficial**; não contém lives reais; nenhuma afirmação de varredura completa.
- `tools/index_youtube.py`: indexador YouTube Data API v3 com paginação, duração >=65 min, filtro de trailers, status embeddable, região BR, deduplicação, canais oficiais e distinção entre ano de upload e produção.
- App Android: guia/seção YouTube, capa, WebView com iframe do YouTube e fallback para player externo, D-pad ↑/↓ para lives retornadas pelo índice; testes Android 7.1 pendentes.
- **B27 passa a cobrir também YouTube Live** de canais oficiais; **B30 cobre YouTube Movies** com foco 1990–2019, além de Dailymotion e Internet Archive.
- Falta credencial `YOUTUBE_API_KEY` segura de um projeto Google Cloud com YouTube Data API v3; não salvá-la no repositório. Quota padrão: até 100 consultas `search.list` por dia. Sem credencial não se promete indexação de todas as lives nem descoberta de novos filmes.
- Nenhum conteúdo protegido é re-hospedado, anúncios/DRM da plataforma não são removidos.

## Critério de aprovação — todas as builds

Cada build contém (1) escopo e fontes exatas, (2) alterações de código, (3) testes unitários e integração, (4) evidência do APK e do backend, (5) matriz de conteúdo testado, (6) resultado no dispositivo alvo, (7) pendências sem eufemismo. **Não concluir build que somente criou cartões e nomes.**

Política operacional: **nenhum GitHub Actions** — não criar, executar nem depender deles. Usar GitHub para código/arquivos, Render ou execução independente para builds, validação local e GitHub Releases para disponibilização. Não confundir Vortex TV (app) com Vortex TV OS.

## Inventário e evidências

- `catalog/fontes_registro.json`: 51 fontes/referências categorizadas por mídia, papel e build.
- `catalog/inventario/iptv_brasil_referencias.csv`: 643 linhas publicadas com identificação/categoria **sem copiar sinais em massa**.
- `catalog/inventario/itens_atuais_apk.csv`: 27 itens seed + 60 do guia com presença de ícone/fonte.
- Planilha-mãe da auditoria anterior: `Catalogo_Origens_APKs_VortexTV_2026-10-28.xlsx` (1.895 URLs, 357 referências, 371 anime). Nova versão organizada mantida na Biblioteca do ChatGPT.
- Relatórios `RELATORIO_BUILD20.md`, `RELATORIO_BUILD21.md`, `RELATORIO_ENGENHARIA_REVERSA_9_APKS.md` no material recuperado.

### Incertezas que NÃO podem ser omitidas

1. Resultados de sites externos e nomes como “filme completo” são **títulos catalogados ou alegações do uploader**, não prova de arquivo, duração ou direitos.
2. Fontes de terceiros podem exigir login, expirar, bloquear por geografia, DRM ou não ter reprodução válida.
3. O TMDb fornece metadados e imagens sob condições próprias — não fornece transmissão.
4. Se não há integração autorizada e mídia decodificada, o status é “INDEXADO / INDISPONÍVEL”, nunca “ASSISTIR”.
5. Builds 26–34 são **plano de trabalho, não itens implementados**. Build 25 registra o levantamento e contratos.