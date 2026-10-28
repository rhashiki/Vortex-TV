-- Vortex TV Cloud: execute ONLY in a NEW dedicated Supabase project.
-- No table in Vortex Cafe / CH projects should be modified.
create table if not exists public.vortex_media (
    id bigint generated always as identity primary key,
    kind text not null check (kind in ('channel','movie','series','episode','documentary','anime','short')),
    display_name text not null check (length(display_name) between 2 and 120),
    category text not null default '',
    tvg_id text not null default '',
    poster_url text not null default '',
    description text not null default '',
    release_year text not null default '',
    created_at timestamptz not null default now()
);

create table if not exists public.vortex_sources (
    id bigint generated always as identity primary key,
    media_id bigint not null references public.vortex_media(id) on delete cascade,
    media_url text not null check (media_url ~ '^https://'),
    format text not null default 'auto' check (format in ('auto','hls','dash','file','youtube-live','youtube-movie','youtube-doc')),
    provider text not null default '',
    rights_status text not null default 'unknown' check (rights_status in ('unknown','authorized','restricted','blocked')),
    validation_status text not null default 'cataloged' check (validation_status in ('cataloged','source_found','http_ok','manifest_ok','playback_ok','blocked_auth','blocked_drm','blocked_rights','unavailable')),
    enabled boolean not null default false,
    priority integer not null default 100,
    last_check timestamptz,
    created_at timestamptz not null default now(),
    unique(media_id,media_url)
);

create table if not exists public.vortex_source_checks (
    id bigint generated always as identity primary key,
    source_id bigint not null references public.vortex_sources(id) on delete cascade,
    checked_at timestamptz not null default now(),
    http_status integer,
    manifest_ok boolean not null default false,
    playback_ok boolean not null default false,
    details text not null default ''
);
create index if not exists vortex_sources_feed_idx
    on public.vortex_sources(enabled,validation_status,rights_status,priority);
create index if not exists vortex_checks_source_time_idx
    on public.vortex_source_checks(source_id,checked_at desc);

alter table public.vortex_media enable row level security;
alter table public.vortex_sources enable row level security;
alter table public.vortex_source_checks enable row level security;

-- Deliberately no anon/authenticated policies. Public read goes through
-- a read-only Edge Function which exports an allowlisted, filtered feed.
revoke all on public.vortex_media,public.vortex_sources,public.vortex_source_checks
    from public,anon,authenticated;
revoke all on all sequences in schema public from anon,authenticated;
