/**
 * Pure payment-verification rules shared by all Flutterwave confirmation paths.
 * The order is created by BLINK's database, never by an Android-supplied price.
 */
export function transactionMatchesOrder(transaction, order) {
  if (!transaction || !order) return false;
  const amount = Number(transaction.amount);
  const id = String(transaction.id ?? "");
  return (
    transaction.status === "successful" &&
    transaction.tx_ref === order.tx_ref &&
    transaction.currency === order.currency &&
    order.currency === "NGN" &&
    Number.isSafeInteger(amount) &&
    amount === order.amount_ngn &&
    /^[0-9]+$/.test(id)
  );
}
