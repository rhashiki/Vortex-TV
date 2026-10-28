// Vortex TV Cloud — read-only public catalog of verified, authorized sources.
// This function serves JSON metadata; it never proxies video.
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "cache-control": status === 200 ? "public, max-age=300" : "no-store",
    "x-content-type-options": "nosniff"
  }
});
Deno.serve(async (request: Request) => {
  if (request.method !== "GET") {
    return new Response(JSON.stringify({ error: "Método não permitido" }), {
      status: 405,
      headers: { "content-type": "application/json", "allow": "GET" }
    });
  }
  const base = Deno.env.get("SUPABASE_URL");
  let secret = "";
  try {
    const secrets = JSON.parse(Deno.env.get("SUPABASE_SECRET_KEYS") || "{}");
    secret = typeof secrets.default === "string" ? secrets.default : "";
  } catch (_ignored) { /* fall back to legacy key below */ }
  const isLegacy = !secret;
  if (!secret) secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
  if (!base || !secret) return json({ error: "Configuração indisponível" }, 503);
  const select = "media_url,format,provider,validation_status,rights_status,media:vortex_media(display_name,kind,category,tvg_id,poster_url,description,release_year)";
  const lab = new URL(request.url).searchParams.get("lab") === "1";
  const filters = lab
    ? "&validation_status=eq.manifest_ok&rights_status=eq.authorized&order=priority.asc&limit=30"
    : "&enabled=eq.true&validation_status=eq.playback_ok&rights_status=eq.authorized&order=priority.asc&limit=500";
  const path = "/rest/v1/vortex_sources?select=" + encodeURIComponent(select) + filters;
  try {
    const headers: Record<string, string> = { "apikey": secret, "accept": "application/json" };
    if (isLegacy) headers["authorization"] = "Bearer " + secret;
    const upstream = await fetch(base + path, { headers, signal: AbortSignal.timeout(8000) });
    if (!upstream.ok) {
      console.error("Vortex catalog DB status", upstream.status);
      return json({ error: "Banco indisponível" }, 503);
    }
    const rows: unknown = await upstream.json();
    if (!Array.isArray(rows)) return json({ error: "Resposta inválida" }, 503);
    const items = rows.flatMap((record: any) => {
      if (!record || typeof record.media_url !== "string" || !record.media_url.startsWith("https://")) return [];
      if (record.rights_status !== "authorized") return [];
      if (lab && record.validation_status !== "manifest_ok") return [];
      if (!lab && record.validation_status !== "playback_ok") return [];
      const media = record.media;
      if (!media || typeof media.display_name !== "string") return [];
      return [{
        name: media.display_name,
        url: record.media_url,
        group: media.category || "",
        tvgId: media.tvg_id || "",
        logo: media.poster_url || "",
        description: media.description || "",
        year: media.release_year || "",
        format: record.format || "auto",
        provider: record.provider || "Vortex TV Cloud",
        status: record.validation_status,
        rights_status: "authorized"
      }];
    });
    return json({
      version: 1,
      updated_at: new Date().toISOString(),
      items: lab ? [] : items,
      ...(lab ? { test_items: items, warning: "Manifesto verificado; reprodução não homologada no aparelho" } : {})
    });
  } catch (e) {
    console.error("Vortex catalog error", String(e));
    return json({ error: "Catálogo temporariamente indisponível" }, 503);
  }
});
