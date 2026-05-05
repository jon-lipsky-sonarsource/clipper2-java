package clipper2;

import java.util.HashMap;
import java.util.Map;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists only
 * so SonarCloud has issues to flag and the sonar-fix agent has things to
 * fix. Each method targets a distinct MAJOR-severity rule the agent
 * hasn't seen on this repo before.
 */
public class PitfallDemo {

    // Triggers java:S1126 — the if-then-else just returns true/false based
    // on the boolean expression, so it collapses to a single return.
    public boolean isLargeRectangle(int width, int height) {
        if (width > 100 && height > 100) {
            return true;
        } else {
            return false;
        }
    }

    // Triggers java:S2864 — when both key and value are needed in the loop
    // body, iterating keySet() and calling get() on each key does a
    // redundant hash lookup per iteration. entrySet() gives both at once.
    public int countNonEmptyValues(Map<String, String> map) {
        int count = 0;
        for (String key : map.keySet()) {
            String value = map.get(key);
            if (value != null && !value.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // Triggers java:S1163 — throwing from finally suppresses any exception
    // that the try block itself raised. The IllegalStateException below is
    // silently swallowed; only the RuntimeException propagates. Real
    // behavior bug masquerading as cleanup code.
    public void cleanup(boolean shouldFail) {
        try {
            if (shouldFail) {
                throw new IllegalStateException("primary failure");
            }
        } finally {
            throw new RuntimeException("cleanup ran");
        }
    }

    // Triggers java:S1244 — IEEE 754 doubles can't represent most decimal
    // fractions exactly, so == is unreliable. Classic example:
    // (0.1 + 0.2) == 0.3 is false. Fix is comparison against an epsilon
    // tolerance, or use BigDecimal when exact arithmetic is required.
    public double computeRatio(double dividend, double divisor) {
        if (divisor == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return dividend / divisor;
    }

    // Helper kept here so the fixture file compiles cleanly without
    // dragging in test-side scaffolding. Not flagged by any rule.
    public static Map<String, String> emptyConfig() {
        return new HashMap<>();
    }
}
