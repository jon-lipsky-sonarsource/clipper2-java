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

    public String greet(String name) {
        if (name == null) {
            return "hello, anonymous";
        }
        if (!name.isEmpty()) {
            return "hello, " + name;
        }
        return "hello, anonymous";
    }

    public byte[] readExactly(InputStream in, int size) throws IOException {
        byte[] buf = new byte[size];
        int n = in.read(buf);
        if (n != size) {
            throw new IOException("short read: got " + n + " of " + size);
        }
        return buf;
    }

    public void requireNonEmpty(String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("string must be non-empty");
        }
    }

    public int scaleAndOffsetDouble(int x) {
        int result = x;
        for (int i = 0; i < 2; i++) {
            result = result * 2 + 1;
        }
        return result;
    }

    public int scaleAndOffsetTriple(int x) {
        return scaleAndOffsetDouble(x);
    }
}
