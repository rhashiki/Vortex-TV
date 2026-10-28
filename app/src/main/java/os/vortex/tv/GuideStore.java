package os.vortex.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONArray;
import os.vortex.tv.integration.XmltvGuideParser;

/** Bounded local EPG: preserve the current and upcoming programmes, not the first day in the feed. */
public final class GuideStore {
    private static final int MAX_PER_CHANNEL = 64;
    private static final long LOOKAHEAD_MS = 48L * 60 * 60 * 1000;
    private final SharedPreferences prefs;
    private JSONObject cached;

    public GuideStore(Context ctx) {
        prefs = ctx.getSharedPreferences("vortex_guide", Context.MODE_PRIVATE);
        try { cached = new JSONObject(prefs.getString("epg", "{}")); }
        catch (Exception ignored) { cached = new JSONObject(); }
    }

    public int put(List<XmltvGuideParser.Programme> programmes) {
        final long now = System.currentTimeMillis();
        Map<String,List<XmltvGuideParser.Programme>> grouped = new HashMap<>();
        for (XmltvGuideParser.Programme p : programmes) {
            if (p.channelId == null || p.channelId.isEmpty()) continue;
            long start = XmltvClock.parseMillis(p.start);
            long stop = XmltvClock.parseMillis(p.stop);
            if (start < 0 || stop <= start || stop <= now || start >= now + LOOKAHEAD_MS) continue;
            insert(grouped, p.channelId, p, start);
            if (p.displayName != null && !p.displayName.isEmpty() && !p.displayName.equals(p.channelId)) {
                insert(grouped, p.displayName, p, start);
            }
        }
        JSONObject data = new JSONObject();
        try {
            for (Map.Entry<String,List<XmltvGuideParser.Programme>> entry : grouped.entrySet()) {
                JSONArray array = new JSONArray();
                for (XmltvGuideParser.Programme p : entry.getValue()) {
                    JSONObject item = new JSONObject();
                    item.put("start", p.start);
                    item.put("stop", p.stop);
                    item.put("title", p.title);
                    item.put("desc", p.description);
                    array.put(item);
                }
                data.put(entry.getKey(), array);
            }
        } catch (Exception ignored) {}
        cached = data;
        prefs.edit().putString("epg", data.toString()).apply();
        return programmes.size();
    }

    private static void insert(Map<String,List<XmltvGuideParser.Programme>> byChannel,
                               String id, XmltvGuideParser.Programme item, long start) {
        List<XmltvGuideParser.Programme> list = byChannel.get(id);
        if (list == null) {
            list = new ArrayList<>();
            byChannel.put(id, list);
        }
        int at = 0;
        while (at < list.size() && XmltvClock.parseMillis(list.get(at).start) <= start) at++;
        if (at >= MAX_PER_CHANNEL) return;
        list.add(at, item);
        if (list.size() > MAX_PER_CHANNEL) list.remove(list.size() - 1);
    }

    public JSONArray get(String tvgId) {
        return tvgId == null ? null : cached.optJSONArray(tvgId);
    }

    /** Never pass a stale programme off as the currently airing one. */
    public String now(String tvgId) {
        JSONArray programmes = get(tvgId);
        if (programmes == null) return "Programação não informada";
        long now = System.currentTimeMillis();
        for (int index = 0; index < programmes.length(); index++) {
            JSONObject item = programmes.optJSONObject(index);
            if (item == null) continue;
            long start = XmltvClock.parseMillis(item.optString("start"));
            long stop = XmltvClock.parseMillis(item.optString("stop"));
            if (start <= now && now < stop && start >= 0) {
                String title = item.optString("title", "Sem título");
                if (title.trim().isEmpty()) title = "Sem título";
                for (int next = index + 1; next < programmes.length(); next++) {
                    JSONObject following = programmes.optJSONObject(next);
                    if (following != null && XmltvClock.parseMillis(following.optString("start")) >= stop) {
                        String nextTitle = following.optString("title", "").trim();
                        if (!nextTitle.isEmpty()) return "Agora: " + title + "  •  A seguir: " + nextTitle;
                        break;
                    }
                }
                return "Agora: " + title;
            }
        }
        return "Programação não informada";
    }
}
