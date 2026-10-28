# Vortex TV · YouTube Live & Filmes Dublados

## Estado em 28/10/2026

- **49 referências individuais** de filmes completos encontradas nos resultados de páginas públicas e atribuídas a canais como NetMovies/Filmelier/Film&Clips; vídeos **não testados no dispositivo**. `seed_referencias.json` é um índice, não prova de disponibilidade atual ou licença para re-hospedar.
- **4 canais oficiais de filmes** com IDs conhecidos e **9 canais adicionais** indicados, sujeitos a revisão.
- **12 emissoras/órgãos brasileiros** no registro para monitoramento de lives. Apenas TV Senado/Canal Gov tiveram vídeos publicados com nome *Ao vivo* encontrados na pesquisa; não há prova de transmissão ativa neste instante.
- **53 buscas configuradas**, incluindo 20 por canais e transmissão ao vivo e 25 buscas de filmes; clássicos 1990–2019 priorizados.
- A API não foi executada neste ambiente por falta da credencial; `index.json` representa fielmente um índice vazio e pendente, sem inventar resultados.

## Coleta real pela API oficial

Ative a **YouTube Data API v3** em projeto do Google Cloud e defina `YOUTUBE_API_KEY` **como segredo do ambiente**, nunca no GitHub. Execute:

```bash
python3 tools/index_youtube.py --check-config
YOUTUBE_API_KEY=... python3 tools/index_youtube.py --max-search-calls 70 --pages-per-query 1
python3 -m unittest discover -s tests -p 'test_*.py'
```

O `index.json` é gerado atomicamente e deve ser versionado **após revisão humana de fontes e direitos**. Sem key, o coletor falha explicitamente e não altera o arquivo. A busca oficial `search.list` suporta `eventType=live`, `videoDuration=long`, `videoEmbeddable=true`, paginação e filtros geográficos; `videos.list` confirma duração real, status público, possibilidade de embed e restrição por região. O padrão de quota Google passou a prever **até 100 chamadas `search.list`/dia**, com 10.000 unidades/dia para outras rotas: https://developers.google.com/youtube/data/determine_quota_cost.

**Importante:** `videoDuration=long` significa acima de **20 minutos**, não longa-metragem. O indexador exige **pelo menos 65 minutos** para filme e rejeita trailer/resumo. Títulos de uploads não comprovam filme integral, dublagem ou licença. Ano de upload não é ano de produção. Mesmo `embeddable=true` não é teste de playback no Android 7.1. 

## Integração no app

A UI Vortex tem seção YouTube, capas do próprio YouTube, categorias e modo de reprodução via **iframe oficial** com fallback para aplicativo/site YouTube. Não usa `Media3` para URLs de páginas `watch?v=...`, pois não são links de mídia. Anúncios do YouTube e controles oficiais não são removidos. O D-Pad ↑/↓ alterna apenas entre lives **ativamente indexadas**; a lista oficial pode estar vazia quando não há API ou live confirmada.

O APK contém 49 referências de filmes para exploração sem API, com status experimental. A versão YouTube Preview 26 é compilada pelo Render; o link só é atualizado após deploy concluído. Em caso de erro de embed, use **Abrir no YouTube**.

## Ordenação e curadoria

P1: canais de distribuidoras oficiais; **1990–1999, 2000–2009, 2010–2019** (anos confirmados pelo TMDb/licenciadora, não por data de upload); filmes dublados de duração >= 65m e acessíveis no Brasil.
P2: outras obras licenciadas e documentários; lives oficiais brasileiras e FAST.
P3: candidatos não verificados provenientes de pesquisa genérica; **nunca** anunciar como canais pagos HBO/ESPN/AXN sem acesso autorizado.

### Testes necessários para cada vídeo

1. Identidade da conta que publicou a obra e licença/distribuição.
2. Metadata: 65m+, título, idioma, imagem e duração corretos.
3. Status público/embeddable/região BR na Data API.
4. Playback real no Android 7.1 ARM32/1GB, inclusive controle e áudio.
5. Monitorar remoções, embargos, DRM, alterações regionais e erro de reprodução.

Objetivo é ampliar cobertura de forma incremental; **não é possível enumerar literalmente todas as lives do YouTube** em uma consulta única nem garantir sua permanência. Não confundir vídeos gratuitos do YouTube com o produto pago **YouTube TV** (cuja disponibilidade é regional e contratual).