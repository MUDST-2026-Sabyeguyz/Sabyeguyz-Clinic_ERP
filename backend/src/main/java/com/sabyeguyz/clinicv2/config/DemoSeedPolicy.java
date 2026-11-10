package com.sabyeguyz.clinicv2.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Fail-closed gate for optional synthetic demo migrations. */
final class DemoSeedPolicy {
    private static final Set<String> MODES = Set.of("none", "minimal", "full");

    private DemoSeedPolicy() {}

    static String resolve(String requestedMode, String databaseKind, String activeProfiles) {
        String mode = requestedMode == null || requestedMode.isBlank()
                ? "none" : requestedMode.trim().toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("CLINIC_SEED_MODE must be none, minimal or full");
        }
        if (!"none".equals(mode)) {
            if (!"local-demo".equalsIgnoreCase(databaseKind == null ? "" : databaseKind.trim())) {
                throw new IllegalStateException("Demo seed requires CLINIC_DATABASE_KIND=local-demo");
            }
            boolean production = Arrays.stream((activeProfiles == null ? "" : activeProfiles).split(","))
                    .map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .anyMatch(value -> value.equals("prod") || value.equals("production"));
            if (production) {
                throw new IllegalStateException("Demo seed is disabled for production profiles");
            }
        }
        return mode;
    }

    static String[] locations(String mode, boolean useLegacyFullSeed) {
        return switch (mode) {
            case "full" -> new String[] {"classpath:db/migration", useLegacyFullSeed
                    ? "classpath:db/seed_v2_legacy" : "classpath:db/seed_v2_legacy_demo"};
            case "minimal" -> new String[] {"classpath:db/migration", "classpath:db/seed_v2"};
            case "none" -> new String[] {"classpath:db/migration"};
            default -> throw new IllegalArgumentException("Unresolved demo seed mode: " + mode);
        };
    }

    static boolean useLegacyFullSeed(boolean fullSeedApplied, boolean bearingVariantApplied) {
        return fullSeedApplied && !bearingVariantApplied;
    }

    static String[] locations(String mode) {
        return locations(mode, false);
    }
}
