// Presentation only: which upgrade/sale banner the Home view shows. Nothing
// here grants or checks access; checkout and pricing are owned by the site.
import { accessDenied, canUseBuildForAccess, hasBetaAccess, hasOwnedAccess, hasOwnerAccess } from "./access-policy.js";

export const UPGRADE_COPY = "Release unlocks every module — $2.99/week or $15 lifetime";
// Narrow windows: same offer and prices, shorter lead, so the banner stays one row.
export const UPGRADE_SHORT_PITCH = "Unlock every module";

const PAID_BUILDS = ["release", "beta_plus", "media", "dev"];
const TIMED_PLANS = ["weekly", "monthly", "yearly"];
// What each purchasable plan unlocks: timed Release < lifetime Release < Beta++.
const PLAN_RANK = { weekly: 1, monthly: 1, yearly: 1, lifetime: 2, beta_plus: 3 };
const PLAN_LABELS = { weekly: "Weekly", monthly: "Monthly", yearly: "Yearly", lifetime: "Lifetime", beta_plus: "Beta++" };
const MAX_PRICE_CENTS = 100_000;

const normalize = (value) => String(value || "").trim().toLowerCase().replaceAll("-", "_");

// Ad Tier = signed in, not banned, and no paid build is usable (lapsed rentals included).
export function isFreeTier(account) {
  return Boolean(account) && !accessDenied(account)
    && !PAID_BUILDS.some((build) => canUseBuildForAccess(account, build));
}

function entitlementRank(account) {
  if (hasBetaAccess(account)) return 3;
  if (!hasOwnedAccess(account)) return 0;
  const plan = normalize(account.selectedPlan);
  if (plan === "lifetime") return 2;
  if (TIMED_PLANS.includes(plan)) return 1;
  const expiresAt = Number(account.accessExpiresAt ?? account.access_expires_at ?? 0);
  return Number.isFinite(expiresAt) && expiresAt > 0 ? 1 : 2;
}

// Everyone except the owner, unless the account already has what the sale sells.
export function saleAppliesTo(account, sale) {
  if (!account || !sale || accessDenied(account) || hasOwnerAccess(account)) return false;
  const have = entitlementRank(account);
  const want = PLAN_RANK[sale.plan];
  if (!want) return have === 0;
  if (have !== want) return have < want;
  // Same tier: a rental can still switch to a different rental length.
  return have === 1 && normalize(account.selectedPlan) !== sale.plan;
}

function cleanText(value, max) {
  return String(value ?? "")
    .replace(/[\u0000-\u001f\u007f]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function cents(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 && number <= MAX_PRICE_CENTS ? number : NaN;
}

// Validates GET /api/sale. The offset maps local time onto the server clock so
// a wrong system clock cannot extend or hide the countdown.
export function normalizeSale(body, receivedAt = Date.now()) {
  if (!body || typeof body !== "object" || body.active !== true) return null;
  const plan = normalize(body.plan);
  const priceCents = cents(body.priceCents);
  const listPriceCents = cents(body.listPriceCents);
  const endsAt = Date.parse(body.endsAt);
  const startsAt = Date.parse(body.startsAt);
  const serverNow = Date.parse(body.serverNow);
  if (!/^[a-z_]{1,24}$/.test(plan) || !Number.isFinite(endsAt)
    || !(priceCents < listPriceCents)) return null;
  const couponCode = String(body.couponCode ?? "").trim();
  const id = cleanText(body.id, 64);
  return {
    id,
    key: id || `${plan}:${endsAt}`,
    title: cleanText(body.title, 48) || "Sale",
    plan,
    priceCents,
    listPriceCents,
    couponCode: /^[A-Za-z0-9_-]{1,32}$/.test(couponCode) ? couponCode : "",
    startsAt: Number.isFinite(startsAt) ? startsAt : -Infinity,
    endsAt,
    offset: Number.isFinite(serverNow) ? serverNow - receivedAt : 0
  };
}

export function saleRemainingMs(sale, localNow = Date.now()) {
  if (!sale) return 0;
  return Math.max(0, sale.endsAt - (localNow + sale.offset));
}

export function saleLive(sale, localNow = Date.now()) {
  if (!sale) return false;
  const serverNow = localNow + sale.offset;
  return serverNow >= sale.startsAt && serverNow < sale.endsAt;
}

const pad = (value) => String(value).padStart(2, "0");

// "1d 04h", then "4h 05m", then "12m 09s" for the final hour.
export function formatSaleCountdown(ms) {
  const total = Math.floor(Math.max(0, Number(ms) || 0) / 1000);
  if (total <= 0) return "";
  const days = Math.floor(total / 86_400);
  const hours = Math.floor((total % 86_400) / 3_600);
  const minutes = Math.floor((total % 3_600) / 60);
  if (days > 0) return `${days}d ${pad(hours)}h`;
  if (hours > 0) return `${hours}h ${pad(minutes)}m`;
  return `${minutes}m ${pad(total % 60)}s`;
}

export function formatUsd(value) {
  const amount = Math.max(0, Number(value) || 0);
  return amount % 100 === 0 ? `$${amount / 100}` : `$${(amount / 100).toFixed(2)}`;
}

export function planLabel(plan) {
  return PLAN_LABELS[normalize(plan)] || "Release";
}

// One banner at most. A live sale wins; for Ad Tier it absorbs the upgrade
// pitch into a single combined banner instead of stacking two.
export function promoBanner({ account, sale, dismissed = {}, now = Date.now() }) {
  if (!account) return null;
  const upgrade = isFreeTier(account);
  if (sale && saleLive(sale, now) && saleAppliesTo(account, sale) && dismissed.sale !== sale.key) {
    return { kind: "sale", sale, upgrade };
  }
  if (upgrade && !dismissed.upgrade) return { kind: "upgrade", sale: null, upgrade: true };
  return null;
}
