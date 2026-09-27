import assert from "node:assert/strict";
import test from "node:test";

import { formatSaleCountdown, formatUsd, isFreeTier, normalizeSale, promoBanner, saleAppliesTo, saleLive, saleRemainingMs } from "../src/promo-policy.js";

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
const nowSeconds = () => Math.floor(Date.now() / 1000);

const accounts = {
  free: { email: "free.river@example.test", selectedPlan: "ad_tier", accessStatus: "ad_tier", adTierAccess: true },
  undecided: { email: "new.sam@example.test", selectedPlan: "undecided", accessStatus: "undecided" },
  lapsedWeekly: { email: "rental.quinn@example.test", selectedPlan: "weekly", accessStatus: "owned", accessExpiresAt: nowSeconds() - 60 },
  weekly: { email: "giveaway.mason@example.test", selectedPlan: "weekly", accessStatus: "owned", accessExpiresAt: nowSeconds() + 3600 },
  monthly: { email: "month.ivy@example.test", selectedPlan: "monthly", accessStatus: "owned" },
  lifetime: { email: "life.jo@example.test", selectedPlan: "lifetime", accessStatus: "owned" },
  keyGrant: { email: "key.ray@example.test", selectedPlan: "", accessStatus: "owned" },
  beta: { email: "beta.nova@example.test", selectedPlan: "beta_plus", accessStatus: "beta_plus", betaAccess: true },
  media: { email: "media.harper@example.test", selectedPlan: "media", accessStatus: "media", mediaAccess: true, betaAccess: true },
  owner: { email: "owner.jordan@example.test", selectedPlan: "owner", accessStatus: "owner", ownerAccess: true, mediaAccess: true, betaAccess: true, devAccess: true },
  dev: { email: "dev@example.test", selectedPlan: "lifetime", accessStatus: "owned", devAccess: true },
  banned: { email: "blocked.casey@example.test", selectedPlan: "ad_tier", accessStatus: "banned" }
};

function saleBody(overrides = {}) {
  const now = Date.now();
  return {
    active: true, id: "weekend-1", title: "Weekend sale", plan: "lifetime", priceCents: 1000, listPriceCents: 1500,
    couponCode: "WEEKEND10", startsAt: new Date(now - HOUR).toISOString(), endsAt: new Date(now + DAY + 4 * HOUR + 60_000).toISOString(),
    serverNow: new Date(now).toISOString(), ...overrides
  };
}

test("the upgrade banner is only for Ad Tier (free) accounts, never paid tiers", () => {
  for (const name of ["free", "undecided", "lapsedWeekly"]) {
    assert.equal(isFreeTier(accounts[name]), true, name);
    assert.deepEqual(promoBanner({ account: accounts[name], sale: null }), { kind: "upgrade", sale: null, upgrade: true }, name);
  }
  for (const name of ["weekly", "monthly", "lifetime", "keyGrant", "beta", "media", "owner", "dev", "banned"]) {
    assert.equal(isFreeTier(accounts[name]), false, name);
    assert.equal(promoBanner({ account: accounts[name], sale: null }), null, name);
  }
  assert.equal(isFreeTier(null), false);
  assert.equal(promoBanner({ account: null, sale: normalizeSale(saleBody()) }), null, "signed out shows nothing");
});

test("a sale reaches every non-owner account that does not already own the sale plan", () => {
  const lifetimeSale = normalizeSale(saleBody());
  const expected = { free: true, undecided: true, lapsedWeekly: true, weekly: true, monthly: true,
    lifetime: false, keyGrant: false, beta: false, media: false, owner: false, dev: false, banned: false };
  for (const [name, shown] of Object.entries(expected)) {
    assert.equal(saleAppliesTo(accounts[name], lifetimeSale), shown, name);
    assert.equal(promoBanner({ account: accounts[name], sale: lifetimeSale })?.kind === "sale", shown, name);
  }

  const weeklySale = normalizeSale(saleBody({ plan: "weekly", priceCents: 199, listPriceCents: 299 }));
  assert.equal(saleAppliesTo(accounts.weekly, weeklySale), false, "already on weekly");
  assert.equal(saleAppliesTo(accounts.monthly, weeklySale), true, "a different rental length is still offered");
  assert.equal(saleAppliesTo(accounts.lifetime, weeklySale), false);
  assert.equal(saleAppliesTo(accounts.free, weeklySale), true);

  const betaSale = normalizeSale(saleBody({ plan: "beta_plus", priceCents: 2000, listPriceCents: 2500 }));
  assert.equal(saleAppliesTo(accounts.lifetime, betaSale), true, "lifetime Release can still upgrade to Beta++");
  assert.equal(saleAppliesTo(accounts.beta, betaSale), false);
  assert.equal(saleAppliesTo(accounts.media, betaSale), false);
  assert.equal(saleAppliesTo(accounts.owner, betaSale), false);

  const unknownPlan = normalizeSale(saleBody({ plan: "extra_device_slot" }));
  assert.equal(saleAppliesTo(accounts.free, unknownPlan), true);
  assert.equal(saleAppliesTo(accounts.weekly, unknownPlan), false, "unknown plans stay with free accounts");
});

test("Ad Tier with a live sale gets one combined banner and the sale wins", () => {
  const sale = normalizeSale(saleBody());
  const combined = promoBanner({ account: accounts.free, sale });
  assert.deepEqual(combined, { kind: "sale", sale, upgrade: true });
  assert.deepEqual(promoBanner({ account: accounts.weekly, sale }), { kind: "sale", sale, upgrade: false });

  // Session dismissals: the combined banner clears both; a new sale still shows.
  assert.equal(promoBanner({ account: accounts.free, sale, dismissed: { sale: sale.key, upgrade: true } }), null);
  assert.equal(promoBanner({ account: accounts.free, sale, dismissed: { sale: sale.key } }).kind, "upgrade");
  assert.equal(promoBanner({ account: accounts.free, sale, dismissed: { upgrade: true } }).kind, "sale");
  assert.equal(promoBanner({ account: accounts.weekly, sale, dismissed: { sale: sale.key } }), null);
  const nextSale = normalizeSale(saleBody({ id: "weekend-2" }));
  assert.equal(promoBanner({ account: accounts.free, sale: nextSale, dismissed: { sale: sale.key, upgrade: true } }).kind, "sale");

  // Outside its window the sale drops out and Ad Tier falls back to the upgrade pitch.
  const ended = normalizeSale(saleBody({ endsAt: new Date(Date.now() - 1000).toISOString() }));
  const upcoming = normalizeSale(saleBody({ startsAt: new Date(Date.now() + HOUR).toISOString() }));
  for (const inactive of [ended, upcoming]) {
    assert.equal(promoBanner({ account: accounts.free, sale: inactive }).kind, "upgrade");
    assert.equal(promoBanner({ account: accounts.weekly, sale: inactive }), null);
  }
});

test("sale countdowns use the server clock, not the local one", () => {
  const local = Date.parse("2026-09-27T12:00:00Z");
  const server = local + 3 * DAY; // local clock three days slow
  const sale = normalizeSale(saleBody({
    startsAt: new Date(server - HOUR).toISOString(),
    endsAt: new Date(server + DAY + 4 * HOUR + 30 * 60_000).toISOString(),
    serverNow: new Date(server).toISOString()
  }), local);
  assert.equal(saleLive(sale, local), true);
  assert.equal(formatSaleCountdown(saleRemainingMs(sale, local)), "1d 04h");
  assert.equal(formatSaleCountdown(saleRemainingMs(sale, local + DAY)), "4h 30m");
  assert.equal(saleLive(sale, local + 2 * DAY), false);
  assert.equal(saleRemainingMs(sale, local + 2 * DAY), 0);

  const noServerNow = normalizeSale(saleBody({ serverNow: "not a date" }), local);
  assert.equal(noServerNow.offset, 0);
});

test("countdown formatting", () => {
  const cases = [
    [DAY + 4 * HOUR + 59 * 60_000, "1d 04h"],
    [2 * DAY, "2d 00h"],
    [12 * DAY + 23 * HOUR, "12d 23h"],
    [DAY - 1000, "23h 59m"],
    [4 * HOUR + 5 * 60_000, "4h 05m"],
    [HOUR, "1h 00m"],
    [12 * 60_000 + 9_000, "12m 09s"],
    [59_999, "0m 59s"],
    [999, ""],
    [0, ""],
    [-5_000, ""],
    [Number.NaN, ""]
  ];
  for (const [ms, text] of cases) assert.equal(formatSaleCountdown(ms), text, String(ms));
  assert.deepEqual([1000, 1500, 299, 699, 2500, 1050].map(formatUsd), ["$10", "$15", "$2.99", "$6.99", "$25", "$10.50"]);
});

test("the sale payload is validated before anything renders", () => {
  assert.equal(normalizeSale({ active: false, serverNow: new Date().toISOString(), next: { plan: "lifetime" } }), null);
  assert.equal(normalizeSale(null), null);
  assert.equal(normalizeSale("active"), null);
  assert.equal(normalizeSale(saleBody({ active: "true" })), null);
  assert.equal(normalizeSale(saleBody({ endsAt: "soon" })), null);
  assert.equal(normalizeSale(saleBody({ priceCents: 1500 })), null, "no discount is not a sale");
  assert.equal(normalizeSale(saleBody({ priceCents: 9.5 })), null);
  assert.equal(normalizeSale(saleBody({ priceCents: -1 })), null);
  assert.equal(normalizeSale(saleBody({ listPriceCents: "1500" })).listPriceCents, 1500);
  assert.equal(normalizeSale(saleBody({ plan: "<script>" })), null);

  const sale = normalizeSale(saleBody({ plan: "Lifetime", couponCode: "BAD CODE<>", title: "  Big\n\u0007sale   ".padEnd(90, "!"), id: "" }));
  assert.equal(sale.plan, "lifetime");
  assert.equal(sale.couponCode, "", "codes outside [A-Za-z0-9_-] are not shown");
  assert.equal(sale.title.startsWith("Big sale"), true);
  assert.equal(sale.title.length <= 48, true);
  assert.match(sale.key, /^lifetime:\d+$/, "a missing id still gives the sale a dismissal key");
  assert.equal(normalizeSale(saleBody({ title: "" })).title, "Sale");
  assert.equal(normalizeSale(saleBody({ startsAt: undefined })).startsAt, -Infinity);
});
