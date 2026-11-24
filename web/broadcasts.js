(() => {
  "use strict";
  const hostId = "blink-website-broadcasts";
  const storageKey = "blink-website-broadcast-dismissed-v1";
  const config = window.BLINK_WEB_CONFIG || {};
  const base = String(config.supabaseUrl || "").replace(/\/$/, "");
  const key = String(config.publishableKey || "");
  if (!base || !key || document.getElementById(hostId)) return;

  function dismissedIds() {
    try {
      const parsed = JSON.parse(localStorage.getItem(storageKey) || "[]");
      return new Set(Array.isArray(parsed) ? parsed.filter(x => typeof x === "string") : []);
    } catch { return new Set(); }
  }
  function saveDismissed(ids) {
    try { localStorage.setItem(storageKey, JSON.stringify([...ids].slice(-100))); } catch {}
  }
  function safeCtaUrl(value) {
    const fallback = "/download/";
    if (typeof value !== "string" || !value.trim()) return fallback;
    try {
      const url = new URL(value, config.siteUrl || location.origin);
      if (url.protocol !== "https:" && url.origin !== location.origin) return fallback;
      if (url.protocol === "https:" && url.hostname !== new URL(config.siteUrl || location.origin).hostname) return fallback;
      return url.pathname + url.search + url.hash;
    } catch { return fallback; }
  }
  function safeImageUrl(value) {
    if (typeof value !== "string" || !value.trim()) return null;
    try {
      const url = new URL(value);
      if (url.protocol !== "https:") return null;
      return url.href;
    } catch { return null; }
  }
  function render(items) {
    const dismissed = dismissedIds();
    const visible = items.filter(item => item && typeof item.id === "string" && !dismissed.has(item.id))
      .slice(0, 3);
    if (!visible.length) return;
    const host = document.createElement("section");
    host.id = hostId;
    host.className = "blink-website-broadcast-host";
    host.setAttribute("aria-label", "BLINK updates");
    for (const item of visible) {
      const card = document.createElement("article");
      card.className = "blink-website-broadcast";
      card.dataset.broadcastId = item.id;
      const title = document.createElement("h2");
      title.textContent = String(item.title || "BLINK update").slice(0, 60);
      const body = document.createElement("p");
      body.textContent = String(item.body || "").slice(0, 240);
      const content = document.createElement("div");
      content.className = "blink-website-broadcast-content";
      const badge = document.createElement("span");
      badge.className = "blink-website-broadcast-kind";
      badge.textContent = String(item.type || "UPDATE").replace(/[^a-z0-9 _-]/gi, "").slice(0, 24);
      content.append(badge, title, body);
      const imageUrl = safeImageUrl(item.image_url);
      if (imageUrl) {
        const image = document.createElement("img");
        image.src = imageUrl;
        image.alt = "";
        image.loading = "lazy";
        image.referrerPolicy = "no-referrer";
        card.append(image);
      }
      const actions = document.createElement("div");
      actions.className = "blink-website-broadcast-actions";
      const cta = document.createElement("a");
      cta.className = "blink-website-broadcast-cta";
      cta.href = safeCtaUrl(item.deep_link);
      cta.textContent = String(item.cta_label || "Learn more").slice(0, 32);
      const close = document.createElement("button");
      close.type = "button";
      close.className = "blink-website-broadcast-dismiss";
      close.setAttribute("aria-label", "Dismiss update");
      close.textContent = "×";
      close.addEventListener("click", () => {
        const ids = dismissedIds();
        ids.add(item.id);
        saveDismissed(ids);
        card.remove();
        if (!host.querySelector(".blink-website-broadcast")) host.remove();
      });
      actions.append(cta, close);
      card.append(content, actions);
      host.append(card);
    }
    if (!host.querySelector(".blink-website-broadcast")) return;
    document.body.prepend(host);
  }

  fetch(base + "/rest/v1/rpc/get_website_broadcasts", {
    method: "POST",
    headers: { apikey: key, Authorization: "Bearer " + key, "Content-Type": "application/json" },
    body: "{}",
    credentials: "omit",
    cache: "no-store",
  }).then(response => {
    if (!response.ok) return [];
    return response.json();
  }).then(data => {
    if (Array.isArray(data)) render(data);
  }).catch(() => {});
})();
