package os.vortex.tv.integration;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** Official YouTube iframe, with external YouTube fallback. No extraction of video streams. */
public final class YouTubePlaybackActivity extends Activity {
    public static final String EXTRA_VIDEO_URL="vortex.youtube.url";
    public static final String EXTRA_LIVE_LIST="vortex.youtube.live_channels";
    public static final String EXTRA_LIVE_INDEX="vortex.youtube.live_index";
    // Real third-party service origin; never claim youtube.com is the embedding page.
    // A standard HTTPS referrer allows YouTube to evaluate app embedding permissions.
    private static final String EMBED_ORIGIN="https://vortex-tv-android-build.onrender.com/";
    private WebView web;
    private TextView info;
    private String videoId;
    private JSONArray liveItems=new JSONArray();
    private int liveIndex=0;
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        String url=getIntent().getStringExtra(EXTRA_VIDEO_URL);
        videoId=extractVideoId(url);
        if(videoId==null){Toast.makeText(this,"Vídeo YouTube inválido",Toast.LENGTH_LONG).show();finish();return;}
        try{String list=getIntent().getStringExtra(EXTRA_LIVE_LIST);if(list!=null)liveItems=new JSONArray(list);}catch(Exception ignored){}
        liveIndex=Math.max(0,Math.min(getIntent().getIntExtra(EXTRA_LIVE_INDEX,0),Math.max(0,liveItems.length()-1)));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.BLACK);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(12),0,dp(10),0);
        toolbar.setBackgroundColor(0xFF11111A);
        TextView back=button("‹ Voltar",()->finish());toolbar.addView(back);
        info=button("YouTube · Player oficial",()->showGuide());
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,dp(48),1);
        toolbar.addView(info,titleParams);
        toolbar.addView(button("Abrir no YouTube ↗",this::openExternal));
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(54)));
        web=new WebView(this);web.setBackgroundColor(Color.BLACK);web.setFocusable(true);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri uri=request.getUrl();
                String host=uri.getHost()==null?"":uri.getHost().toLowerCase(java.util.Locale.ROOT);
                if(host.equals("www.youtube.com")||host.equals("youtube.com")||host.equals("www.youtube-nocookie.com")||host.equals("youtube-nocookie.com"))return false;
                return true;
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        loadVideo();
    }
    private TextView button(String title,Runnable action){
        TextView t=new TextView(this);t.setText(title);t.setTextColor(0xFFF2EAFE);t.setTextSize(13);
        t.setGravity(Gravity.CENTER);t.setPadding(dp(10),0,dp(10),0);
        t.setFocusable(true);t.setOnClickListener(v->action.run());return t;
    }
    private static String extractVideoId(String url){
        if(url==null)return null;
        try{
            Uri u=Uri.parse(url);
            String host=u.getHost();if(host==null)return null;
            host=host.toLowerCase(java.util.Locale.ROOT);
            String id=null;
            if(host.equals("youtube.com")||host.equals("www.youtube.com"))id=u.getQueryParameter("v");
            else if(host.equals("youtu.be"))id=u.getLastPathSegment();
            return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?id:null;
        }catch(Exception ex){return null;}
    }
    private void loadVideo(){
        // A real iframe player: YouTube controls, ads, branding and media remain intact.
        // A distinct base URL gives the embedded player a nonempty origin/referrer.
        String html="<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'>"+
            "<meta name='referrer' content='strict-origin-when-cross-origin'></head>"+
            "<body style='padding:0;margin:0;background:#000;overflow:hidden'>"+
            "<iframe style='position:absolute;inset:0;width:100%;height:100%;border:0' " +
            "src='https://www.youtube-nocookie.com/embed/"+videoId+"?autoplay=1&playsinline=1&rel=0' " +
            "referrerpolicy='strict-origin-when-cross-origin' "+
            "allow='autoplay;encrypted-media;picture-in-picture' allowfullscreen></iframe></body></html>";
        // youtube.com as base URL is not an embedding origin: YouTube may reject it (152-4).
        // Keeping the official iframe and a truthful third-party origin is required.
        web.loadDataWithBaseURL(EMBED_ORIGIN,html,"text/html","UTF-8",null);
    }
    private void openExternal(){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/watch?v="+videoId)));}
        catch(ActivityNotFoundException ex){Toast.makeText(this,"Nenhum navegador/player YouTube disponível",Toast.LENGTH_LONG).show();}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&liveItems.length()>0){
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_DPAD_UP){zap(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){zap(-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){showGuide();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    private void zap(int delta){
        int length=liveItems.length();if(length==0)return;
        for(int n=0;n<length;n++){
            liveIndex=(liveIndex+delta+length)%length;
            JSONObject obj=liveItems.optJSONObject(liveIndex);
            if(obj==null)continue;
            String id=extractVideoId(obj.optString("url"));if(id==null)continue;
            videoId=id;info.setText((liveIndex+1)+" · "+obj.optString("name","Canal"));loadVideo();return;
        }
    }
    private void showGuide(){
        if(liveItems.length()==0)return;
        String[] names=new String[liveItems.length()];
        for(int n=0;n<names.length;n++){
            JSONObject item=liveItems.optJSONObject(n);
            names[n]=String.format(java.util.Locale.ROOT,"%03d",n+1)+"   "+(item==null?"Canal":item.optString("name","Canal"));
        }
        new AlertDialog.Builder(this).setTitle("Canais YouTube • Guia vertical")
            .setSingleChoiceItems(names,liveIndex,(dialog,position)->{
                dialog.dismiss();
                JSONObject item=liveItems.optJSONObject(position);
                if(item!=null){String id=extractVideoId(item.optString("url"));if(id!=null){liveIndex=position;videoId=id;info.setText(names[position]);loadVideo();}}
            }).setNegativeButton("Fechar",null).show();
    }
    @Override public void onBackPressed(){
        // Return to the Vortex catalog rather than keeping a stale webpage in memory.
        finish();
    }
    @Override protected void onDestroy(){
        if(web!=null){web.loadUrl("about:blank");web.destroy();web=null;}
        super.onDestroy();
    }
}