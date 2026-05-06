package clipper2;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Test fixture for the Codex/Claude post-extraheader-fix verification.
 *
 * Submitted as parallel sibling PRs that differ only in the `agent:`
 * value of `.github/sonar-fix-config.yml`, so we can confirm that
 * sonar-fix#11's extraheader unset actually unblocks Codex's loop
 * the same way Claude's already iterates.
 *
 * Intentionally bad code. The class isn't used anywhere — pure fixture.
 */
public class CheckDemo {

    private static final Logger LOG = Logger.getLogger(CheckDemo.class.getName());

    // Triggers java:S2390 — CheckDemo's static initializer references
    // ChildOfCheck.CHILD_CONSTANT, but ChildOfCheck is a subclass of
    // CheckDemo. The JVM's class-init ordering says ChildOfCheck
    // can't fully initialize until CheckDemo does, but CheckDemo's
    // init is currently in progress and depending on the subclass.
    // Result: the value can be observed as 0 (the default for int)
    // before ChildOfCheck's own static init runs. Real behavior bug.
    // Fix: move CHILD_CONSTANT to CheckDemo (or a non-subclass), or
    // remove the dependency entirely.
    public static final int CHILD_VALUE = ChildOfCheck.CHILD_CONSTANT;

    // Triggers java:S2629 — building the log message via string
    // concatenation pays the formatting cost even when the log level
    // is filtered out. Fix: parameterized logging
    // (LOG.log(Level.INFO, "...{0}...", user)) so the message is
    // assembled only when the log is actually emitted.
    public void logLogin(String user) {
        LOG.info("user " + user + " logged in at " + System.currentTimeMillis());
    }

    // Triggers java:S2147 — the catch block does nothing but rethrow
    // the same exception it caught. Useless wrapping. Fix: drop the
    // try-catch; the throws declaration already lets IOException
    // propagate.
    public byte[] readBytes(InputStream in, int size) throws IOException {
        try {
            byte[] buf = new byte[size];
            int n = in.read(buf);
            if (n != size) {
                throw new IOException("short read");
            }
            return buf;
        } catch (IOException e) {
            throw e;
        }
    }

    // Triggers java:S1148 — Throwable.printStackTrace() dumps to
    // stderr without logger context or severity. Fix: replace with a
    // logger call, or rethrow as a wrapped exception with diagnostic
    // context.
    public int parseOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }
}

class ChildOfCheck extends CheckDemo {
    public static final int CHILD_CONSTANT = 42;
}
