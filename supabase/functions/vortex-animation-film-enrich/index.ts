// Film artwork metadata only. Admin POST, no video link extraction.
const host=Deno.env.get("SUPABASE_URL")||"";
const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const headers={apikey:key,authorization:"Bearer "+key,"content-type":"application/json"};
const answer=(x:any,s=200)=>new Response(JSON.stringify(x),{
 status:s,headers:{"content-type":"application/json","cache-control":"no-store"}});
async function call(path:string,method="GET",body?:any){
 const r=await fetch(host+"/rest/v1/"+path,{
  method,headers,signal:AbortSignal.timeout(11000),
  ...(body===undefined?{}:{body:JSON.stringify(body)})});
 if(!r.ok)throw Error("Database "+r.status);
 const t=await r.text();return t?JSON.parse(t):null;
}
async function summary(title:string){
 const u="https://en.wikipedia.org/api/rest_v1/page/summary/"+
  encodeURIComponent(title.replace(/ /g,"_"));
 const r=await fetch(u,{signal:AbortSignal.timeout(9000),
   headers:{accept:"application/json","user-agent":"VortexTV-Covers/1.0"}});
 return r.ok?await r.json():null;
}
Deno.serve(async req=>{
 if(req.method!=="POST")return answer({error:"POST only"},405);
 if(!key||req.headers.get("authorization")!=="Bearer "+key)
  return answer({error:"Unauthorized"},401);
 try{
  const a=await req.json().catch(()=>({}));
  const off=Math.min(99,Math.max(0,Number(a.offset)||0));
  const items=await call("vortex_animation_user_priority?kind=eq.film"+
    "&select=priority_key,title_pt,original_title,release_year,animation_id"+
    "&order=priority_key.asc&offset="+off+"&limit=3");
  const result=[];
  for(const i of items||[]){
   let picture="";
   let href="";
   try{
    for(const t of [i.original_title,i.original_title+" (film)",
                     i.original_title+" ("+i.release_year+" film)"]){
     const j=await summary(t);
     if(!j)continue;
     const description=String(j.description||"")+" "+String(j.extract||"");
     if(!/animated|anime|film|movie|stop.motion/i.test(description))continue;
     const src=String(j.thumbnail?.source||"");
     if(!src.startsWith("https://"))continue;
     picture=src;href=String(j.content_urls?.desktop?.page||"");break;
    }
    if(picture&&i.animation_id)
     await call("vortex_animation_catalog?id=eq."+i.animation_id,"PATCH",{
      poster_url:picture,metadata_url:href,metadata_provider:"Wikimedia",
      updated_at:new Date().toISOString()
     });
    await call("vortex_animation_user_priority?priority_key=eq."+
      encodeURIComponent(i.priority_key),"PATCH",{
       poster_status:picture?"found":"not_found",
       metadata_checked_at:new Date().toISOString()
      });
    result.push({title:i.title_pt,poster:!!picture});
   }catch(_){result.push({title:i.title_pt,status:"retry_later"});}
  }
  return answer({checked:result,next_offset:off+result.length<100?off+result.length:null,
   streaming_urls_created:0});
 }catch(_){return answer({error:"Metadata provider unavailable"},503);}
});