/**
 * Enforce the recipient's authoritative chat preview preference BEFORE sending
 * Firebase data. A missing/unrecognized setting is treated as hidden.
 */
export function chatPushPreview(mode, senderName, messageContent, senderAvatar = "") {
  const setting = mode === "full" || mode === "sender_only" ? mode : "hidden";
  const sender = String(senderName ?? "").trim() || "Blink user";
  const content = String(messageContent ?? "");
  const fullBody = content.length > 1200 ? `${content.slice(0, 1197)}...` : content;
  return {
    title: setting === "hidden" ? "BLINK Messages" : sender,
    body: setting === "full" ? (fullBody || "New message") : "New message",
    senderName: setting === "hidden" ? "" : sender,
    senderAvatar: setting === "hidden" ? "" : String(senderAvatar ?? ""),
  };
}
