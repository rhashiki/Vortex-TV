from pathlib import Path
import json,unittest
ROOT=Path(__file__).resolve().parents[1]
class ContentQuality(unittest.TestCase):
    def test_channel_audit_visible_and_bad_sources_hidden(self):
        seeds=json.loads((ROOT/'app/src/main/assets/seed_catalog.json').read_text())
        self.assertEqual([x['name'] for x in seeds if x.get('status')=='server_segment_ok'],['TV Brasil','Canal Gov','TV Câmara'])
        self.assertEqual(sum(x.get('status')=='server_unavailable' for x in seeds),19)
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        for token in ('liveCandidates(true)','liveCandidates(false)','FONTES EM MANUTENÇÃO','Testar mesmo assim'):
            self.assertIn(token,ui)
        self.assertNotIn('rowOf("Ao vivo agora",filter(0)',ui)
    def test_known_movies_hero(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        for token in ('Cinema conhecido • Filmes em canais oficiais','Nerve: Um Jogo Sem Regras','Os Mercenários 2','List<CatalogStore.Item> curated=featuredMovies();'):
            self.assertIn(token,ui)
        self.assertIn('"CINDIE TV".equals(name)',(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text())
    def test_dead_youtube_refs_and_poster_fallback(self):
        store=(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text()
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        for vid in ('DUAyQ8yxg38','5Au2Mboodro','HfQoBFovpG8'):self.assertIn(vid,store)
        self.assertIn('if(CatalogStore.deadYouTubeReference(item))continue;',ui)
        self.assertIn('if(ok&&!CatalogStore.deadYouTubeReference(i))out.add(i);',ui)
        for token in ('/mqdefault.jpg','request.into(view)','VORTEX CINEMA'):self.assertIn(token,ui)
    def test_archive_opt_in_and_local_preserved(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertNotIn('loadOpenArchive(false);',ui)
        self.assertIn('loadOpenArchive(true)',ui)
        store=(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text()
        self.assertIn('bundledQuality.containsKey(url)',store)
        self.assertIn('loadLocal()',store)
if __name__=='__main__':unittest.main()
