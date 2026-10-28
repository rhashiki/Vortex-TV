from pathlib import Path
import json,unittest
ROOT=Path(__file__).resolve().parents[1]
class NetMoviesFeed(unittest.TestCase):
    def test_edge_official_id_and_filters(self):
        edge=(ROOT/'supabase/functions/vortex-netmovies-feed/index.ts').read_text()
        self.assertIn('UCK5RRQ3TdwAbbCWrtnXsN3Q',edge)
        self.assertIn('filme\\s+completo',edge)
        self.assertIn('yt:channelId',edge)
        self.assertIn('"catalog_candidate"',edge)
        self.assertIn('no-store',edge)
        self.assertNotIn('service_role',edge)
        self.assertNotIn('yt-dlp',edge)
    def test_configuration_and_client_validation(self):
        config=json.loads((ROOT/'app/src/main/assets/cloud_config.json').read_text())
        self.assertEqual(config['official_movies_url'],
                         'https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-netmovies-feed')
        java=(ROOT/'app/src/main/java/os/vortex/tv/RemoteCatalogSync.java').read_text()
        self.assertIn('refreshOfficialMovies(Context context,CatalogStore store)',java)
        self.assertIn('"NetMovies".equals(payload.optString("provider"))',java)
        self.assertIn('"UCK5RRQ3TdwAbbCWrtnXsN3Q".equals',java)
        self.assertIn('id.matches("[A-Za-z0-9_-]{11}")',java)
        self.assertIn('store.mergeOfficialMovies(titles)',java)
    def test_rolling_cache(self):
        store=(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text()
        self.assertIn('OFFICIAL_YOUTUBE="official_netmovies_v1"',store)
        self.assertIn('mergeOfficialMovies(List<Item> incoming)',store)
        self.assertIn('for(String layer:new String[]{CLOUD,OFFICIAL_YOUTUBE})',store)
        self.assertIn('merged.size()>=150',store)
        self.assertIn('prefs.edit().remove(KEY).remove(CLOUD).remove(OFFICIAL_YOUTUBE)',store)
    def test_auto_and_manual_refresh(self):
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('syncNetMovies(false);',ui)
        self.assertIn('syncNetMovies(true);',ui)
        self.assertIn('netmovies_last_success_v1',ui)
        self.assertIn('Novidades NetMovies • Filmes gratuitos',ui)
        self.assertIn('Atualizar filmes NetMovies',ui)
        self.assertIn('Filmes completos • YouTube',ui.replace('Mais filmes completos • YouTube','Filmes completos • YouTube'))
if __name__=='__main__':unittest.main()
