import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

// A visible native window alone is not proof that its WebView rendered.
export function verifyRenderProof(proof) {
  if (proof?.webviewPresent !== true) throw new Error("No WebView2 process was observed.");
  if (typeof proof.domHealthy !== "boolean") throw new Error("Missing DOM probe result.");
  for (const key of ["mean", "variance", "range", "colorBuckets", "samples"]) {
    if (typeof proof[key] !== "number" || !Number.isFinite(proof[key]) || proof[key] < 0) {
      throw new Error(`Invalid render metric: ${key}`);
    }
  }
  if (proof.samples === 0) throw new Error("No WebView pixels were sampled.");
  const pixelsHealthy = proof.mean <= 240 && proof.variance >= 40
    && proof.range >= 60 && proof.colorBuckets >= 12;
  if (!pixelsHealthy && !proof.domHealthy) {
    throw new Error("The installed launcher has no nonblank pixel proof or successful DOM probe.");
  }
  return { pixelsHealthy, domHealthy: proof.domHealthy,
    evidence: pixelsHealthy ? "desktop-pixels" : "successful-dom-probe" };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const proof = JSON.parse(readFileSync(0, "utf8").replace(/^\uFEFF/, ""));
    const result = verifyRenderProof(proof);
    console.log(JSON.stringify(result));
    if (!result.pixelsHealthy) console.warn("Desktop pixels were inconclusive; the installed launcher's DOM probe succeeded.");
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
