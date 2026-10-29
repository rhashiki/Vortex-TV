/**
 * Vortex TV automatic catalog worker
 * - Runs from Supabase pg_cron with a Vault-held scheduler secret.
 * - Checks direct HLS manifests AND an actual media segment.
 * - Publishes new verified candidates to the private family catalog.
 * - Flags failed existing streams; the catalog shows "Buscando novo servidor".
 * - Never proxies video, exposes credentials, or fetches user-provided URLs.
 */
const base=Deno.env.get("SUPABASE_URL")||"";
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const rpcHeaders={
  "apikey":service,
  "Authorization":"Bearer "+service,
  "Content-Type":"application/json",
};
type Source={hash:string;url:string;origin:"candidate"|"catalog";title:string;category:string};
const output=(body:unknown,status=200)=>new Response(JSON.stringify(body),{
  status,headers:{"content-type":"application/json","cache-control":"no-store",
    "x-content-type-options":"nosniff"}
});
const tokenHash=async(text:string)=>{
 const hash=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(text));
 return Array.from(new Uint8Array(hash)).map(x=>x.toString(16).padStart(2,"0")).join("");
};
async function rpc<T>(fn:string,values:Record<string,unknown>):Promise<T>{
 const r=await fetch(base+"/rest/v1/rpc/"+fn,{
   method:"POST",headers:rpcHeaders,body:JSON.stringify(values),
   signal:AbortSignal.timeout(15000)
 });
 if(!r.ok)throw new Error("database_rpc_"+r.status);
 return await r.json() as T;
}
function safeTarget(s:string):URL|null{
 try{
  const u=new URL(s);
  if(u.protocol!=="http:"&&u.protocol!=="https:")return null;
  if(u.username||u.password||u.port&&u.port!=="80"&&u.port!=="443")return null;
  const host=u.hostname.toLowerCase();
  if(host.length<4||host.length>253||!host.includes(".")
      ||host==="localhost"||host.endsWith(".localhost")
      ||host.endsWith(".local")||host.endsWith(".internal")
      ||host.endsWith(".test")||host.endsWith(".invalid")
      ||host.includes("metadata")
      ||host.includes("%")
      ||/^\d+\.\d+\.\d+\.\d+$/.test(host)||host.includes(":"))
    return null;
  if(!/^[a-z0-9.-]+$/.test(host))return null;
  return u;
 }catch{return null;}
}
async function textBody(r:Response,limit:number):Promise<string>{
 const length=Number(r.headers.get("content-length")||0);
 if(length>limit)throw new Error("oversized_manifest");
 const reader=r.body?.getReader();
 if(!reader)throw new Error("empty_manifest");
 let lengthRead=0;
 const chunks:Uint8Array[]=[];
 try{
  for(;;){
   const {value,done}=await reader.read();
   if(done)break;
   if(value){
     lengthRead+=value.length;
     if(lengthRead>limit)throw new Error("oversized_manifest");
     chunks.push(value);
   }
  }
 }finally{try{await reader.cancel()}catch{/* closed */}}
 const combined=new Uint8Array(lengthRead);let offset=0;
 for(const c of chunks){combined.set(c,offset);offset+=c.length;}
 return new TextDecoder().decode(combined);
}
async function getSafe(u:URL,range:boolean):Promise<Response>{
 const r=await fetch(u.toString(),{
   method:"GET",redirect:"manual",
   headers:range?{"Range":"bytes=0-4095","Accept":"*/*"}:
     {"Accept":"application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*"},
   signal:AbortSignal.timeout(8000)
 });
 if([301,302,303,307,308].includes(r.status)){
   const location=r.headers.get("location");
   try{await r.body?.cancel()}catch{}
   if(!location)throw new Error("redirect_without_location");
   const next=safeTarget(new URL(location,u).href);
   if(!next)throw new Error("unsafe_redirect");
   const redirected=await fetch(next.toString(),{
     method:"GET",redirect:"manual",
     headers:range?{"Range":"bytes=0-4095"}:{},
     signal:AbortSignal.timeout(8000)
   });
   if([301,302,303,307,308].includes(redirected.status))
     throw new Error("too_many_redirects");
   return redirected;
 }
 return r;
}
function child(lines:string[],directive:string){
 const index=lines.findIndex(x=>x.startsWith(directive));
 return index<0?"":lines.slice(index+1).find(x=>x.length>0&&!x.startsWith("#"))||"";
}
async function probe(source:Source):Promise<{ok:boolean;reason:string}>{
 let manifest=safeTarget(source.url);
 if(!manifest)return {ok:false,reason:"unsafe_or_invalid_url"};
 try{
  for(let hops=0;hops<3;hops++){
   const resp=await getSafe(manifest,false);
   if(resp.status!==200){try{await resp.body?.cancel()}catch{}
     return {ok:false,reason:"manifest_http_"+resp.status};}
   const m3u=await textBody(resp,262144);
   if(!m3u.trimStart().startsWith("#EXTM3U"))
     return {ok:false,reason:"not_hls"};
   const lines=m3u.split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
   const variant=child(lines,"#EXT-X-STREAM-INF");
   if(variant){
     const next=safeTarget(new URL(variant,resp.url).href);
     if(!next)return {ok:false,reason:"unsafe_variant"};
     manifest=next;continue;
   }
   const segment=child(lines,"#EXTINF");
   if(!segment)return {ok:false,reason:"no_media_segment"};
   const segmentUrl=safeTarget(new URL(segment,resp.url).href);
   if(!segmentUrl)return {ok:false,reason:"unsafe_segment"};
   const clip=await getSafe(segmentUrl,true);
   const code=clip.status;
   if(code!==200&&code!==206){try{await clip.body?.cancel()}catch{}
     return {ok:false,reason:"segment_http_"+code};}
   const reader=clip.body?.getReader();
   let received=0;
   try{
    const first=await reader?.read();
    received=first?.value?.length||0;
   }finally{try{await reader?.cancel()}catch{}}
   return {ok:received>0,reason:received>0?"manifest_and_segment_ok":"segment_empty"};
  }
  return {ok:false,reason:"too_many_manifests"};
 }catch(e){
   const name=e instanceof Error?e.message:"network_error";
   if(name==="oversized_manifest")return {ok:false,reason:name};
   return {ok:false,reason:"request_error"};
 }
}
Deno.serve(async(req:Request)=>{
 if(req.method!=="GET")return output({error:"method"},405);
 if(!base.startsWith("https://")||!service)return output({error:"config"},503);
 const token=req.headers.get("x-vortex-monitor-key")?.trim()||"";
 if(!/^[A-F0-9]{48}$/.test(token))return output({error:"authentication_required"},401);
 try{
  const authorized=await rpc<boolean>("vortex_monitor_authorized",{
    p_token_hash:await tokenHash(token)
  });
  if(authorized!==true)return output({error:"not_authorized"},403);
  const list=await rpc<Source[]>("vortex_monitor_pull",{p_candidates:8,p_existing:10});
  if(!Array.isArray(list)||list.length>24)return output({error:"invalid_batch"},502);
  const results=await Promise.all(list.map(async item=>{
    if(!/^[a-f0-9]{32}$/.test(item.hash)
        ||(item.origin!=="catalog"&&item.origin!=="candidate"))
      return {checked:false,ok:false};
    const verdict=await probe(item);
    try{
      await rpc("vortex_monitor_record",{
        p_hash:item.hash,p_origin:item.origin,p_passed:verdict.ok,p_note:verdict.reason
      });
      return {checked:true,ok:verdict.ok,published:verdict.ok&&item.origin==="candidate"};
    }catch(e){return {checked:false,ok:false,write_error:e instanceof Error?e.message.slice(0,70):"unknown"};}
  }));
  return output({
    checked:results.filter(x=>x.checked).length,
    healthy:results.filter(x=>x.ok).length,
    failed:results.filter(x=>x.checked&&!x.ok).length,
    newly_available:results.filter(x=>x.published).length,
    write_errors:results.filter(x=>"write_error" in x).reduce((acc:Record<string,number>,r)=>{const k=("write_error" in r?r.write_error:"unknown")||"unknown";acc[k]=(acc[k]||0)+1;return acc;},{} as Record<string,number>)
  });
 }catch{
  return output({error:"catalog_monitor_temporarily_unavailable"},503);
 }
});