#!/usr/bin/env python3
"""BLINK official-domain and crawlability regression guard.

Legacy filename retained because multiple CI workflows invoke it.
This checks repository artifacts, NOT external DNS, search ranking, or Search Console.
"""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "web"
SITE = "https://www.blink.com.ng"


def check(condition, message):
    if not condition:
        raise SystemExit("BLINK domain / SEO configuration error: " + message)


def read(name):
    return (WEB / name).read_text(encoding="utf-8")


check(read("CNAME").strip() == "www.blink.com.ng", "Pages CNAME")
config = read("config.js")
check(f'siteUrl: "{SITE}"' in config, "web client siteUrl")
index = read("index.html")
check(f'<link rel="canonical" href="{SITE}/"' in index, "homepage canonical")
check(f'<meta property="og:url" content="{SITE}/"' in index, "homepage og:url")
check(f"{SITE}/blink-logo-v2.png" in index, "absolute social image")
check('<meta name="robots" content="index,follow,max-image-preview:large"' in index, "homepage indexability")
check("<h1" in index and "campus community" in index.lower(), "crawlable BLINK brand content")
check("<title>BLINK" in index, "descriptive brand title")
check('<meta name="description"' in index, "description")

match = re.search(r'<script type="application/ld\+json">\s*([\s\S]*?)\s*</script>', index)
check(match is not None, "homepage structured data")
data = json.loads(match.group(1)) if match else {}
graph = data.get("@graph", [])
types = {item.get("@type") for item in graph}
check({"Organization", "WebSite"} <= types, "Organization and WebSite schema types")
check(all(item.get("url") == SITE + "/" for item in graph), "schema URLs")
check(all(item.get("name") == "BLINK" for item in graph), "schema brand name")
privacy = read("privacy/index.html")
check(f'<link rel="canonical" href="{SITE}/privacy/"' in privacy, "privacy canonical")
check(f'<meta property="og:url" content="{SITE}/privacy/"' in privacy, "privacy social URL")
check(f"{SITE}/blink-logo-v2.png" in privacy, "privacy image")
sitemap = ET.fromstring(read("sitemap.xml"))
locs = [x.text for x in sitemap.iter() if x.tag.endswith("loc")]
check(locs == [SITE + "/", SITE + "/about/", SITE + "/privacy/"], "sitemap lists only public canonical pages")
about = read("about/index.html")
check(f'<link rel="canonical" href="{SITE}/about/"' in about, "about canonical URL")
check(f'<meta property="og:url" content="{SITE}/about/"' in about, "about sharing URL")
check("About BLINK" in about and "Nigeria" in about, "about page describes the brand")
check('href="./about/"' in index, "homepage links to about")
check("About BLINK Nigeria" in read("app.js"), "interactive landing links to about")
check("Sitemap: " + SITE + "/sitemap.xml" in read("robots.txt"), "robots sitemap URL")
check(("glimmar-code.github.io/Blinkng" not in index + privacy + read("robots.txt") + read("sitemap.xml")), "no obsolete indexable GitHub Pages URLs")

app = read("app.js")
check("location.hostname.endsWith('github.io') ? '/Blinkng' : ''" in app, "legacy SPA route support")
check("'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk'" in app, "latest-APK alias")
check("redirect_to:`${redirectBase}/`" in app, "OAuth redirect base wiring")
fallback = read("404.html")
check("location.hostname.endsWith('github.io')?'/Blinkng/':'/'" in fallback, "SPA fallback routing")
check("blink-web-v16-brand-about" in read("sw.js"), "new service worker version")
workflow = (ROOT / ".github/workflows/deploy-web-preview.yml").read_text(encoding="utf-8")
check('tr -d' in workflow and 'web/CNAME' in workflow, "deployment verifies CNAME")
check("scripts/check_github_pages_site.py" in workflow, "deployment validates site")
print("BLINK domain, schema, sitemap, robots, metadata and SPA static guards passed.")
