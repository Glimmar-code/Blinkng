/**
 * Cloudflare Pages appends an Insights beacon immediately before </body>.
 * Accept only that one known injection when checking public source identity.
 * Everything else (including unexpected injected tags) fails the fingerprint.
 */
export function normalizePublicHtml(live,source){
 if(live===source)return live;
 const closing=source.lastIndexOf('</body>');
 if(closing<0)return live;
 const prefix=source.slice(0,closing),suffix=source.slice(closing);
 if(!live.startsWith(prefix)||!live.endsWith(suffix))return live;
 const added=live.slice(prefix.length,live.length-suffix.length);
 const valid=/^<script type="module" src="https:\/\/static\.cloudflareinsights\.com\/[^"]+"[^>]*><\/script>\s*$/.test(added);
 return valid?source:live;
}
