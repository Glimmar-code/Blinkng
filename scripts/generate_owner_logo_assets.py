#!/usr/bin/env python3
"""Build BLINK platform assets from the owner's staged BLINK artwork.

This is a visual resampling of the owner-approved logo, never a replacement
font or newly invented logo. The optimized WebP transport keeps its exact
black/white/blue design; the source JPEG remains in the user's original
attachment and separately distributed asset package.
"""
from __future__ import annotations
import hashlib
import io
import json
import shutil
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"branding/approved/BLINK-logo-source.webp"
EXPECTED="3f69cbbd8d662c17f2be1962ad981bb10e853f9228d5d25ad5d111550040279d"
if hashlib.sha256(SOURCE.read_bytes()).hexdigest()!=EXPECTED:
    raise SystemExit("Approved BLINK logo checksum mismatch; refusing asset rollout")
image=Image.open(SOURCE).convert("RGB")
assert image.size==(512,512), "Unexpected logo source size"
catalogue=[]
def save(path, size, *, transparent=False, content=None):
    target=ROOT/path
    target.parent.mkdir(parents=True,exist_ok=True)
    if content is None:
        content=image.resize(size,Image.Resampling.LANCZOS)
    if transparent:
        content=content.convert("RGBA")
    content.save(target,format="PNG",optimize=True)
    catalogue.append({"path":str(path),"width":size[0],"height":size[1],
        "sha256":hashlib.sha256(target.read_bytes()).hexdigest()})
    return target
def both(src,dst):
    target=ROOT/dst;target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(ROOT/src,target)

# Android: app icon, legacy + adaptive launcher, notification silhouette.
for name in ("app_icon.png","blink_logo_foreground.png"):
    save(f"app/src/main/res/drawable/{name}",(512,512))
for density,pixel in {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}.items():
    for name in ("ic_launcher.png","ic_launcher_round.png"):
        save(f"app/src/main/res/mipmap-{density}/{name}",(pixel,pixel))
for density,pixel in {"mdpi":108,"hdpi":162,"xhdpi":216,"xxhdpi":324,"xxxhdpi":432}.items():
    save(f"app/src/main/res/mipmap-{density}/ic_launcher_foreground.png",(pixel,pixel))
# White b + central dot, as required by Android status-bar notification rules.
mask=Image.new("L",(512,512),0)
raw=image.load();o=mask.load()
for y in range(108,405):
    for x in range(130,378):
        r,g,b=raw[x,y]
        if min(r,g,b)>=200 or (b>r*1.25 and b>g*1.10 and r<190):
            o[x,y]=255
for density,pixel in {"mdpi":24,"hdpi":36,"xhdpi":48,"xxhdpi":72,"xxxhdpi":96}.items():
    alpha=mask.resize((pixel,pixel),Image.Resampling.LANCZOS)
    small=Image.new("RGBA",(pixel,pixel),(255,255,255,0))
    small.putalpha(alpha)
    save(f"app/src/main/res/drawable-{density}/ic_stat_blink.png",(pixel,pixel),content=small)
for n in (256,512,1024):
    save(f"branding/approved/exports/android/app-icon-{n}.png",(n,n))

# Website, favicons, PWA, social and search preview.
for n in (16,32,48,64,96,128,180,192,256,384,512,1024):
    save(f"branding/approved/exports/web/icon-{n}.png",(n,n))
for name,n in {"blink-logo.png":512,"blink-logo-v2.png":512,"apple-touch-icon.png":180,
                "favicon-32.png":32,"pwa-192.png":192,"pwa-512.png":512,
                "maskable-512.png":512}.items():
    save(f"web/{name}",(n,n))
ico=ROOT/"web/favicon.ico"
image.save(ico,format="ICO",sizes=[(16,16),(32,32),(48,48),(64,64),(256,256)])
for name,sizes in [("BLINK.ico",(16,24,32,48,64,128,256))]:
    target=ROOT/"desktopApp/src/main/resources"/name
    image.save(target,format="ICO",sizes=[(n,n) for n in sizes])
save("desktopApp/src/main/resources/blink-logo.png",(512,512))

# iOS, store artwork, widget and marketing/library assets.
save("ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png",(1024,1024))
contents={"images":[{"idiom":"universal","platform":"ios","size":"1024x1024",
                    "filename":"AppIcon-1024.png"}],"info":{"author":"xcode","version":1}}
(ROOT/"ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/Contents.json").write_text(
    json.dumps(contents,indent=2)+"\n",encoding="utf-8")
for group,sizes in {"ui":(24,32,48,64,72,96,128,200,256,400,512),
                    "email":(48,64,128,200,256,400),
                    "payments":(48,128,200,256,512),
                    "marketplace":(48,128,256,512),
                    "game":(64,128,256,512),
                    "app_store":(512,1024),
                    "ios":(60,120,180),
                    "windows":(16,24,32,40,44,48,64,96,128,150,256,310,512)}.items():
    for n in sizes:
        save(f"branding/approved/exports/{group}/BLINK-{n}.png",(n,n))
for dest,dim in (("web/og-blink.png",(1200,630)),
                 ("branding/approved/exports/app_store/google-feature-1024x500.png",(1024,500)),
                 ("branding/approved/exports/website/hero-1200x600.png",(1200,600))):
    w,h=dim
    canvas=Image.new("RGB",dim,"#08080B")
    s=int(h*.77)
    canvas.paste(image.resize((s,s),Image.Resampling.LANCZOS),(int(w*.12),(h-s)//2))
    painter=ImageDraw.Draw(canvas)
    font=ImageFont.load_default()
    for fp in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
               "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"):
        if Path(fp).exists():
            font=ImageFont.truetype(fp,max(38,int(h*.14)));break
    painter.text((int(w*.53),int(h*.40)),"BLINK",font=font,fill="#FFFFFF")
    save(dest,dim,content=canvas)

# Existing web and native shared logo references pick up the new artwork.
def replace(path, before, after):
    target=ROOT/path
    source=target.read_text(encoding="utf-8")
    if before not in source and after not in source:
        raise SystemExit(f"Unexpected logo integration point in {path}")
    if before in source:
        target.write_text(source.replace(before,after),encoding="utf-8")
replace("app/src/main/AndroidManifest.xml",'android:icon="@drawable/app_icon"',
        'android:icon="@mipmap/ic_launcher"')
replace("app/src/main/AndroidManifest.xml",'android:roundIcon="@drawable/app_icon"',
        'android:roundIcon="@mipmap/ic_launcher_round"')
replace("app/src/main/res/drawable/ic_launcher_foreground.xml",
        "@drawable/blink_logo_foreground","@mipmap/ic_launcher_foreground")
replace("web/index.html",
        '<link rel="icon" type="image/png" sizes="512x512" href="./blink-logo-v2.png" />',
        '<link rel="icon" type="image/png" sizes="32x32" href="./favicon-32.png" />\n'
        '  <link rel="shortcut icon" href="./favicon.ico" />')
replace("web/index.html",
        '<link rel="apple-touch-icon" href="./blink-logo-v2.png" />',
        '<link rel="apple-touch-icon" sizes="180x180" href="./apple-touch-icon.png" />')
replace("web/index.html","https://www.blink.com.ng/blink-logo-v2.png",
        "https://www.blink.com.ng/og-blink.png")
replace("web/index.html",'<meta name="twitter:card" content="summary" />',
        '<meta name="twitter:card" content="summary_large_image" />')
manifestfile=ROOT/"web/manifest.webmanifest"
manifest=json.loads(manifestfile.read_text())
manifest["icons"]=[{"src":"pwa-192.png","sizes":"192x192","type":"image/png","purpose":"any"},
                   {"src":"pwa-512.png","sizes":"512x512","type":"image/png","purpose":"any"},
                   {"src":"maskable-512.png","sizes":"512x512","type":"image/png","purpose":"maskable"}]
manifestfile.write_text(json.dumps(manifest,indent=2)+"\n",encoding="utf-8")
replace("web/sw.js","blink-web-v10-logo-display-fix","blink-web-v12-owner-approved-logo")
if "  './favicon.ico'," not in (ROOT/"web/sw.js").read_text():
    replace("web/sw.js","  './blink-logo-v2.png',",
            "  './blink-logo-v2.png',\n  './favicon.ico',\n  './favicon-32.png',"
            "\n  './apple-touch-icon.png',\n  './pwa-192.png',\n  './pwa-512.png',"
            "\n  './maskable-512.png',\n  './og-blink.png',")
# iOS release helper must verify generated branded icon (see sha below).
expected_ios="9a051cdde15465c117250fa67347535ba862ff5257d81f64219a0b86cc332785"
icon=ROOT/"ios/BLINKiOS/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
if hashlib.sha256(icon.read_bytes()).hexdigest()!=expected_ios:
    raise SystemExit("iOS asset is not the expected approved logo")
replace("scripts/prepare_ios_icon.sh",
        "cc986e2faabb39ebdfc41e349a4b277bba1d872fdf4f50ea39996c7ce94a7ab8",
        expected_ios)
(ROOT/"branding/approved/exports/manifest.json").write_text(json.dumps(
    {"approved_derived_source_sha256":EXPECTED,"generated_assets":catalogue},indent=2)+"\n")
print(f"Staged {len(catalogue)} logo assets from verified owner-approved design")
