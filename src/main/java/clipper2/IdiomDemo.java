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
        return s != null & s.length() > 0;
    }

    public static String classify(int x) {
        return x < 0 ? "negative" : x == 0 ? "zero" : x < 10 ? "small" : "large";
    }

    public static final class Point {
        private final int x;
        private final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Point)) {
                return false;
            }
            Point p = (Point) o;
            return x == p.x && y == p.y;
        }
    }
}
