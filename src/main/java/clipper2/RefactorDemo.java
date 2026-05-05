package clipper2;

import java.util.List;

/**
 * Third sample class for sonar-fix end-to-end testing. Targets rules
 * the agent hasn't seen on this repo before — these are pattern
 * rewrites rather than the delete-or-extract patterns of SonarFixDemo
 * and BugfixDemo.
 *
 * Not referenced by production code.
 *
 * Expected issues (by rule):
 *   - java:S1144 — unused private method
 *   - java:S1132 — Yoda condition for `equals` (literal should be the receiver)
 *   - java:S1149 — String concatenation in a loop (use StringBuilder)
 *   - java:S2142 — InterruptedException must not be silently swallowed
 */
public final class RefactorDemo {

    private RefactorDemo() {
        // Utility class
    }

    private static String unusedPrivateHelper(int seed) {
        return "result-" + seed;
    }

    public static String findMatch(String input) {
        if (input.equals("expected")) {
            return "matched";
        }
        return "no match";
    }

    public static String concatItems(List<String> items) {
        String out = "";
        for (String item : items) {
            out = out + item + ",";
        }
        return out;
    }

    public static void waitBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // intentionally swallowed
        }
    }
}
