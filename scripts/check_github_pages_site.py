#!/usr/bin/env python3
"""Regression guard for the temporary BLINK GitHub Pages project website.

The downloaded APK stays on GitHub Releases /latest/download; the paid domain
and Android manifest/App Links configuration are handled in a later rollout.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
SITE = "https://glimmar-code.github.io/Blinkng"


def check(condition, message):
    if not condition:
        raise SystemExit("GitHub Pages website configuration error: " + message)


def read(name):
    return (WEB / name).read_text(encoding="utf-8")


check(not (WEB / "CNAME").exists(), "CNAME must be absent until a domain is purchased")
config = read("config.js")
check(f'siteUrl: "{SITE}"' in config, "client siteUrl must use the GitHub Pages project path")
check("www.blink.com.ng" not in config, "do not redirect sign-in to an unowned custom domain")

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
check("blink-web-v14-github-pages-primary" in sw, "web service worker version must be bumped for URL migration")

workflow = (ROOT / ".github/workflows/deploy-web-preview.yml").read_text(encoding="utf-8")
check("web/CNAME" in workflow and "test ! -e web/CNAME" in workflow, "deploy must block custom domains")
check("scripts/check_github_pages_site.py" in workflow, "deployment must validate Pages project setup")

print("BLINK GitHub Pages project URL, SPA navigation and latest APK regression checks passed.")
