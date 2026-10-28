-- Vortex TV Cloud: address security/performance advisors.
-- All async probe/discovery requests were confirmed processed before this migration.
create schema if not exists extensions;
drop extension if exists pg_net;
create extension pg_net with schema extensions;
create index if not exists vortex_probe_requests_source_idx
  on vortex_internal.probe_requests(source_id);
