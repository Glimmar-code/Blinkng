/* BLINK marketing-first public homepage. Route-specific app, OAuth and shared links remain in app.js. */
(() => {
  'use strict';
  const root = document.getElementById('app');
  if (!root) return;
  const isHomepage = () => ['/', '/index.html', '/Blinkng/', '/Blinkng/index.html'].includes(location.pathname);
  const markup = [
    '<a class="br-skip" href="#br-main">Skip to main content</a>',
    '<header class="br-header"><div class="br-wrap br-head-row">',
      '<a class="br-brand" href="./" aria-label="BLINK homepage"><img src="./blink-logo-v2.png" alt="" width="48" height="48"><span>BLINK</span></a>',
      '<nav class="br-nav" aria-label="Website navigation"><a href="./" aria-current="page">Home</a><a href="#blink-read-more" data-reveal>About BLINK</a><a href="./features/">Features</a><a href="./faq/">Support</a></nav>',
      '<a class="br-header-download" href="https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk" aria-label="Download official BLINK Android APK"><span aria-hidden="true">↓</span> Download App</a>',
    '</div></header>',
    '<div id="br-main" class="br-main">',
      '<section class="br-wrap br-hero" aria-labelledby="br-title">',
        '<div class="br-copy">',
          '<span class="br-hashtag">#BeyondLINK</span>',
          '<h1 id="br-title">Connect,<br>Discover and<br>Grow <span class="br-gradient">Beyond LINK</span></h1>',
          '<p class="br-description">BLINK is a social and campus community app for students and young people in Nigeria to connect, share, discover creators, explore reels and grow beyond the usual.</p>',
          '<div class="br-actions"><a class="br-button br-button-main" href="https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk"><span aria-hidden="true">↓</span><span>Download BLINK APK<small>For Android</small></span></a><button type="button" class="br-button br-button-outline" data-reveal>Read more about BLINK <span aria-hidden="true">↘</span></button></div>',
          '<p class="br-download-note">Free to join · Official Android APK · Direct download from BLINK</p>',
          '<div class="br-trust"><span>✦ Your campus, your community</span><span>◉ Discover creators</span><span>↗ Beyond the usual</span></div>',
        '</div>',
        '<div class="br-showcase" aria-label="Illustrative BLINK app previews">',
          '<div class="br-aura"></div>',
          '<div class="br-phone br-phone-feed" aria-label="Illustrative BLINK feed screen">',
            '<div class="br-phone-notch"></div><div class="br-phone-content">',
              '<div class="br-status"><span>9:41</span><span>▰ ᴡɪꜰɪ ▱</span></div>',
              '<div class="br-app-head"><img src="./blink-logo-v2.png" alt="" width="23" height="23"><b>BLINK</b><span class="br-head-icons">⌕ ◉</span></div>',
              '<div class="br-stories"><div><i class="br-ring"><span>◎</span></i><small>Your story</small></div><div><i class="br-ring"><span>✦</span></i><small>Campus</small></div><div><i class="br-ring"><span>↗</span></i><small>Creators</small></div><div><i class="br-ring"><span>◉</span></i><small>Explore</small></div></div>',
              '<div class="br-feed-tabs"><strong>For You</strong><span>Following</span></div>',
              '<div class="br-post-head"><i class="br-avatar"></i><div><strong>BLINK Community</strong><small>Campus updates · Discover</small></div><span>•••</span></div>',
              '<p class="br-post-copy">Different people. Same campus.<br><em>Connect beyond the usual.</em></p>',
              '<div class="br-post-visual"><span class="br-sun"></span><span class="br-hills"></span><span class="br-building"></span></div>',
              '<div class="br-post-actions">♡ &nbsp;&nbsp; ◯ &nbsp;&nbsp; ➤ <span>♧</span></div>',
              '<div class="br-bottom-tabs"><span>⌂<small>Home</small></span><span>▣<small>Reels</small></span><span>⊕</span><span>♙<small>Connect</small></span><span>✉<small>Message</small></span></div>',
            '</div></div>',
          '<div class="br-phone br-phone-reels" aria-label="Illustrative BLINK reels screen">',
            '<div class="br-phone-notch"></div><div class="br-phone-content">',
              '<div class="br-status"><span>9:41</span><span>▰ ▱</span></div>',
              '<div class="br-reels-title">Reels <span>⌕</span></div>',
              '<div class="br-reel-scene"><div class="br-reel-sun"></div><div class="br-reel-land"></div><div class="br-reel-caption"><b>Campus moments ✦</b><small>Share what makes your campus yours.</small></div></div>',
              '<div class="br-reel-buttons">♡<br>◯<br>♧<br>➤</div>',
              '<div class="br-bottom-tabs"><span>⌂<small>Home</small></span><span>▣<small>Reels</small></span><span>⊕</span><span>♙<small>Connect</small></span><span>✉<small>Message</small></span></div>',
            '</div></div>',
          '<span class="br-preview-disclaimer">Illustrative app previews</span>',
        '</div>',
      '</section>',
      '<div class="br-scroll-hint"><a href="#blink-read-more" data-reveal>READ MORE ABOUT BLINK <span aria-hidden="true">⌄</span></a></div>',
      '<section class="br-wrap br-info-section" aria-label="Learn more about BLINK">',
        '<details id="blink-read-more" class="br-details">',
          '<summary>Read more about BLINK <span aria-hidden="true">＋</span></summary>',
          '<div class="br-detail-content">',
            '<h2>BLINK Nigeria — Connect, Discover and Grow Beyond LINK</h2>',
            '<p>BLINK helps people discover public profiles, share posts and reels, stay in touch through messages, and explore university and campus communities. You can discover creators, build connections and learn about the BLINK marketplace. Features and availability depend on the app version and your account.</p>',
            '<div class="br-detail-grid">',
              '<article><h3>Campus connections</h3><p>Connect with other students, explore university communities and discover people with shared interests.</p><a href="./students/">Learn about BLINK for students</a></article>',
              '<article><h3>Feed, Reels and creators</h3><p>Find posts and short-form videos, explore public profiles and connect with creators on BLINK.</p><a href="./features/">Explore app features</a></article>',
              '<article><h3>BLINK Market</h3><p>Learn about product discovery and approved seller experiences on the BLINK marketplace.</p><a href="./marketplace/">Explore BLINK Market</a></article>',
              '<article><h3>Download BLINK</h3><p>Get the current published Android APK from the official GitHub release. Review the release and your device security settings before installing.</p><a href="./download/">Download information</a></article>',
            '</div>',
            '<nav class="br-info-links" aria-label="More information"><a href="./about/">About BLINK</a><a href="./faq/">FAQ</a><a href="./safety/">Safety</a><a href="./privacy/">Privacy Policy</a><a href="./download/">Official download</a><a href="./login" data-app-route>Sign in on web</a></nav>',
            '<div id="br-retained" class="br-retained" aria-label="More BLINK website tools and information"></div>',
          '</div>',
        '</details>',
      '</section>',
    '</div>',
    '<footer class="br-footer"><div class="br-wrap"><span>© BLINK · Beyond LINK</span><nav aria-label="Footer"><a href="./about/">About</a><a href="./features/">Features</a><a href="./download/">Download</a><a href="./faq/">Help</a><a href="./privacy/">Privacy</a></nav></div></footer>'
  ].join('');

  function refreshHomepage() {
    if (!isHomepage()) {
      document.body.classList.remove('br-marketing-active');
      return;
    }
    const landing = root.querySelector(':scope > .landing-page:not([data-brand-refresh])');
    if (!landing) return;
    // Keep existing public-profile search, campus catalogue, feature tour and
    // all attached listeners; merely move them behind the expandable overview.
    const publicSearch = landing.querySelector('#public-search');
    const legacySections = Array.from(landing.querySelectorAll(':scope > section.landing-container'))
      .filter(section => !section.classList.contains('landing-hero'));
    // The initial crawlable shell uses #app.boot. Its grid centering was
    // leaking into the rendered homepage and causing oversized gaps.
    root.classList.remove('boot');
    document.body.classList.add('br-marketing-active');
    landing.setAttribute('data-brand-refresh','1');
    landing.className = 'br-page';
    document.title = 'BLINK Nigeria — Connect, Discover and Grow Beyond LINK | Download App';
    landing.innerHTML = markup;
    const retained = landing.querySelector('#br-retained');
    if (publicSearch) retained.append(publicSearch);
    for (const section of legacySections) retained.append(section);
    const details = landing.querySelector('#blink-read-more');
    landing.addEventListener('click', event => {
      const appLink = event.target.closest('a[data-app-route]');
      if (appLink) {
        // Preserve the in-app router in local preview as well as Cloudflare production.
        event.preventDefault();
        const href = new URL(appLink.getAttribute('href'), location.href);
        history.pushState({}, '', href.pathname);
        window.dispatchEvent(new PopStateEvent('popstate'));
        window.scrollTo({top:0,behavior:'instant'});
        return;
      }
      const trigger = event.target.closest('[data-reveal]');
      if (!trigger) return;
      event.preventDefault();
      details.open = true;
      details.scrollIntoView({behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start'});
    });
  }
  const observer = new MutationObserver(refreshHomepage);
  observer.observe(root,{childList:true});
  window.addEventListener('popstate', refreshHomepage);
  refreshHomepage();
})();