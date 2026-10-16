#!/usr/bin/env python3
"""Regression guard for the BLINK custom-domain website.

The APK stays on GitHub Releases. Live DNS, HTTPS, and dashboard settings
are checked separately before promotion to production.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
SITE = "https://www.blink.com.ng"


def check(condition, message):
    if not condition:
        raise SystemExit("GitHub Pages website configuration error: " + message)


def read(name):
    return (WEB / name).read_text(encoding="utf-8")


check(read("CNAME").strip() == "www.blink.com.ng", "CNAME must match the canonical custom domain")
config = read("config.js")
check(f'siteUrl: "{SITE}"' in config, "client siteUrl must use the canonical custom domain")
check("glimmar-code.github.io/Blinkng" not in config, "temporary site URL still in client config")

index = read("index.html")
check(f'<link rel="canonical" href="{SITE}/"' in index, "home canonical URL")
check(f'<meta property="og:url" content="{SITE}/"' in index, "home sharing URL")
check(f"{SITE}/blink-logo.png" in index, "absolute social preview image")

privacy = read("privacy/index.html")
check(f'<link rel="canonical" href="{SITE}/privacy/"' in privacy, "privacy canonical URL")
check(f"{SITE}/blink-logo-v2.png" in privacy, "privacy sharing image")

sitemap = ET.fromstring(read("sitemap.xml"))
locs = [element.text for element in sitemap.iter() if element.tag.endswith("loc")]
check(locs == [SITE + "/", SITE + "/privacy/"], "sitemap must list homepage and privacy")
check("Sitemap: " + SITE + "/sitemap.xml" in read("robots.txt"), "sitemap index URL")

app = read("app.js")
check("location.hostname.endsWith('github.io') ? '/Blinkng' : ''" in app, "SPA routes must preserve the project prefix")
check("'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk'" in app, "all app download links must use the latest APK alias")
check("redirect_to:`${redirectBase}/`" in app, "OAuth redirects must resolve against active siteUrl")

fallback = read("404.html")
check("location.hostname.endsWith('github.io')?'/Blinkng/':'/'" in fallback, "SPA deep-link fallback must preserve /Blinkng")

sw = read("sw.js")
check("blink-web-v15-blink-com-ng-primary" in sw, "web service worker version must be bumped for URL migration")

workflow = (ROOT / ".github/workflows/deploy-web-preview.yml").read_text(encoding="utf-8")
check("web/CNAME" in workflow and "www.blink.com.ng" in workflow, "deploy must assert the canonical custom domain")
check("scripts/check_github_pages_site.py" in workflow, "deployment must validate Pages project setup")

import json

ads = read("app-ads.txt").strip()
check(ads == "google.com, pub-9152580730716304, DIRECT, f08c47fec0942fa0", "app-ads.txt publisher authorization")
links = json.loads(read(".well-known/assetlinks.json"))
check(len(links) == 1 and links[0]["target"]["package_name"] == "com.aistudio.blink.appvtwo", "Android App Links package mismatch")
check(bool(links[0]["target"]["sha256_cert_fingerprints"]), "Android App Links signing fingerprint missing")
check("glimmar-code.github.io/Blinkng" not in index + privacy + read("sitemap.xml") + read("robots.txt"), "temporary domain metadata remains")
print("BLINK custom domain, AdMob, App Links metadata, SPA and latest APK static checks passed.")
