const normalize = (value) => String(value || "").trim().toLowerCase().replaceAll("-", "_");

export function accessDenied(account) {
  return ["banned", "revoked"].includes(normalize(account?.accessStatus));
}

// Mirrors the server's accessGrantActive(): accessExpiresAt is seconds since
// epoch; null/0/non-finite means no expiry. Lapsed paid time is Ad Tier there,
// so status/plan labels must not keep selecting a paid build here.
export function accessLapsed(account, nowSeconds = Math.floor(Date.now() / 1000)) {
  const expiresAt = Number(account?.accessExpiresAt ?? account?.access_expires_at ?? 0);
  return Number.isFinite(expiresAt) && expiresAt > 0 && expiresAt < nowSeconds;
}

const lapsedPaid = (account) => accessLapsed(account) && !accessDenied(account);
const accessStatus = (account) => lapsedPaid(account) ? "ad_tier" : normalize(account?.accessStatus);
const selectedPlan = (account) => lapsedPaid(account) ? "ad_tier" : normalize(account?.selectedPlan);
const labels = (account) => [accessStatus(account), selectedPlan(account)];

// Presentation only: the native launch handler re-fetches these server-issued roles.
export function canLaunchMultiple(account) {
  return Boolean(account) && !accessDenied(account)
    && (account.ownerAccess === true || account.devAccess === true);
}

export function hasOwnerAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    account.ownerAccess === true || labels(account).includes("owner")
  );
}

export function hasMediaAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    hasOwnerAccess(account)
    || account.mediaAccess === true
    || account.testerAccess === true
    || labels(account).some((value) => ["media", "tester"].includes(value))
  );
}

export function hasBetaAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    hasMediaAccess(account)
    || account.betaAccess === true
    || labels(account).some((value) => ["beta_plus", "lifetime_beta"].includes(value))
  );
}

export function hasOwnedAccess(account) {
  return Boolean(account) && !accessDenied(account)
    && ["owned", "beta_plus", "media", "owner"].includes(accessStatus(account));
}

export function preferredBuildForAccess(account) {
  if (!account) return "release";
  if (account.devAccess === true) return "dev";
  if (hasMediaAccess(account)) return "media";
  if (hasBetaAccess(account)) return "beta_plus";
  if (hasOwnedAccess(account)) return "release";
  return canUseBuildForAccess(account, "ad_tier") ? "ad_tier" : "release";
}

export function canUseBuildForAccess(account, buildId) {
  if (!account || accessDenied(account)) return false;
  const build = normalize(buildId);
  if (build === "dev") return account.devAccess === true || hasOwnerAccess(account);
  if (build === "media") return hasMediaAccess(account);
  if (build === "beta_plus") return hasBetaAccess(account);
  if (build === "release") return hasOwnedAccess(account);
  if (build === "ad_tier") {
    const adTier = account.adTierAccess === true
      || labels(account).some((value) => ["ad_tier", "undecided"].includes(value));
    return !hasOwnedAccess(account) && Boolean(String(account.email || "").trim()) && adTier;
  }
  return false;
}
