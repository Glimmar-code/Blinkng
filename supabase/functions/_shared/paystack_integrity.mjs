/** Pure receipt integrity check. Amounts are integer kobo, NOT naira. */
export function matchesPaystackReceipt(receipt, order) {
  if (!receipt || !order) return false;
  const amount = Number(receipt.amount);
  const id = String(receipt.id ?? "");
  const paidEmail = String(receipt.customer?.email ?? "").trim().toLowerCase();
  return (
    receipt.status === "success" &&
    receipt.reference === order.reference &&
    receipt.currency === "NGN" &&
    order.currency === "NGN" &&
    Number.isSafeInteger(amount) &&
    amount === Number(order.amount_kobo) &&
    /^[0-9]+$/.test(id) &&
    paidEmail !== "" &&
    paidEmail === String(order.customer_email ?? "").toLowerCase()
  );
}
