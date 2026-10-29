# Vortex TV — catálogo de 100 animações e séries (29/10/2026)

## Entregue e verificado
- A lista original de **100 produções** foi gravada em `vortex_internal.animation_user_requests` e registrada neste repositório em `catalog/animation/series_requested_100.json`.
- Cada série tem nome em português, título original para consulta, ano, franquia e identificador distinto. As Tartarugas Ninja (2003) e (2012) não são fundidas.
- As produções foram associadas aos alvos de atualização (`vortex_internal.animation_series_targets`).
- O código `supabase/functions/vortex-animation-series-update/index.ts` consulta os alvos autorizados no banco, procura correspondência por título original e ano na TVMaze, lê episódios incluindo especiais, e grava os metadados.
- A atualização de metadados executa automaticamente em lotes de até 3 séries a cada cinco minutos (Supabase Cron; sem GitHub Actions).
- As fichas publicadas usam **metadados**, não URLs de reprodução inventadas. A grade precisa diferenciar `metadata_only`, `source_candidate` e `verified`.
- O banco já possui mais de 1.300 filmes, mas muitos ainda exigem capas de fonte confiável.

## Arquitetura
- `vortex_animation_catalog`: séries, filmes, episódios, temporada, número, cartaz e sinopse.
- `vortex_internal.animation_user_requests`: exatamente 100 séries solicitadas pelo usuário.
- `vortex_internal.animation_series_targets`: enriquecimento periódico de capas/episódios.
- `vortex_internal.animation_source_matches`: correspondência com endereços de playlist, **sem homologação de playback**. Links potencialmente com credenciais ficam nas tabelas privadas.
- `vortex_internal.media_bot_candidates`: coletor diário de M3U/HLS; somente fontes verificadas/permitidas devem ser promovidas para reprodução.

## Limitação incontornável
Não é possível garantir imagem/áudio para todos os 100 títulos, especialmente conteúdo protegido por direitos e disponível apenas por serviços com autenticação/DRM. Nenhum stream foi marcado como `playback_ok` apenas por constar de uma lista ou responder HTTP 200.

## Pendências
- Completar metadados dos títulos sem resultado exato na TVMaze e filmes com capas ausentes.
- Validar as fontes de streaming por título/episódio, sem misturar versões ou temporadas.
- Integrar as fichas de animação e os estados de disponibilidade na interface Android do Vortex TV.
- Testar reprodução no dispositivo real.

Não alterar Vortex TV OS neste projeto; firmware é responsabilidade do Codex.
