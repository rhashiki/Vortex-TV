package os.vortex.tv.integration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * Vortex native player. Live channel navigation:
 * DPAD_UP = next channel, DPAD_DOWN = previous channel,
 * DPAD_LEFT / RIGHT = vertical channel guide.
 *
 * This activity only plays supplied media URLs and does not circumvent authentication or DRM.
 */
public final class VortexPlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URL = "vortex.media_url";
    public static final String EXTRA_CHANNELS_JSON = "vortex.channels_json";
    public static final String EXTRA_CHANNEL_INDEX = "vortex.channel_index";
    public static final String EXTRA_MEDIA_FALLBACKS_JSON = "vortex.media_fallbacks";
    private static final int LIGHT=0xFFF6F4FF, MUTED=0xFFB7B1CC, PURPLE=0xFFB48AFF;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private JSONArray channels = new JSONArray();
    private int channelIndex;
    private PlayerView playerView;
    private ExoPlayer player;
    private String mediaUrl;
    private final java.util.ArrayList<String> backupUrls=new java.util.ArrayList<>();
    private final java.util.HashSet<String> attempted=new java.util.HashSet<>();
    private LinearLayout channelInfo;
    private TextView infoNumber;
    private TextView infoTitle;
    private TextView infoProgramme;
    private final Runnable hideInfo = () -> { if(channelInfo!=null)channelInfo.setVisibility(View.GONE); };
    private Dialog guideDialog;

    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int fill,int radius) {
        GradientDrawable s=new GradientDrawable();
        s.setColor(fill);s.setCornerRadius(dp(radius));s.setStroke(dp(1),0xFF39304D);return s;
    }
    private TextView label(String content,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(null,android.graphics.Typeface.BOLD);
        return t;
    }
    private JSONObject selectedChannel() { return channels.optJSONObject(channelIndex); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mediaUrl=getIntent().getStringExtra(EXTRA_MEDIA_URL);
        try {
            JSONArray fallbackData=new JSONArray(getIntent().getStringExtra(EXTRA_MEDIA_FALLBACKS_JSON));
            for(int i=0;i<fallbackData.length()&&i<3;i++){
                String candidate=fallbackData.optString(i,"");
                if(candidate.startsWith("https://")&&!candidate.equals(mediaUrl)
                    &&M3uPlaylistParser.isHttpUrl(candidate))backupUrls.add(candidate);
            }
        }catch(Exception ignored){}
        try {
            String raw=getIntent().getStringExtra(EXTRA_CHANNELS_JSON);
            if(raw!=null)channels=new JSONArray(raw);
        } catch(Exception ignored) { channels=new JSONArray(); }
        channelIndex=Math.max(0,Math.min(getIntent().getIntExtra(EXTRA_CHANNEL_INDEX,0),Math.max(0,channels.length()-1)));
        if(mediaUrl==null || !M3uPlaylistParser.isHttpUrl(mediaUrl)) {
            Toast.makeText(this,"URL de mídia inválida",Toast.LENGTH_LONG).show();finish();return;
        }
        FrameLayout screen=new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        screen.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

        channelInfo=new LinearLayout(this);channelInfo.setOrientation(LinearLayout.VERTICAL);
        channelInfo.setPadding(dp(23),dp(17),dp(31),dp(18));
        channelInfo.setBackground(shape(0xEB10101B,15));
        infoNumber=label("",14,PURPLE,true);
        infoTitle=label("",23,LIGHT,true);
        infoProgramme=label("",13,MUTED,false);
        infoProgramme.setMaxLines(2);
        channelInfo.addView(infoNumber);
        channelInfo.addView(infoTitle);
        channelInfo.addView(infoProgramme);
        FrameLayout.LayoutParams infoParams=new FrameLayout.LayoutParams(
            Math.min(dp(440),getResources().getDisplayMetrics().widthPixels-dp(32)),-2,Gravity.TOP|Gravity.START);
        infoParams.leftMargin=dp(16);infoParams.topMargin=dp(16);
        screen.addView(channelInfo,infoParams);
        channelInfo.setVisibility(View.GONE);
        setContentView(screen);
        if(channels.length()>0)channelInfo.post(this::showChannelInfo);
    }
    @Override protected void onStart() {
        super.onStart();
        if(playerView==null)return;
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                if(tryAlternative())return;
                new AlertDialog.Builder(VortexPlayerActivity.this)
                    .setTitle("Falha de reprodução • Detalhes")
                    .setMessage(describePlayerError(error))
                    .setPositiveButton("Abrir em player externo",(dialog,which)->{
                        if(!ExternalMediaRouter.openExternal(VortexPlayerActivity.this,mediaUrl,true))
                            Toast.makeText(VortexPlayerActivity.this,"Nenhum player externo compatível",Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Fechar",null).show();
            }
        });
        playSelected();
    }

    /** Exposes precise HTTP status and resource type without leaking URL query credentials. */
    @UnstableApi
    private String describePlayerError(PlaybackException error){
        String code=error.getErrorCodeName();
        Throwable cause=error;
        for(int hops=0;cause!=null&&hops<12;cause=cause.getCause(),hops++){
            if(cause instanceof HttpDataSource.InvalidResponseCodeException){
                HttpDataSource.InvalidResponseCodeException http=
                    (HttpDataSource.InvalidResponseCodeException)cause;
                Uri blocked=http.dataSpec.uri;
                String host=blocked==null?"desconhecida":blocked.getHost();
                String path=blocked==null?"":blocked.getPath();
                String resource=(path!=null&&path.toLowerCase(java.util.Locale.ROOT).endsWith(".m3u8"))
                    ?"playlist HLS":(path!=null&&(path.toLowerCase(java.util.Locale.ROOT).endsWith(".ts")
                       ||path.toLowerCase(java.util.Locale.ROOT).endsWith(".m4s")
                       ||path.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4")))
                    ?"segmento de vídeo":"recurso remoto";
                String reason=http.responseCode==403
                    ?"A origem recusou o acesso (403).":http.responseCode==404
                    ?"O endereço não existe ou expirou (404).":http.responseCode==429
                    ?"O servidor limitou as requisições (429).":
                    "O servidor devolveu uma resposta HTTP inesperada.";
                return reason+"\nHTTP "+http.responseCode+" • "+resource+
                    "\nHost: "+(host==null?"desconhecido":host)+
                    "\nDiagnóstico: "+code+
                    "\n\nA playlist pode responder normalmente, mas algum segmento pode falhar. Teste outra fonte ou o player externo.";
            }
        }
        return "Não foi possível reproduzir: "+code+
            ".\nPode haver erro de rede, codec ou indisponibilidade da fonte.";
    }

    private boolean tryAlternative(){
        while(!backupUrls.isEmpty()){
            String next=backupUrls.remove(0);
            if(attempted.contains(next))continue;
            mediaUrl=next;
            Toast.makeText(this,"Fonte indisponível; tentando alternativa...",Toast.LENGTH_SHORT).show();
            playSelected();
            return true;
        }
        return false;
    }
    private void playSelected() {
        if(player==null || mediaUrl==null)return;
        attempted.add(mediaUrl);
        MediaItem.Builder item=new MediaItem.Builder().setUri(Uri.parse(mediaUrl));
        String format=M3uPlaylistParser.kind(mediaUrl);
        if("hls".equals(format))item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if("dash".equals(format))item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.stop();
        player.setMediaItem(item.build());player.prepare();player.play();
    }
    private void showChannelInfo() {
        if(channels.length()==0 || channelInfo==null)return;
        JSONObject current=selectedChannel();
        if(current==null)return;
        infoNumber.setText(String.format(java.util.Locale.ROOT,"CANAL %03d",current.optInt("number",channelIndex+1)));
        infoTitle.setText(current.optString("name","Canal"));
        String programme=current.optString("programme","");
        infoProgramme.setText(programme.isEmpty()?"TV AO VIVO":programme);
        channelInfo.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideInfo);
        handler.postDelayed(hideInfo,3300);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(channels.length()>0 && event.getAction()==KeyEvent.ACTION_DOWN) {
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_DPAD_UP){changeChannel(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){changeChannel(-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){
                showChannelGuide(key==KeyEvent.KEYCODE_DPAD_LEFT);return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    private void changeChannel(int delta) {
        final int length=channels.length();
        if(length==0)return;
        for(int attempts=0;attempts<length;attempts++) {
            channelIndex=(channelIndex+delta+length)%length;
            JSONObject candidate=selectedChannel();
            if(candidate==null)continue;
            String target=candidate.optString("url","");
            if(!M3uPlaylistParser.isHttpUrl(target))continue;
            mediaUrl=target;playSelected();showChannelInfo();return;
        }
        Toast.makeText(this,"Nenhuma transmissão disponível",Toast.LENGTH_SHORT).show();
    }
    private void chooseChannel(int position) {
        if(position<0 || position>=channels.length())return;
        JSONObject c=channels.optJSONObject(position);
        if(c==null || !M3uPlaylistParser.isHttpUrl(c.optString("url","")))return;
        channelIndex=position;mediaUrl=c.optString("url");
        if(guideDialog!=null)guideDialog.dismiss();
        playSelected();showChannelInfo();
    }
    private void showChannelGuide(boolean fromLeft) {
        if(guideDialog!=null && guideDialog.isShowing())return;
        Dialog dialog=new Dialog(this);
        guideDialog=dialog;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xFF0B0C14);panel.setPadding(dp(14),dp(18),dp(14),dp(16));
        TextView title=label("TV AO VIVO",21,LIGHT,true);title.setPadding(dp(8),0,0,dp(6));panel.addView(title);
        TextView help=label("↑ ↓  Navegar      OK  Assistir      Voltar  Fechar",12,MUTED,false);
        help.setPadding(dp(8),0,0,dp(16));panel.addView(help);
        ListView list=new ListView(this);list.setDividerHeight(dp(5));
        list.setDivider(null);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        ArrayAdapter<JSONObject> adapter=new ArrayAdapter<JSONObject>(this,android.R.layout.simple_list_item_1) {
            @Override public int getCount(){return channels.length();}
            @Override public JSONObject getItem(int position){return channels.optJSONObject(position);}
            @Override public View getView(int position,View convertView,ViewGroup parent){
                JSONObject item=getItem(position);
                boolean current=position==channelIndex;
                LinearLayout row=new LinearLayout(VortexPlayerActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15),dp(13),dp(13),dp(13));
                row.setBackground(shape(current?0xFF392451:0xFF181925,11));
                TextView num=label(String.format(java.util.Locale.ROOT,"%03d",item==null?position+1:item.optInt("number",position+1)),18,PURPLE,true);
                row.addView(num,new LinearLayout.LayoutParams(dp(56),-2));
                LinearLayout detail=new LinearLayout(VortexPlayerActivity.this);
                detail.setOrientation(LinearLayout.VERTICAL);
                TextView name=label(item==null?"Canal":item.optString("name","Canal"),16,LIGHT,true);
                name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView program=label(item==null?"":item.optString("programme",""),12,MUTED,false);
                program.setSingleLine(true);program.setEllipsize(android.text.TextUtils.TruncateAt.END);
                detail.addView(name);detail.addView(program);
                row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                if(current)row.addView(label("●",17,0xFF45DEC9,true));
                return row;
            }
        };
        list.setAdapter(adapter);
        panel.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->chooseChannel(position));
        dialog.setContentView(panel);
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams a=w.getAttributes();
            a.dimAmount=0.42f;
            w.setAttributes(a);
            w.setGravity((fromLeft?Gravity.START:Gravity.END)|Gravity.CENTER_VERTICAL);
            w.setLayout(Math.min(dp(530),getResources().getDisplayMetrics().widthPixels-dp(48)),-1);
        }
        dialog.setOnDismissListener(d->guideDialog=null);
        dialog.show();
        Window shown=dialog.getWindow();
        if(shown!=null) {
            shown.setGravity((fromLeft?Gravity.START:Gravity.END)|Gravity.CENTER_VERTICAL);
            shown.setLayout(Math.min(dp(530),getResources().getDisplayMetrics().widthPixels-dp(48)),-1);
        }
        list.setSelection(channelIndex);
        list.setItemChecked(channelIndex,true);
        list.requestFocus();
    }
    @Override protected void onStop() {
        handler.removeCallbacks(hideInfo);
        if(guideDialog!=null){guideDialog.dismiss();guideDialog=null;}
        if(player!=null){player.release();player=null;}
        if(playerView!=null)playerView.setPlayer(null);
        super.onStop();
    }
}
