const GOOD = new Set(["PAYMENT_CONFIRMED", "ACTIVE"]);
const BAD = new Set(["FAILED", "REJECTED", "CANCELLED", "EXPIRED", "CLOSED"]);

/** Maps a Payment/PaymentLink state to a badge class + human label — never shows the raw enum to a merchant either. */
export function statusBadgeClass(state) {
  if (GOOD.has(state)) return "badge badge-good";
  if (BAD.has(state)) return "badge badge-bad";
  return "badge badge-warn";
}

const LABELS = {
  CREATED: "Created",
  AWAITING_PAYMENT: "Awaiting payment",
  REDIRECTED_TO_BANK: "At bank",
  PAYMENT_SUBMITTED: "Submitted",
  PAYMENT_PENDING: "Pending",
  PAYMENT_CONFIRMED: "Confirmed",
  FAILED: "Failed",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
  EXPIRED: "Expired",
  ACTIVE: "Active",
  CLOSED: "Closed",
};

export function statusLabel(state) {
  return LABELS[state] || state;
}
