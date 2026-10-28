from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLOCK = ROOT / "app/src/main/java/os/vortex/tv/XmltvClock.java"
GUIDE = ROOT / "app/src/main/java/os/vortex/tv/GuideStore.java"
UI = ROOT / "app/src/main/java/os/vortex/tv/MainActivity.java"


class EpgClockTests(unittest.TestCase):
    def test_xmltv_timestamp_offsets_and_rejections(self):
        if not (shutil.which("java") and shutil.which("javac")):
            self.skipTest("JDK indisponível; executar no ambiente de build")
        harness = """import os.vortex.tv.XmltvClock;
public class ClockHarness {
  public static void main(String[] args) {
    long br=XmltvClock.parseMillis("20261028160000 -0300");
    long utc=XmltvClock.parseMillis("20261028190000 +0000");
    if(br != utc || br <= 0) throw new AssertionError("timezone");
    if(XmltvClock.parseMillis("202610281600 -0300") != br) throw new AssertionError("minutes");
    if(XmltvClock.parseMillis("20260230160000 -0300") != -1) throw new AssertionError("invalid date");
    if(XmltvClock.parseMillis("20261028160000 +2460") != -1) throw new AssertionError("bad timezone");
    if(XmltvClock.parseMillis("invalid") != -1) throw new AssertionError("invalid data");
  }
}"""
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp)
            (target / "ClockHarness.java").write_text(harness)
            subprocess.run(["javac", "-d", tmp, str(CLOCK), str(target / "ClockHarness.java")],
                           check=True, capture_output=True, text=True)
            subprocess.run(["java", "-cp", tmp, "ClockHarness"], check=True, capture_output=True, text=True)

    def test_epg_selects_current_not_first(self):
        guide = GUIDE.read_text()
        ui = UI.read_text()
        self.assertIn("stop <= now", guide)
        self.assertIn("start <= now && now < stop", guide)
        self.assertIn("MAX_PER_CHANNEL = 64", guide)
        self.assertIn("guide.now(tvgId)", ui)
        self.assertNotIn("entries.optJSONObject(0)", ui)


if __name__ == "__main__":
    unittest.main()
