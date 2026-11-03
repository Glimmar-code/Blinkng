/**
 * Referral push delivery acknowledgement is intentionally strict: only a real
 * send, a previously completed send, or an intentional privacy skip settles
 * the outbox item. Transient skips (no token, sending, rate limits) must retry.
 */
export function isSettledReferralDelivery(httpOk, body) {
  if (!httpOk || !body || typeof body !== "object" || body.ok === false || body.error) {
    return false;
  }
  const delivered = Number(body.delivered ?? 0);
  if (Number.isFinite(delivered) && delivered > 0) return true;
  return [
    "already_sent",
    "recipient_preferences",
    "self_or_missing_recipient",
  ].includes(body.skipped);
}

/** The SQL RPC returns false for a stale lease even when there is no error. */
export function isPersistedOutboxFinish(result, error) {
  return !error && result === true;
}
