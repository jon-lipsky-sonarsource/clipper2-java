package clipper2;

/**
 * Fifth sample class for sonar-fix end-to-end testing. Targets three
 * confidently-MAJOR rules with three distinct fix shapes the agent
 * hasn't done on this repo before.
 *
 * Not referenced by production code.
 *
 * Expected issues (by rule):
 *   - java:S2178 — non-short-circuit logic operator (`&` instead of `&&`)
 *                   risks NPE because both operands are evaluated.
 *   - java:S3358 — nested ternary expressions are hard to read; extract.
 *   - java:S2160 — class overrides `equals` without overriding `hashCode`,
 *                   breaking the equals/hashCode contract.
 */
public final class IdiomDemo {

    private IdiomDemo() {
        // Utility holder
    }

    public static boolean isValid(String s) {
        return s != null && !s.isEmpty();
    }

    public static String classify(int x) {
        if (x < 0) {
            return "negative";
        }
        if (x == 0) {
            return "zero";
        }
        return x < 10 ? "small" : "large";
    }

    public record Point(int x, int y) {}
}
