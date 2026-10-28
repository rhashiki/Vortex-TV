// Read-only discovery of new movies posted by the official NetMovies channel.
// Original YouTube iframe is used for playback; no media extraction or proxying.
const channelId="UCK5RRQ3TdwAbbCWrtnXsN3Q";
const rss="https://www.youtube.com/feeds/videos.xml?channel_id="+channelId;
const json=(obj:unknown,status=200)=>new Response(JSON.stringify(obj),{
 status,headers:{"content-type":"application/json; charset=utf-8",
 "cache-control":status===200?"public, max-age=1200":"no-store"}
});
const decode=(s:string)=>s.replace(/&amp;/g,"&").replace(/&quot;/g,'"')
 .replace(/&#39;|&apos;/g,"'").replace(/&lt;/g,"<").replace(/&gt;/g,">");
const field=(xml:string,tag:string)=>{
 const escaped=tag.replace(/:/g,"\\:");
 const pattern=new RegExp("<"+escaped+"(?:\\s[^>]*)?>([\\s\\S]*?)<\\/"+escaped+">","i");
 return decode((xml.match(pattern)?.[1]||"").trim());
};
Deno.serve(async(request:Request)=>{
 if(request.method!=="GET")return json({error:"Método não permitido"},405);
 try{
  const response=await fetch(rss,{signal:AbortSignal.timeout(10000),
   headers:{"accept":"application/atom+xml,application/xml,text/xml"}});
  if(!response.ok)return json({error:"Feed temporariamente indisponível",http_status:response.status},503);
  const xml=await response.text();
  if(xml.length>200000 || !xml.includes("<feed ") || !xml.includes("NetMovies"))
   return json({error:"Feed inesperado"},503);
  const entries=[...xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)].slice(0,25);
  const used=new Set<string>();
  const items=entries.flatMap(match=>{
   const content=match[1];
   if(field(content,"yt:channelId")!==channelId)return [];
   const id=field(content,"yt:videoId");
   const title=field(content,"title").replace(/\s+/g," ").trim();
   if(!/^[A-Za-z0-9_-]{11}$/.test(id)||used.has(id))return [];
   if(!/filme\s+completo/i.test(title)||/(trailer|teaser|melhores cenas|shorts)/i.test(title))
    return [];
   used.add(id);
   let genre="Outros";
   for(const g of ["Ação","Aventura","Suspense","Terror","Comédia","Romance","Drama",
      "Animação","Fantasia","Ficção científica"]){
     if(new RegExp("\\b"+g+"\\b","i").test(title)){genre=g;break;}
   }
   const yearMatch=title.match(/\((19\d{2}|20\d{2})\)/);
   const name=title.replace(/\s*[|•]\s*Filme Completo[\s\S]*$/i,"")
     .replace(/\s*[|•]\s*(?:Filme\s+de\s+)?(?:Ação|Aventura|Suspense|Terror|Comédia|Drama)[\s\S]*$/i,"")
     .trim().slice(0,120);
   if(name.length<3)return [];
   return [{
    name,url:"https://www.youtube.com/watch?v="+id,
    format:"youtube-movie",group:"Filmes • "+genre,genre,
    logo:"https://i.ytimg.com/vi/"+id+"/hqdefault.jpg",
    tvgId:"",provider:"NetMovies • YouTube oficial",
    year:yearMatch?.[1]||"",
    description:"Publicado no canal oficial NetMovies. Reprodução via player YouTube; disponibilidade e incorporação dependem do YouTube.",
    status:"catalog_candidate",playback_status:"unverified",
    video_id:id
   }];
  });
  return json({version:1,provider:"NetMovies",channel_id:channelId,
    fetched_at:new Date().toISOString(),items});
 }catch(e){console.error("NetMovies feed",String(e));return json({error:"Feed indisponível"},503);}
});