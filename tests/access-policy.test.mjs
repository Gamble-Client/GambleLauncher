import assert from "node:assert/strict";
import test from "node:test";

import { canUseBuildForAccess, preferredBuildForAccess } from "../src/access-policy.js";

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
