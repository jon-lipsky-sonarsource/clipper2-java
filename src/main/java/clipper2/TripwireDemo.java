package clipper2;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists
 * only so SonarCloud has issues to flag and the sonar-fix agent has
 * things to fix. Each method targets a distinct MAJOR-severity Java
 * rule the agent hasn't seen on this repo before.
 */
public class TripwireDemo {

    private final ReentrantLock lock = new ReentrantLock();
    private int counter;

    // Triggers java:S2222 — the lock is acquired and released directly
    // without a try-finally block. If the body between lock() and
    // unlock() throws (e.g. an arithmetic overflow, an interrupt
    // converting to RuntimeException, etc.), unlock() is skipped and
    // the lock leaks for the rest of the JVM's life. Fix: wrap the
    // critical section in try-finally with unlock() in the finally.
    public int incrementUnsafe(int delta) {
        lock.lock();
        counter += delta;
        if (counter < 0) {
            throw new IllegalStateException("counter overflow");
        }
        lock.unlock();
        return counter;
    }

    // Triggers java:S128 — case 2 falls through to case 3 because the
    // break is missing. Looks intentional from a glance ("share the
    // return value") but reads as a bug to maintainers and to Sonar.
    // Fix: add an explicit break after the inner statement, OR add a
    // `// fall through` comment that Sonar recognizes as intentional.
    public String classify(int code) {
        switch (code) {
            case 1:
                return "one";
            case 2:
                System.setProperty("classify.last", "two");
                // (missing break — falls through to case 3)
            case 3:
                return "two-or-three";
            default:
                return "unknown";
        }
    }

    // Triggers java:S107 — the method takes more parameters than the
    // default Sonar threshold (7). Eight unrelated coordinates is the
    // classic anti-pattern; the fix is usually to introduce a
    // parameter-object class (e.g. a Quadrilateral record) so the
    // call site stops being a sequence of indistinguishable ints.
    public boolean isMonotonic(int x1, int y1, int x2, int y2,
                               int x3, int y3, int x4, int y4) {
        return x1 <= x2 && x2 <= x3 && x3 <= x4
            && y1 <= y2 && y2 <= y3 && y3 <= y4;
    }

    // Triggers java:S2275 — the format string has two %d specifiers
    // but only one argument is supplied. java.util.Formatter throws
    // MissingFormatArgumentException at runtime; Sonar catches it
    // statically. Real behavior bug. Fix: pass the missing argument
    // (or remove the unused specifier).
    public String summarize(int total) {
        return String.format("%d items, %d skipped", total);
    }
}
