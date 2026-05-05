package clipper2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Fourth sample class for sonar-fix end-to-end testing. Targets a mix
 * of behavior-bug rules and redundancy patterns the agent hasn't seen
 * on this repo before.
 *
 * Not referenced by production code.
 *
 * Expected issues (by rule):
 *   - java:S1217 — `Thread.run()` called instead of `Thread.start()`
 *                   (the run() call executes synchronously on the calling
 *                   thread, defeating the purpose of using a Thread)
 *   - java:S1858 — `.toString()` on a String is redundant
 *   - java:S1141 — nested try blocks should be combined
 *   - java:S2225 — `toString()` should never return null
 */
public final class BehaviorDemo {

    private BehaviorDemo() {
        // Utility class
    }

    public static void runInBackground(Runnable task) {
        Thread t = new Thread(task);
        t.start();
    }

    public static String describe(String name) {
        return "Name: " + name.toString();
    }

    public static String readNested(String outerFile, String innerFile) throws IOException {
        try (BufferedReader outer = new BufferedReader(new FileReader(outerFile))) {
            String firstLine = outer.readLine();
            try (BufferedReader inner = new BufferedReader(new FileReader(innerFile))) {
                return firstLine + ":" + inner.readLine();
            }
        }
    }

    public static class Marker {
        private final String label;

        public Marker(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            if (label == null) {
                return "";
            }
            return label;
        }
    }
}
