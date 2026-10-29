-- Vortex TV self-healing streaming catalog. Existing base catalog is a prerequisite.
-- The private scheduler token belongs to Supabase Vault, never source control.
ALTER TABLE vortex_family.playlist_sources
 ADD COLUMN IF NOT EXISTS last_probe_at timestamptz,
 ADD COLUMN IF NOT EXISTS health_failures integer NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS health_note text NOT NULL DEFAULT '';
ALTER TABLE vortex_internal.media_bot_candidates
 ADD COLUMN IF NOT EXISTS last_probe_at timestamptz,
 ADD COLUMN IF NOT EXISTS probe_status text NOT NULL DEFAULT 'untested',
 ADD COLUMN IF NOT EXISTS probe_failures integer NOT NULL DEFAULT 0,
 ADD COLUMN IF NOT EXISTS probe_error text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS playlist_sources_health_scan
 ON vortex_family.playlist_sources(last_probe_at,id) WHERE media_format='hls';
CREATE INDEX IF NOT EXISTS playlist_sources_status_idx
 ON vortex_family.playlist_sources(identity_key,status);
CREATE INDEX IF NOT EXISTS media_bot_candidate_probe_idx
 ON vortex_internal.media_bot_candidates(priority_score DESC,last_probe_at ASC)
 WHERE media_format='hls';
CREATE TABLE IF NOT EXISTS vortex_internal.media_repair_queue(
 identity_key text PRIMARY KEY,display_name text NOT NULL,
 category text NOT NULL DEFAULT '',trigger_source_hash text NOT NULL DEFAULT '',
 state text NOT NULL DEFAULT 'searching',
 requested_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),resolved_at timestamptz,
 attempts integer NOT NULL DEFAULT 0,last_error text NOT NULL DEFAULT '');
ALTER TABLE vortex_internal.media_repair_queue ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON vortex_internal.media_repair_queue FROM PUBLIC,anon,authenticated;
CREATE TABLE IF NOT EXISTS vortex_internal.media_bot_secrets(
 key_hash text PRIMARY KEY,enabled boolean NOT NULL DEFAULT true,
 created_at timestamptz NOT NULL DEFAULT now());
ALTER TABLE vortex_internal.media_bot_secrets ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON vortex_internal.media_bot_secrets FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.vortex_family_health_changes(p_since_ms bigint DEFAULT 0, p_after bigint DEFAULT 0, p_limit integer DEFAULT 500)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
select coalesce(jsonb_agg(to_jsonb(t) order by t.id),'[]'::jsonb)
from (
 select s.id,s.media_url,
  case when exists(
       select 1 from vortex_family.playlist_sources h
        where h.identity_key=s.identity_key and h.status='online')
       then 'available'
       when s.status='offline' or (q.identity_key is not null and q.state='searching')
       then 'searching' else 'candidate' end as status
 from vortex_family.playlist_sources s
 left join vortex_internal.media_repair_queue q on q.identity_key=s.identity_key
 where s.id>greatest(p_after,0)
  and (coalesce(s.last_probe_at,'epoch'::timestamptz)>to_timestamp(greatest(p_since_ms,0)/1000.0)
       or coalesce(q.updated_at,'epoch'::timestamptz)>to_timestamp(greatest(p_since_ms,0)/1000.0))
 order by s.id
 limit least(greatest(p_limit,1),500)
) t;
$function$;

CREATE OR REPLACE FUNCTION public.vortex_family_page(p_after bigint DEFAULT 0, p_limit integer DEFAULT 500)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
select coalesce(jsonb_agg(to_jsonb(t) order by t.id),'[]'::jsonb)
from (
  select s.id,s.display_name,s.group_title,s.tvg_id,s.media_url,s.media_format,
         s.identity_key,
         coalesce(nullif(s.logo_url,''),
            (select a.poster_url from public.vortex_animation_catalog a
             where a.kind in ('film','series')
               and lower(btrim(a.title))=lower(btrim(s.display_name))
               and a.poster_url ~ '^https://'
             order by a.release_year desc nulls last,a.id limit 1),'') as logo_url,
         case
          when exists(select 1 from vortex_family.playlist_sources h
                  where h.identity_key=s.identity_key and h.status='online')
            then 'available'
          when exists(select 1 from vortex_internal.media_repair_queue q
                  where q.identity_key=s.identity_key and q.state='searching')
            or s.status='offline'
            then 'searching'
          else 'candidate'
         end as catalog_status
  from vortex_family.playlist_sources s
  where s.id>greatest(p_after,0)
  order by s.id
  limit least(500,greatest(1,p_limit))
) t;
$function$;

CREATE OR REPLACE FUNCTION public.vortex_monitor_authorized(p_token_hash text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
 select exists(select 1 from vortex_internal.media_bot_secrets
    where key_hash=p_token_hash and enabled=true);
$function$;

CREATE OR REPLACE FUNCTION public.vortex_monitor_pull(p_candidates integer DEFAULT 8, p_existing integer DEFAULT 10)
 RETURNS jsonb
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
with candidate as (
  select c.source_hash as hash,c.media_url as url,'candidate'::text as origin,
         c.display_name as title,c.group_title as category
  from vortex_internal.media_bot_candidates c
  where c.media_format='hls' and c.media_url ~* '^https?://'
    and (c.last_probe_at is null or c.last_probe_at<now()-interval '20 hours' or (c.probe_failures>0 and c.last_probe_at<now()-interval '20 minutes'))
  order by
   (case when exists (select 1 from vortex_internal.media_repair_queue z
                     where z.state='searching'
                       and z.identity_key=vortex_internal.media_identity_key(
                            c.display_name,c.group_title,c.media_format))
         then 0 else 1 end),
    c.priority_score desc,c.last_probe_at asc nulls first
  limit least(greatest(p_candidates,0),12)
), family as (
  select f.source_hash as hash,f.media_url as url,'catalog'::text as origin,
         f.display_name as title,f.group_title as category
  from vortex_family.playlist_sources f
  where f.media_format='hls' and f.media_url ~* '^https?://'
    and (f.last_probe_at is null or f.last_probe_at<now()-interval '20 hours' or (f.health_failures>0 and f.last_probe_at<now()-interval '10 minutes'))
  order by case when f.health_failures>0 then 0 else 1 end,
           f.last_probe_at nulls first,f.id
  limit least(greatest(p_existing,0),20)
)
select coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb)
from (select * from candidate union all select * from family) t;
$function$;

CREATE OR REPLACE FUNCTION public.vortex_monitor_record(p_hash text, p_origin text, p_passed boolean, p_note text DEFAULT ''::text)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  c record;
  s record;
  v_identity text;
  v_failures integer:=0;
  v_healthy boolean:=false;
  v_published boolean:=false;
  v_category text;
  v_poster text;
  v_source text;
begin
 if p_hash !~ '^[a-f0-9]{32}$' or p_origin not in ('candidate','catalog') then
   raise exception 'Invalid probe identity';
 end if;
 if p_origin='candidate' then
   select * into c from vortex_internal.media_bot_candidates
      where source_hash=p_hash for update;
   if not found then return jsonb_build_object('updated',false); end if;
   update vortex_internal.media_bot_candidates
      set last_probe_at=now(),
          probe_failures=case when p_passed then 0 else least(probe_failures+1,100) end,
          probe_status=case when p_passed then 'manifest_segment_ok' else 'failed' end,
          probe_error=case when p_passed then '' else left(coalesce(p_note,''),160) end,
          discovery_status=case when p_passed then 'validated'
                     else discovery_status end
      where source_hash=p_hash returning probe_failures into v_failures;
   if p_passed then
      v_category:=case
         when upper(c.group_title) ~ '(KIDS|INFANTIL|CHILDREN|BABY)' then 'Infantil'
         when upper(c.group_title) ~ '(ANIMATION|ANIMAÇ|ANIME|CARTOON)' then 'Animação'
         when upper(c.group_title) ~ '(MOVIE|FILME|CINEMA)' then 'Filmes'
         when upper(c.group_title) ~ '(SERIES|SÉRIES|SHOWS)' then 'Séries'
         when upper(c.group_title) ~ '(DOCUMENTAR|DOCS)' then 'Documentários'
         else coalesce(nullif(c.group_title,''),'Canais') end;
      v_identity:=vortex_internal.media_identity_key(c.display_name,v_category,c.media_format);
      select coalesce(nullif(c.logo_url,''),coalesce((
        select a.poster_url from public.vortex_animation_catalog a
        where a.kind in ('film','series')
          and lower(btrim(a.title))=lower(btrim(c.display_name))
          and a.poster_url ~ '^https://'
        order by a.release_year desc nulls last,a.id limit 1
      ),'')) into v_poster;
      insert into vortex_family.playlist_sources as f(
        source_hash,media_url,display_name,group_title,tvg_id,logo_url,
        media_format,identity_key,imported_collection,status,last_good_at,
        last_probe_at,health_failures,health_note
      ) values (
        c.source_hash,c.media_url,c.display_name,v_category,c.tvg_id,
        coalesce(v_poster,''),c.media_format,v_identity,
        'media_bot:'||c.feed_name,'online',now(),now(),0,''
      ) on conflict(source_hash) do update
        set last_good_at=now(),last_probe_at=now(),health_failures=0,
            health_note='',status='online',
            logo_url=case when f.logo_url='' then excluded.logo_url else f.logo_url end,
            group_title=case when f.group_title='' then excluded.group_title else f.group_title end;
      v_published:=true;
   end if;
 else
   select * into s from vortex_family.playlist_sources
     where source_hash=p_hash for update;
   if not found then return jsonb_build_object('updated',false); end if;
   v_identity:=s.identity_key;
   update vortex_family.playlist_sources
       set last_probe_at=now(),
           health_failures=case when p_passed then 0 else least(health_failures+1,100) end,
           last_good_at=case when p_passed then now() else last_good_at end,
           last_bad_at=case when p_passed then last_bad_at else now() end,
           status=case when p_passed then 'online'
                      when health_failures+1>=2 then 'offline'
                      else status end,
           health_note=case when p_passed then '' else left(coalesce(p_note,''),160) end
       where source_hash=p_hash
       returning health_failures into v_failures;
 end if;
 if v_identity is not null and v_identity<>'' then
    select exists(select 1 from vortex_family.playlist_sources fs
       where fs.identity_key=v_identity and fs.status='online')
       into v_healthy;
    if v_healthy then
       update vortex_internal.media_repair_queue
          set state='resolved',updated_at=now(),resolved_at=now(),last_error=''
        where identity_key=v_identity and state='searching';
    elsif not p_passed and v_failures>=2 then
       insert into vortex_internal.media_repair_queue as q(
         identity_key,display_name,category,trigger_source_hash,state,
         requested_at,updated_at,attempts,last_error)
       values(v_identity,coalesce(s.display_name,'Mídia'),
              coalesce(s.group_title,''),p_hash,
              'searching',now(),now(),1,left(coalesce(p_note,''),160))
       on conflict(identity_key) do update
          set state='searching',updated_at=now(),resolved_at=null,
              attempts=q.attempts+1,last_error=excluded.last_error;
    end if;
 end if;
 return jsonb_build_object('updated',true,'healthy',v_healthy,
     'published',v_published,'failures',v_failures);
end;
$function$;

CREATE OR REPLACE FUNCTION vortex_internal.media_identity_key(p_name text, p_group text, p_format text)
 RETURNS text
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO ''
AS $function$
select
 (case when upper(coalesce(p_group,'')) ~ '(FILME|CINEMA|MOVIE|SERIE|SÉRIE|VOD|EPISOD|ANIMAÇ)'
         or p_format='file'
       then 'vod:' else 'channel:' end)
 || btrim(regexp_replace(
     regexp_replace(
       btrim(regexp_replace(translate(upper(coalesce(p_name,'')),
        'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ','AAAAAEEEEIIIIOOOOOUUUUC'),
          '[^A-Z0-9]+',' ','g')),
       ' (HD|SD|FHD|FULL HD|4K|UHD|1080P|720P|ALT|ALTERNATIVO|BACKUP)( [0-9]+)?$','','g'
     ),'[[:space:]]+',' ','g'));
$function$;
REVOKE ALL ON FUNCTION public.vortex_monitor_pull(integer,integer)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.vortex_monitor_record(text,text,boolean,text)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.vortex_monitor_authorized(text)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.vortex_family_page(bigint,integer)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.vortex_family_health_changes(bigint,bigint,integer)
 FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.vortex_monitor_pull(integer,integer),
 public.vortex_monitor_record(text,text,boolean,text),
 public.vortex_monitor_authorized(text),
 public.vortex_family_page(bigint,integer),
 public.vortex_family_health_changes(bigint,bigint,integer) TO service_role;
