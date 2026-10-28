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
        self.assertIn('loadFrom(CLOUD)',store)
        self.assertIn('saveItems(CLOUD,accepted)',store)
    def test_cloud_must_not_proxy_video(self):
        edge=(ROOT/'supabase/functions/vortex-catalog/index.ts').read_text()
        self.assertIn('request.method !== "GET"',edge)
        self.assertIn('validation_status=eq.playback_ok',edge)
        self.assertIn('rights_status=eq.authorized',edge)
        self.assertNotIn('video/mp2t',edge)
    def test_no_anonymous_writes(self):
        migration=(ROOT/'supabase/migrations/20261028_vortex_tv_cloud.sql').read_text()
        self.assertEqual(migration.count('enable row level security'),3)
        self.assertIn('from public,anon,authenticated',migration)

if __name__=='__main__':
    unittest.main()
