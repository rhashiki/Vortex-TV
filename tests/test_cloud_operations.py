from pathlib import Path
import json
import unittest

ROOT=Path(__file__).resolve().parents[1]

class CloudOperations(unittest.TestCase):
    def test_seed_rights_are_conservative(self):
        doc=json.loads((ROOT/'catalog/cloud/curated_sources.json').read_text())
        self.assertEqual(doc['schema'],1)
        rows=doc['sources']
        self.assertGreaterEqual(len(rows),9)
        self.assertEqual(sum(x['rights_status']=='authorized' for x in rows),3)
        for r in rows:
            self.assertTrue(r['url'].startswith('https://'))
            self.assertTrue(r['probe_enabled'])
            if r['kind']=='channel':
                self.assertEqual(r['rights_status'],'unknown')
    def test_bounded_health_pipeline_and_cron(self):
        sql=(ROOT/'supabase/migrations/20261028_vortex_manifest_checks.sql').read_text()
        scheduler=(ROOT/'supabase/migrations/20261028_vortex_scheduler.sql').read_text()
        self.assertIn("allowed_stream_hosts",sql)
        self.assertIn("timeout_milliseconds := 7000",sql)
        self.assertIn("manifest_ok then 'manifest_ok'",sql)
        self.assertIn("playback_ok' and manifest_ok",sql)
        self.assertIn("vortex-tv-hls-enqueue",scheduler)
        self.assertIn("vortex-tv-hls-results",scheduler)
    def test_directory_is_explicit_allowlist(self):
        sql=(ROOT/'supabase/migrations/20261028_vortex_directory_discovery.sql').read_text()
        self.assertIn("discovery_allowlist",sql)
        self.assertIn("rights_status,validation_status",sql)
        self.assertIn("'unknown','source_found'",sql)
        self.assertIn("enabled,probe_enabled",sql)
        self.assertIn("vortex-tv-directory-enqueue",sql)
        self.assertIn("vortex-tv-directory-results",sql)

if __name__=='__main__':
    unittest.main()
