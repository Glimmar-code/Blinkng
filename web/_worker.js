// Cloudflare Pages advanced-mode router for shareable BLINK web links.
// Only known application paths render the public SPA shell; everything else
// (real files, SEO pages, unknown routes, robots and assetlinks) stays static.
const EXACT_ROUTES = new Set([
  '/feed', '/reels', '/search', '/messages', '/activity', '/market',
  '/connect', '/games', '/scheduled', '/ai', '/settings', '/login',
  '/site-stats', '/more', '/store', '/vault', '/vip', '/boosts',
  '/gifts', '/calls', '/offline', '/drafts', '/verification',
  '/analytics', '/study', '/accounts', '/professional',
  '/leaderboard', '/seller',
]);

export function isBlinkAppRoute(pathname) {
  const path = String(pathname || '').replace(/\/+$/, '') || '/';
  if (EXACT_ROUTES.has(path)) return true;
  return (
    /^\/@[a-z0-9._-]{2,30}$/i.test(path) ||
    /^\/profile\/[^/]{1,128}$/i.test(path) ||
    /^\/(post|reel)\/[0-9a-f-]{8,64}$/i.test(path) ||
    /^\/invite\/[a-z0-9._-]{2,30}$/i.test(path) ||
    /^\/contest\/[0-9a-f-]{36}\/[0-9a-f-]{36}$/i.test(path) ||
    /^\/messages\/[^/]{1,128}$/i.test(path) ||
    /^\/settings\/[^/]{1,128}$/i.test(path)
  );
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if ((request.method !== 'GET' && request.method !== 'HEAD') ||
        !isBlinkAppRoute(url.pathname)) {
      return env.ASSETS.fetch(request);
    }

    // Fetch '/' rather than '/index.html': Pages may canonicalize the latter
    // with a redirect, which would otherwise erase the incoming shared URL.
    const root = new URL('/', url);
    const shellRequest = new Request(root, request);
    const response = await env.ASSETS.fetch(shellRequest);
    if (!response.ok) return response;

    // An app shell is never public search-engine content or personal data.
    const headers = new Headers(response.headers);
    headers.set('X-Robots-Tag', 'noindex, nofollow');
    headers.set('Referrer-Policy', 'no-referrer');
    headers.set('Cache-Control', 'no-store');
    return new Response(request.method === 'HEAD' ? null : response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  },
};
