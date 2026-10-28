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
import android.widget.FrameLayout;
import android.graphics.drawable.Drawable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
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
import os.vortex.tv.integration.YouTubePlaybackActivity;

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
    private final List<GuideChannel> unavailableGuide=new ArrayList<>();
    private String selectedFilmGenre="Todos";
    private static final String[] FILM_GENRES={"Todos","Animação","Aventura","Fantasia","Ficção científica","Terror","Suspense","Romance","Drama","Guerra","LGBT","Documentário","Outros"};
    private static final int POSITIVE=0xFF22CDB7;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        catalog=new CatalogStore(this);guide=new GuideStore(this);
        uiPrefs=getSharedPreferences("vortex_ui",MODE_PRIVATE);
        String saved=uiPrefs.getString("preferred_mode", "");
        tv=saved.isEmpty()?getResources().getConfiguration().smallestScreenWidthDp>=600:"TV".equals(saved);
        try {catalog.appendItems(YoutubeCatalogBridge.loadSeed(this));}
        catch(Exception ex){android.util.Log.w("VortexTV","YouTube seed unavailable",ex);}
        items=catalog.load();
        loadGuideOnly();
        render();
        syncCloud(false);
        syncNetMovies(false);
        if(saved.isEmpty())showModeChooser();
        // Large Internet Archive imports are now opt-in: automatic discovery
        // flooded the home page with unrelated titles and untested images.
        // The manual "Carregar mais filmes livres" action remains available.
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
        if(!tv)root.addView(mobileNavigation(),new LinearLayout.LayoutParams(-1,d(73)));
        setContentView(root);
        if("Buscar".equals(section)) renderSearch();
        else if("Minha lista".equals(section))renderFavorites();
        else if("TV ao vivo".equals(section))renderLiveGuide();
        else if("Filmes".equals(section))renderFilms();
        else if("YouTube".equals(section))renderYouTube();
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
        String[] sections={"Início","TV ao vivo","Filmes","YouTube","Séries","Documentários","Minha lista","Buscar"};
        for(String s:sections){
            TextView t=text(s,tv?14:12,s.equals(section)?LIGHT:MUTED,s.equals(section));
            t.setPadding(d(tv?13:9),d(12),d(tv?13:9),d(12));
            t.setOnClickListener(v->{section=s;render();});focusable(t);nav.addView(t);
        }
        scroll.addView(nav);
        if(tv)bar.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        else bar.addView(new View(this),new LinearLayout.LayoutParams(0,d(1),1));
        TextView add=text("⊕  Fontes",tv?14:12,LIGHT,true);add.setPadding(d(12),d(10),d(12),d(10));
        add.setBackground(shape(0xFF24243B,9));focusable(add);
        add.setOnClickListener(v->showSources());bar.addView(add);
        return bar;
    }
    private View mobileNavigation(){
        LinearLayout bottom=row();bottom.setPadding(d(4),d(3),d(4),d(3));
        bottom.setBackground(shape(0xFF13131D,0));
        String[] destinations={"Início","TV ao vivo","Filmes","YouTube","Séries","Buscar"};
        String[] labels={"⌂\nInício","▣\nTV","▤\nFilmes","▶\nYouTube","▥\nSéries","⌕\nBuscar"};
        for(int n=0;n<destinations.length;n++){
            final String destination=destinations[n];
            boolean active=destination.equals(section);
            TextView entry=text(labels[n],11,active?0xFFF3EAFF:0xFF9691AA,active);
            entry.setGravity(Gravity.CENTER);entry.setLineSpacing(d(2),1.0f);
            entry.setBackground(shape(active?0xFF302543:0xFF13131D,10));
            entry.setOnClickListener(v->{section=destination;render();});
            focusable(entry);
            LinearLayout.LayoutParams pos=new LinearLayout.LayoutParams(0,-1,1);
            pos.setMargins(d(2),d(2),d(2),d(2));bottom.addView(entry,pos);
        }
        return bottom;
    }
    private List<CatalogStore.Item> featuredMovies(){
        String[] order={"Nerve: Um Jogo Sem Regras","Deu a Louca nos Bichos",
            "Os Mercenários 2","O Grito","O Nevoeiro",
            "Super-Herói: O Filme","Os 13 Pecados","Trumbo: Lista Negra",
            "Traffik — Liberdade Roubada","A Loja Mágica de Brinquedos",
            "O Jantar","Por um Corredor Escuro","Nurse: A Enfermeira Assassina",
            "Totalmente Trocados","Vingança","Sprite Fright"};
        ArrayList<CatalogStore.Item> selected=new ArrayList<>();
        java.util.HashSet<String> known=new java.util.HashSet<>();
        for(String name:order){
            for(CatalogStore.Item item:items){
                if(!name.equals(item.name)||CatalogStore.deadYouTubeReference(item))continue;
                if(known.add(item.url))selected.add(item);
                break;
            }
        }
        return selected;
    }
    private List<CatalogStore.Item> liveCandidates(boolean available){
        List<CatalogStore.Item> result=new ArrayList<>();
        for(CatalogStore.Item item:items){
            if(!item.isLive())continue;
            String quality=catalog.sourceQuality(item.url);
            boolean unavailable="server_unavailable".equals(quality)
                ||"unverified".equals(quality)||"not_verified".equals(quality);
            if(available!=unavailable)result.add(item);
        }
        return result;
    }
    private void renderHome(){
        hero();
        if(items.isEmpty()){
            emptyState("Seu catálogo começa com uma fonte", "Importe uma playlist M3U autorizada ou adicione uma URL direta de vídeo. O Vortex TV não inclui catálogos piratas nem conteúdos fictícios.");
            return;
        }
        rowOf("Cinema conhecido • Filmes em canais oficiais",featuredMovies(),1);
        ArrayList<CatalogStore.Item> latestOfficial=new ArrayList<>();
        for(CatalogStore.Item it:items)
            if(it.provider.startsWith("NetMovies • YouTube oficial")
                &&!CatalogStore.deadYouTubeReference(it))
                latestOfficial.add(it);
        rowOf("Novidades NetMovies • Filmes gratuitos",latestOfficial,1);
        ArrayList<CatalogStore.Item> youtubeFilms=new ArrayList<>();
        ArrayList<CatalogStore.Item> archiveFilms=new ArrayList<>();
        ArrayList<CatalogStore.Item> openMovies=new ArrayList<>();
        for(CatalogStore.Item it:filter(1)){
            if(it.format.startsWith("youtube-"))youtubeFilms.add(it);
            else if(it.provider.startsWith("Internet Archive"))archiveFilms.add(it);
            else openMovies.add(it);
        }
        rowOf("Mais filmes completos • YouTube",youtubeFilms,1);
        rowOf("Curtas e filmes de licença aberta",openMovies,1);
        rowOf("Canais com sinal detectado no servidor • Teste",liveCandidates(true),0);
        rowOf("Cinema independente • Internet Archive",archiveFilms,1);
        addOpenArchiveAction();
        for(String genre:FILM_GENRES){
            if("Todos".equals(genre))continue;
            List<CatalogStore.Item> group=filmGenre(genre);
            if(!group.isEmpty())rowOf(genre,group,1);
        }
        rowOf("Séries",filter(2),1);
        rowOf("Documentários",filter(3),1);
        rowOf("Vídeos adicionados",filter(4),1);
        ArrayList<CatalogStore.Item> fav=new ArrayList<>();for(CatalogStore.Item i:items)if(catalog.isFavorite(i.url))fav.add(i);
        rowOf("Minha lista",fav,1);
    }
    private void hero(){
        CatalogStore.Item candidate=null;
        List<CatalogStore.Item> curated=featuredMovies();
        if(!curated.isEmpty())candidate=curated.get(0);
        if(candidate==null){
            for(CatalogStore.Item film:items){
                if(film.isFilm()&&!CatalogStore.deadYouTubeReference(film)){
                    candidate=film;break;
                }
            }
        }
        if(candidate==null&&!items.isEmpty())candidate=items.get(0);
        final CatalogStore.Item featured=candidate;
        FrameLayout heroFrame=new FrameLayout(this);heroFrame.setBackground(gradient(new int[]{0xFF241D4F,0xFF101522,0xFF080A13},16));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,d(tv?390:310));
        hp.bottomMargin=d(27);body.addView(heroFrame,hp);
        if(featured!=null&&!featured.logo.isEmpty()){
            ImageView backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroFrame.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));loadPoster(backdrop,featured.logo);
            View dim=new View(this);dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF0080912,0xD0101225,0x880F1129,0x33080912}));
            heroFrame.addView(dim,new FrameLayout.LayoutParams(-1,-1));
        }
        LinearLayout h=column();h.setGravity(Gravity.BOTTOM);h.setPadding(d(tv?46:23),d(28),d(26),d(30));
        heroFrame.addView(h,new FrameLayout.LayoutParams(-1,-1));
        TextView chip=text(featured==null?"UMA NOVA FORMA DE ASSISTIR":"EM DESTAQUE",11,0xFFCCB8FF,true);
        h.addView(chip);
        String headline=featured==null?"Seu entretenimento.\nDo seu jeito.":featured.name;
        TextView title=text(headline,tv?42:30,LIGHT,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(tv?d(570):-1,-2);tp.topMargin=d(8);h.addView(title,tp);
        TextView subtitle=text(featured==null?"Uma experiência cinematográfica, simples e feita para sua TV.":""+(featured.provider.isEmpty()?"Acervo aberto":featured.provider)+"  •  "+(featured.year.isEmpty()?featured.group:featured.year),tv?14:12,0xFFE1DDF0,false);
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
            if(ok&&!CatalogStore.deadYouTubeReference(i))out.add(i);
        }
        return out;
    }
    private void renderFiltered(String title,int type){
        heading(title,"Explore os conteúdos disponíveis em suas fontes cadastradas.");
        List<CatalogStore.Item> group=filter(type);
        if(group.isEmpty())emptyState("Nenhum título nesta categoria","Os títulos aparecem aqui após importar uma fonte compatível.");
        else rowOf(title,group,type==0?0:1);
    }

    private void renderYouTube(){
        heading("YouTube", "Filmes dublados e transmissões oficiais • indexação em expansão");
        TextView disclaimer=text("Os vídeos e lives são hospedados pelo YouTube. Disponibilidade, anúncios e direitos pertencem às fontes; ainda exigem teste no dispositivo.",tv?13:11,MUTED,false);
        disclaimer.setPadding(0,d(12),0,d(12));body.addView(disclaimer);
        body.addView(button("↻ Buscar novidades NetMovies",false,()->syncNetMovies(true)));
        ArrayList<CatalogStore.Item> movies=new ArrayList<>();
        ArrayList<CatalogStore.Item> documentaries=new ArrayList<>();
        ArrayList<CatalogStore.Item> live=new ArrayList<>();
        ArrayList<CatalogStore.Item> year90=new ArrayList<>(),year00=new ArrayList<>(),year10=new ArrayList<>();
        for(CatalogStore.Item item:items){
            if(CatalogStore.deadYouTubeReference(item))continue;
            if("youtube-movie".equals(item.format)){
                movies.add(item);
                int year=0;try{year=Integer.parseInt(item.year);}catch(Exception ignored){}
                if(year>=1990&&year<=1999)year90.add(item);
                else if(year>=2000&&year<=2009)year00.add(item);
                else if(year>=2010&&year<=2019)year10.add(item);
            }else if("youtube-doc".equals(item.format))documentaries.add(item);
            else if("youtube-live".equals(item.format))live.add(item);
        }
        rowOf("Clássicos dos anos 90",year90,1);
        rowOf("Clássicos dos anos 2000",year00,1);
        rowOf("Clássicos dos anos 2010",year10,1);
        rowOf("Filmes completos em canais do YouTube",movies,1);
        rowOf("Documentários",documentaries,1);
        rowOf("TV ao vivo no YouTube • Confirmada pelo índice",live,0);
        renderYouTubeChannelDirectory();
        if(movies.isEmpty()&&documentaries.isEmpty()&&live.isEmpty())
            emptyState("Índice YouTube ainda vazio","O catálogo oficial depende de coleta e validação. Importe ou atualize um índice habilitado.");
    }
    private void renderYouTubeChannelDirectory(){
        List<YoutubeCatalogBridge.YoutubeChannel> channels;
        try{channels=YoutubeCatalogBridge.loadChannelDirectory(this);}
        catch(Exception ex){android.util.Log.w("VortexTV","Canal YouTube directory unavailable",ex);return;}
        TextView title=text("Canais oficiais para acompanhar",tv?23:19,LIGHT,true);
        title.setPadding(0,d(20),0,d(8));body.addView(title);
        TextView note=text("Acesso ao canal no YouTube. Estes links não confirmam uma live ativa agora.",tv?13:11,MUTED,false);
        note.setPadding(0,0,0,d(14));body.addView(note);
        for(YoutubeCatalogBridge.YoutubeChannel channel:channels){
            LinearLayout line=row();line.setPadding(d(16),d(12),d(15),d(12));
            GradientDrawable bg=shape(PANEL,11);bg.setStroke(d(1),BORDER);line.setBackground(bg);
            TextView symbol=text("▶",tv?20:17,0xFFB5A0FF,true);
            line.addView(symbol,new LinearLayout.LayoutParams(d(tv?58:42),-2));
            LinearLayout col=column();
            TextView name=text(channel.name,tv?17:15,LIGHT,true);col.addView(name);
            TextView category=text(channel.category+"  •  Live não verificada",tv?12:11,MUTED,false);
            col.addView(category);
            line.addView(col,new LinearLayout.LayoutParams(0,-2,1));
            TextView action=text("ABRIR ↗",tv?12:10,PURPLE,true);line.addView(action);
            focusable(line);
            line.setOnClickListener(v->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(channel.url)));}
                catch(Exception ex){Toast.makeText(this,"Não foi possível abrir o canal",Toast.LENGTH_LONG).show();}
            });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
            params.bottomMargin=d(6);body.addView(line,params);
        }
    }
    private void refreshYouTubeIndex(){
        runTask(()->{
            List<CatalogStore.Item> from=YoutubeCatalogBridge.fetchIndex();
            int count=catalog.appendItems(from);
            return "YouTube • "+from.size()+" referências no índice • "+count+" novas";
        },true);
    }
    private void renderFilms(){
        heading("Filmes", "Catálogo por gênero • Imagens e fontes reais • Obras abertas e importadas");
        addOpenArchiveAction();
        HorizontalScrollView categoryScroll=new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=row();chips.setPadding(0,d(17),0,d(18));
        for(String genre:FILM_GENRES){
            boolean chosen=genre.equals(selectedFilmGenre);
            TextView chip=text(genre,tv?15:13,chosen?BG:LIGHT,true);
            chip.setPadding(d(17),d(10),d(17),d(10));
            GradientDrawable shape=shape(chosen?0xFFE6E2FF:PANEL,22);shape.setStroke(d(1),chosen?0xFFE6E2FF:BORDER);
            chip.setBackground(shape);focusable(chip);
            chip.setOnClickListener(v->{selectedFilmGenre=genre;render();});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=d(8);chips.addView(chip,cp);
        }
        categoryScroll.addView(chips);body.addView(categoryScroll);
        List<CatalogStore.Item> selected="Todos".equals(selectedFilmGenre)?filter(1):filmGenre(selectedFilmGenre);
        // Promote familiar studio releases before uncurated Archive material.
        final java.util.Map<String,Integer> priority=new java.util.HashMap<>();
        int rank=0;for(CatalogStore.Item entry:featuredMovies())priority.put(entry.url,rank++);
        selected.sort((left,right)->{
            int a=priority.containsKey(left.url)?priority.get(left.url):999;
            int b=priority.containsKey(right.url)?priority.get(right.url):999;
            if(a!=b)return Integer.compare(a,b);
            boolean archiveA=left.provider.startsWith("Internet Archive");
            boolean archiveB=right.provider.startsWith("Internet Archive");
            return Boolean.compare(archiveA,archiveB);
        });
        if(selected.isEmpty())emptyState("Ainda sem filmes neste gênero","Atualize o acervo aberto ou importe sua coleção de mídia para preencher esta categoria.");
        else {
            rowOf(""+selectedFilmGenre+"  ·  "+selected.size()+" títulos",selected,1);
            if("Todos".equals(selectedFilmGenre)){
                for(String genre:FILM_GENRES){
                    if("Todos".equals(genre))continue;
                    List<CatalogStore.Item> subset=filmGenre(genre);
                    if(!subset.isEmpty())rowOf(""+genre,subset,1);
                }
                java.util.LinkedHashMap<String,List<CatalogStore.Item>> providers=new java.util.LinkedHashMap<>();
                for(CatalogStore.Item item:selected){
                    String origin=item.provider.isEmpty()?"Outras fontes":item.provider;
                    if(!providers.containsKey(origin))providers.put(origin,new ArrayList<>());
                    providers.get(origin).add(item);
                }
                for(java.util.Map.Entry<String,List<CatalogStore.Item>> origin:providers.entrySet())
                    rowOf("Origem: "+origin.getKey(),origin.getValue(),1);
            }
        }
    }
    private List<CatalogStore.Item> filmGenre(String genre){
        ArrayList<CatalogStore.Item> out=new ArrayList<>();
        for(CatalogStore.Item i:items){
            if(CatalogStore.deadYouTubeReference(i))continue;
            if(!i.isFilm() && !("Documentário".equals(genre)&&i.isDoc()))continue;
            String tags=(i.group+" "+i.genre+" "+i.description).toLowerCase(Locale.ROOT);
            String g=genre.toLowerCase(Locale.ROOT);
            if(tags.contains(g) || ("Animação".equals(genre)&&tags.contains("anim"))
                || ("Ficção científica".equals(genre)&&(tags.contains("sci-fi")||tags.contains("science fiction")))
                || ("Terror".equals(genre)&&(tags.contains("horror")||tags.contains("assust")))
                || ("Documentário".equals(genre)&&i.isDoc()))out.add(i);
        }
        return out;
    }

    private static final class GuideChannel {
        final String name,group,logo,tvgId; final int number;
        GuideChannel(String name,String group,String logo,String tvgId,int number){
            this.name=name;this.group=group;this.logo=logo;this.tvgId=tvgId;this.number=number;
        }
    }
    private void loadGuideOnly(){
        unavailableGuide.clear();
        try(InputStream in=getAssets().open("guide_only_channels.json")){
            java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();
            byte[] b=new byte[4096];int count;
            while((count=in.read(b))!=-1)data.write(b,0,count);
            JSONArray list=new JSONArray(data.toString("UTF-8"));
            for(int n=0;n<list.length();n++){
                JSONObject j=list.optJSONObject(n);if(j==null)continue;
                unavailableGuide.add(new GuideChannel(j.optString("name"),j.optString("group"),
                    j.optString("logo"),j.optString("tvgId"),j.optInt("number",100+n)));
            }
        }catch(Exception ignored){}
    }
    private String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private CatalogStore.Item streamFor(GuideChannel item){
        String key=normalized(item.name);
        for(CatalogStore.Item i:items)if(i.isLive()&&(key.equals(normalized(i.name))
                || (!item.tvgId.isEmpty() && item.tvgId.equalsIgnoreCase(i.tvgId))))return i;
        return null;
    }
    private String epgNow(String tvgId){
        if(tvgId==null||tvgId.isEmpty())return "Programação não informada";
        return guide.now(tvgId);
    }
    private void renderLiveGuide(){
        heading("TV ao vivo", "Guia vertical • ↑/↓ troca de canais no player • ←/→ abre a lista");
        TextView note=text("AO VIVO  •  Canais pagos sem fonte ativa são identificados e não reproduzem até que uma fonte compatível seja adicionada.",tv?13:11,MUTED,false);
        note.setPadding(0,d(12),0,d(18));body.addView(note);
        TextView available=text("SINAL DETECTADO EM TESTE DE REDE • SEM HOMOLOGAÇÃO NO APARELHO",tv?16:14,0xFFBDA9FF,true);
        available.setPadding(0,d(12),0,d(12));body.addView(available);
        int number=1;
        for(CatalogStore.Item channel:liveCandidates(true))
            addGuideRow(number++,channel.name,channel.group,channel.logo,
                epgNow(channel.tvgId.isEmpty()?channel.name:channel.tvgId),channel,true);
        TextView offline=text("FONTES EM MANUTENÇÃO • NÃO HOMOLOGADAS",tv?16:14,0xFFBDA9FF,true);
        offline.setPadding(0,d(28),0,d(12));body.addView(offline);
        for(CatalogStore.Item channel:liveCandidates(false))
            addGuideRow(number++,channel.name,channel.group,channel.logo,
                "Sem resposta de vídeo no servidor; pode variar por rede",channel,false);
        TextView premium=text("CANAIS POR ASSINATURA • GUIA",tv?16:14,0xFFBDA9FF,true);
        premium.setPadding(0,d(28),0,d(12));body.addView(premium);
        for(GuideChannel channel:unavailableGuide){
            CatalogStore.Item matched=streamFor(channel);
            if(matched==null)addGuideRow(channel.number,channel.name,channel.group,channel.logo,epgNow(channel.tvgId.isEmpty()?channel.name:channel.tvgId),null,false);
        }
    }
    private void addGuideRow(int number,String name,String group,String logo,String program,CatalogStore.Item source,boolean playable){
        LinearLayout line=row();line.setPadding(d(tv?19:12),d(10),d(15),d(10));
        GradientDrawable bg=shape(PANEL,12);bg.setStroke(d(1),BORDER);line.setBackground(bg);
        TextView channelNumber=text(String.format(Locale.ROOT,"%03d",number),tv?21:16,0xFFBDA9FF,true);
        line.addView(channelNumber,new LinearLayout.LayoutParams(d(tv?64:48),-2));
        if(logo!=null&&logo.startsWith("https://")){
            ImageView channelLogo=new ImageView(this);channelLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            line.addView(channelLogo,new LinearLayout.LayoutParams(d(tv?94:60),d(tv?54:42)));
            loadPoster(channelLogo,logo);
        }else{
            TextView letter=text(name.isEmpty()?"TV":name.substring(0,Math.min(2,name.length())).toUpperCase(Locale.ROOT),tv?18:13,0xFFDDD3FF,true);
            letter.setGravity(Gravity.CENTER);letter.setBackground(shape(0xFF29223F,9));
            line.addView(letter,new LinearLayout.LayoutParams(d(tv?80:48),d(tv?48:40)));
        }
        LinearLayout titles=column();titles.setPadding(d(tv?22:13),0,d(10),0);
        TextView title=text(name,tv?19:15,LIGHT,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);titles.addView(title);
        TextView detail=text((group.isEmpty()?"TV":group)+"  •  "+program,tv?12:11,MUTED,false);
        detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);titles.addView(detail);
        line.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        String quality=source==null?"none":catalog.sourceQuality(source.url);
        String statusText=playable?("server_segment_ok".equals(quality)?"▶  TESTAR":"▶  ASSISTIR")
            :(source!=null?"INDISPONÍVEL":"SEM FONTE");
        TextView status=text(statusText,tv?13:10,playable?POSITIVE:MUTED,true);
        line.addView(status);
        focusable(line);
        line.setOnClickListener(v->{
            if(playable&&source!=null)play(source);
            else if(source!=null)new AlertDialog.Builder(this).setTitle(name+" • Fonte indisponível")
                .setMessage("A última verificação no servidor não encontrou vídeo. A disponibilidade pode variar na rede da TV Box. Deseja testar esta URL mesmo assim?")
                .setPositiveButton("Testar mesmo assim",(dialog,which)->play(source))
                .setNegativeButton("Fechar",null).show();
            else new AlertDialog.Builder(this).setTitle(name)
                .setMessage("Canal cadastrado apenas no guia. Nenhuma URL reproduzível foi homologada.")
                .setPositiveButton("Importar M3U",(dialog,which)->showSources())
                .setNegativeButton("Fechar",null).show();
        });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=d(7);
        body.addView(line,params);
    }
    private void addOpenArchiveAction(){
        LinearLayout actions=row();actions.setPadding(0,d(9),0,d(18));
        actions.addView(button("✦  Carregar mais filmes livres",true,()->loadOpenArchive(true)));
        TextView info=text("  Creative Commons / domínio público • Internet Archive",tv?12:10,MUTED,false);
        if(tv)actions.addView(info);body.addView(actions);
    }
    private volatile boolean discovering=false;
    private void loadOpenArchive(boolean userInitiated){
        if(discovering)return;
        discovering=true;
        if(userInitiated)Toast.makeText(this,"Buscando filmes com licenças abertas...",Toast.LENGTH_LONG).show();
        new Thread(()->{
            String result;
            try{
                OpenFilmCatalog service=new OpenFilmCatalog();
                int page=uiPrefs.getInt("open_archive_page_v2",1);
                int totalPages=uiPrefs.getInt("open_archive_total_pages_v2",Integer.MAX_VALUE);
                if(page>totalPages){
                    result="Todas as páginas deste acervo já foram consultadas. Você pode importar outras fontes.";
                } else {
                    OpenFilmCatalog.Batch batch=service.fetchPage(page,40);
                    int added=catalog.appendItems(batch.items);
                    uiPrefs.edit().putInt("open_archive_page_v2",batch.nextPage)
                        .putInt("open_archive_total_pages_v2",batch.pages).apply();
                    result="Página "+page+"/"+batch.pages+" • "+batch.items.size()
                        +" filmes elegíveis encontrados • "+added+" adicionados.";
                }
            }catch(Exception ex){result="Acervo indisponível no momento: "+ex.getMessage();}
            final String message=result;
            discovering=false;
            runOnUiThread(()->{items=catalog.load();render();if(userInitiated)Toast.makeText(this,message,Toast.LENGTH_LONG).show();});
        },"Vortex-OpenFilmDiscovery").start();
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
                for(CatalogStore.Item i:items)if(i.name.toLowerCase(Locale.ROOT).contains(query)||i.group.toLowerCase(Locale.ROOT).contains(query)||i.genre.toLowerCase(Locale.ROOT).contains(query)||i.provider.toLowerCase(Locale.ROOT).contains(query))matches.add(i);
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
        TextView label=text(name,tv?23:19,LIGHT,true);label.setPadding(0,0,0,d(14));sectionRoot.addView(label);
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setClipToPadding(false);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout cards=row();cards.setPadding(d(5),d(6),d(5),d(10));strip.addView(cards);
        sectionRoot.addView(strip,new LinearLayout.LayoutParams(-1,-2));
        addCards(cards,content,cardType,tv?60:36);
    }
    private void loadPoster(ImageView view,String url){
        if(url==null||!url.startsWith("https://"))return;
        android.graphics.drawable.Drawable transparent=
            new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT);
        com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request=
            Glide.with(this).load(url).diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .timeout(8000).placeholder(transparent).centerCrop();
        // YouTube may retire a thumbnail variant; try the alternate official image.
        if(url.startsWith("https://i.ytimg.com/vi/")&&url.endsWith("/hqdefault.jpg")){
            String alternative=url.replace("/hqdefault.jpg","/mqdefault.jpg");
            request=request.error(Glide.with(this).load(alternative).timeout(8000)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop().error(transparent));
        }else request=request.error(transparent);
        request.into(view);
    }
    private void addCards(LinearLayout target,List<CatalogStore.Item> content,int type,int max){
        int total=Math.min(content.size(),max);
        for(int index=0;index<total;index++){
            CatalogStore.Item i=content.get(index);
            LinearLayout holder=column();holder.setClipChildren(false);
            int width=d(type==0?(tv?220:182):(tv?172:143));
            int height=d(type==0?(tv?135:108):(tv?240:196));
            FrameLayout poster=new FrameLayout(this);
            int theme=(i.name.hashCode()&0x00777777)|0xFF151522;
            poster.setBackground(gradient(new int[]{theme,0xFF171322,0xFF090D1A},11));
            TextView watermark=text(i.isLive()?"▣  TV AO VIVO":"✦  VORTEX CINEMA",
                tv?13:11,0xFFB8A0DE,true);
            FrameLayout.LayoutParams markParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
            markParams.setMargins(d(12),d(12),d(8),0);
            poster.addView(watermark,markParams);
            TextView glyph=text(i.isLive()?"◉":"▶",tv?54:37,0x667A72A0,true);
            glyph.setGravity(Gravity.CENTER);
            poster.addView(glyph,new FrameLayout.LayoutParams(-1,-1));
            if(i.logo.startsWith("https://")){
                ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                poster.addView(cover,new FrameLayout.LayoutParams(-1,-1));loadPoster(cover,i.logo);
                View shading=new View(this);
                shading.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00000000,0x24000000,0xDD070812}));
                poster.addView(shading,new FrameLayout.LayoutParams(-1,-1));
            }
            TextView posterName=text(i.name,tv?17:14,LIGHT,true);
            posterName.setMaxLines(2);posterName.setEllipsize(TextUtils.TruncateAt.END);
            posterName.setShadowLayer(5,0,2,Color.BLACK);
            FrameLayout.LayoutParams nameParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
            nameParams.setMargins(d(12),d(8),d(12),d(13));
            poster.addView(posterName,nameParams);
            holder.addView(poster,new LinearLayout.LayoutParams(width,height));
            TextView tag=text(i.genre.isEmpty()?i.group:i.genre,11,MUTED,false);
            tag.setSingleLine(true);tag.setEllipsize(TextUtils.TruncateAt.END);
            tag.setPadding(d(2),d(7),d(2),0);holder.addView(tag);
            LinearLayout.LayoutParams holderParams=new LinearLayout.LayoutParams(width,-2);
            holderParams.rightMargin=d(tv?16:12);target.addView(holder,holderParams);
            // First selection immediately plays; long-press opens details/favorites.
            poster.setOnClickListener(v->play(i));
            poster.setOnLongClickListener(v->{detail(i);return true;});
            poster.setContentDescription(i.name+"; tocar para assistir, segurar para informações");
            focusable(poster);
        }
    }
    private void detail(CatalogStore.Item i){
        String summary="Categoria: "+(i.genre.isEmpty()?i.group:i.genre)
            +"\nOrigem: "+(i.provider.isEmpty()?"Fonte importada":i.provider)
            +(i.year.isEmpty()?"":"\nAno: "+i.year)
            +(i.description.isEmpty()?"":"\n\n"+i.description)
            +"\n\nDisponibilidade externa sujeita a teste de reprodução.";
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
            .setNegativeButton("✎ Corrigir título",(dialog,which)->showTitleCorrection(i)).show();
    }
    private void showTitleCorrection(CatalogStore.Item item){
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setText(item.name);field.selectAll();
        field.setPadding(d(20),d(16),d(20),d(16));
        new AlertDialog.Builder(this).setTitle("Corrigir título")
            .setMessage("Altera apenas o nome exibido no Vortex TV. O link do vídeo não muda.")
            .setView(field)
            .setPositiveButton("Salvar",(dialog,which)->{
                if(catalog.rename(item.url,field.getText().toString())){
                    items=catalog.load();render();
                }else Toast.makeText(this,"Informe um título válido",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar",null).show();
    }
    private void play(CatalogStore.Item i){
        if(i.format.startsWith("youtube-")){
            Intent yt=new Intent(this,YouTubePlaybackActivity.class);
            yt.putExtra(YouTubePlaybackActivity.EXTRA_VIDEO_URL,i.url);
            if("youtube-live".equals(i.format)){
                JSONArray channels=new JSONArray();int selected=0;
                for(CatalogStore.Item channel:items){
                    if(!"youtube-live".equals(channel.format))continue;
                    if(i.url.equals(channel.url))selected=channels.length();
                    JSONObject v=new JSONObject();
                    try{v.put("name",channel.name);v.put("url",channel.url);}catch(Exception ignored){}
                    channels.put(v);
                }
                yt.putExtra(YouTubePlaybackActivity.EXTRA_LIVE_LIST,channels.toString());
                yt.putExtra(YouTubePlaybackActivity.EXTRA_LIVE_INDEX,selected);
            }
            startActivity(yt);return;
        }
        if(!i.url.startsWith("https://")){
            new AlertDialog.Builder(this).setTitle("Fonte HTTP sem criptografia")
                .setMessage("A reprodução por HTTP pode expor seu tráfego. Esta versão de desenvolvimento utiliza HTTPS. Importe uma fonte HTTPS válida.")
                .setPositiveButton("Entendi",null).show();return;
        }
        Intent intent=new Intent(this,VortexPlayerActivity.class);
        intent.putExtra(VortexPlayerActivity.EXTRA_MEDIA_URL,i.url);
        JSONArray alternatives=new JSONArray();
        if("Big Buck Bunny".equals(i.name)
            &&i.url.equals("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")){
            alternatives.put("https://cdn.jwplayer.com/manifests/pZxWPRg4.m3u8");
        }
        if("Tears of Steel".equals(i.name)
            &&i.url.equals("https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8")){
            alternatives.put("https://archive.org/download/TearsOfSteel/TearsOfSteel_720p_512kb.mp4");
        }
        if(alternatives.length()>0)
            intent.putExtra(VortexPlayerActivity.EXTRA_MEDIA_FALLBACKS_JSON,alternatives.toString());
        if(i.isLive()){
            JSONArray playable=new JSONArray();int selected=0;
            for(CatalogStore.Item channel:items) {
                if(!channel.isLive() || !channel.url.startsWith("https://"))continue;
                if(channel.url.equals(i.url))selected=playable.length();
                JSONObject entry=new JSONObject();
                try {
                    entry.put("name",channel.name);
                    entry.put("url",channel.url);
                    entry.put("number",playable.length()+1);
                    entry.put("programme",epgNow(channel.tvgId.isEmpty()?channel.name:channel.tvgId));
                }catch(Exception ignored){}
                playable.put(entry);
            }
            intent.putExtra(VortexPlayerActivity.EXTRA_CHANNELS_JSON,playable.toString());
            intent.putExtra(VortexPlayerActivity.EXTRA_CHANNEL_INDEX,selected);
        }
        startActivity(intent);
    }
    private volatile boolean officialSyncBusy=false;
    private void syncNetMovies(boolean manual){
        if(officialSyncBusy)return;
        long now=System.currentTimeMillis();
        long previous=uiPrefs.getLong("netmovies_last_success_v1",0L);
        if(!manual && previous>0L && now>=previous && now-previous<6L*60L*60L*1000L)
            return;
        officialSyncBusy=true;
        if(manual)Toast.makeText(this,"Buscando novidades oficiais...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String message="";
            boolean ok=false;
            try {
                RemoteCatalogSync.Result result=RemoteCatalogSync.refreshOfficialMovies(
                    getApplicationContext(),catalog);
                message="NetMovies: "+result.received+" títulos recentes • "+result.accepted+" sincronizados.";
                ok=true;
            }catch(Exception ex){
                message="Feed NetMovies indisponível: "+ex.getMessage();
                android.util.Log.w("VortexTV","Official movie sync failed",ex);
            }finally{
                final boolean success=ok;
                final String feedback=message;
                runOnUiThread(()->{
                    officialSyncBusy=false;
                    if(success){
                        uiPrefs.edit().putLong("netmovies_last_success_v1",
                            System.currentTimeMillis()).apply();
                        items=catalog.load();render();
                    }
                    if(manual)Toast.makeText(this,feedback,Toast.LENGTH_LONG).show();
                });
            }
        },"Vortex-NetMovies-Feed").start();
    }
    private void showSources(){
        new AlertDialog.Builder(this).setTitle("Gerenciar fontes")
            .setItems(new String[]{"Sincronizar catálogo Vortex Cloud","Laboratório: testar filmes livres","Atualizar filmes NetMovies","Importar arquivo M3U","Importar playlist HTTPS","Adicionar vídeo HTTPS","Importar EPG XMLTV","Atualizar EPG por URL","Descobrir filmes abertos","Atualizar índice YouTube","Escolher modo TV/Smartphone","Limpar catálogo local"},(d,which)->{
                if(which==0)syncCloud(true);
                if(which==1)showCloudLab();
                if(which==2)syncNetMovies(true);
                if(which==3)openFile(PICK_M3U);
                if(which==4)importRemote();
                if(which==5)addDirect();
                if(which==6)openFile(PICK_EPG);
                if(which==7)importRemoteGuide();
                if(which==8)loadOpenArchive(true);
                if(which==9)refreshYouTubeIndex();
                if(which==10)showModeChooser();
                if(which==11)new AlertDialog.Builder(this).setTitle("Apagar o catálogo?")
                    .setMessage("Remove sua lista de conteúdo e favoritos somente deste dispositivo.")
                    .setPositiveButton("Apagar",(d2,w)->{catalog.clear();items=catalog.load();render();})
                    .setNegativeButton("Cancelar",null).show();
            }).show();
    }

    /** Separate lab navigation: no sample is added to production favorites or live guide. */
    private void showCloudLab(){
        Toast.makeText(this,"Consultando testes de vídeo...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                final List<CatalogStore.Item> samples=RemoteCatalogSync.fetchLab(getApplicationContext());
                runOnUiThread(()->{
                    if(isFinishing())return;
                    if(samples.isEmpty()){
                        new AlertDialog.Builder(this).setTitle("Laboratório Vortex Cloud")
                            .setMessage("Nenhuma fonte de teste disponível neste momento.")
                            .setPositiveButton("Fechar",null).show();
                        return;
                    }
                    String[] labels=new String[samples.size()];
                    for(int i=0;i<samples.size();i++)labels[i]=samples.get(i).name+"  •  Testar reprodução HLS";
                    new AlertDialog.Builder(this).setTitle("Laboratório • Escolha um filme")
                        .setItems(labels,(dialog,which)->{
                            CatalogStore.Item selected=samples.get(which);
                            Intent video=new Intent(this,VortexPlayerActivity.class);
                            video.putExtra(VortexPlayerActivity.EXTRA_MEDIA_URL,selected.url);
                            startActivity(video);
                        }).setNegativeButton("Fechar",null).show();
                });
            } catch(Exception ex) {
                android.util.Log.w("VortexTV","Cloud lab unavailable",ex);
                runOnUiThread(()->{
                    if(!isFinishing())Toast.makeText(this,"Laboratório indisponível: "+ex.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        },"VortexCloud-Lab").start();
    }

    /** No blocking network calls on the Android UI thread. Keep previous cache on error. */
    private volatile boolean cloudSyncBusy=false;
    private void syncCloud(boolean manual){
        if(cloudSyncBusy)return;
        long now=System.currentTimeMillis();
        long last=uiPrefs.getLong("cloud_last_success",0L);
        if(!manual && last>0L && now>=last && now-last<6L*60L*60L*1000L)return;
        cloudSyncBusy=true;
        if(manual)Toast.makeText(this,"Sincronizando catálogo...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            String notice="";
            boolean success=false;
            try {
                RemoteCatalogSync.Result result=RemoteCatalogSync.refresh(getApplicationContext(),catalog);
                notice="Catálogo atualizado: "+result.accepted+" entradas disponíveis";
                success=true;
            } catch(Exception error) {
                notice="Falha na sincronização: "+error.getMessage();
                android.util.Log.w("VortexTV","Cloud refresh unavailable",error);
            } finally {
                final String feedback=notice;
                final boolean ok=success;
                runOnUiThread(()->{
                    cloudSyncBusy=false;
                    if(ok){
                        uiPrefs.edit().putLong("cloud_last_success",System.currentTimeMillis()).apply();
                        items=catalog.load();render();
                    }
                    if(manual)Toast.makeText(this,feedback,Toast.LENGTH_LONG).show();
                });
            }
        },"VortexCloud-Sync").start();
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
                        for(CatalogStore.Item item:items){
                            if(!item.tvgId.isEmpty())allowed.add(item.tvgId);
                            if(item.isLive())allowed.add(item.name);
                        }
                        for(GuideChannel item:unavailableGuide){
                            if(!item.tvgId.isEmpty())allowed.add(item.tvgId);
                            allowed.add(item.name);
                        }
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
            HashSet<String> allowed=new HashSet<>();
            for(CatalogStore.Item i:items){if(!i.tvgId.isEmpty())allowed.add(i.tvgId);if(i.isLive())allowed.add(i.name);}
            for(GuideChannel i:unavailableGuide){if(!i.tvgId.isEmpty())allowed.add(i.tvgId);allowed.add(i.name);}
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
