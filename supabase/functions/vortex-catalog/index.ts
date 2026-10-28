// Supabase Edge Function "vortex-catalog". Read-only and no video proxying.
// Set verify_jwt=false: this endpoint exports ONLY curated, approved data.
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are server-side environment secrets.
Deno.serve(async (request: Request) => {
  if (request.method !== "GET") {
    return new Response(JSON.stringify({ error: "Método não permitido" }), {
      status: 405, headers: { "content-type": "application/json", "allow": "GET" }
    });
  }
  const base = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!base || !key) return new Response("Configuration unavailable", { status: 503 });
  const select = "media_url,format,provider,validation_status,rights_status,media:vortex_media(display_name,kind,category,tvg_id,poster_url,description,release_year)";
  const path = "/rest/v1/vortex_sources?select=" + encodeURIComponent(select)
    + "&enabled=eq.true&validation_status=eq.playback_ok&rights_status=eq.authorized&order=priority.asc&limit=500";
  try {
    const upstream = await fetch(base + path, {
      headers: { "apikey": key, "Authorization": "Bearer " + key, "Accept": "application/json" },
      signal: AbortSignal.timeout(8000)
    });
    if (!upstream.ok) throw new Error("Database unavailable");
    const rows = await upstream.json();
    if (!Array.isArray(rows)) throw new Error("Malformed database response");
    const items = rows.flatMap((record: any) => {
      const media = record.media;
      const url = record.media_url;
      if (!media || typeof url !== "string" || !url.startsWith("https://")) return [];
      if (record.validation_status !== "playback_ok" || record.rights_status !== "authorized") return [];
      return [{
        name: media.display_name,
        url,
        group: media.category,
        tvgId: media.tvg_id,
        logo: media.poster_url,
        description: media.description,
        year: media.release_year,
        format: record.format,
        provider: record.provider,
        status: "playback_ok",
        rights_status: "authorized"
      }];
    });
    return new Response(JSON.stringify({ version: 1, updated_at: new Date().toISOString(), items }), {
      headers: { "content-type": "application/json; charset=utf-8", "cache-control": "public, max-age=300" }
    });
  } catch (_error) {
    return new Response(JSON.stringify({ error: "Catálogo temporariamente indisponível" }), {
      status: 503, headers: { "content-type": "application/json", "cache-control": "no-store" }
    });
  }
});
