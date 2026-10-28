-- Vortex TV Cloud: bounded, allowlisted manifest-only health checks.
-- No bypass of DRM/auth; no video segments are downloaded or stored.
-- pg_net and pg_cron must be installed separately on this dedicated project.
create schema if not exists vortex_internal;
revoke all on schema vortex_internal from public, anon, authenticated;

alter table public.vortex_sources
  add column if not exists probe_enabled boolean not null default false,
  add column if not exists provenance_url text not null default '',
  add column if not exists last_error text not null default '';

create table if not exists vortex_internal.allowed_stream_hosts (
  hostname text primary key,
  purpose text not null default 'curated HLS manifest',
  enabled boolean not null default true,
  constraint hostname_format check (hostname ~ '^[a-z0-9][a-z0-9.-]*[a-z0-9]$')
);
create table if not exists vortex_internal.probe_requests (
  request_id bigint primary key,
  source_id bigint not null references public.vortex_sources(id) on delete cascade,
  requested_at timestamptz not null default now(),
  processed_at timestamptz
);
create index if not exists vortex_probe_pending_idx
  on vortex_internal.probe_requests(requested_at) where processed_at is null;
create index if not exists vortex_sources_check_due_idx
  on public.vortex_sources(last_check) where probe_enabled;

alter table vortex_internal.allowed_stream_hosts enable row level security;
alter table vortex_internal.probe_requests enable row level security;
revoke all on all tables in schema vortex_internal from public, anon, authenticated;
revoke all on all sequences in schema vortex_internal from public, anon, authenticated;

create or replace function vortex_internal.enqueue_probes(p_limit integer default 10)
returns integer
language plpgsql security invoker
set search_path = ''
as $$
declare
  candidate record;
  request_id bigint;
  queued integer := 0;
begin
  -- Only explicit, HTTPS, allowlisted .m3u8 origins can be fetched.
  for candidate in
    select s.id, s.media_url
    from public.vortex_sources s
    join vortex_internal.allowed_stream_hosts h
      on h.hostname = lower(substring(s.media_url from '^https://([^/:?#]+)'))
     and h.enabled
    where s.probe_enabled
      and s.format = 'hls'
      and s.media_url ~ '^https://[^/?#]+/[^?#]*\.m3u8(\?.*)?$'
      and (s.last_check is null or s.last_check < now()-interval '6 hours')
      and not exists (
        select 1 from vortex_internal.probe_requests j
        where j.source_id = s.id and j.processed_at is null
          and j.requested_at > now()-interval '15 minutes'
      )
    order by s.last_check nulls first, s.id
    limit least(greatest(p_limit,0),12)
  loop
    begin
      request_id := net.http_get(
        url := candidate.media_url,
        headers := '{"Accept":"application/vnd.apple.mpegurl"}'::jsonb,
        timeout_milliseconds := 7000
      );
      insert into vortex_internal.probe_requests (request_id,source_id)
      values (request_id,candidate.id);
      update public.vortex_sources set last_check=now() where id=candidate.id;
      queued := queued+1;
    exception when others then
      update public.vortex_sources set last_error=left(sqlerrm,240),last_check=now()
        where id=candidate.id;
    end;
  end loop;
  return queued;
end;
$$;

create or replace function vortex_internal.process_probes(p_limit integer default 30)
returns integer
language plpgsql security invoker
set search_path = ''
as $$
declare
  entry record;
  manifest_ok boolean;
  status text;
  reason text;
  processed integer := 0;
begin
  for entry in
    select j.request_id,j.source_id,j.requested_at,r.id as response_id,
           r.status_code,r.content,r.error_msg,r.timed_out
    from vortex_internal.probe_requests j
    left join net._http_response r on r.id=j.request_id
    where j.processed_at is null
      and (r.id is not null or j.requested_at < now()-interval '3 minutes')
    order by j.requested_at
    limit least(greatest(p_limit,0),50)
    for update of j skip locked
  loop
    manifest_ok := entry.status_code=200
      and left(coalesce(entry.content,''),7)='#EXTM3U'
      and (
        strpos(coalesce(entry.content,''),'#EXT-X-STREAM-INF')>0
        or strpos(coalesce(entry.content,''),'#EXT-X-TARGETDURATION')>0
      );
    status := case
      when manifest_ok then 'manifest_ok'
      when entry.status_code in (401,403) then 'blocked_auth'
      else 'unavailable'
    end;
    reason := case
      when manifest_ok then 'Manifesto HLS válido; playback ainda não testado'
      when entry.response_id is null then 'Sem resposta no prazo'
      when nullif(entry.error_msg,'') is not null then entry.error_msg
      when entry.timed_out is true then 'Tempo limite da requisição'
      else 'HTTP '||coalesce(entry.status_code::text,'sem resposta')||' ou manifesto inválido'
    end;
    insert into public.vortex_source_checks
      (source_id,http_status,manifest_ok,playback_ok,details)
    values (entry.source_id,entry.status_code,manifest_ok,false,left(reason,500));
    update public.vortex_sources s set
      validation_status = case
        when s.validation_status='playback_ok' and manifest_ok then 'playback_ok'
        else status
      end,
      last_error = case when manifest_ok then '' else left(reason,240) end,
      last_check = now()
    where s.id=entry.source_id;
    update vortex_internal.probe_requests
      set processed_at=now() where request_id=entry.request_id;
    processed := processed+1;
  end loop;
  -- Keep only a bounded log of request mappings; durable results remain in source_checks.
  delete from vortex_internal.probe_requests
    where processed_at < now()-interval '2 days';
  return processed;
end;
$$;

revoke all on function vortex_internal.enqueue_probes(integer) from public,anon,authenticated;
revoke all on function vortex_internal.process_probes(integer) from public,anon,authenticated;
