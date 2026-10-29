# Build 33 — Bot de descoberta e autorrecuperação Vortex TV

## Regra de produto — 29/10/2026
Descoberta diária → identificação do título → deduplicação → categoria → capa → teste HLS → publicação.
Depois da publicação: monitoramento → duas falhas → fila de recuperação → busca de outra fonte do mesmo conteúdo → nova verificação → retorno à disponibilidade.

**No aplicativo:** títulos cujo servidor falhou e não têm outra fonte confirmada exibem a etiqueta **⟳ Buscando novo servidor**. Uma fonte nova só substitui a prioridade da anterior quando passa no teste. A URL antiga permanece no histórico, sem impedir uma reversão.

## Supabase já implantado
- Descoberta diária: `vortex-tv-media-bot-discover-daily` (cron já existente).
- Processamento de listas: `vortex-tv-media-bot-process` (já existente).
- Monitor: `vortex-tv-source-monitor` a cada minuto, pesquisando até 8 candidatas e 20 fontes existentes por ciclo.
- Busca de emergência: `vortex-tv-offline-repair-discovery` a cada 30 minutos, apenas quando existe título em recuperação e não houve coleta recente.
- Edge Functions:
  - `vortex-source-monitor`: lê manifesto HLS e tenta obter um segmento de mídia; registra HTTP/rede, atualiza prioridade e publica novas origens verificadas.
  - `vortex-family-catalog`: fornece páginas M3U com atributo `vortex-status` e `mode=health` com delta de status.
- Banco:
  - `vortex_family.playlist_sources`: URLs originais e novas variantes, metadados de capa e status por fonte.
  - `vortex_internal.media_bot_candidates`: deduplicação, candidatos, prioridades e telemetria.
  - `vortex_internal.media_repair_queue`: títulos que perderam as fontes verificadas; resolução automática quando outra fonte passa no teste.
  - `public.vortex_animation_catalog`: metadados de filmes e séries com temporada/episódio e pôster quando há correspondência exata.
- As funções SQL ficam espelhadas em `supabase/migrations/20261029_vortex_tv_source_repair.sql`.

## Camada Android
- Versão `0.3.5-source-repair`, Android 7.1+.
- M3U usa `vortex-status=available|candidate|searching`, sem modificar o título.
- SQLite local preserva os estados; o catálogo prioriza links disponíveis ao abrir o título ou navegar.
- A Home mostra **Buscando novo servidor** no cartão quando o backend o classifica como em recuperação.
- O app atualiza estados a cada 10 minutos enquanto aberto, sem transferir novamente as 33 mil entradas.
- A sincronização completa (a cada 12 h) retorna à primeira página para atualizar links antigos e capas.
- O player continua trocando automaticamente entre URLs do mesmo título (sem misturar episódios de outras séries).

## Reconciliação e limites
- Ter um `.m3u8` acessível ou conseguir ler um segmento NÃO garante decodificação de áudio/vídeo em uma TV Box específica.
- Fonte que exige DRM, login ou bloqueios adicionais não é automaticamente contornada; somente reprodução direta ou oficialmente incorporável.
- O monitor consulta fontes em ciclos. Falhas temporárias precisam ser repetidas para evitar falso status offline.
- Fonte sem logo pode receber capa de metadados com correspondência exata ou arte de fallback do app, não uma capa falsa.
- A publicação armazena URLs e metadados no backend; não hospeda nem retransmite conteúdo.
- URLs com possíveis credenciais não são incluídas no GitHub nem no APK público. Serviço protegido por token de cron no Supabase Vault e acesso OEM ao catálogo.

## Validação
- Consulta ao cron confirmou os agendamentos ativos.
- Teste SQL transacional com duas falhas consecutivas confirmou entrada na fila de recuperação; a transação foi revertida, sem interferir em mídia real.
- Há registros de execução bem-sucedida do monitor e amostras de manifestos/segmentos validados no Supabase.
- A compilação Android e teste de reprodução no RK3229 devem ser consultados/feitos separadamente; não confundir com Vortex TV OS, que pertence ao Codex.
