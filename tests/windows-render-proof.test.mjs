import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { verifyRenderProof } from "../scripts/windows-render-proof.mjs";

const healthy = { webviewPresent: true, domHealthy: false,
  mean: 55, variance: 120, range: 180, colorBuckets: 32, samples: 1000 };

test("production pixels are sufficient with DevTools disabled", () => {
  assert.equal(verifyRenderProof(healthy).evidence, "desktop-pixels");
});

for (const [name, metric] of Object.entries({
  white: { mean: 255 }, flat: { variance: 0 },
  lowContrast: { range: 1 }, emptyPalette: { colorBuckets: 1 }
})) {
  test(`${name} capture cannot pass without an actual successful DOM probe`, () => {
    assert.throws(() => verifyRenderProof({ ...healthy, ...metric }), /no nonblank pixel proof/);
    assert.equal(verifyRenderProof({ ...healthy, ...metric, domHealthy: true }).evidence,
      "successful-dom-probe");
  });
}

test("missing WebView is rejected even if other evidence claims success", () => {
  assert.throws(() => verifyRenderProof({ ...healthy, webviewPresent: false, domHealthy: true }), /No WebView2/);
});

test("missing, non-finite, and coercible metrics fail closed", () => {
  for (const invalid of [{}, { domHealthy: "true" }, { mean: NaN }, { range: Infinity },
    { samples: 0 }, { variance: -1 }, { colorBuckets: "32" }]) {
    assert.throws(() => verifyRenderProof(Object.keys(invalid).length ? { ...healthy, ...invalid } : invalid));
  }
});

test("CLI returns failure to PowerShell for blank or malformed proof", () => {
  const script = new URL("../scripts/windows-render-proof.mjs", import.meta.url);
  for (const input of [JSON.stringify({ ...healthy, mean: 255 }), "not json"]) {
    const result = spawnSync(process.execPath, [fileURLToPath(script)], { input, encoding: "utf8" });
    assert.equal(result.status, 1);
  }
  const result = spawnSync(process.execPath, [fileURLToPath(script)], { input: JSON.stringify(healthy), encoding: "utf8" });
  assert.equal(result.status, 0);
  assert.equal(JSON.parse(result.stdout).evidence, "desktop-pixels");
});

test("Windows workflow explicitly propagates DOM and render proof failures", () => {
  const workflow = readFileSync(new URL("../.github/workflows/windows-production-smoke.yml", import.meta.url), "utf8");
  assert.match(workflow, /\$domHealthy = \$false/);
  assert.match(workflow, /node \$probePath \$target.webSocketDebuggerUrl\s+if \(\$LASTEXITCODE -ne 0\) \{ throw/);
  assert.match(workflow, /\$domHealthy = \$true/);
  assert.match(workflow, /node scripts\/windows-render-proof\.mjs\s+if \(\$LASTEXITCODE -ne 0\) \{ throw/);
  assert.doesNotMatch(workflow, /the embedded DOM probe passed/);
});
