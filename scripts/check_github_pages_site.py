#!/usr/bin/env python3
"""Regression guard for BLINK on the purchased GO54 custom domain.

BLINK uses its production custom domain for web links and APK downloads
remain on the verified latest GitHub Release alias.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
SITE = "https://blink.com.ng"


def check(condition, message):
    if not condition:
        raise SystemExit("GitHub Pages website configuration error: " + message)


def read(name):
    return (WEB / name).read_text(encoding="utf-8")


check(read("CNAME").strip() == "blink.com.ng", "CNAME must match the verified GO54 domain")
config = read("config.js")
check(f'siteUrl: "{SITE}"' in config, "client siteUrl must use the GitHub Pages project path")
check("glimmar-code.github.io" not in config, "OAuth must use the purchased domain")

index = read("index.html")
check(f'<link rel="canonical" href="{SITE}/"' in index, "home canonical URL")
check(f'<meta property="og:url" content="{SITE}/"' in index, "home sharing URL")
check(f"{SITE}/blink-logo.png" in index, "absolute social preview image")

privacy = read("privacy/index.html")
check(f'<link rel="canonical" href="{SITE}/privacy/"' in privacy, "privacy canonical URL")
check(f"{SITE}/blink-logo-v2.png" in privacy, "privacy sharing image")

sitemap = ET.fromstring(read("sitemap.xml"))
locs = [element.text for element in sitemap.iter() if element.tag.endswith("loc")]
check(locs == [SITE + "/", SITE + "/privacy/"], "sitemap must list Pages homepage and privacy")
check("Sitemap: " + SITE + "/sitemap.xml" in read("robots.txt"), "sitemap index URL")

app = read("app.js")
check("location.hostname.endsWith('github.io') ? '/Blinkng' : ''" in app, "SPA routes must preserve the project prefix")
check("'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk'" in app, "all app download links must use the latest APK alias")
check("redirect_to:`${redirectBase}/`" in app, "OAuth redirects must resolve against active siteUrl")

fallback = read("404.html")
check("location.hostname.endsWith('github.io')?'/Blinkng/':'/'" in fallback, "SPA deep-link fallback must preserve /Blinkng")

sw = read("sw.js")
check("blink-web-v15-go54-canonical-domain" in sw, "service worker cache must refresh for the domain cutover")

workflow = (ROOT / ".github/workflows/deploy-web-preview.yml").read_text(encoding="utf-8")
check("web/CNAME" in workflow and "blink.com.ng" in workflow, "deploy must verify the live custom domain")
check("scripts/check_github_pages_site.py" in workflow, "deployment must validate Pages project setup")

ads = read("app-ads.txt").strip()
check(ads == "google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0",
      "AdMob publisher authorization must match the approved account")
check((WEB / ".nojekyll").exists(), "Pages must serve .well-known/assetlinks.json")
import json
links = json.loads(read(".well-known/assetlinks.json"))
check(any(item.get("target", {}).get("package_name") == "com.aistudio.blink.appvtwo"
          for item in links), "Android App Links must retain BLINK package association")
print("BLINK GO54 domain, privacy, SEO, ads, App Links and APK link checks passed.")
