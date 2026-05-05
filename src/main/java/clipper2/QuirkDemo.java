package clipper2;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists
 * only so SonarCloud has issues to flag and the sonar-fix agent has
 * things to fix. Each method targets a distinct MAJOR-severity Java
 * rule the agent hasn't seen on this repo before.
 */
public class QuirkDemo {

    private static QuirkDemo INSTANCE;
    private int payload;

    public static synchronized QuirkDemo getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new QuirkDemo();
        }
        return INSTANCE;
    }

    public Boolean hasPayload() {
        if (payload == 0) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    // Triggers java:S2189 — the while loop's condition never becomes
    // false because `n` is never modified inside the body. Once
    // entered (start > 0), the method spins forever. Fix: decrement
    // `n` (or otherwise change a value the condition depends on), or
    // add a `break` on the right termination signal.
    public int sumDownTo(int start) {
        int n = start;
        int total = 0;
        while (n > 0) {
            total = total + n;
            // forgot to decrement n
        }
        return total;
    }

    // Triggers java:S2197 — `x % 2 == 1` is always false for negative
    // odd numbers. In Java -3 % 2 evaluates to -1, not 1, so isOdd(-3)
    // returns false. Fix: compare against `!= 0` instead of `== 1`,
    // or use Math.floorMod for sign-correct modular arithmetic.
    public boolean isOdd(int x) {
        return x % 2 == 1;
    }
}
