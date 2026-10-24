# BLINK owner-approved logo rollout — staging checklist

**Approved source:** owner's uploaded artwork (black rounded-square, white lowercase b, electric-blue dot), unchanged. The uploaded file has a misleading `.png` extension but its bytes are JPEG. **SHA-256:** `9c1b905cbbdf904462d909b799c4c2f6927b665e59bc63c2c77ce5aba79fff68`.

## Safety and fidelity

- Keep the original file bytes, never redraw the b or replace it with a font.
- Use image resampling for required icon sizes; **only** Android's monochrome small-notification icon may omit the blue color, as required by that OS.
- Rounded app icons are masked by Android/iOS; an exact unmasked icon outline cannot be guaranteed on all launchers. Match the glyph and circle as closely as those system masks allow.
- No production/main change until screenshots, builds and real runtime checks pass.
- Hosted vendor dashboards, published stores, search indexing and users' installed APKs cannot be updated just by changing repository files.

## Installation

1. From the provided **BLINK_Official_Logo_Pack.zip**, extract `BLINK_Official_Logo_Pack/` outside the repository.
2. On a feature branch targeting `Testlab`, run `python scripts/install_owner_approved_blink_logo.py /path/to/BLINK_Official_Logo_Pack --dry-run`.
3. Review the intended destinations, then run the same command without `--dry-run`.
4. Inspect all binary replacements with `git diff --stat`, commit changed files, open a PR into `Testlab`.
5. Run Android build / launcher / splash / notification screenshots; Windows desktop launch / taskbar / installer screenshots; iOS signing and TestFlight icon audit; web PWA, favicon, OG, SW cache and install audit. Check 100%–200% font scaling where the logo is placed in app UI.
6. Inspect on actual light/dark mode screens and verify every checklist row below.
7. Merge into main only with passing required checks and manual platform screenshot approval; publish **one** signed APK and verify its actual download.

## Resource mapping

| Surface | Shared assets / reference | Validation |
| --- | --- | --- |
| Android launcher, home screen, app drawer, Settings app icon | `app/src/main/res/mipmap-*/ic_launcher*.png`, adaptive launcher XML, Manifest | Installed Android launcher masks, icon at 48–192px |
| Android splash, login, header, app menus | `drawable/blink_logo_foreground.png`, `drawable/app_icon.png`; inspect other hardcoded logos | Screenshots on phone |
| Android small status icon and push/message/payment alerts | `drawable-*/ic_stat_blink.png` | Must be white-only on transparent BG |
| iOS app icon, Spotlight, App Library, App Store asset | `ios/.../AppIcon.appiconset` | Xcode archive and Store review |
| Windows desktop logo and icon | `desktopApp/src/main/resources/blink-logo.png` / `BLINK.ico` | Desktop UI + installer/taskbar |
| Website navigation, footer, auth, splash, offline, messages, Store, Connect, Market | Shared `web/blink-logo-v2.png` / `blink-logo.png` references | Desktop/mobile audits; grep hardcoded b or alternate logos |
| Web favicon, bookmarks, Apple touch, PWA shortcut/install, social previews | `web/favicon.*`, `apple-touch-icon.png`, `pwa-*.png`, `maskable-512.png`, `og-blink.png` | Browser tabs, install, social crawler |
| Email headers: welcome, reset, verification, security, notifications, receipts | Public `https://blink.com.ng/blink-logo-v2.png` URL with explicit HTML image width | Brevo template previews, Gmail, Outlook |
| Paystack checkout, payment pages, receipts, subscription | Same published logo URL or uploaded official image | Paystack dashboard branding (manual), staging checkout |
| Play Console, Apple App Store Connect, TestFlight, store screenshots | Store assets from `app_store/`, `ios/`, `social/` in exported pack | Manual console upload and review |
| BLINK LIFE, Market, Connect, Boost, Coins, AI, official announcements | Brand assets from `game/`, `marketplace/`, `payments/`, `ui/` | Screen-by-screen inventory / runtime |
| Search engine, Open Graph, X/WhatsApp/Facebook previews | Website icon + social preview, organization schema | Crawl preview / indexing |
| Admin pages, moderation notices, invoices, report PDFs | Same source master; per-template bindings | Template tests and actual sends |
| 404, offline/maintenance, update center and install UI | Web app logo or native drawable where appropriate | Screenshot and update flow |

## Explicit limitations of this installer

- A single source logo can cover many UI placements via shared assets, but **does not prove every one of the previously enumerated ~120 placements has been audited**.
- Does not update external Paystack/Brevo/Google Play/App Store consoles, existing messages already delivered, or installed APKs.
- Does not fabricate unsupported UI placements just to display a logo.
- Email HTML, Android notification handlers, Xcode builds and Windows installers still need real integration tests before release.
- The PR carrying this script alone is a **preparation PR**, not the completed logo rollout. Do not merge it into production as evidence that assets have been installed.
