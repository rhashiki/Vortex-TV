package os.vortex.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import os.vortex.tv.integration.XmltvGuideParser;

public final class GuideStore {
    private final SharedPreferences prefs;
    public GuideStore(Context ctx){prefs=ctx.getSharedPreferences("vortex_guide",Context.MODE_PRIVATE);}
    public int put(List<XmltvGuideParser.Programme> programmes){
        Map<String,JSONArray> byChannel=new HashMap<>();
        for(XmltvGuideParser.Programme p:programmes){
            if(p.channelId.isEmpty())continue;
            JSONArray list=byChannel.get(p.channelId);
            if(list==null){list=new JSONArray();byChannel.put(p.channelId,list);}
            if(list.length()>=24)continue;
            JSONObject o=new JSONObject();
            try{o.put("start",p.start);o.put("stop",p.stop);o.put("title",p.title);o.put("desc",p.description);list.put(o);}catch(Exception ignored){}
        }
        JSONObject data=new JSONObject();
        try{for(Map.Entry<String,JSONArray> e:byChannel.entrySet())data.put(e.getKey(),e.getValue());}catch(Exception ignored){}
        prefs.edit().putString("epg",data.toString()).apply();return programmes.size();
    }
    public JSONArray get(String tvgId){
        try {return new JSONObject(prefs.getString("epg","{}" )).optJSONArray(tvgId);}
        catch(Exception e){return null;}
    }
}
