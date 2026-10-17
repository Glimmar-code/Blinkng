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
check("<title>BLINK Nigeria | Social &amp; Campus Community App</title>" in index, "distinctive homepage brand title")
check("<h1" in index and "BLINK Nigeria" in index, "crawlable homepage identifies BLINK Nigeria")
check("BLINK Nigeria | Social & Campus Community App" in read("app.js"), "runtime title matches official BLINK Nigeria brand")
check("BLINK Nigeria</span>" in read("app.js"), "interactive hero heading distinguishes BLINK Nigeria")
check('<meta name="description"' in index, "description")

match = re.search(r'<script type="application/ld\+json">\s*([\s\S]*?)\s*</script>', index)
check(match is not None, "homepage structured data")
data = json.loads(match.group(1)) if match else {}
graph = data.get("@graph", [])
types = {item.get("@type") for item in graph}
check({"Organization", "WebSite"} <= types, "Organization and WebSite schema types")
check(all(item.get("url") == SITE + "/" for item in graph), "schema URLs")
check(all(item.get("name") == "BLINK" for item in graph), "schema brand name")
WHATSAPP = "https://whatsapp.com/channel/0029VbBU5Hr7tkj5mIvJNH1u"
organization = next(item for item in graph if item.get("@type") == "Organization")
check(organization.get("sameAs") == [WHATSAPP], "official WhatsApp social link in Organization schema")
check(index.count(WHATSAPP) == 2, "homepage links WhatsApp in schema and crawlable navigation")
check(WHATSAPP in read("app.js"), "interactive landing includes WhatsApp channel link")
for page in ("about/index.html", "download/index.html", "features/index.html", "faq/index.html", "privacy/index.html"):
    check(WHATSAPP in read(page), f"WhatsApp channel link present in {page}")
    check('target="_blank" rel="noopener noreferrer"' in read(page), f"safe external-link attributes in {page}")
privacy = read("privacy/index.html")
check(f'<link rel="canonical" href="{SITE}/privacy/"' in privacy, "privacy canonical")
check(f'<meta property="og:url" content="{SITE}/privacy/"' in privacy, "privacy social URL")
check(f"{SITE}/blink-logo-v2.png" in privacy, "privacy image")
sitemap = ET.fromstring(read("sitemap.xml"))
locs = [x.text for x in sitemap.iter() if x.tag.endswith("loc")]
check(locs == [SITE + "/", SITE + "/about/", SITE + "/features/", SITE + "/download/", SITE + "/faq/", SITE + "/privacy/"], "sitemap lists only public canonical pages")
for slug in ("features", "download", "faq"):
    page = read(f"{slug}/index.html")
    check(f'<link rel="canonical" href="{SITE}/{slug}/">' in page, f"{slug} canonical URL")
    check(f'<meta property="og:url" content="{SITE}/{slug}/">' in page, f"{slug} sharing URL")
    check('<meta name="description"' in page and '<h1>' in page, f"{slug} informative page")
    check('href="../privacy/"' in page, f"{slug} links to privacy policy")
    check('href="../features/"' in page and 'href="../download/"' in page and 'href="../faq/"' in page, f"{slug} cross-links")
check("https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk" in read("download/index.html"), "official APK download")
download_schema_scripts = re.findall(r'<script type="application/ld\+json">\s*([\s\S]*?)\s*</script>', read("download/index.html"))
check(len(download_schema_scripts) == 2, "download page WebPage and MobileApplication structured data")
download_json_objects = [json.loads(script) for script in download_schema_scripts]
download_schema = next((item for item in download_json_objects if item.get("@type") == "MobileApplication"), {})
check(download_schema.get("name") == "BLINK", "app structured data brand name")
check(download_schema.get("operatingSystem") == "Android", "app structured data platform")
check(download_schema.get("downloadUrl") == "https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk", "app structured data uses official APK")
check("Github is not" not in read("download/index.html"), "no false Google Play claims")
check(read("faq/index.html").count("<details ") >= 7, "FAQ contains substantial readable answers")
check('href="./features/"' in index and 'href="./download/"' in index and 'href="./faq/"' in index, "homepage contains crawlable discovery links")
check('About BLINK Nigeria' in read("app.js") and "Download BLINK App" in read("app.js"), "dynamic landing page has public discovery links")
check('href="../features/"' in read("about/index.html"), "about page has links to new pages")
about = read("about/index.html")
check(f'<link rel="canonical" href="{SITE}/about/"' in about, "about canonical URL")
check(f'<meta property="og:url" content="{SITE}/about/"' in about, "about sharing URL")
check("About BLINK" in about and "Nigeria" in about, "about page describes the brand")
check('href="./about/"' in index, "homepage links to about")
check("About BLINK Nigeria" in read("app.js"), "interactive landing links to about")
check('<main class="auth-wrap" aria-label="BLINK Nigeria">' in app, "homepage provides accessible main landmark")
check('>WhatsApp Channel</a></p></div></main>`' in app, "homepage main landmark has a closing tag")
check("Sitemap: " + SITE + "/sitemap.xml" in read("robots.txt"), "robots sitemap URL")
check(("glimmar-code.github.io/Blinkng" not in index + privacy + read("robots.txt") + read("sitemap.xml")), "no obsolete indexable GitHub Pages URLs")

app = read("app.js")
check("location.hostname.endsWith('github.io') ? '/Blinkng' : ''" in app, "legacy SPA route support")
check("'https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk'" in app, "latest-APK alias")
check("redirect_to:`${redirectBase}/`" in app, "OAuth redirect base wiring")
fallback = read("404.html")
check("location.hostname.endsWith('github.io')?'/Blinkng/':'/'" in fallback, "SPA fallback routing")
check("blink-web-v19-brand-search" in read("sw.js"), "new service worker version")
workflow = (ROOT / ".github/workflows/deploy-web-preview.yml").read_text(encoding="utf-8")
check('tr -d' in workflow and 'web/CNAME' in workflow, "deployment verifies CNAME")
check("scripts/check_github_pages_site.py" in workflow, "deployment validates site")
print("BLINK domain, schema, sitemap, robots, metadata and SPA static guards passed.")
