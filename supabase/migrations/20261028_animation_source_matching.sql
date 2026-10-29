-- Associate animation metadata with user-supplied catalog entries.
-- No series-level URL is treated as a specific episode and no raw URL is published.
CREATE TABLE IF NOT EXISTS vortex_internal.animation_source_matches(
  animation_id bigint NOT NULL REFERENCES public.vortex_animation_catalog(id) ON DELETE CASCADE,
  source_hash text NOT NULL REFERENCES vortex_family.playlist_sources(source_hash) ON DELETE CASCADE,
  match_quality integer NOT NULL DEFAULT 60,
  validation_status text NOT NULL DEFAULT 'candidate'
    CHECK(validation_status IN('candidate','checking','verified','failed','expired')),
  checked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(animation_id,source_hash)
);
CREATE INDEX IF NOT EXISTS animation_source_matches_status_idx
  ON vortex_internal.animation_source_matches(validation_status,animation_id);
REVOKE ALL ON vortex_internal.animation_source_matches FROM PUBLIC,anon,authenticated;
GRANT ALL ON vortex_internal.animation_source_matches TO service_role;

CREATE OR REPLACE FUNCTION vortex_internal.match_animation_film_sources(p_limit integer DEFAULT 500)
RETURNS integer
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE n integer;
BEGIN
  -- Conservative exact-name matcher. No guessing URLs or mixing seasons.
  WITH canonical AS(
    SELECT a.id,p.priority_key,a.release_year,
      regexp_replace(lower(p.title_pt),'[^a-z0-9]','','g') AS pt_key,
      regexp_replace(lower(p.original_title),'[^a-z0-9]','','g') AS original_key
    FROM public.vortex_animation_user_priority p
    JOIN public.vortex_animation_catalog a ON a.id=p.animation_id
    WHERE a.kind='film'
  ),
  candidates AS(
    SELECT f.id,f.priority_key,l.source_hash
    FROM canonical f
    JOIN vortex_family.playlist_sources l
      ON l.group_title ~* 'filme|cinema|movie|vod'
    WHERE (
       regexp_replace(lower(regexp_replace(l.display_name,
        '([[:space:]]|[-])(4k|uhd|fhd|hd|sd|1080p|720p)$','','gi')),
         '[^a-z0-9]','','g') IN (f.pt_key,f.original_key)
      )
    ORDER BY f.id,l.source_hash
    LIMIT LEAST(10000,GREATEST(1,p_limit))
  ),
  inserted AS(
    INSERT INTO vortex_internal.animation_source_matches(
      animation_id,source_hash,match_quality)
    SELECT id,source_hash,70 FROM candidates
    ON CONFLICT DO NOTHING RETURNING animation_id
  )
  SELECT count(*) INTO n FROM inserted;
  UPDATE public.vortex_animation_user_priority p SET status='source_candidate'
  WHERE status='metadata_only'
    AND EXISTS(
      SELECT 1 FROM vortex_internal.animation_source_matches m
      WHERE m.animation_id=p.animation_id
    );
  -- No source is marked verified without playback checks.
  RETURN n;
END;
$$;
REVOKE ALL ON FUNCTION vortex_internal.match_animation_film_sources(integer)
  FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION vortex_internal.match_animation_film_sources(integer) TO service_role;
