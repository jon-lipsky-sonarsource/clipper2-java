package clipper2;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Test fixture for the Codex path of the sonar-fix workflow.
 *
 * Intentionally bad code. The class isn't used anywhere — it exists
 * only so SonarCloud has issues to flag and the sonar-fix agent has
 * things to fix. Each method/field targets a distinct MAJOR-severity
 * Java rule the agent hasn't seen on this repo before.
 */
public class LapseDemo {

    // Triggers java:S2386 — a mutable public static field exposes the
    // contents to direct mutation by any caller. Common fix shapes:
    // make the field private + provide an accessor, wrap in
    // Collections.unmodifiableList, or change to an immutable List.of().
    public static List<String> ALLOWED_TYPES = new ArrayList<>();

    // Triggers java:S2885 — SimpleDateFormat is not thread-safe, so
    // sharing one as a static field across threads can corrupt parsing
    // or formatting state. Fix shapes: switch to DateTimeFormatter
    // (java.time, immutable), construct per-call, or wrap in ThreadLocal.
    private static final SimpleDateFormat ISO_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd");

    // Triggers java:S2696 — recordProcessed() is an instance method
    // but writes to a static field, polluting shared state across all
    // instances of the class. Fix shapes: convert to an instance field
    // (preferred), or rename to a static method to make the class-level
    // intent explicit.
    private static int processedCount = 0;

    public void recordProcessed() {
        processedCount++;
    }

    public String formatDate(Date date) {
        return ISO_DATE_FORMAT.format(date);
    }

    // Triggers java:S2674 — InputStream.read(byte[]) may return fewer
    // bytes than requested without reaching EOF. Ignoring the return
    // value silently produces a buffer with partial or undefined
    // trailing bytes. Fix shapes: loop until size bytes are read, use
    // InputStream.readNBytes(int), or DataInputStream.readFully().
    public byte[] readFully(InputStream in, int size) throws IOException {
        byte[] buffer = new byte[size];
        in.read(buffer);
        return buffer;
    }
}
