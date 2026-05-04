package clipper2;

/**
 * Sample class with intentional SonarQube issues used to exercise the
 * sonar-fix workflow end-to-end.
 *
 * Each method below is designed to trigger one or more well-known Java
 * rules from SonarCloud's default profile. The class is not referenced
 * anywhere in the codebase — it exists only so the agent has something
 * concrete to fix in a PR.
 *
 * Expected issues (by rule):
 *   - java:S1118 — utility class should not have a public default constructor
 *   - java:S1481 — unused local variables (twice)
 *   - java:S106  — replace System.out with a logger
 *   - java:S1854 — dead store (assignment overwritten before use)
 *   - java:S1186 — empty method body
 *   - java:S1172 — unused method parameter
 */
public class SonarFixDemo {

    public static void demonstrateUnusedVariable() {
        String unused = "this is never used";
        int alsoUnused = 42;
        System.out.println("hello from the demo");
    }

    public static int demonstrateDeadStore(int input) {
        int result = input * 2;
        result = input * 3;
        return result;
    }

    public static void demonstrateEmptyMethod() {
    }

    public static int demonstrateUnusedParameter(int useful, int unusedParam) {
        return useful * 2;
    }
}
