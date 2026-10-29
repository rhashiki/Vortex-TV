// Official TVMaze metadata import. No playback URLs, DRM, or media fetching.
// Called by a scheduler token stored in Supabase Vault, not by users.
// Show titles are loaded from a restricted database target list, not hardcoded.
type Target={show_key:string,search_title:string,display_title:string,franchise:string,expected_year:number};
function norm(value:string):string {
 return value.normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase()
  .replace(/[^a-z0-9]+/g,"");
}
function selectShow(results:unknown, target:Target):Record<string,unknown> {
 if(!Array.isArray(results) || results.length>100)throw new Error("Provider search malformed");
 const name=norm(target.search_title);
 const matches=results
  .filter(x=>x&&typeof x==="object"&&x.show&&typeof x.show==="object")
  .map(x=>x.show as Record<string,unknown>)
  .filter(x=>Number.isSafeInteger(Number(x.id))&&
      Number(x.id)>0&&Number(x.id)<100000000)
  .map(x=>{
    const candidate=norm(String(x.name||""));
    const year=Number(String(x.premiered||"").slice(0,4));
    const exact=candidate===name;
    const fuzzy=name.length>7 && (candidate.includes(name)||name.includes(candidate));
    const delta=target.expected_year>0 && year>=1900
      ?Math.abs(target.expected_year-year):0;
    return {x,exact,fuzzy,delta};
  }).filter(x=>(x.exact||x.fuzzy)&&
      (target.expected_year===0||x.delta<=2))
  .sort((a,b)=>Number(b.exact)-Number(a.exact)||a.delta-b.delta);
 if(!matches.length)throw new Error("No matching TVMaze series/year; manual review required");
 return matches[0].x;
}
const base=Deno.env.get("SUPABASE_URL")||"";
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const apiHeaders={"Content-Type":"application/json","apikey":service,"Authorization":"Bearer "+service};
const json=(value:unknown,status=200)=>
  new Response(JSON.stringify(value),{status,headers:{"Content-Type":"application/json",
  "Cache-Control":"no-store"}});
async function hash(value:string){
  const d=await crypto.subtle.digest("SHA-256",new TextEncoder().encode(value));
  return Array.from(new Uint8Array(d)).map(b=>b.toString(16).padStart(2,"0")).join("");
}
async function rpc(name:string,data:unknown){
  const res=await fetch(base+"/rest/v1/rpc/"+name,{
   method:"POST",headers:apiHeaders,body:JSON.stringify(data),
   signal:AbortSignal.timeout(24000)});
  if(!res.ok)throw new Error("database rpc "+res.status+" "+(await res.text()).slice(0,250));
  return await res.json();
}
async function fetchJson(url:string,maxChars:number) {
 const response=await fetch(url,{
  headers:{"Accept":"application/json","User-Agent":"VortexTV/1.0 animation catalog bot"},
  signal:AbortSignal.timeout(14000),redirect:"error"});
 if(!response.ok)throw new Error("TVMaze HTTP "+response.status);
 const length=Number(response.headers.get("content-length")||0);
 if(length>maxChars)throw new Error("Provider response exceeds size limit");
 const str=await response.text();
 if(str.length>maxChars)throw new Error("Provider payload too large");
 return JSON.parse(str);
}
Deno.serve(async (request:Request)=>{
 if(request.method!=="GET")return json({error:"Method not allowed"},405);
 const incoming=request.headers.get("x-vortex-scheduler")?.trim().toUpperCase()||"";
 if(!/^[A-F0-9]{32}$/.test(incoming))return json({error:"Unauthorized"},401);
 if(!base.startsWith("https://")||!service)return json({error:"Service unavailable"},503);
 const authorized=await rpc("vortex_animation_bot_authorized",{p_hash:await hash(incoming)});
 if(authorized!==true)return json({error:"Forbidden"},403);
 const showKey=new URL(request.url).searchParams.get("show")||"";
 if(!/^[a-z0-9]{3,60}$/.test(showKey))
    return json({error:"Invalid series key"},400);
 try{
   const target=await rpc("vortex_animation_target_lookup",
      {p_show_key:showKey}) as Target|null;
   if(!target||typeof target.search_title!=="string"||
      target.search_title.length<2||target.search_title.length>150)
      return json({error:"Series not in curated catalog"},404);
   const results=await fetchJson("https://api.tvmaze.com/search/shows?q="+
     encodeURIComponent(target.search_title),350000);
   const show=selectShow(results,target);
   const id=Number(show?.id);
   if(!Number.isSafeInteger(id)||id<=0||id>99999999)
      throw new Error("Unrecognized provider response");
   const episodes=await fetchJson("https://api.tvmaze.com/shows/"+id+
     "/episodes?specials=1",3500000);
   if(!Array.isArray(episodes)||episodes.length>1500)
      throw new Error("Episode collection invalid");
   const result=await rpc("vortex_animation_import_tvmaze",
       {p_show_key:showKey,p_show:show,p_episodes:episodes});
   return json({show_key:showKey,show_id:id,result});
 }catch(e){
   console.error("TVMaze metadata update failed",showKey,String(e));
   return json({show_key:showKey,error:"Metadata provider temporarily unavailable",
     reason:String(e).slice(0,220)},503);
 }
});