package com.gambleclient.launcher;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LauncherAccessPolicyTest {
    private static final Set<String> BUILDS = Set.of("ad_tier", "release", "beta_plus", "media", "dev");

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
