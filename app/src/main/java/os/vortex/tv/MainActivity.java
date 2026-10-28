package os.vortex.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import os.vortex.tv.integration.M3uPlaylistParser;
import os.vortex.tv.integration.XmltvGuideParser;
import os.vortex.tv.integration.VortexPlayerActivity;

/**
 * Vortex TV streaming app, distinct from Vortex TV OS.
 * Accessible Android 7.1+ UI for TV remote and touch; no scraped feeds or external DEX.
 * Only presents user-imported content. Playback is not claimed validated until device testing.
 */
public final class MainActivity extends Activity {
    private static final int BG=0xFF070811, PANEL=0xFF141725, LIGHT=0xFFF7F6FF;
    private static final int MUTED=0xFFA3A5B8, PURPLE=0xFF9B69FF, BORDER=0xFF303044;
    private static final int PICK_M3U=41,PICK_EPG=42;
    private CatalogStore catalog;
    private GuideStore guide;
    private List<CatalogStore.Item> items=new ArrayList<>();
    private LinearLayout body;
    private boolean tv;
    private android.content.SharedPreferences uiPrefs;
    private String section="Início";
    private int previousCount=-1;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        catalog=new CatalogStore(this);guide=new GuideStore(this);
        uiPrefs=getSharedPreferences("vortex_ui",MODE_PRIVATE);
        String saved=uiPrefs.getString("preferred_mode", "");
        tv=saved.isEmpty()?getResources().getConfiguration().smallestScreenWidthDp>=600:"TV".equals(saved);
        items=catalog.load();
        render();
        if(saved.isEmpty())showModeChooser();
    }

    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);render();}
    private void showModeChooser(){
        new AlertDialog.Builder(this).setTitle("Bem-vindo ao Vortex TV")
            .setMessage("Onde você quer usar o aplicativo?\n\nVocê pode mudar esta escolha nas configurações.")
            .setPositiveButton("TV • Controle remoto",(dialog,which)->saveMode(true))
            .setNegativeButton("Smartphone • Toque",(dialog,which)->saveMode(false))
            .setCancelable(false).show();
    }
    private void saveMode(boolean isTv){
        tv=isTv;
        uiPrefs.edit().putString("preferred_mode",tv?"TV":"SMARTPHONE").apply();
        render();
    }

    private int d(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(true);return t;
    }
    private GradientDrawable shape(int color,float radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(d(radius));return drawable;
    }
    private GradientDrawable gradient(int[] colors,int radius){
        GradientDrawable drawable=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);drawable.setCornerRadius(d(radius));return drawable;
    }
    private void focusable(View v){
        v.setFocusable(true);v.setFocusableInTouchMode(false);
        v.setOnFocusChangeListener((view,focused)->{
            view.setScaleX(focused?1.055f:1.0f);view.setScaleY(focused?1.055f:1.0f);
            view.setElevation(focused?d(10):d(1));
            if(view.getBackground() instanceof GradientDrawable){
                GradientDrawable bg=(GradientDrawable)view.getBackground();
                bg.setStroke(d(focused?3:1),focused?0xFFC8A8FF:BORDER);
            }
        });
    }
    private TextView button(String label,boolean filled,Runnable action){
        TextView b=text(label,14,filled?BG:LIGHT,true);
        b.setGravity(Gravity.CENTER);b.setPadding(d(19),d(12),d(19),d(12));
        GradientDrawable bg=shape(filled?0xFFF0EDFF:0xFF22243A,10);bg.setStroke(d(1),filled?0xFFF0EDFF:BORDER);b.setBackground(bg);
        b.setOnClickListener(v->action.run());focusable(b);return b;
    }
    private void render(){
        previousCount=items.size();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);
        root.addView(header(),new LinearLayout.LayoutParams(-1,d(tv?74:64)));
        ScrollView viewport=new ScrollView(this);viewport.setFillViewport(false);viewport.setVerticalScrollBarEnabled(false);
        body=column();body.setPadding(d(tv?42:20),d(8),d(tv?42:20),d(48));
        viewport.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        if("Buscar".equals(section)) renderSearch();
        else if("Minha lista".equals(section))renderFavorites();
        else if("TV ao vivo".equals(section))renderFiltered("Ao vivo",0);
        else if("Filmes".equals(section))renderFiltered("Filmes",1);
        else if("Séries".equals(section))renderFiltered("Séries",2);
        else if("Documentários".equals(section))renderFiltered("Documentários",3);
        else renderHome();
    }
    private View header(){
        LinearLayout bar=row();bar.setPadding(d(tv?42:20),0,d(tv?42:20),0);
        ImageView emblem=new ImageView(this);
        emblem.setImageResource(os.vortex.tv.R.drawable.vortex_icon);
        emblem.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bar.addView(emblem,new LinearLayout.LayoutParams(d(tv?48:39),d(tv?48:39)));
        TextView brand=text("VORTEX TV",tv?19:15,LIGHT,true);
        LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(-2,-1);
        brandParams.leftMargin=d(8);bar.addView(brand,brandParams);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=row();nav.setPadding(d(tv?32:14),0,0,0);
        String[] sections={"Início","TV ao vivo","Filmes","Séries","Documentários","Minha lista","Buscar"};
        for(String s:sections){
            TextView t=text(s,tv?14:12,s.equals(section)?LIGHT:MUTED,s.equals(section));
            t.setPadding(d(tv?13:9),d(12),d(tv?13:9),d(12));
            t.setOnClickListener(v->{section=s;render();});focusable(t);nav.addView(t);
        }
        scroll.addView(nav);
        bar.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        TextView add=text("⊕  Fontes",tv?14:12,LIGHT,true);add.setPadding(d(12),d(10),d(12),d(10));
        add.setBackground(shape(0xFF24243B,9));focusable(add);
        add.setOnClickListener(v->showSources());bar.addView(add);
        return bar;
    }
    private void renderHome(){
        hero();
        if(items.isEmpty()){
            emptyState("Seu catálogo começa com uma fonte", "Importe uma playlist M3U autorizada ou adicione uma URL direta de vídeo. O Vortex TV não inclui catálogos piratas nem conteúdos fictícios.");
            return;
        }
        rowOf("Adicionados recentemente",items,0);
        rowOf("TV ao vivo",filter(0),0);
        rowOf("Filmes",filter(1),1);
        rowOf("Séries",filter(2),1);
        rowOf("Documentários",filter(3),1);
        rowOf("Vídeos adicionados",filter(4),1);
        ArrayList<CatalogStore.Item> fav=new ArrayList<>();for(CatalogStore.Item i:items)if(catalog.isFavorite(i.url))fav.add(i);
        rowOf("Minha lista",fav,1);
    }
    private void hero(){
        CatalogStore.Item featured=items.isEmpty()?null:items.get(0);
        LinearLayout h=column();h.setGravity(Gravity.BOTTOM);h.setPadding(d(tv?46:23),d(28),d(26),d(30));
        h.setBackground(gradient(new int[]{0xFF392B70,0xFF151B43,0xFF090C16},16));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,d(tv?285:235));
        hp.bottomMargin=d(28);body.addView(h,hp);
        TextView chip=text(featured==null?"UMA NOVA FORMA DE ASSISTIR":"EM DESTAQUE",11,0xFFCCB8FF,true);
        h.addView(chip);
        String headline=featured==null?"Seu entretenimento.\nDo seu jeito.":featured.name;
        TextView title=text(headline,tv?34:26,LIGHT,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(tv?d(570):-1,-2);tp.topMargin=d(8);h.addView(title,tp);
        TextView subtitle=text(featured==null?"Uma experiência cinematográfica, simples e feita para sua TV.":"Da sua biblioteca • Reprodução sujeita à disponibilidade da fonte",tv?14:12,0xFFE1DDF0,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=d(8);h.addView(subtitle,sp);
        LinearLayout actions=row();LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=d(19);h.addView(actions,ap);
        TextView primary=button(featured==null?"⊕  Adicionar fonte":"▶  Assistir",true,()->{if(featured==null)showSources();else play(featured);});
        actions.addView(primary);
        TextView secondary=button(featured==null?"Buscar conteúdo":"ⓘ  Informações",false,()->{if(featured==null){section="Buscar";render();}else detail(featured);});
        LinearLayout.LayoutParams ps=new LinearLayout.LayoutParams(-2,-2);ps.leftMargin=d(10);actions.addView(secondary,ps);
    }
    private void emptyState(String heading,String explanation){
        LinearLayout wrapper=column();wrapper.setPadding(d(20),d(17),d(16),d(26));
        wrapper.setBackground(shape(PANEL,14));
        wrapper.addView(text(heading,18,LIGHT,true));
        TextView para=text(explanation,13,MUTED,false);para.setPadding(0,d(10),0,d(19));wrapper.addView(para);
        LinearLayout buttons=row();buttons.addView(button("Importar M3U",true,()->openFile(PICK_M3U)));
        TextView url=button("URL direta",false,()->addDirect());LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=d(10);buttons.addView(url,gap);
        wrapper.addView(buttons);body.addView(wrapper,new LinearLayout.LayoutParams(-1,-2));
    }
    private List<CatalogStore.Item> filter(int type){
        List<CatalogStore.Item> out=new ArrayList<>();
        for(CatalogStore.Item i:items){
            boolean ok=type==0?i.isLive():type==1?i.isFilm():type==2?i.isSeries():type==3?i.isDoc():i.isVideo();
            if(ok)out.add(i);
        }
        return out;
    }
    private void renderFiltered(String title,int type){
        heading(title,"Conteúdo importado das suas fontes. A disponibilidade depende de cada endereço.");
        List<CatalogStore.Item> group=filter(type);
        if(group.isEmpty())emptyState("Nenhum título nesta categoria","Use ⊕ Fontes para importar uma playlist ou um vídeo compatível.");
        else rowOf(title,group,type==0?0:1);
    }
    private void renderFavorites(){
        heading("Minha lista","Seus títulos favoritos, armazenados somente neste aparelho.");
        ArrayList<CatalogStore.Item> fav=new ArrayList<>();for(CatalogStore.Item i:items)if(catalog.isFavorite(i.url))fav.add(i);
        if(fav.isEmpty())emptyState("Ainda sem favoritos","Abra o detalhe de um título para adicioná-lo à sua lista.");
        else rowOf("Favoritos",fav,1);
    }
    private void heading(String name,String subtitle){
        TextView t=text(name,tv?29:24,LIGHT,true);t.setPadding(0,d(28),0,d(9));body.addView(t);
        TextView sub=text(subtitle,13,MUTED,false);body.addView(sub);
    }
    private void renderSearch(){
        heading("Buscar","Pesquise entre canais, filmes, séries e documentários importados.");
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Buscar no meu catálogo...");
        input.setTextColor(LIGHT);input.setHintTextColor(MUTED);input.setTextSize(17);input.setPadding(d(16),d(12),d(16),d(12));
        input.setBackground(shape(PANEL,10));
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,d(54));ip.topMargin=d(18);body.addView(input,ip);
        LinearLayout results=column();body.addView(results);
        input.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                results.removeAllViews();
                ArrayList<CatalogStore.Item> matches=new ArrayList<>();String query=s.toString().toLowerCase(Locale.ROOT);
                for(CatalogStore.Item i:items)if(i.name.toLowerCase(Locale.ROOT).contains(query)||i.group.toLowerCase(Locale.ROOT).contains(query))matches.add(i);
                addCards(results,matches,1,60);
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        input.setText("");
    }
    private void rowOf(String name,List<CatalogStore.Item> content,int cardType){
        if(content.isEmpty())return;
        LinearLayout sectionRoot=column();
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=d(24);body.addView(sectionRoot,outer);
        TextView label=text(name,20,LIGHT,true);label.setPadding(0,0,0,d(14));sectionRoot.addView(label);
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setClipToPadding(false);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout cards=row();cards.setPadding(d(5),d(6),d(5),d(10));strip.addView(cards);
        sectionRoot.addView(strip,new LinearLayout.LayoutParams(-1,-2));
        addCards(cards,content,cardType,tv?60:36);
    }
    private void addCards(LinearLayout target,List<CatalogStore.Item> content,int type,int max){
        int total=Math.min(content.size(),max);
        for(int index=0;index<total;index++){
            CatalogStore.Item i=content.get(index);
            LinearLayout holder=column();holder.setClipChildren(false);
            int width=d(type==0?(tv?203:180):(tv?159:139));
            int height=d(type==0?120:(tv?202:186));
            int seed=Math.abs(i.name.hashCode()%4);
            int[][] palettes={{0xFF43235E,0xFF131B43},{0xFF164658,0xFF182039},{0xFF603D2A,0xFF1E2440},{0xFF30435F,0xFF20152C}};
            LinearLayout poster=column();poster.setGravity(Gravity.BOTTOM);
            poster.setPadding(d(14),d(14),d(14),d(14));
            GradientDrawable bg=gradient(palettes[seed],12);bg.setStroke(d(1),BORDER);poster.setBackground(bg);
            poster.addView(text(type==0?"◉  AO VIVO":"VORTEX • TV",10,0xFFDED2FF,true));
            LinearLayout.LayoutParams grow=new LinearLayout.LayoutParams(-1,0,1);poster.addView(new View(this),grow);
            TextView posterName=text(i.name,type==0?16:17,LIGHT,true);
            posterName.setMaxLines(3);posterName.setEllipsize(TextUtils.TruncateAt.END);poster.addView(posterName);
            holder.addView(poster,new LinearLayout.LayoutParams(width,height));
            TextView group=text(i.group.isEmpty()?"Minha biblioteca":i.group,11,MUTED,false);
            group.setSingleLine(true);group.setEllipsize(TextUtils.TruncateAt.END);group.setPadding(d(3),d(7),d(3),0);holder.addView(group);
            LinearLayout.LayoutParams holderParams=new LinearLayout.LayoutParams(width,-2);holderParams.rightMargin=d(14);
            target.addView(holder,holderParams);
            poster.setOnClickListener(v->detail(i));focusable(poster);
        }
    }
    private void detail(CatalogStore.Item i){
        String summary="Grupo: "+(i.group.isEmpty()?"Sem categoria":i.group)+"\nTipo: "+i.format+
            "\nFonte: importada pelo usuário\nEstado: reprodução não verificada";
        if(!i.tvgId.isEmpty()){
            JSONArray epg=guide.get(i.tvgId);
            if(epg!=null && epg.length()>0){
                summary+="\n\nPROGRAMAÇÃO EPG";
                for(int n=0;n<Math.min(5,epg.length());n++){
                    JSONObject p=epg.optJSONObject(n);
                    if(p!=null)summary+="\n"+p.optString("start")+"  "+p.optString("title");
                }
            }
        }
        new AlertDialog.Builder(this).setTitle(i.name).setMessage(summary)
            .setPositiveButton("▶ Assistir",(d,w)->play(i))
            .setNeutralButton(catalog.isFavorite(i.url)?"★ Remover":"☆ Minha lista",(d,w)->{catalog.toggleFavorite(i.url);render();})
            .setNegativeButton("Voltar",null).show();
    }
    private void play(CatalogStore.Item i){
        if(!i.url.startsWith("https://")){
            new AlertDialog.Builder(this).setTitle("Fonte HTTP sem criptografia")
                .setMessage("A reprodução por HTTP pode expor seu tráfego. Esta versão de desenvolvimento utiliza HTTPS. Importe uma fonte HTTPS válida.")
                .setPositiveButton("Entendi",null).show();return;
        }
        Intent intent=new Intent(this,VortexPlayerActivity.class);
        intent.putExtra(VortexPlayerActivity.EXTRA_MEDIA_URL,i.url);
        if(i.isLive()){
            JSONArray playable=new JSONArray();int selected=0;
            for(CatalogStore.Item channel:items) {
                if(!channel.isLive() || !channel.url.startsWith("https://"))continue;
                if(channel.url.equals(i.url))selected=playable.length();
                JSONObject entry=new JSONObject();
                try {entry.put("name",channel.name);entry.put("url",channel.url);}catch(Exception ignored){}
                playable.put(entry);
            }
            intent.putExtra(VortexPlayerActivity.EXTRA_CHANNELS_JSON,playable.toString());
            intent.putExtra(VortexPlayerActivity.EXTRA_CHANNEL_INDEX,selected);
        }
        startActivity(intent);
    }
    private void showSources(){
        new AlertDialog.Builder(this).setTitle("Gerenciar fontes")
            .setItems(new String[]{"Importar arquivo M3U","Importar playlist HTTPS","Adicionar vídeo HTTPS","Importar EPG XMLTV","Atualizar EPG por URL","Escolher modo TV/Smartphone","Limpar catálogo local"},(d,which)->{
                if(which==0)openFile(PICK_M3U);
                if(which==1)importRemote();
                if(which==2)addDirect();
                if(which==3)openFile(PICK_EPG);
                if(which==4)importRemoteGuide();
                if(which==5)showModeChooser();
                if(which==6)new AlertDialog.Builder(this).setTitle("Apagar o catálogo?")
                    .setMessage("Remove sua lista de conteúdo e favoritos somente deste dispositivo.")
                    .setPositiveButton("Apagar",(d2,w)->{catalog.clear();items=catalog.load();render();})
                    .setNegativeButton("Cancelar",null).show();
            }).show();
    }
    private void openFile(int request){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,request);
    }
    private void importRemote(){
        EditText field=new EditText(this);field.setSingleLine(true);field.setHint("https://exemplo.com/minha-lista.m3u");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Playlist M3U por HTTPS").setView(field)
            .setPositiveButton("Importar",(d,w)->{
                String address=field.getText().toString().trim();
                if(!address.startsWith("https://")||!M3uPlaylistParser.isHttpUrl(address)){
                    Toast.makeText(this,"Use um endereço HTTPS válido",Toast.LENGTH_LONG).show();return;
                }
                runTask(()->{
                    HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
                    conn.setConnectTimeout(7000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("Accept","application/x-mpegURL, audio/x-mpegurl, text/plain");
                    try {
                        if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
                        if(conn.getContentLengthLong()>4_000_000)throw new Exception("Arquivo excede 4 MB");
                        try(InputStream source=new LimitedInputStream(conn.getInputStream(),4_000_000);
                            InputStreamReader rdr=new InputStreamReader(source,"UTF-8")){
                            List<M3uPlaylistParser.Channel> channels=new M3uPlaylistParser().parse(rdr);
                            return "Adicionados: "+catalog.append(channels)+" de "+channels.size()+" itens encontrados";
                        }
                    }finally{conn.disconnect();}
                },true);
            }).setNegativeButton("Cancelar",null).show();
    }
    private void importRemoteGuide(){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setHint("https://exemplo.org/guia.xml");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Atualizar EPG XMLTV (HTTPS)").setView(field)
            .setPositiveButton("Importar",(dialog,which)->{
                String address=field.getText().toString().trim();
                if(!address.startsWith("https://")||!M3uPlaylistParser.isHttpUrl(address)){
                    Toast.makeText(this,"Use uma URL HTTPS",Toast.LENGTH_LONG).show();return;
                }
                runTask(()->{
                    HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
                    conn.setConnectTimeout(8000);conn.setReadTimeout(18000);conn.setInstanceFollowRedirects(false);
                    try {
                        if(conn.getResponseCode()!=200)throw new Exception("HTTP "+conn.getResponseCode());
                        if(conn.getContentLengthLong()>20_000_000)throw new Exception("EPG excede 20 MB");
                        HashSet<String> allowed=new HashSet<>();
                        for(CatalogStore.Item item:items)if(!item.tvgId.isEmpty())allowed.add(item.tvgId);
                        try(InputStream in=new LimitedInputStream(conn.getInputStream(),20_000_000);
                            InputStreamReader rdr=new InputStreamReader(in,"UTF-8")){
                            return "Eventos importados: "+guide.put(new XmltvGuideParser().parse(rdr,allowed));
                        }
                    } finally {conn.disconnect();}
                },true);
            }).setNegativeButton("Cancelar",null).show();
    }
    private void addDirect(){
        LinearLayout box=column();box.setPadding(d(18),0,d(18),0);
        EditText name=new EditText(this);name.setSingleLine();name.setHint("Nome do vídeo ou canal");box.addView(name);
        EditText url=new EditText(this);url.setSingleLine();url.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://.../stream.m3u8");box.addView(url);
        new AlertDialog.Builder(this).setTitle("Adicionar reprodução direta").setView(box)
            .setPositiveButton("Adicionar",(d,w)->{
                String value=url.getText().toString().trim();
                if(!value.startsWith("https://")||!M3uPlaylistParser.isHttpUrl(value)){
                    Toast.makeText(this,"Insira uma URL HTTPS válida",Toast.LENGTH_LONG).show();return;
                }
                String title=name.getText().toString().trim();if(title.isEmpty())title="Vídeo personalizado";
                catalog.addDirect(title,value);items=catalog.load();section="Início";render();
            }).setNegativeButton("Cancelar",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri uri=data.getData();
        if(request==PICK_M3U)runTask(()->{
            try(InputStream in=new LimitedInputStream(getContentResolver().openInputStream(uri),4_000_000);
                InputStreamReader rdr=new InputStreamReader(in,"UTF-8")){
                List<M3uPlaylistParser.Channel> channels=new M3uPlaylistParser().parse(rdr);
                int count=catalog.append(channels);
                return "Importados "+count+" novos itens.";
            }
        },true);
        if(request==PICK_EPG)runTask(()->{
            HashSet<String> allowed=new HashSet<>();for(CatalogStore.Item i:items)if(!i.tvgId.isEmpty())allowed.add(i.tvgId);
            try(InputStream in=new LimitedInputStream(getContentResolver().openInputStream(uri),20_000_000);
                InputStreamReader rdr=new InputStreamReader(in,"UTF-8")){
                List<XmltvGuideParser.Programme> programmes=new XmltvGuideParser().parse(rdr,allowed);
                return "Eventos EPG correspondentes: "+guide.put(programmes);
            }
        },false);
    }
    private interface IoJob{String run()throws Exception;}
    private void runTask(IoJob job,boolean refresh){
        Toast.makeText(this,"Importando...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String answer;try{answer=job.run();}catch(Exception e){answer="Falha: "+e.getMessage();}
            String finalAnswer=answer;
            runOnUiThread(()->{Toast.makeText(this,finalAnswer,Toast.LENGTH_LONG).show();if(refresh){items=catalog.load();render();}});
        },"Vortex-Import").start();
    }
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long remaining;
        LimitedInputStream(InputStream input,long limit){super(input);remaining=limit;}
        @Override public int read()throws java.io.IOException{
            if(remaining<=0)throw new java.io.IOException("Limite de tamanho excedido");
            int v=super.read();if(v>=0)remaining--;return v;
        }
        @Override public int read(byte[] bytes,int offset,int length)throws java.io.IOException{
            if(remaining<=0)throw new java.io.IOException("Limite de tamanho excedido");
            int count=super.read(bytes,offset,(int)Math.min(remaining,length));if(count>0)remaining-=count;return count;
        }
    }
}
