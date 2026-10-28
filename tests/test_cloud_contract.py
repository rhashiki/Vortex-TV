from pathlib import Path
import json
import unittest

ROOT=Path(__file__).resolve().parents[1]

class CloudContractTests(unittest.TestCase):
    def test_feed_and_config(self):
        payload=json.loads((ROOT/'catalog/cloud/catalog.json').read_text())
        config=json.loads((ROOT/'app/src/main/assets/cloud_config.json').read_text())
        self.assertEqual(payload['version'],1)
        self.assertIsInstance(payload['items'],list)
        self.assertTrue(config['catalog_url'].startswith('https://'))
        self.assertEqual(config['version'],1)
        self.assertEqual(config['catalog_url'], 'https://nozbyjbtxbgchvkdpnrp.supabase.co/functions/v1/vortex-catalog')
    def test_remote_gate_and_separation(self):
        sync=(ROOT/'app/src/main/java/os/vortex/tv/RemoteCatalogSync.java').read_text()
        store=(ROOT/'app/src/main/java/os/vortex/tv/CatalogStore.java').read_text()
        self.assertIn('"playback_ok".equals',sync)
        self.assertIn('"authorized".equals',sync)
        self.assertIn('replaceRemote(approved)',sync)
        self.assertIn('loadLocal()',store)
        self.assertIn('for(String layer:new String[]{CLOUD,OFFICIAL_YOUTUBE})',store)
        self.assertIn('saveItems(CLOUD,accepted)',store)
    def test_cloud_must_not_proxy_video(self):
        edge=(ROOT/'supabase/functions/vortex-catalog/index.ts').read_text()
        self.assertIn('request.method !== "GET"',edge)
        self.assertIn('validation_status=eq.playback_ok',edge)
        self.assertIn('rights_status=eq.authorized',edge)
        self.assertNotIn('video/mp2t',edge)
        self.assertIn('lab ? [] : items',edge)
        self.assertIn('test_items: items',edge)
        self.assertIn('validation_status=eq.manifest_ok',edge)
        self.assertIn('rights_status=eq.authorized',edge)
        remote=(ROOT/'app/src/main/java/os/vortex/tv/RemoteCatalogSync.java').read_text()
        self.assertIn('fetchLab(Context context)',remote)
        self.assertIn('"manifest_ok".equals',remote)
        self.assertIn('"authorized".equals',remote)
        ui=(ROOT/'app/src/main/java/os/vortex/tv/MainActivity.java').read_text()
        self.assertIn('showCloudLab()',ui)
        self.assertIn('Laboratório: testar filmes livres',ui)
        self.assertIn('.setTitle("Laboratório • Escolha um filme")',ui)
        lab=ui[ui.index('new AlertDialog.Builder(this).setTitle("Laboratório • Escolha um filme")'):]
        lab=lab[:lab.index('}).setNegativeButton("Fechar",null).show();')]
        self.assertIn('.setItems(labels,',lab)
        self.assertNotIn('.setMessage("Fontes abertas com manifesto validado',lab)
    def test_no_anonymous_writes(self):
        migration=(ROOT/'supabase/migrations/20261028_vortex_tv_cloud.sql').read_text()
        self.assertEqual(migration.count('enable row level security'),3)
        self.assertIn('from public,anon,authenticated',migration)

if __name__=='__main__':
    unittest.main()
