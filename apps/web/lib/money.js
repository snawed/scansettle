/** amountMinorUnits (integer pence) -> "£2,500.00" — the API never sends floats for money. */
export function formatMinorUnits(minorUnits, currencyCode = "GBP") {
  const pounds = minorUnits / 100;
  return new Intl.NumberFormat("en-GB", { style: "currency", currency: currencyCode }).format(pounds);
}

/** "2500.00" (user input string) -> 250000 (integer minor units), or null if invalid. */
export function parseToMinorUnits(input) {
  const trimmed = String(input).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) return null;
  return Math.round(parseFloat(trimmed) * 100);
}
