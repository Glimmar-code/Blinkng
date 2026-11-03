/* BLINK hosted-Paystack return: informational only, never client-side payment approval. */
(() => {
  const query = new URLSearchParams(window.location.search);
  if (query.get("blink_payment_return") !== "1") return;
  const panel = document.createElement("section");
  panel.id = "blink-paystack-return";
  panel.setAttribute("role", "dialog");
  panel.setAttribute("aria-modal", "true");
  panel.setAttribute("aria-label", "BLINK payment return");
  Object.assign(panel.style, {
    position: "fixed", zIndex: "2147483000", inset: "0",
    display: "grid", placeItems: "center", background: "rgba(0,0,0,.90)",
    color: "#fff", padding: "24px", fontFamily: "system-ui,sans-serif"
  });
  const card = document.createElement("div");
  Object.assign(card.style, {
    maxWidth: "440px", background: "#101010", border: "1px solid #666",
    borderRadius: "20px", padding: "26px", boxSizing: "border-box",
    lineHeight: "1.55"
  });
  const title = document.createElement("h2");
  title.textContent = "Return to BLINK";
  title.style.marginTop = "0";
  const description = document.createElement("p");
  description.textContent =
    "Your payment window has returned to BLINK. This page does not confirm a payment. " +
    "Open the BLINK app and tap “I've paid · Verify” to check your Paystack payment securely.";
  const link = document.createElement("a");
  link.textContent = "BLINK app and download information";
  link.href = "/download/";
  link.style.color = "white";
  link.style.textDecoration = "underline";
  const close = document.createElement("button");
  close.textContent = "Continue to BLINK website";
  close.type = "button";
  Object.assign(close.style, {
    display: "block", width: "100%", padding: "12px", marginTop: "20px",
    borderRadius: "12px", background: "white", border: "0", color: "black",
    fontWeight: "bold", cursor: "pointer"
  });
  close.addEventListener("click", () => panel.remove());
  card.append(title, description, link, close);
  panel.appendChild(card);
  document.body.appendChild(panel);
})();
