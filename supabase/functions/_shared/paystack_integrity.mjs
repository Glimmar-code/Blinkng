/** Provider receipt must match a server-created order, in NGN kobo. */
export function paystackMatchesOrder(tx, order) {
  if (!tx || !order) return false;
  const kobo = Number(tx.amount);
  const providerId = tx.id;
  return tx.status === "success" &&
    tx.reference === order.reference &&
    tx.currency === "NGN" && order.currency === "NGN" &&
    Number.isSafeInteger(kobo) && kobo === order.amount_ngn * 100 &&
    ((typeof providerId === "number" && Number.isSafeInteger(providerId) && providerId > 0) ||
      (typeof providerId === "string" && /^[0-9]+$/.test(providerId)));
}
