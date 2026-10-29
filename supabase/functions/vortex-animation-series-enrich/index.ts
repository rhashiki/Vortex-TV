// Animation series metadata only; authentic covers and episode numbering via TVMaze.
// Admin-only POST. Does not fabricate or proxy video streams.
const url=Deno.env.get("SUPABASE_URL")||"";
const key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const hd={apikey:key,authorization:"Bearer "+key,"content-type":"application/json"};
const result=(v:any,s=200)=>new Response(JSON.stringify(v),{status:s,
 headers:{"content-type":"application/json","cache-control":"no-store"}});
async function db(path:string,method="GET",body?:any){
 const r=await fetch(url+"/rest/v1/"+path,{method,
  headers:{...hd,...(method!=="GET"?{prefer:"resolution=ignore-duplicates,return=minimal"}:{})},
  ...(body===undefined?{}:{body:JSON.stringify(body)}),
  signal:AbortSignal.timeout(14000)});
 if(!r.ok)throw new Error("DB "+r.status);
 const t=await r.text();return t?JSON.parse(t):null;
}
async function tv(path:string){
 const r=await fetch("https://api.tvmaze.com/"+path,{
  headers:{"user-agent":"VortexTV-Metadata/1.0"},signal:AbortSignal.timeout(9000)});
 if(!r.ok)return null;return r.json();
}
const norm=(v:string)=>v.normalize("NFD").replace(/[\u0300-\u036f]/g,"")
 .toLowerCase().replace(/\s*\(\d{4}\)\s*$/,"").replace(/[^a-z0-9]+/g," ").trim();
Deno.serve(async req=>{
 if(req.method!=="POST")return result({error:"POST required"},405);
 if(!key||req.headers.get("authorization")!=="Bearer "+key)
  return result({error:"Unauthorized"},401);
 try{
  const cfg=await req.json().catch(()=>({}));
  const start=Math.max(0,Math.min(99,Number(cfg.offset)||0));
  const episodeOffset=Math.max(0,Number(cfg.episode_offset)||0);
  const rows=await db("vortex_animation_user_priority?kind=eq.series"+
    "&order=priority_key.asc&select=priority_key,title_pt,original_title,release_year,animation_id"+
    "&offset="+start+"&limit=2");
  const report=[];
  for(const row of rows||[]){
   try{
    const title=row.original_title.replace(/\s*\(\d{4}\)\s*$/,"");
    const matches=await tv("search/shows?q="+encodeURIComponent(title));
    const show=(matches||[]).map((x:any)=>x.show).find((s:any)=>{
     const year=Number(String(s.premiered||"").slice(0,4));
     return norm(s.name||"")===norm(title)
       &&Math.abs(year-Number(row.release_year))<=1;
    });
    if(!show||!row.animation_id){report.push({title:row.title_pt,match:false});continue;}
    const poster=String(show.image?.medium||"");
    const metadata={poster_url:poster.startsWith("https://")?poster:"",
     metadata_provider:"TVMaze",metadata_url:show.url,
     updated_at:new Date().toISOString()};
    await db("vortex_animation_catalog?id=eq."+row.animation_id,"PATCH",metadata);
    await db("vortex_animation_user_priority?priority_key=eq."+
      encodeURIComponent(row.priority_key),"PATCH",{
        poster_status:poster.startsWith("https://")?"found":"not_found",
        metadata_checked_at:new Date().toISOString()
      });
    const parent=await db("vortex_animation_catalog?id=eq."+row.animation_id+
       "&select=external_id&limit=1");
    let epCount=0,more=null;
    if(cfg.include_episodes&&parent?.[0]){
     const all=await tv("shows/"+show.id+"/episodes?specials=1");
     if(Array.isArray(all)){
      const slice=all.slice(episodeOffset,episodeOffset+150);
      const episodes=slice.filter((e:any)=>e?.id&&e?.name).map((e:any)=>({
       external_id:"tvmaze:episode:"+e.id,parent_external_id:parent[0].external_id,
       kind:e.number==null?"special":"episode",title:e.name,
       original_title:e.name,franchise:row.title_pt,
       release_year:Math.max(1989,Math.min(2026,
         Number(String(e.airdate||"").slice(0,4))||row.release_year)),
       season_number:e.season||null,episode_number:e.number||null,
       poster_url:e.image?.medium||"",metadata_url:e.url||"",
       metadata_provider:"TVMaze",availability:"metadata_only"}));
      for(let i=0;i<episodes.length;i+=40)
       await db("vortex_animation_catalog?on_conflict=external_id","POST",episodes.slice(i,i+40));
      epCount=episodes.length;
      more=episodeOffset+slice.length<all.length?episodeOffset+slice.length:null;
     }
    }
    report.push({title:row.title_pt,match:true,poster:!!metadata.poster_url,
                 episodes:epCount,next_episode_offset:more});
   }catch(_){report.push({title:row.title_pt,error:"metadata_request_failed"});}
  }
  return result({offset:start,processed:report.length,report,
    next_offset:start+report.length<100?start+report.length:null,
    video_urls_added:0});
 }catch(_){return result({error:"Metadata provider unavailable"},503);}
});