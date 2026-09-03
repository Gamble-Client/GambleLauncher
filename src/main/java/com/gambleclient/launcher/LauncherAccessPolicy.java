package com.gambleclient.launcher;

import java.util.Locale;

final class LauncherAccessPolicy {
    private LauncherAccessPolicy() {
    }

    record Account(
        String email,
        String selectedPlan,
        String accessStatus,
        boolean ownerAccess,
        boolean mediaAccess,
        boolean testerAccess,
        boolean betaAccess,
        boolean devAccess,
        boolean adTierAccess
    ) {
        Account {
            email = normalize(email);
            selectedPlan = normalize(selectedPlan);
            accessStatus = normalize(accessStatus);
        }
    }

    static String preferredBuild(Account account) {
        if (account == null) return "release";
        if (account.devAccess()) return "dev";
        if (hasMediaAccess(account)) return "media";
        if (hasBetaAccess(account)) return "beta_plus";
        if (hasOwnedAccess(account)) return "release";
        return canUseBuild(account, "ad_tier") ? "ad_tier" : "release";
    }

    static boolean canUseBuild(Account account, String buildId) {
        if (account == null || isBlocked(account.accessStatus())) return false;
        return switch (normalize(buildId)) {
            case "dev" -> account.devAccess() || hasOwnerAccess(account);
            case "media" -> hasMediaAccess(account);
            case "beta_plus" -> hasBetaAccess(account);
            case "release" -> hasOwnedAccess(account);
            case "ad_tier" -> !hasOwnedAccess(account)
                && !account.email().isBlank()
                && (account.adTierAccess()
                    || "ad_tier".equals(account.accessStatus())
                    || "ad_tier".equals(account.selectedPlan())
                    || "undecided".equals(account.selectedPlan()));
            default -> false;
        };
    }

    static boolean hasOwnerAccess(Account account) {
        return account != null && !isBlocked(account.accessStatus()) && (account.ownerAccess()
            || "owner".equals(account.accessStatus())
            || "owner".equals(account.selectedPlan()));
    }

    static boolean hasMediaAccess(Account account) {
        return account != null && !isBlocked(account.accessStatus()) && (hasOwnerAccess(account)
            || account.mediaAccess()
            || account.testerAccess()
            || "media".equals(account.accessStatus())
            || "media".equals(account.selectedPlan())
            || "tester".equals(account.selectedPlan()));
    }

    static boolean hasBetaAccess(Account account) {
        return account != null && !isBlocked(account.accessStatus()) && (hasMediaAccess(account)
            || account.betaAccess()
            || "beta_plus".equals(account.accessStatus())
            || "beta_plus".equals(account.selectedPlan())
            || "lifetime_beta".equals(account.selectedPlan()));
    }

    static boolean hasOwnedAccess(Account account) {
        if (account == null || isBlocked(account.accessStatus())) return false;
        return switch (account.accessStatus()) {
            case "owned", "beta_plus", "media", "owner" -> true;
            default -> false;
        };
    }

    static boolean isBlocked(String value) {
        String normalized = normalize(value);
        return "banned".equals(normalized) || "revoked".equals(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
