/** Defensive check shared by webhook and authenticated payment confirmation. */
export function transactionMatchesOrder(payment,order) {
  if (!payment || !order) return false;
  const transactionId = String(payment.id ?? "");
  return payment.status==="success" &&
    payment.reference===order.reference &&
    payment.currency==="NGN" && order.currency==="NGN" &&
    Number.isSafeInteger(payment.amount) && payment.amount>0 &&
    payment.amount===order.amount_ngn*100 &&
    /^[0-9]+$/.test(transactionId);
}

/** Never initialize a real charge with a live merchant key in staging. */
export function merchantKeyMatchesMode(key, mode) {
  return (mode === "test" || mode === "live") &&
    typeof key === "string" && key.startsWith(`sk_${mode}_`) && key.length > 12;
}
