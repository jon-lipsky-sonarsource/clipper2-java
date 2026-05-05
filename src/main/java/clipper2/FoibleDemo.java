package clipper2;

import java.util.Iterator;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists
 * only so SonarCloud has issues to flag and the sonar-fix agent has
 * things to fix. Each method targets a distinct MAJOR-severity Java
 * rule the agent hasn't seen on this repo before.
 */
public class FoibleDemo {

    // Triggers java:S1075 — hardcoded URI literal embedded directly
    // in code. Hard to update across environments, hard to test
    // against fakes, and brittle when the upstream service moves.
    // Fix: extract to a private static final constant, or pull
    // from configuration / an injected dependency.
    public String fetchUrl() {
        return "https://api.example.com/v1/clipper/data";
    }

    // Triggers java:S1148 — Throwable.printStackTrace() dumps to
    // stderr with no severity level, no logger framework context,
    // and no structured fields. In production it's effectively
    // invisible. Fix: replace with a logger call (e.g.
    // LOG.error("parse failed", e)) or rethrow as a wrapped
    // exception with diagnostic context.
    public int parseOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // Triggers java:S134 — control-flow statements nested six deep
    // (if → for → if → if → for → if). Sonar's default threshold
    // is 3. Hard to read, hard to test, easy to introduce subtle
    // bugs in. Fix: extract inner blocks into private helper
    // methods, or invert conditions and use early returns to
    // flatten the structure.
    public int processMatrix(int[][] matrix) {
        int sum = 0;
        if (matrix != null) {
            for (int[] row : matrix) {
                if (row != null) {
                    if (row.length > 0) {
                        for (int v : row) {
                            if (v > 0) {
                                sum += v;
                            }
                        }
                    }
                }
            }
        }
        return sum;
    }

    // Triggers java:S2272 — Iterator.next() should throw
    // NoSuchElementException once the iteration is exhausted, per
    // the Iterator contract. This implementation silently returns
    // -1 instead, so callers that ignore hasNext() (or that catch
    // NoSuchElementException as their loop terminator) silently
    // get a sentinel value mixed into their data stream. Fix:
    // throw NoSuchElementException when current >= to.
    public Iterator<Integer> rangeIterator(int from, int to) {
        return new Iterator<Integer>() {
            private int current = from;

            @Override
            public boolean hasNext() {
                return current < to;
            }

            @Override
            public Integer next() {
                if (current < to) {
                    return current++;
                }
                return -1;
            }
        };
    }
}
