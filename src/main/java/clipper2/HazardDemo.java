package clipper2;

/**
 * Sixth sample class for sonar-fix end-to-end testing — first one
 * intended for the Copilot path. Three confidently-MAJOR rules, each
 * with a distinct fix shape.
 *
 * Not referenced by production code.
 *
 * Expected issues (by rule):
 *   - java:S5411 — auto-unboxing of a `Boolean` parameter risks NPE
 *                   when the caller passes null.
 *   - java:S2737 — catch block rethrows a new exception without
 *                   preserving the original cause (`, e` argument).
 *   - java:S1872 — comparing classes by their name string instead of
 *                   using `instanceof` or class-literal equality.
 */
public record HazardDemo(String label) {

    public boolean process(Boolean enabled) {
        if (enabled) {
            return true;
        }
        return false;
    }

    public int parseValue(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number: " + text);
        }
    }

    public boolean isMatchingType(Object obj) {
        return obj instanceof HazardDemo;
    }
}
