-- Daily source-discovery worker for Vortex TV (not Vortex TV OS).
-- Call enqueue once a day and process queued pg_net responses every 10 minutes.
-- Raw candidate URLs remain private; no claim that discovery proves playback rights.
CREATE OR REPLACE FUNCTION vortex_internal.process_media_bot(p_limit integer DEFAULT 10)
RETURNS TABLE(processed integer,candidates integer,published integer,failed integer)
LANGUAGE plpgsql SET search_path = ''
AS $$
DECLARE
  r record;
  stats record;
  body jsonb;
  item jsonb;
  v_url text;
  v_id text;
  v_name text;
  count_found integer;
  count_new integer;
  count_published integer;
  affected integer;
  message text;
  v_processed integer:=0;
  v_candidates integer:=0;
  v_published integer:=0;
  v_failed integer:=0;
BEGIN
  FOR r IN
    SELECT job.request_id,job.run_id,job.feed_id,job.requested_at,
      feed.feed_type,feed.feed_name,feed.feed_url,
      response.status_code,response.content,response.error_msg
    FROM vortex_internal.media_bot_requests job
    JOIN vortex_internal.media_bot_feeds feed ON feed.id=job.feed_id
    LEFT JOIN net._http_response response ON response.id=job.request_id
    WHERE job.processed_at IS NULL
      AND (response.id IS NOT NULL OR job.requested_at<now()-interval '4 minutes')
    ORDER BY job.requested_at,job.request_id
    LIMIT LEAST(GREATEST(p_limit,1),20)
    FOR UPDATE OF job SKIP LOCKED
  LOOP
    count_found:=0;count_new:=0;count_published:=0;message:='';
    BEGIN
      IF r.status_code<>200 OR r.content IS NULL THEN
        RAISE EXCEPTION 'Feed indisponível: HTTP %',COALESCE(r.status_code,0);
      END IF;
      IF r.feed_type='m3u' THEN
        SELECT found,new_candidates,approved_sources
          INTO count_found,count_new,count_published
          FROM vortex_internal.stage_media_bot_m3u(r.feed_id,r.content);
      ELSIF r.feed_type='official_vod' THEN
        IF length(r.content)>150000 THEN RAISE EXCEPTION 'JSON muito grande'; END IF;
        body:=r.content::jsonb;
        IF body->>'provider'<>'NetMovies' OR jsonb_typeof(body->'items')<>'array'
          THEN RAISE EXCEPTION 'Feed oficial inesperado'; END IF;
        FOR item IN SELECT value FROM jsonb_array_elements(body->'items') LIMIT 30
        LOOP
          v_id:=item->>'video_id';
          v_name:=left(coalesce(item->>'name',''),180);
          v_url:=item->>'url';
          IF v_id !~ '^[A-Za-z0-9_-]{11}$'
            OR v_url<>'https://www.youtube.com/watch?v='||v_id
            OR length(v_name)<3 THEN CONTINUE; END IF;
          count_found:=count_found+1;
          INSERT INTO vortex_internal.media_bot_candidates(
             source_hash,media_url,display_name,group_title,tvg_id,logo_url,
             media_format,feed_name,origin_url,origin_host,rights_status,
             discovery_status,priority_score)
          VALUES (
             md5(v_url),v_url,v_name,'Filmes • NetMovies',v_id,
             'https://i.ytimg.com/vi/'||v_id||'/hqdefault.jpg',
             'youtube',r.feed_name,r.feed_url,'www.youtube.com',
             'official_publisher','candidate',90
          )
          ON CONFLICT(source_hash) DO NOTHING;
          GET DIAGNOSTICS affected=ROW_COUNT;
          count_new:=count_new+affected;
        END LOOP;
      ELSE
        RAISE EXCEPTION 'Tipo de feed desconhecido';
      END IF;
    EXCEPTION WHEN OTHERS THEN
      message:=left(SQLERRM,220);
      v_failed:=v_failed+1;
    END;
    UPDATE vortex_internal.media_bot_requests SET processed_at=now(),
      http_status=r.status_code,candidates_found=count_found,
      new_candidates=count_new,published_sources=count_published,error=message
    WHERE request_id=r.request_id;
    UPDATE vortex_internal.media_bot_feeds SET last_run_at=now(),
      last_status=CASE WHEN message='' THEN 'indexed' ELSE 'error' END,
      last_found=count_found,last_error=message
    WHERE id=r.feed_id;
    UPDATE vortex_internal.media_bot_runs SET
      feeds_processed=feeds_processed+1,
      new_candidates=new_candidates+count_new,
      published_sources=published_sources+count_published,
      errors=errors+CASE WHEN message='' THEN 0 ELSE 1 END
    WHERE id=r.run_id;
    IF NOT EXISTS(SELECT 1 FROM vortex_internal.media_bot_requests
      WHERE run_id=r.run_id AND processed_at IS NULL) THEN
      UPDATE vortex_internal.media_bot_runs
      SET state=CASE WHEN errors>0 THEN 'partial' ELSE 'completed' END,
          finished_at=now() WHERE id=r.run_id;
    END IF;
    v_processed:=v_processed+1;
    v_candidates:=v_candidates+count_new;
    v_published:=v_published+count_published;
  END LOOP;
  RETURN QUERY SELECT v_processed,v_candidates,v_published,v_failed;
END;
$$;
REVOKE ALL ON FUNCTION vortex_internal.process_media_bot(integer)
  FROM PUBLIC,anon,authenticated;

-- A named job is idempotent in pg_cron. This does not use GitHub Actions.
DO $$
BEGIN
  IF EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_cron')
    AND EXISTS(SELECT 1 FROM pg_extension WHERE extname='pg_net') THEN
    PERFORM cron.schedule('vortex-media-bot-daily','17 5 * * *',
      'SELECT vortex_internal.enqueue_media_bot(false)');
    PERFORM cron.schedule('vortex-media-bot-results','*/10 * * * *',
      'SELECT * FROM vortex_internal.process_media_bot(15)');
  END IF;
END $$;
