import assert from "node:assert/strict";
import test from "node:test";

import { accessLapsed, canUseBuildForAccess, preferredBuildForAccess } from "../src/access-policy.js";

const builds = ["ad_tier", "release", "beta_plus", "media", "dev"];
const allowed = (account) => builds.filter((build) => canUseBuildForAccess(account, build));

test("realistic launcher accounts receive the correct preferred build and access set", () => {
  const accounts = [
    [{ email: "free.river@example.test", selectedPlan: "ad_tier", accessStatus: "ad_tier", adTierAccess: true }, "ad_tier", ["ad_tier"]],
    [{ email: "giveaway.mason@example.test", selectedPlan: "weekly", accessStatus: "owned" }, "release", ["release"]],
    [{ email: "beta.nova@example.test", selectedPlan: "beta_plus", accessStatus: "beta_plus", betaAccess: true }, "beta_plus", ["release", "beta_plus"]],
    [{ email: "media.harper@example.test", selectedPlan: "media", accessStatus: "media", mediaAccess: true, betaAccess: true }, "media", ["release", "beta_plus", "media"]],
    [{ email: "owner.jordan@example.test", selectedPlan: "owner", accessStatus: "owner", ownerAccess: true, mediaAccess: true, betaAccess: true, devAccess: true }, "dev", ["release", "beta_plus", "media", "dev"]],
    [{ email: "blocked.casey@example.test", selectedPlan: "lifetime", accessStatus: "banned" }, "release", []]
  ];

  for (const [account, preferred, expectedAllowed] of accounts) {
    assert.equal(preferredBuildForAccess(account), preferred, account.email);
    assert.deepEqual(allowed(account), expectedAllowed, account.email);
  }
});

test("lapsed paid access falls back to Ad Tier like the server's accessGrantActive", () => {
  const now = Math.floor(Date.now() / 1000);
  const paid = { email: "rental.quinn@example.test", selectedPlan: "weekly", accessStatus: "owned" };
  for (const accessExpiresAt of [null, 0, undefined, now + 3600, "not-a-number"]) {
    assert.equal(preferredBuildForAccess({ ...paid, accessExpiresAt }), "release", String(accessExpiresAt));
    assert.deepEqual(allowed({ ...paid, accessExpiresAt }), ["release"], String(accessExpiresAt));
  }
  const lapsed = { ...paid, accessExpiresAt: now - 60 };
  assert.equal(accessLapsed(lapsed), true);
  assert.equal(preferredBuildForAccess(lapsed), "ad_tier");
  assert.deepEqual(allowed(lapsed), ["ad_tier"]);
  assert.deepEqual(allowed({ ...lapsed, accessStatus: "beta_plus", selectedPlan: "beta_plus" }), ["ad_tier"]);
  // Server-issued role flags stay authoritative; banned/revoked still fail closed.
  assert.equal(preferredBuildForAccess({ ...lapsed, accessStatus: "owner", devAccess: true }), "dev");
  assert.deepEqual(allowed({ ...lapsed, accessStatus: "banned" }), []);
  assert.deepEqual(allowed({ ...lapsed, email: "" }), []);
});
