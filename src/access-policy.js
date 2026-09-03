const normalize = (value) => String(value || "").trim().toLowerCase().replaceAll("-", "_");

export function accessDenied(account) {
  return ["banned", "revoked"].includes(normalize(account?.accessStatus));
}

export function hasOwnerAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    account.ownerAccess === true || [normalize(account.accessStatus), normalize(account.selectedPlan)].includes("owner")
  );
}

export function hasMediaAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    hasOwnerAccess(account)
    || account.mediaAccess === true
    || account.testerAccess === true
    || [normalize(account.accessStatus), normalize(account.selectedPlan)].some((value) => ["media", "tester"].includes(value))
  );
}

export function hasBetaAccess(account) {
  return Boolean(account) && !accessDenied(account) && (
    hasMediaAccess(account)
    || account.betaAccess === true
    || [normalize(account.accessStatus), normalize(account.selectedPlan)].some((value) => ["beta_plus", "lifetime_beta"].includes(value))
  );
}

export function hasOwnedAccess(account) {
  return Boolean(account) && !accessDenied(account)
    && ["owned", "beta_plus", "media", "owner"].includes(normalize(account.accessStatus));
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
      || [normalize(account.accessStatus), normalize(account.selectedPlan)].some((value) => ["ad_tier", "undecided"].includes(value));
    return !hasOwnedAccess(account) && Boolean(String(account.email || "").trim()) && adTier;
  }
  return false;
}
