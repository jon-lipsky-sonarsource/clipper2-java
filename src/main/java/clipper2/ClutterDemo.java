package clipper2;

import java.io.IOException;
import java.io.InputStream;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists
 * only so SonarCloud has issues to flag and the sonar-fix agent has
 * things to fix. Each method targets a distinct MAJOR-severity Java
 * rule the agent hasn't seen on this repo before.
 */
public class ClutterDemo {

    // Triggers java:S2589 — `name != null` on line 26 is gratuitous.
    // The earlier `if (name == null) return ...` guarantees name is
    // non-null on the second branch, so the second check is always
    // true. Sonar detects this via dataflow analysis. Fix: drop the
    // redundant null check and just test isEmpty().
    public String greet(String name) {
        if (name == null) {
            return "hello, anonymous";
        }
        if (name != null && !name.isEmpty()) {
            return "hello, " + name;
        }
        return "hello, anonymous";
    }

    // Triggers java:S2147 — the catch block does nothing but rethrow
    // the exception it caught. The try-catch wrapper adds noise without
    // adding behavior. Fix: drop the try-catch entirely; the throws
    // declaration already lets IOException propagate.
    public byte[] readExactly(InputStream in, int size) throws IOException {
        try {
            byte[] buf = new byte[size];
            int n = in.read(buf);
            if (n != size) {
                throw new IOException("short read: got " + n + " of " + size);
            }
            return buf;
        } catch (IOException e) {
            throw e;
        }
    }

    // Triggers java:S112 — throwing the bare `RuntimeException` type
    // rather than a specific subclass forces callers to catch the
    // generic supertype (or worse, suppress it). Erodes the type
    // contract. Fix: throw a specific subclass — IllegalArgumentException
    // is the conventional choice for input-validation failures.
    public void requireNonEmpty(String s) {
        if (s == null || s.isEmpty()) {
            throw new RuntimeException("string must be non-empty");
        }
    }

    // Triggers java:S4144 — `scaleAndOffsetDouble` and `scaleAndOffsetTriple`
    // have byte-identical method bodies. The names imply they do
    // different things, but the implementations don't match the names.
    // Sonar flags both methods. Fix shapes vary: have one delegate to
    // the other, extract a private helper, or correct whichever method
    // has the wrong body.
    public int scaleAndOffsetDouble(int x) {
        int result = x;
        for (int i = 0; i < 2; i++) {
            result = result * 2 + 1;
        }
        return result;
    }

    public int scaleAndOffsetTriple(int x) {
        int result = x;
        for (int i = 0; i < 2; i++) {
            result = result * 2 + 1;
        }
        return result;
    }
}
