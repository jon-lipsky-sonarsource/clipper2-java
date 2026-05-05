package clipper2;

import java.io.Serializable;

/**
 * Test fixture for the parallel-3-agents test of the sonar-fix workflow.
 *
 * Three sibling PRs ship this same file and the same bugs but switch
 * `.github/sonar-fix-config.yml` to a different `agent:` value, so we
 * can compare how each agent handles the identical input.
 *
 * Intentionally bad code. The class isn't used anywhere — pure fixture.
 * Each method/element targets a distinct MAJOR-severity Java rule the
 * agent hasn't seen on this repo before.
 */
public class MultiAgentDemo implements Serializable {

    private static final long serialVersionUID = 1L;

    // Triggers java:S1948 — Thread is not Serializable, but this is a
    // non-transient field of a Serializable class. Attempting to
    // serialize an instance would throw NotSerializableException at
    // runtime. Fix shapes: mark the field transient, change the type
    // to something Serializable, or stop the class implementing
    // Serializable.
    private final Thread worker = new Thread(() -> { /* stub */ });

    // Triggers java:S4524 — switch statement without a default branch.
    // Sonar wants every switch to handle the unmatched case explicitly.
    // Fix: add a `default:` arm — either a sensible fallback value, or
    // throw IllegalArgumentException to make the unmatched case loud.
    public String classify(int code) {
        switch (code) {
            case 1:
                return "one";
            case 2:
                return "two";
            case 3:
                return "three";
        }
        return "unknown";
    }

    // Triggers java:S1610 — Base has no fields and only abstract
    // methods, so it's acting as an interface in disguise. Fix:
    // convert `abstract class Base` to `interface Base`. Subtle —
    // also requires updating any `extends` to `implements`.
    public abstract static class Base {
        public abstract int compute(int n);
    }

    // Triggers java:S2065 — `transient` keyword on a field of a class
    // that doesn't implement Serializable. The keyword is meaningless
    // here (transient only affects serialization) and signals
    // confusion about the class's intent. Fix: drop the `transient`
    // modifier, or make the class Serializable if that was the goal.
    public static class Cache {
        private transient String lastKey;

        public String getLastKey() {
            return lastKey;
        }

        public void setLastKey(String key) {
            this.lastKey = key;
        }
    }
}
