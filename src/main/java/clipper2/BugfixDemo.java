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
 */
public final class BugfixDemo {

    private static final String WIDGET = "widget";

    private BugfixDemo() {
        // Utility class
    }

    public static void processWidgets() {
        process(WIDGET);
        process(WIDGET);
        process(WIDGET);
    }

    public static String readFirstLine(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            return reader.readLine();
        }
    }

    public static String greet(String name) {
        return "Hello, " + name;
    }

    public static int compute(int x) {
        return x * 3;
    }

    private static void process(String name) {
        // pretend to do something with name
        if (name.length() > 100) {
            throw new IllegalArgumentException("name too long: " + name);
        }
    }
}
