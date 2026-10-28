-- Auto-discovery from one public playlist, restricted to explicit institutional hosts.
-- It indexes ONLY candidate URLs (rights unknown, disabled), never pay-TV or IP mirrors.
create table if not exists vortex_internal.discovery_allowlist (
  tvg_id text not null,
  hostname text not null,
  display_name text not null,
  category text not null,
  provider text not null,
  provenance_url text not null,
  primary key(tvg_id,hostname)
);
alter table vortex_internal.discovery_allowlist enable row level security;
revoke all on vortex_internal.discovery_allowlist from public,anon,authenticated;

insert into vortex_internal.discovery_allowlist
(tvg_id,hostname,display_name,category,provider,provenance_url)
values
('TVBrasil.br','tvbrasil-stream.ebc.com.br','TV Brasil','TV aberta • Brasil','Empresa Brasil de Comunicação','https://tvbrasil.ebc.com.br/'),
('CanalGov.br','canalgov-stream.ebc.com.br','Canal Gov','Público','Empresa Brasil de Comunicação','https://www.gov.br/'),
('TVCamara.br','stream3.camara.gov.br','TV Câmara','Legislativo','Câmara dos Deputados','https://www.camara.leg.br/tv'),
('TVCamara2.br','stream3.camara.gov.br','TV Câmara 2','Legislativo','Câmara dos Deputados','https://www.camara.leg.br/tv')
on conflict(tvg_id,hostname) do nothing;

insert into vortex_internal.allowed_stream_hosts(hostname,purpose)
values ('canalgov-stream.ebc.com.br','Brazil govt broadcaster')
on conflict(hostname) do nothing;

create table if not exists vortex_internal.discovery_requests (
  request_id bigint primary key,
  requested_at timestamptz not null default now(),
  processed_at timestamptz,
  discovered integer not null default 0,
  error text not null default ''
);
alter table vortex_internal.discovery_requests enable row level security;
revoke all on vortex_internal.discovery_requests from public,anon,authenticated;

create or replace function vortex_internal.import_curated_m3u(payload text)
returns integer
language plpgsql security invoker
set search_path = ''
as $$
declare
  lines text[];
  i integer;
  line text;
  url text;
  tvg text;
  origin text;
  entry record;
  target_id bigint;
  inserted_count integer:=0;
  affected integer;
begin
  if length(payload)>160000 or left(payload,7)<>'#EXTM3U' then
    raise exception 'M3U inválido ou acima do limite';
  end if;
  lines:=regexp_split_to_array(replace(payload,chr(13),''),E'\n');
  if array_length(lines,1)>5000 then
    raise exception 'M3U com entradas demais';
  end if;
  for i in 1..coalesce(array_length(lines,1)-1,0) loop
    line:=lines[i];
    if left(line,8)<>'#EXTINF:' then continue; end if;
    tvg:=split_part(coalesce(substring(line from 'tvg-id="([^"]+)"'),''),'@',1);
    if tvg='' then continue; end if;
    url:=btrim(lines[i+1]);
    if length(url)>2048 or url !~ '^https://[a-zA-Z0-9.-]+/[^[:space:]]*\.m3u8(\?.*)?$' then
      continue;
    end if;
    origin:=lower(substring(url from '^https://([^/:?#]+)'));
    select a.* into entry from vortex_internal.discovery_allowlist a
    where a.tvg_id=tvg and a.hostname=origin;
    if not found then continue; end if;

    insert into public.vortex_media(kind,display_name,category,tvg_id,description)
    select 'channel',entry.display_name,entry.category,tvg,
           'Fonte encontrada em diretório público; reprodução e direitos não homologados'
    where not exists(
      select 1 from public.vortex_media m
      where m.kind='channel' and m.display_name=entry.display_name
    );
    select id into target_id from public.vortex_media
    where kind='channel' and display_name=entry.display_name
    order by id limit 1;
    if target_id is null then continue; end if;
    insert into public.vortex_sources
      (media_id,media_url,format,provider,rights_status,validation_status,
       enabled,probe_enabled,provenance_url)
    values
      (target_id,url,'hls',entry.provider,'unknown','source_found',
       false,true,'https://github.com/iptv-org/iptv/blob/master/streams/br.m3u')
    on conflict(media_id,media_url) do nothing;
    get diagnostics affected=row_count;
    inserted_count:=inserted_count+affected;
  end loop;
  return inserted_count;
end;
$$;

create or replace function vortex_internal.enqueue_discovery()
returns bigint
language plpgsql security invoker
set search_path = ''
as $$
declare req bigint;
begin
  if exists(
    select 1 from vortex_internal.discovery_requests j
    where j.requested_at > now()-interval '20 hours'
  ) then return 0; end if;
  req:=net.http_get(
    url:='https://raw.githubusercontent.com/iptv-org/iptv/master/streams/br.m3u',
    headers:='{"Accept":"text/plain"}'::jsonb,
    timeout_milliseconds:=12000
  );
  insert into vortex_internal.discovery_requests(request_id) values(req);
  return req;
end;
$$;

create or replace function vortex_internal.process_discovery()
returns integer
language plpgsql security invoker
set search_path = ''
as $$
declare row_data record; n integer:=0; imported integer; message text;
begin
  for row_data in
    select j.request_id,j.requested_at,r.id as response_id,r.status_code,
           r.content,r.error_msg
    from vortex_internal.discovery_requests j
    left join net._http_response r on r.id=j.request_id
    where j.processed_at is null
      and (r.id is not null or j.requested_at < now()-interval '3 minutes')
    order by j.requested_at
    limit 4 for update of j skip locked
  loop
    imported:=0;
    message:='';
    if row_data.status_code=200 and row_data.content is not null
       and length(row_data.content)<160000 then
      begin
        imported:=vortex_internal.import_curated_m3u(row_data.content);
      exception when others then
        message:=left(sqlerrm,240);
      end;
    else
      message:=coalesce(row_data.error_msg,
              'HTTP '||coalesce(row_data.status_code::text,'sem resposta'));
    end if;
    update vortex_internal.discovery_requests set
      processed_at=now(),discovered=imported,error=message
    where request_id=row_data.request_id;
    n:=n+1;
  end loop;
  delete from vortex_internal.discovery_requests
  where processed_at<now()-interval '30 days';
  return n;
end;
$$;

revoke all on function vortex_internal.import_curated_m3u(text) from public,anon,authenticated;
revoke all on function vortex_internal.enqueue_discovery() from public,anon,authenticated;
revoke all on function vortex_internal.process_discovery() from public,anon,authenticated;

select cron.schedule(
  'vortex-tv-directory-enqueue',
  '23 4 * * *',
  'select vortex_internal.enqueue_discovery();'
);
select cron.schedule(
  'vortex-tv-directory-results',
  '*/10 * * * *',
  'select vortex_internal.process_discovery();'
);
