import assert from "node:assert/strict";
import test from "node:test";
import { launchState } from "../src/launch-state.js";

const free = { profile: { client: true }, signedIn: true, running: false, starting: false,
  busy: false, buildId: "ad_tier", ads: { active: false }, updateAvailable: true };

test("sponsor requirement belongs only to Gamble profiles", () => {
  assert.equal(launchState(free).action, "dashboard");
  for (const loader of ["vanilla", "fabric"]) {
    const result = launchState({ ...free, profile: { client: false, loader } });
    assert.equal(result.action, "play");
    assert.equal(result.label, "Play");
  }
});

test("sign in precedes sponsor setup, and an existing process can always be stopped", () => {
  assert.equal(launchState({ ...free, signedIn: false }).action, "signin");
  assert.equal(launchState({ ...free, signedIn: false, running: true }).action, "stop");
});

test("fresh authorized installs offer one setup-and-play action", () => {
  assert.equal(launchState({ ...free, ads: { active: true } }).label, "Set up & play");
  for (const buildId of ["release", "beta_plus", "media", "dev"]) {
    assert.equal(launchState({ ...free, buildId }).action, "play");
    assert.equal(launchState({ ...free, buildId, updateAvailable: false }).label, "Play");
  }
});

test("boot and active operations cannot trigger duplicate launch actions", () => {
  assert.equal(launchState({ ...free, starting: true }).disabled, true);
  for (const override of [{}, { running: true }, { signedIn: false }, { ads: { active: true } }]) {
    assert.equal(launchState({ ...free, ...override, busy: true }).disabled, true);
  }
});
