/*******************************************************************************
 * Java port of C# Clipper2 tests                                               *
 * Original Author :  Angus Johnson                                             *
 * License         :  https://www.boost.org/LICENSE_1_0.txt                    *
 *******************************************************************************/

package clipper2;

import clipper2.core.*;
import clipper2.engine.Clipper64;
import clipper2.utils.ClipperFileIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestLines {

  private static String getTestDataPath(String filename) {
    java.net.URL resource = TestLines.class.getResource("/");
    if (resource != null) {
      try {
        java.io.File dir = new java.io.File(resource.toURI());
        for (int i = 0; i < 10; i++) {
          java.io.File candidate = new java.io.File(dir, "Tests/" + filename);
          if (candidate.exists()) return candidate.getAbsolutePath();
          dir = dir.getParentFile();
          if (dir == null) break;
        }
      } catch (Exception ignored) {}
    }
    return "../Tests/" + filename;
  }

  @Test
  public void testOpenPaths() {
    String dataFile = getTestDataPath("Lines.txt");

    for (int i = 0; i <= 16; i++) {
      Clipper64 c64 = new Clipper64();
      Paths64 subj = new Paths64();
      Paths64 subjOpen = new Paths64();
      Paths64 clip = new Paths64();
      Paths64 solution = new Paths64();
      Paths64 solutionOpen = new Paths64();

      ClipperFileIO.TestCase tc = ClipperFileIO.loadTestNum(dataFile, i, subj, subjOpen, clip);
      assertTrue(tc.found, String.format("Loading test %d failed.", i));

      c64.addSubject(subj);
      c64.addOpenSubject(subjOpen);
      c64.addClip(clip);
      c64.execute(tc.clipType, tc.fillRule, solution, solutionOpen);

      if (tc.area > 0) {
        double area2 = Clipper.area(solution);
        if (area2 != 0) {
          double a = tc.area / area2;
          assertTrue(a > 0.995 && a < 1.005,
              String.format("Incorrect area in test %d: ratio=%.4f", i, a));
        }
      }

      if (tc.count > 0) {
        int diff = Math.abs(solution.size() - tc.count);
        assertTrue(diff < 2,
            String.format("Incorrect count in test %d: expected=%d got=%d",
                i, tc.count, solution.size()));
      }
    }
  }

}
