"""Assemble pinned local engine artifacts and documented public design assets."""
from pathlib import Path
import hashlib, json, shutil, subprocess, urllib.request, urllib.parse, re, struct
import xml.etree.ElementTree as ET
from PIL import Image
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parent
WORK = ROOT.parent
MAIN = ROOT / 'app/src/main'
ASSETS = MAIN / 'assets'
RES = MAIN / 'res'
PROVENANCE = ROOT / 'asset-provenance'
for p in (ASSETS, RES / 'drawable', RES / 'font', PROVENANCE): p.mkdir(parents=True, exist_ok=True)

def fetch(url, path):
    if not path.exists():
        request = urllib.request.Request(url, headers={'User-Agent': 'Pianyu-build/0.1'})
        path.write_bytes(urllib.request.urlopen(request, timeout=90).read())
    return path.read_bytes()

# Profiles/constants from the upstream-only controlled experiment, Android SPIR-V from the phone-tested build.
shutil.copytree(WORK / 'film-practice/resources', ASSETS / 'engine', dirs_exist_ok=True)
shutil.copytree(WORK / 'native-mobile/resources/vulkan', ASSETS / 'engine/vulkan', dirs_exist_ok=True)
# Android's optical pipeline does not enter the Apple-only minilab branch. Its
# empty index is retained; unreachable tables are omitted without changing output.
for table in (ASSETS / 'engine/minilab').glob('*.lut3d'):
    table.unlink()
# Output is explicitly sRGB. Retain its baked setup rather than unrelated colour spaces.
for table in (ASSETS / 'engine/cam16').glob('*.cam16'):
    data = table.read_bytes()
    magic = b'SPKCAM16BAKE'
    assert data.startswith(magic)
    length = struct.unpack_from('<Q', data, len(magic) + 8)[0]
    start = len(magic) + 16
    if data[start:start + length].decode() != 'sRGB':
        table.unlink()
libs = MAIN / 'jniLibs/arm64-v8a'
libs.mkdir(parents=True, exist_ok=True)
shutil.copy2(WORK / 'native-mobile/spektralab_engine.android-overlay.so', libs / 'libpianyu_core.so')
ndk = Path('C:/android_sdk/ndk/28.2.13676358')
subprocess.run([str(ndk / 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'), '--strip-unneeded', str(libs / 'libpianyu_core.so')], check=True)
# libc++_shared is packaged automatically by externalNativeBuild with ANDROID_STL=c++_shared.
image = Image.open(ROOT / 'asset-provenance/demo-original.png').convert('RGB').resize((768, 512), Image.Resampling.LANCZOS)
image.save(ASSETS / 'demo.jpg', quality=94, subsampling=0)

icons = {
    'photo': ('image', 'photo_library'), 'compare': ('image', 'compare'),
    'expand': ('navigation', 'fullscreen'), 'crop': ('image', 'crop'),
    'adjust': ('image', 'tune'), 'back': ('navigation', 'arrow_back'),
    'undo': ('content', 'undo'), 'redo': ('content', 'redo'),
    'close': ('navigation', 'close'), 'export': ('file', 'file_upload'),
    'check': ('navigation', 'check'),
}
sources = []
for target, (category, name) in icons.items():
    url = f'https://raw.githubusercontent.com/google/material-design-icons/master/src/{category}/{name}/materialiconsoutlined/24px.svg'
    original = PROVENANCE / f'{target}.svg'
    svg = ET.fromstring(fetch(url, original))
    paths = [p for p in svg.iter() if p.tag.endswith('path') and p.get('fill') != 'none' and p.get('d')]
    assert paths, url
    # Exact conversion of library path data; no hand-drawn replacement icons.
    body = '\n'.join(f'    <path android:fillColor="#FFF4EBDD" android:pathData="{p.get("d")}" />' for p in paths)
    (RES / 'drawable' / f'ic_{target}.xml').write_text(
        '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n' + body + '\n</vector>\n', encoding='utf-8')
    sources.append({'file': f'ic_{target}.xml', 'url': url, 'source_sha256': hashlib.sha256(original.read_bytes()).hexdigest()})
fetch('https://raw.githubusercontent.com/google/material-design-icons/master/LICENSE', PROVENANCE / 'Material-Icons-LICENSE.txt')

text = '片屿PIANYU日常暖色清新绿意浓郁调电影光夜晕自然正彩风景克制复古柔和黑白纪实调整裁切导出'
css_url = 'https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@600&text=' + urllib.parse.quote(text)
css = fetch(css_url, PROVENANCE / 'serif.css').decode()
font_url = re.search(r'url\((https://[^)]+)\)', css).group(1)
font_path = PROVENANCE / 'serif-subset.woff2'
fetch(font_url, font_path)
font = TTFont(font_path)
font.flavor = None
font.save(RES / 'font/editor_serif.ttf')
fetch('https://raw.githubusercontent.com/google/fonts/main/ofl/notoserifsc/OFL.txt', PROVENANCE / 'Noto-Serif-SC-OFL.txt')
sources.append({'file': 'editor_serif.ttf', 'css': css_url, 'url': font_url, 'subset': text})
(PROVENANCE / 'sources.json').write_text(json.dumps(sources, indent=2, ensure_ascii=False), encoding='utf-8')

hashes = {str(p.relative_to(ASSETS / 'engine')).replace('\\', '/'): hashlib.sha256(p.read_bytes()).hexdigest()
    for p in sorted((ASSETS / 'engine').rglob('*')) if p.is_file()}
manifest = json.dumps(hashes, sort_keys=True, indent=2)
(ASSETS / 'engine-manifest.json').write_text(manifest, encoding='utf-8')
(ASSETS / 'engine-version.txt').write_text(hashlib.sha256(manifest.encode()).hexdigest(), encoding='utf-8')
print(f'Prepared {len(hashes)} engine assets; core={ (libs / "libpianyu_core.so").stat().st_size } bytes')
