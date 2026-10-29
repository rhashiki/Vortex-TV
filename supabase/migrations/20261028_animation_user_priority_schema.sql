-- Vortex TV animation priority ledger.
-- The 200 human-curated rows come from the user. No video URLs are invented.
-- Safe to apply repeatedly; existing series, films and episode identifiers are preserved.
DO $$
DECLARE c record;
BEGIN
  FOR c IN
    SELECT conname FROM pg_constraint
    WHERE conrelid='public.vortex_animation_catalog'::regclass
      AND contype='c'
      AND pg_get_constraintdef(oid) ILIKE '%release_year%'
      AND pg_get_constraintdef(oid) LIKE '%1990%'
  LOOP
    EXECUTE format('ALTER TABLE public.vortex_animation_catalog DROP CONSTRAINT %I',c.conname);
  END LOOP;
END $$;
ALTER TABLE public.vortex_animation_catalog
  DROP CONSTRAINT IF EXISTS vortex_animation_year_user_range;
ALTER TABLE public.vortex_animation_catalog
  ADD CONSTRAINT vortex_animation_year_user_range
  CHECK (release_year IS NULL OR release_year BETWEEN 1989 AND 2026);

CREATE TABLE IF NOT EXISTS public.vortex_animation_user_priority(
  priority_key text PRIMARY KEY,
  kind text NOT NULL CHECK(kind IN('series','film')),
  title_pt text NOT NULL,
  original_title text NOT NULL,
  release_year integer NOT NULL CHECK(release_year BETWEEN 1989 AND 2026),
  franchise text NOT NULL DEFAULT '',
  animation_id bigint REFERENCES public.vortex_animation_catalog(id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'metadata_only'
    CHECK(status IN('metadata_only','source_candidate','verified')),
  poster_status text NOT NULL DEFAULT 'pending'
    CHECK(poster_status IN('pending','found','not_found')),
  metadata_checked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS vortex_animation_user_priority_title_idx
  ON public.vortex_animation_user_priority (lower(title_pt) text_pattern_ops);
CREATE INDEX IF NOT EXISTS vortex_animation_user_priority_animation_idx
  ON public.vortex_animation_user_priority(animation_id);
ALTER TABLE public.vortex_animation_user_priority ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.vortex_animation_user_priority FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.vortex_animation_user_priority TO anon,authenticated;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.vortex_animation_user_priority TO service_role;
CREATE POLICY "Public names for animation priority" ON public.vortex_animation_user_priority
  FOR SELECT TO anon,authenticated USING (true);

CREATE OR REPLACE FUNCTION public.vortex_animation_link_user_priorities()
RETURNS integer
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE linked integer:=0;
BEGIN
  -- Match on exact production and year, including the original title.
  -- Distinct years remain distinct (e.g. TMNT 2003 and TMNT 2012).
  WITH candidates AS(
    SELECT p.priority_key,a.id,
      row_number() OVER (PARTITION BY p.priority_key
        ORDER BY CASE WHEN lower(a.original_title)=lower(p.original_title)
          THEN 0 ELSE 1 END,a.id) AS rank
    FROM public.vortex_animation_user_priority p
    JOIN public.vortex_animation_catalog a ON a.kind=p.kind
      AND (a.release_year=p.release_year OR a.release_year IS NULL)
      AND (lower(a.title)=lower(p.title_pt)
           OR lower(a.title)=lower(p.original_title)
           OR lower(a.original_title)=lower(p.original_title))
    WHERE p.animation_id IS NULL
  )
  UPDATE public.vortex_animation_user_priority p SET animation_id=c.id
    FROM candidates c WHERE c.priority_key=p.priority_key AND c.rank=1;
  GET DIAGNOSTICS linked=ROW_COUNT;
  -- Unmatched entries become metadata-only items without a fake playback URL.
  INSERT INTO public.vortex_animation_catalog
    (external_id,kind,title,original_title,release_year,franchise,metadata_provider,
     availability,poster_url)
  SELECT p.priority_key,p.kind,p.title_pt,p.original_title,p.release_year,p.franchise,
    'User priority / pending enrichment','metadata_only',''
  FROM public.vortex_animation_user_priority p
  WHERE p.animation_id IS NULL
  ON CONFLICT(external_id) DO NOTHING;
  UPDATE public.vortex_animation_user_priority p
    SET animation_id=a.id,
        updated_at=now()
    FROM public.vortex_animation_catalog a
    WHERE p.animation_id IS NULL AND a.external_id=p.priority_key;
  RETURN linked;
END;
$$;
REVOKE ALL ON FUNCTION public.vortex_animation_link_user_priorities() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.vortex_animation_link_user_priorities() TO service_role;
