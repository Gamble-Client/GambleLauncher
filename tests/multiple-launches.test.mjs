import test from "node:test";
import assert from "node:assert/strict";
import { canLaunchMultiple } from "../src/access-policy.js";

test("multiple launch control accepts only explicit owner/developer roles", () => {
  for (const role of ["ownerAccess", "devAccess"]) {
    assert.equal(canLaunchMultiple({ [role]: true }), true);
    for (const accessStatus of ["banned", "revoked", " BANNED "]) {
      assert.equal(canLaunchMultiple({ [role]: true, accessStatus }), false);
    }
    for (const value of [false, "true", 1, null]) {
      assert.equal(canLaunchMultiple({ [role]: value }), false);
    }
  }
  for (const account of [null, {}, { selectedPlan: "dev" }, { accessStatus: "owner" },
    { mediaAccess: true }, { testerAccess: true }, { betaAccess: true }]) {
    assert.equal(canLaunchMultiple(account), false);
  }
});
