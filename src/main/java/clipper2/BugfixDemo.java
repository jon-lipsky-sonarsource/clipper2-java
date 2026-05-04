package clipper2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Sample class with intentional SonarQube issues used to exercise the
 * sonar-fix workflow end-to-end. Targets a different mix of rules than
 * SonarFixDemo (which was already fixed) so we can verify the agent
 * handles refactoring patterns it hasn't seen on this repo before.
 *
 * Not referenced anywhere in the production code.
 *
 * Expected issues (by rule):
 *   - java:S1135 — TODO comments need follow-up (allow-listed)
 *   - java:S2095 — resources should be closed (BufferedReader leak)
 *   - java:S1192 — duplicate string literal "widget" appears 3+ times
 *   - java:S2129 — redundant `new String("...")` constructor
 *   - java:S125  — commented-out code should be removed
 */
public final class BugfixDemo {

    private BugfixDemo() {
        // Utility class
    }

    public static void processWidgets() {
        // TODO: add caching to avoid the repeated process() calls
        process("widget");
        process("widget");
        process("widget");
    }

    public static String readFirstLine(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        return reader.readLine();
    }

    public static String greet(String name) {
        return new String("Hello, ") + name;
    }

    public static int compute(int x) {
        // int oldResult = x * 2;
        // return oldResult + 5;
        return x * 3;
    }

    private static void process(String name) {
        // pretend to do something with name
        if (name.length() > 100) {
            throw new IllegalArgumentException("name too long: " + name);
        }
    }
}
