// Vortex TV Family: private, paged source-catalog delivery.
// This endpoint is NEVER public despite verify_jwt=false: it demands a
// separate high-entropy family key and checks its SHA-256 in private Postgres.
// No media proxying, no user-submitted URL fetching, no logging of credentials.
const project = Deno.env.get("SUPABASE_URL") || "";
const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const headers = {
  "apikey": service,
  "Authorization": "Bearer " + service,
  "Content-Type": "application/json"
};
const result=(body:string,status:number,extra:Record<string,string>={})=>
  new Response(body,{status,headers:{"Cache-Control":"no-store",
    "X-Content-Type-Options":"nosniff",...extra}});
const hashToken=async(s:string)=>{
  const bytes=new TextEncoder().encode(s);
  const digest=await crypto.subtle.digest("SHA-256",bytes);
  return Array.from(new Uint8Array(digest)).map(x=>x.toString(16).padStart(2,"0")).join("");
};
async function rpc<T>(name:string,body:Record<string,unknown>):Promise<T>{
  const response=await fetch(project+"/rest/v1/rpc/"+name,{
    method:"POST",headers,body:JSON.stringify(body),
    signal:AbortSignal.timeout(11000)
  });
  if(!response.ok)throw new Error("Catalog database unavailable");
  return await response.json() as T;
}
function clean(s:unknown){
  return String(s??"").replace(/[\r\n]/g," ").replace(/"/g,"'").trim();
}
Deno.serve(async(req:Request)=>{
  if(req.method!=="GET")return result("Method not allowed",405);
  if(!project.startsWith("https://")||!service)return result("Configuration unavailable",503);
  const code=req.headers.get("x-vortex-family-key")?.trim().toUpperCase()||"";
  // The secret is NOT placed in any public Android build, source repository or URL.
  if(!/^[A-F0-9]{20}$/.test(code))return result("Pairing required",401);
  try {
    const ok=await rpc<boolean>("vortex_family_verify",{p_hash:await hashToken(code)});
    if(ok!==true)return result("Not authorized",403);
    const u=new URL(req.url),cursor=u.searchParams.get("after")||"0";
    if(!/^\d{1,18}$/.test(cursor))return result("Invalid cursor",400);
    const after=Number(cursor);
    if(!Number.isSafeInteger(after)||after<0)return result("Invalid cursor",400);
    const rows=await rpc<Array<{
      id:number,display_name:string,group_title:string,tvg_id:string,
      logo_url:string,media_url:string,media_format:string,identity_key:string,catalog_status:string
    }>>("vortex_family_page",{p_after:after,p_limit:500});
    if(!Array.isArray(rows)||rows.length>500)return result("Malformed catalog",502);
    let m3u="#EXTM3U\n";
    for(const row of rows){
      if(typeof row.media_url!=="string"||!/^https?:\/\//i.test(row.media_url))continue;
      const name=clean(row.display_name).slice(0,240)||"Sem título";
      const logo=clean(row.logo_url).slice(0,2048);
      const group=clean(row.group_title).slice(0,140);
      const tvg=clean(row.tvg_id).slice(0,140);
      const state=["available","searching","candidate"].includes(row.catalog_status)
        ?row.catalog_status:"candidate";
      m3u+='#EXTINF:-1 tvg-id="'+tvg+'" tvg-name="'+name+
           '" group-title="'+group+'" tvg-logo="'+logo+
           '" vortex-status="'+state+'",'+name+'\n'+row.media_url+'\n';
    }
    const next=rows.length?rows[rows.length-1].id:after;
    return result(m3u,200,{
      "Content-Type":"application/x-mpegURL; charset=utf-8",
      "X-Vortex-Next-Cursor":String(next),
      "X-Vortex-Has-More":rows.length===500?"true":"false",
      "X-Vortex-Page-Count":String(rows.length),
      "Access-Control-Expose-Headers":"X-Vortex-Next-Cursor,X-Vortex-Has-More,X-Vortex-Page-Count",
    });
  }catch(_){
    return result("Catalog temporarily unavailable",503);
  }
});