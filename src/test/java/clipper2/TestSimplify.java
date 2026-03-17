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
import clipper2.core.Point64;
import clipper2.core.PointD;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestSimplify {

  // -------------------------------------------------------------------------
  // TrimCollinear
  // -------------------------------------------------------------------------

  @Test
  public void testTrimCollinearRemovesMidEdgePoint() {
    // (0,0)→(50,0)→(100,0)→(100,100)→(0,100): (50,0) is collinear, should be removed
    Path64 path = Clipper.makePath(new long[]{0, 0, 50, 0, 100, 0, 100, 100, 0, 100});
    Path64 trimmed = Clipper.trimCollinear(path);

    assertEquals(4, trimmed.size());
    assertEquals(100 * 100, (long) Math.abs(Clipper.area(trimmed)));
  }

  @Test
  public void testTrimCollinearNoChange() {
    // Clean rectangle — no collinear points, no change
    Path64 path = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Path64 trimmed = Clipper.trimCollinear(path);

    assertEquals(4, trimmed.size());
  }

  @Test
  public void testTrimCollinearOpenPath() {
    // Open path: (0,0)→(50,0)→(100,0)→(100,100): (50,0) is collinear
    Path64 path = Clipper.makePath(new long[]{0, 0, 50, 0, 100, 0, 100, 100});
    Path64 trimmed = Clipper.trimCollinear(path, true);

    assertEquals(3, trimmed.size());
  }

  @Test
  public void testTrimCollinearMultipleCollinear() {
    // Three consecutive collinear points on the bottom edge
    Path64 path = Clipper.makePath(
        new long[]{0, 0, 25, 0, 50, 0, 75, 0, 100, 0, 100, 100, 0, 100});
    Path64 trimmed = Clipper.trimCollinear(path);

    assertEquals(4, trimmed.size());
  }

  @Test
  public void testTrimCollinearPathD() {
    // Collinear point on a closed PathD
    PathD path = Clipper.makePath(new double[]{0, 0, 50, 0, 100, 0, 100, 100, 0, 100});
    PathD trimmed = Clipper.trimCollinear(path, 2);

    assertEquals(4, trimmed.size());
  }

  // -------------------------------------------------------------------------
  // SimplifyPath / SimplifyPaths
  // -------------------------------------------------------------------------

  @Test
  public void testSimplifyPathNoChange() {
    // Clean 4-point square: no simplification needed
    Path64 path = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Path64 simplified = Clipper.simplifyPath(path, 1.0);

    assertEquals(4, simplified.size());
  }

  @Test
  public void testSimplifyPathRemovesCollinearPoint() {
    // A collinear mid-point has zero perpendicular distance → removed with any ε > 0
    Path64 path = Clipper.makePath(new long[]{0, 0, 50, 0, 100, 0, 100, 100, 0, 100});
    Path64 simplified = Clipper.simplifyPath(path, 1.0);

    assertEquals(4, simplified.size());
    assertEquals(10000.0, Math.abs(Clipper.area(simplified)), 1.0);
  }

  @Test
  public void testSimplifyPathSmallProtrusion() {
    // A tiny 1-unit bump above the edge — removed at ε=2, kept at ε=0.5
    // Points: (0,0)→(50,0)→(50,1)→(100,0)→(100,100)→(0,100) — the (50,1) is a 1-unit bump
    Path64 path = Clipper.makePath(new long[]{0, 0, 50, 0, 51, 1, 100, 0, 100, 100, 0, 100});

    Path64 kept = Clipper.simplifyPath(path, 0.5);
    assertEquals(6, kept.size(), "Protrusion should be kept at ε=0.5");

    Path64 removed = Clipper.simplifyPath(path, 2.0);
    assertEquals(4, removed.size(), "Protrusion should be removed at ε=2.0");
  }

  @Test
  public void testSimplifyPaths() {
    // Two squares: one clean, one with an extra collinear point each
    Paths64 paths = new Paths64();
    paths.add(Clipper.makePath(new long[]{0, 0, 50, 0, 100, 0, 100, 100, 0, 100}));
    paths.add(Clipper.makePath(new long[]{200, 0, 250, 0, 300, 0, 300, 100, 200, 100}));

    Paths64 simplified = Clipper.simplifyPaths(paths, 1.0);

    assertEquals(2, simplified.size());
    assertEquals(4, simplified.get(0).size());
    assertEquals(4, simplified.get(1).size());
  }

  @Test
  public void testSimplifyPathD() {
    PathD path = Clipper.makePath(new double[]{0, 0, 50, 0, 100, 0, 100, 100, 0, 100});
    PathD simplified = Clipper.simplifyPath(path, 1.0);

    assertEquals(4, simplified.size());
  }

  // -------------------------------------------------------------------------
  // RamerDouglasPeucker
  // -------------------------------------------------------------------------

  @Test
  public void testRDPKeepsEndpoints() {
    // A straight diagonal line with extra midpoints — all midpoints within ε=2 of the line
    Path64 path = new Path64();
    for (int i = 0; i <= 100; i += 10) {
      path.add(new Point64(i, i));  // exact diagonal, no deviation
    }
    Path64 simplified = Clipper.ramerDouglasPeucker(path, 1.0);

    // All interior points lie exactly on the line; only endpoints should remain
    assertEquals(2, simplified.size());
    assertEquals(0L, simplified.get(0).x);
    assertEquals(100L, simplified.get(simplified.size() - 1).x);
  }

  @Test
  public void testRDPPreservesSignificantPoints() {
    // A zigzag: large deviations should NOT be removed
    Path64 path = Clipper.makePath(new long[]{0, 0, 50, 100, 100, 0, 150, 100, 200, 0});
    Path64 simplified = Clipper.ramerDouglasPeucker(path, 5.0);

    // All points deviate significantly from the straight line — all should be kept
    assertEquals(5, simplified.size());
  }

  @Test
  public void testRDPSmallEpsilonPreservesPoints() {
    // Near-straight path with 1-unit bump at (50,1); extra collinear points to reach ≥5 pts
    // (RDP skips paths with <5 points). At ε=0.5 the bump is kept; at ε=2 it is removed.
    Path64 path = Clipper.makePath(new long[]{0, 0, 10, 0, 50, 1, 90, 0, 100, 0});

    Path64 kept = Clipper.ramerDouglasPeucker(path, 0.5);
    assertEquals(3, kept.size(), "Should keep middle point at ε=0.5");

    Path64 removed = Clipper.ramerDouglasPeucker(path, 2.0);
    assertEquals(2, removed.size(), "Should remove middle point at ε=2.0");
  }

  @Test
  public void testRDPPaths64() {
    // Batch version: two redundant lines (≥5 pts each) → both simplified to 2 endpoints
    Paths64 paths = new Paths64();
    paths.add(Clipper.makePath(new long[]{0, 0, 25, 0, 50, 0, 75, 0, 100, 0}));
    paths.add(Clipper.makePath(new long[]{0, 100, 25, 100, 50, 100, 75, 100, 100, 100}));

    Paths64 simplified = Clipper.ramerDouglasPeucker(paths, 1.0);

    assertEquals(2, simplified.size());
    assertEquals(2, simplified.get(0).size());
    assertEquals(2, simplified.get(1).size());
  }

  @Test
  public void testRDPPathD() {
    // 0.5-unit bump at (50,0.5); extra collinear points to reach ≥5 pts.
    // At ε=0.3 the bump is kept; at ε=1.0 it is removed.
    PathD path = Clipper.makePath(new double[]{0, 0, 10, 0, 50.0, 0.5, 90, 0, 100, 0});

    PathD kept = Clipper.ramerDouglasPeucker(path, 0.3);
    assertEquals(3, kept.size(), "Should keep middle point at ε=0.3");

    PathD removed = Clipper.ramerDouglasPeucker(path, 1.0);
    assertEquals(2, removed.size(), "Should remove middle point at ε=1.0");
  }

  @Test
  public void testRDPPathsD() {
    // Two fully collinear paths (≥5 pts each) → both simplified to 2 endpoints
    PathsD paths = new PathsD();
    paths.add(Clipper.makePath(new double[]{0, 0, 25, 0, 50, 0, 75, 0, 100, 0}));
    paths.add(Clipper.makePath(new double[]{0, 0, 0, 25, 0, 50, 0, 75, 0, 100}));

    PathsD simplified = Clipper.ramerDouglasPeucker(paths, 1.0);

    assertEquals(2, simplified.size());
    for (PathD p : simplified) {
      assertEquals(2, p.size());
    }
  }
}
