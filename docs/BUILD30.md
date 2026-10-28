# Build 30 — Multi-source playback and private IPTV imports

Data: 28/10/2026. Vortex TV app, separate from Vortex TV OS.

## Upload audit (URLs and credentials never included)
- Unified playlist: 33,601 distinct URLs, about 8 MB. Across all uploaded playlists: 33,605 unique URLs.
- Only four additional unique URLs outside the unified list, from CANAISJOABE.M3U.
- PLAYLIST(1).txt duplicates PLAYLIST.txt exactly; Canais Full(1).txt duplicates Canais Full.txt exactly.
- 33,610 metadata entries in unified list; some are malformed, not validated live streams.
- Uploaded IPTV Smarters Pro APK is a third-party player, not a source directory; inspected ZIP structure only.
- Thousands of URL paths resemble username/password bearer credentials. Do not commit files to public GitHub, bake them into an APK, or upload them to Supabase.

## Android implementation
- M3uPlaylistParser.parseToSink streams M3U entries to a consumer (no giant in-memory list).
- Private SQLite ImportedSourceStore persists up to 120,000 deduplicated URLs, keyed by SourceIdentity.
- SourceIdentity strips HD/SD/4K/alternative suffixes but prevents merging identical episode codes from different series.
- Local file and remote HTTPS M3U imports now accept up to 16 MB; imported URLs remain only in the device.
- Existing browsing caches at most 150 representatives; full catalogue can be queried using Search.
- Multiple URLs of the same exact title are sent to Media3; HTTP error, decode/network error, startup timeout (12 s), prolonged buffering (18 s), or interrupted live signal automatically advances the next alternate.
- Successfully played sources are remembered locally and preferred next time.
- HTTP playback is available only for locally imported URLs, with explicit import-time warning that unencrypted requests can expose credentials.
- User sees playback error only after all available, non-repeated candidates fail.
- Do not promise uninterrupted playback when every source is down, blocked, georestricted or DRM-protected.
- No GitHub Actions, third-party APK binaries, or confidential M3Us in source control.

## Use
1. Download user's private unified M3U and CANAISJOABE.M3U onto phone or TV Box.
2. Vortex TV > Fontes > Importar arquivo M3U. Import unified list (8 MB) first, then CANAISJOABE.M3U.
3. Read HTTP credential-exposure warning and confirm only for trusted sources.
4. Browse/search content. Tap once to play; fallback is automatic.

## Validation
- Tests include synthetic 33,601-entry streaming parse and channel/movie identity cases; no real URL credentials in tests.
- APK published at https://vortex-tv-android-build.onrender.com/VortexTV-Android-Debug.apk only when Render LIVE.
- Device-side audio/video/remote/codec and source availability remain to be tested.
