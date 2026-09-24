package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LauncherAccessPolicyTest {
    private static final Set<String> BUILDS = Set.of("ad_tier", "release", "beta_plus", "media", "dev");

    @Test
    void extraLaunchRequiresAnActualRoleNotAnAccountLabelOrBuild() {
        for (String label : Set.of("owner", "dev", "media", "beta_plus", "weekly", "ad_tier")) {
            assertEquals(false, LauncherAccessPolicy.canLaunchAnother(account("user@example.test", label, label,
                false, true, true, true, false, true)), label);
        }
        assertEquals(true, LauncherAccessPolicy.canLaunchAnother(account("owner@example.test", "lifetime", "owned",
            true, false, false, false, false, false)));
        assertEquals(true, LauncherAccessPolicy.canLaunchAnother(account("dev@example.test", "lifetime", "owned",
            false, false, false, false, true, false)));
        for (String blocked : Set.of("banned", "revoked")) {
            assertEquals(false, LauncherAccessPolicy.canLaunchAnother(account("user@example.test", "owner", blocked,
                true, true, true, true, true, true)));
        }
        assertEquals(false, LauncherAccessPolicy.canLaunchAnother(null));
    }

    @Test
    void refreshedSelectionDowngradesAndRejectsBlockedAccounts() {
        var free = account("free@example.test", "ad_tier", "ad_tier", false, false, false, false, false, true);
        assertEquals("ad_tier", LauncherAccessPolicy.refreshedBuild(free, "media"));
        var paid = account("paid@example.test", "lifetime", "owned", false, false, false, false, false, false);
        assertEquals("release", LauncherAccessPolicy.refreshedBuild(paid, "ad_tier"));
        assertEquals("release", LauncherAccessPolicy.refreshedBuild(paid, "release"));
        var blocked = account("blocked@example.test", "owner", "banned", true, true, false, true, true, true);
        assertEquals("", LauncherAccessPolicy.refreshedBuild(blocked, "dev"));
        assertEquals("", LauncherAccessPolicy.refreshedBuild(null, "release"));
    }

    @Test
    void representativeAccountsReceiveOnlyTheirServerAuthorizedBuilds() {
        assertAccess(account("free.river@example.test", "ad_tier", "ad_tier", false, false, false, false, false, true),
            "ad_tier", Set.of("ad_tier"));
        assertAccess(account("giveaway.mason@example.test", "weekly", "owned", false, false, false, false, false, false),
            "release", Set.of("release"));
        assertAccess(account("beta.nova@example.test", "beta_plus", "beta_plus", false, false, false, true, false, false),
            "beta_plus", Set.of("release", "beta_plus"));
        assertAccess(account("media.harper@example.test", "media", "media", false, true, false, true, false, false),
            "media", Set.of("release", "beta_plus", "media"));
        assertAccess(account("owner.jordan@example.test", "owner", "owner", true, true, false, true, true, false),
            "dev", Set.of("release", "beta_plus", "media", "dev"));
        assertAccess(account("blocked.casey@example.test", "lifetime", "banned", false, false, false, false, false, false),
            "release", Set.of());
    }

    private static void assertAccess(LauncherAccessPolicy.Account account, String preferred, Set<String> allowed) {
        assertEquals(preferred, LauncherAccessPolicy.preferredBuild(account));
        assertEquals(allowed, BUILDS.stream()
            .filter(build -> LauncherAccessPolicy.canUseBuild(account, build))
            .collect(Collectors.toSet()));
    }

    @Test
    void lapsedPaidAccessFallsBackToAdTierLikeTheServer() {
        long now = System.currentTimeMillis() / 1000L;
        for (long expiry : new long[]{0L, -1L, now + 3600L}) {
            var paid = new LauncherAccessPolicy.Account("rental@example.test", "weekly", "owned",
                false, false, false, false, false, false, expiry);
            assertEquals("release", LauncherAccessPolicy.preferredBuild(paid), Long.toString(expiry));
            assertEquals(false, LauncherAccessPolicy.canUseBuild(paid, "ad_tier"));
        }
        var lapsed = new LauncherAccessPolicy.Account("rental@example.test", "weekly", "owned",
            false, false, false, false, false, false, now - 60L);
        assertEquals(true, LauncherAccessPolicy.accessLapsed(now - 60L, now));
        assertEquals(false, LauncherAccessPolicy.hasOwnedAccess(lapsed));
        assertEquals("ad_tier", LauncherAccessPolicy.preferredBuild(lapsed));
        assertEquals("ad_tier", LauncherAccessPolicy.refreshedBuild(lapsed, "release"));
        var lapsedBeta = new LauncherAccessPolicy.Account("beta@example.test", "beta_plus", "beta_plus",
            false, false, false, false, false, false, now - 60L);
        assertEquals("ad_tier", LauncherAccessPolicy.preferredBuild(lapsedBeta));
        var banned = new LauncherAccessPolicy.Account("banned@example.test", "weekly", "banned",
            false, false, false, false, false, true, now - 60L);
        assertEquals("", LauncherAccessPolicy.refreshedBuild(banned, "ad_tier"));
        var noEmail = new LauncherAccessPolicy.Account("", "weekly", "owned",
            false, false, false, false, false, false, now - 60L);
        assertEquals(false, LauncherAccessPolicy.canUseBuild(noEmail, "ad_tier"));
    }

    private static LauncherAccessPolicy.Account account(
        String email,
        String plan,
        String status,
        boolean owner,
        boolean media,
        boolean tester,
        boolean beta,
        boolean dev,
        boolean adTier
    ) {
        return new LauncherAccessPolicy.Account(email, plan, status, owner, media, tester, beta, dev, adTier);
    }
}
