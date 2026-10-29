// Vortex TV — importer for the user's 100 animation series and 100 films.
// Runs with Supabase service_role only; NEVER serves, scrapes or proxies videos.
// Sources are two reviewed, versioned GitHub TSV files (no user-provided URLs).
const base="https://raw.githubusercontent.com/rhashiki/Vortex-TV/main/catalog/animation_priority/";
const names=[["series","series.tsv"],["film","films.tsv"]] as const;
const project=Deno.env.get("SUPABASE_URL")||"";
const secret=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{
 status,headers:{"content-type":"application/json","cache-control":"no-store"}
});
async function readList(kind:"series"|"film",filename:string){
 const r=await fetch(base+filename,{
  headers:{"accept":"text/plain","user-agent":"Vortex-TV-Animation-Metadata/1.0"},
  signal:AbortSignal.timeout(12000),redirect:"error"
 });
 if(!r.ok||Number(r.headers.get("content-length")||"0")>30000)
  throw new Error("Priority list unavailable");
 const text=await r.text();
 if(text.length>30000||!text.startsWith("title_pt|year|original_title\n"))
  throw new Error("Unexpected list format");
 const lines=text.trimEnd().split(/\r?\n/).slice(1);
 if(lines.length!==100)throw new Error("Expected 100 "+kind+" records");
 return lines.map((line,i)=>{
  const fields=line.split("|");
  if(fields.length!==3)throw new Error("Invalid priority item");
  const [title,year,original]=fields;
  if(!title||title.length>180||!original||original.length>180||
      !/^(1989|19[9][0-9]|20[012][0-9])$/.test(year))
    throw new Error("Invalid priority metadata");
  return {
   priority_key:"vortex-priority-"+kind+"-"+String(i+1).padStart(3,"0"),
   kind,title_pt:title,original_title:original,release_year:Number(year),
   franchise:kind==="series"?title:franchise(title)
  };
 });
}
function franchise(title:string){
 const text=title.normalize("NFD").replace(/[\u0300-\u036f]/g,"").toLowerCase();
 if(/shrek|gato de botas/.test(text))return "Shrek";
 if(/era do gelo/.test(text))return "A Era do Gelo";
 if(/kung fu panda/.test(text))return "Kung Fu Panda";
 if(/madagascar|pinguins de madagascar/.test(text))return "Madagascar";
 if(/carros(?:\s|\d|$)/.test(text))return "Carros";
 if(/meu malvado favorito|minions/.test(text))return "Meu Malvado Favorito";
 if(/frozen/.test(text))return "Frozen";
 if(/hotel transilvania/.test(text))return "Hotel Transilvânia";
 if(/toy story/.test(text))return "Toy Story";
 if(/como treinar o seu dragao/.test(text))return "Como Treinar o Seu Dragão";
 if(/rio(?:\s|\d|$)/.test(text))return "Rio";
 if(/simpsons/.test(text))return "Os Simpsons";
 if(/monstros s\.a|universidade monstros/.test(text))return "Monstros S.A.";
 return title;
}
async function post(path:string,body:unknown,prefer:string){
 const r=await fetch(project+"/rest/v1/"+path,{
  method:"POST",redirect:"error",signal:AbortSignal.timeout(16000),
  headers:{
   "apikey":secret,"authorization":"Bearer "+secret,
   "content-type":"application/json","prefer":prefer
  },body:JSON.stringify(body)
 });
 if(!r.ok)throw new Error("Database error ("+r.status+")");
 return await r.text();
}
Deno.serve(async request=>{
 if(request.method!=="POST")return json({error:"POST only"},405);
 const authorization=request.headers.get("authorization")||"";
 if(!secret||authorization!=="Bearer "+secret)return json({error:"Unauthorized"},401);
 if(!project.startsWith("https://"))return json({error:"Configuration unavailable"},503);
 try{
  const [series,films]=await Promise.all(names.map(([kind,file])=>readList(kind,file)));
  const all=[...series,...films];
  if(new Set(all.map(x=>x.priority_key)).size!==200)throw new Error("Duplicate priority keys");
  for(let start=0;start<all.length;start+=50)
   await post("vortex_animation_user_priority?on_conflict=priority_key",
       all.slice(start,start+50),"resolution=merge-duplicates,return=minimal");
  // Link to any existing TVMaze/Wikidata metadata first; only then add missing.
  await post("rpc/vortex_animation_link_user_priorities",{},"return=representation");
  return json({imported:200,series:100,films:100,playback_sources_created:0,
               status:"metadata_only",date:"2026-10-28"});
 }catch(error){
  console.error("Animation priority import failed:",String(error));
  return json({error:"Import failed; previous metadata preserved"},503);
 }
});