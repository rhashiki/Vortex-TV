-- Vortex TV Cloud: server-side SQL scheduling (no tokens, no third-party runners).
create extension if not exists pg_cron with schema pg_catalog;

-- 9 curated HTTPS HLS sources at most; batches are limited to 12.
-- Enqueue every six hours, collect async network results every two minutes.
select cron.schedule(
  'vortex-tv-hls-enqueue',
  '13 */6 * * *',
  'select vortex_internal.enqueue_probes(12);'
);
select cron.schedule(
  'vortex-tv-hls-results',
  '*/2 * * * *',
  'select vortex_internal.process_probes(50);'
);
