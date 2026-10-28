#!/usr/bin/env python3
"""Structural acceptance gate. Presence of native code != playback verification."""
import sys, zipfile
from pathlib import Path
if len(sys.argv) != 2:
    raise SystemExit('Uso: python tools/verify_apk.py app/build/outputs/apk/debug/app-debug.apk')
p = Path(sys.argv[1]);
if not p.is_file():
    raise SystemExit(f'ERRO: APK não encontrado: {p}')
with zipfile.ZipFile(p) as z:
    names=set(z.namelist())
    dex=[z.read(n) for n in sorted(names) if n.startswith('classes') and n.endswith('.dex')]
    all_dex=b''.join(dex)
    checks={
        'Manifesto Android': 'AndroidManifest.xml' in names,
        'Recursos Android compilados': 'resources.arsc' in names,
        'Código DEX não-trivial': len(all_dex)>250_000,
        'Player Android nativo': b'L' + b'os/vortex/tv/integration/VortexPlayerActivity;' in all_dex,
        'ExoPlayer Media3': b'Landroidx/media3/exoplayer/ExoPlayer;' in all_dex,
        'M3U parser': b'M3uPlaylistParser' in all_dex,
        'Guia XMLTV': b'XmltvGuideParser' in all_dex,
    }
for label, ok in checks.items():
    print(('OK' if ok else 'FALHA') + ' | ' + label)
if not all(checks.values()):
    raise SystemExit('REPROVADO: Não distribuir como APK nativo do Vortex TV.')
print('Estrutura compilada presente. Ainda são necessários testes reais de reprodução em TV e celular.')
