package os.vortex.tv.integration;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import android.app.AlertDialog;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/** Native player for sources the app/user is authorized to access. No scraping or DRM bypass. */
public final class VortexPlayerActivity extends Activity {
    public static final String EXTRA_MEDIA_URL = "vortex.media_url";
    public static final String EXTRA_CHANNELS_JSON="vortex.channels_json";
    public static final String EXTRA_CHANNEL_INDEX="vortex.channel_index";
    private JSONArray channels;
    private int channelIndex;
    private PlayerView playerView;
    private ExoPlayer player;
    private String mediaUrl;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        mediaUrl=getIntent().getStringExtra(EXTRA_MEDIA_URL);
        try {channels=new JSONArray(getIntent().getStringExtra(EXTRA_CHANNELS_JSON));}
        catch(Exception ex){channels=new JSONArray();}
        channelIndex=getIntent().getIntExtra(EXTRA_CHANNEL_INDEX,0);
        if (mediaUrl==null || !M3uPlaylistParser.isHttpUrl(mediaUrl)) {
            Toast.makeText(this,"URL de midia invalida",Toast.LENGTH_LONG).show(); finish();return;
        }
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        setContentView(playerView);
    }
    @Override protected void onStart() {
        super.onStart();
        if (playerView==null)return;
        player=new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                new AlertDialog.Builder(VortexPlayerActivity.this)
                    .setTitle("Não foi possível reproduzir")
                    .setMessage("Fonte indisponível ou incompatível ("+error.getErrorCodeName()+").")
                    .setPositiveButton("Abrir em player externo",(dialog,which)->{
                        if(!ExternalMediaRouter.openExternal(VortexPlayerActivity.this,mediaUrl,true))
                            Toast.makeText(VortexPlayerActivity.this,"Nenhum player externo compatível",Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Fechar",null).show();
            }
        });
        playSelected();
    }
    private void playSelected(){
        if(player==null || mediaUrl==null)return;
        MediaItem.Builder item=new MediaItem.Builder().setUri(Uri.parse(mediaUrl));
        String format=M3uPlaylistParser.kind(mediaUrl);
        if ("hls".equals(format)) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        if ("dash".equals(format)) item.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        player.prepare();player.play();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(channels!=null&&channels.length()>0&&event.getAction()==KeyEvent.ACTION_DOWN) {
            int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_DPAD_UP){changeChannel(1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){changeChannel(-1);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT){showChannelGuide();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    private void changeChannel(int delta){
        int length=channels.length();if(length==0)return;
        channelIndex=(channelIndex+delta+length)%length;
        JSONObject channel=channels.optJSONObject(channelIndex);
        if(channel==null)return;
        mediaUrl=channel.optString("url");
        Toast.makeText(this,channel.optString("name","Canal"),Toast.LENGTH_SHORT).show();
        playSelected();
    }
    private void showChannelGuide(){
        String[] names=new String[channels.length()];
        for(int n=0;n<names.length;n++) {
            JSONObject channel=channels.optJSONObject(n);
            names[n]=(n+1)+"  "+(channel==null?"Canal":channel.optString("name"));
        }
        new AlertDialog.Builder(this).setTitle("Guia de canais")
            .setSingleChoiceItems(names,channelIndex,(dialog,position)->{
                dialog.dismiss();channelIndex=position;
                JSONObject chosen=channels.optJSONObject(position);
                if(chosen!=null){mediaUrl=chosen.optString("url");playSelected();}
            }).setNegativeButton("Voltar",null).show();
    }
    @Override protected void onStop() {
        if (player!=null) {player.release();player=null;}
        if (playerView!=null)playerView.setPlayer(null);
        super.onStop();
    }
}
