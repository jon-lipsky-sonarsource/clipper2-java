/*******************************************************************************
 * Java port of C# Clipper2 tests                                               *
 * Original Author :  Angus Johnson                                             *
 * License         :  https://www.boost.org/LICENSE_1_0.txt                    *
 *******************************************************************************/

package clipper2;

import clipper2.core.Path64;
import clipper2.core.PathD;
import clipper2.core.Paths64;
import clipper2.core.PathsD;
import clipper2.minkowski.Minkowski;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestMinkowski {

  // -------------------------------------------------------------------------
  // Minkowski Sum
  // -------------------------------------------------------------------------

  @Test
  public void testMinkowskiSumPath64() {
    // A ⊕ B where A = 10×10 square and B = 5×5 square (both starting at origin).
    // The algorithm sweeps A's boundary along B's boundary, producing a frame shape:
    // outer 15×15 minus inner 5×5 = 225 - 25 = 200.
    Path64 pattern = Clipper.makePath(new long[]{0, 0, 10, 0, 10, 10, 0, 10});
    Path64 path    = Clipper.makePath(new long[]{0, 0,  5, 0,  5,  5, 0,  5});

    Paths64 result = Minkowski.sum(pattern, path, true);

    assertFalse(result.isEmpty(), "Sum result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiSumFacadeMethod() {
    // Verify Clipper.minkowskiSum delegates correctly
    Path64 pattern = Clipper.makePath(new long[]{0, 0, 10, 0, 10, 10, 0, 10});
    Path64 path    = Clipper.makePath(new long[]{0, 0,  5, 0,  5,  5, 0,  5});

    Paths64 result = Clipper.minkowskiSum(pattern, path, true);

    assertFalse(result.isEmpty(), "Facade sum result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiSumPathD() {
    PathD pattern = Clipper.makePath(new double[]{0, 0, 10, 0, 10, 10, 0, 10});
    PathD path    = Clipper.makePath(new double[]{0, 0,  5, 0,  5,  5, 0,  5});

    PathsD result = Minkowski.sum(pattern, path, true);

    assertFalse(result.isEmpty(), "PathD sum result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiSumFacadeMethodD() {
    PathD pattern = Clipper.makePath(new double[]{0, 0, 10, 0, 10, 10, 0, 10});
    PathD path    = Clipper.makePath(new double[]{0, 0,  5, 0,  5,  5, 0,  5});

    PathsD result = Clipper.minkowskiSum(pattern, path, true);

    assertFalse(result.isEmpty(), "Facade PathD sum result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiSumOpenPath() {
    // isClosed=false: pattern swept along an open path
    Path64 pattern = Clipper.makePath(new long[]{0, 0, 5, 0, 5, 5, 0, 5});
    Path64 path    = Clipper.makePath(new long[]{0, 0, 20, 0});  // horizontal open line

    Paths64 result = Minkowski.sum(pattern, path, false);

    // The pattern sweeps along the line: result covers at least 20*5 + 5*5 = 125 area
    assertFalse(result.isEmpty(), "Open-path sum result should not be empty");
    double area = Math.abs(Clipper.area(result));
    assertTrue(area > 100, "Open-path sum area should be > 100, got: " + area);
  }

  // -------------------------------------------------------------------------
  // Minkowski Difference
  // -------------------------------------------------------------------------

  @Test
  public void testMinkowskiDiffPath64() {
    // A - B where A = 10×10, B = 5×5 (both starting at origin).
    // Same frame behaviour as sum: produces outer 15×15 minus inner 5×5 = 200.
    Path64 pattern = Clipper.makePath(new long[]{0, 0, 10, 0, 10, 10, 0, 10});
    Path64 path    = Clipper.makePath(new long[]{0, 0,  5, 0,  5,  5, 0,  5});

    Paths64 result = Minkowski.diff(pattern, path, true);

    assertFalse(result.isEmpty(), "Diff result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiDiffFacadeMethod() {
    Path64 pattern = Clipper.makePath(new long[]{0, 0, 10, 0, 10, 10, 0, 10});
    Path64 path    = Clipper.makePath(new long[]{0, 0,  5, 0,  5,  5, 0,  5});

    Paths64 result = Clipper.minkowskiDiff(pattern, path, true);

    assertFalse(result.isEmpty(), "Facade diff result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }

  @Test
  public void testMinkowskiDiffPathD() {
    PathD pattern = Clipper.makePath(new double[]{0, 0, 10, 0, 10, 10, 0, 10});
    PathD path    = Clipper.makePath(new double[]{0, 0,  5, 0,  5,  5, 0,  5});

    PathsD result = Clipper.minkowskiDiff(pattern, path, true);

    assertFalse(result.isEmpty(), "PathD diff result should not be empty");
    assertEquals(200.0, Math.abs(Clipper.area(result)), 10.0);
  }
}
