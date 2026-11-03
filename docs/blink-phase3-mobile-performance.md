# BLINK website Phase 3 — mobile performance verification

Domain: https://blink.com.ng/ (registered through Go54).

## Zero-tracking Core Web Vitals diagnostics

Diagnostics do not run on ordinary visits. To enable **local-only** measurement on a device you control:

1. Open `https://blink.com.ng/?blink_perf=1` on Chrome.
2. Open Chrome DevTools → Console (on desktop/remote Android debugging) and wait 12 seconds. Navigate or interact normally.
3. Look for `[BLINK performance diagnostics — local only]`. It reports approximate FCP, LCP, CLS, INP, TTFB and transferred asset KB. Not all metrics appear on all devices.
4. Repeat once with the browser cache cleared and once with the service worker/cache warm. Use Network → Slow 4G + mobile emulation to find regressions.

Only local browser timings are printed to the local console. **No cookies, visitor IDs, page URLs, events or timing data are transmitted or stored** by the diagnostics module.

## Mobile asset budgets (uncompressed source)

The Testlab CI guard checks:
- Main web app JavaScript <= 160 KB, parity JavaScript <= 115 KB, discovery script <= 20 KB.
- Main CSS <= 35 KB, landing CSS <= 25 KB, parity CSS <= 35 KB.
- Total initially required first-party JS + CSS <= 360 KB (source budget, not network gzip or real Core Web Vitals).
- The 127 KB maskable PWA icon remains available on demand but is no longer downloaded for every initial service-worker installation.

## Manual real-device criteria

Test at 360, 390 and 430 CSS pixels; portrait and landscape; 2x/3x display density; reduced motion; 200% system font setting; poor connection and offline revisits. Verify visible focus, no horizontal overflow, working menu, official APK download, real public search errors, campus search, and phone-level app links.

**Do not claim a Lighthouse score from a static code budget.** Capture an actual Lighthouse mobile report and address its critical findings before treating Phase 3 as production complete.
