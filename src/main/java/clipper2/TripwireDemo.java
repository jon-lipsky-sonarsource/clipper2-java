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

    public record Point(int x, int y) {
    }

    // Uses a point value type so the call site isn't a sequence of
    // indistinguishable ints.
    public boolean isMonotonic(Point first, Point second, Point third, Point fourth) {
        return first.x() <= second.x() && second.x() <= third.x() && third.x() <= fourth.x()
            && first.y() <= second.y() && second.y() <= third.y() && third.y() <= fourth.y();
    }

    // Reports both counters required by the format string.
    public String summarize(int total) {
        return String.format("%d items, %d skipped", total, 0);
    }
}
