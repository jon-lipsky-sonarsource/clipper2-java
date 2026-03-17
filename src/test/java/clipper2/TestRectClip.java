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
import clipper2.core.Rect64;
import clipper2.core.RectD;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestRectClip {

  // -------------------------------------------------------------------------
  // rectClip(Rect64, Path64)
  // -------------------------------------------------------------------------

  @Test
  public void testRectClipPathLargerThanRect() {
    // Path completely contains the rect → result is the rect, area = 100*100 = 10000
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 path = Clipper.makePath(new long[]{-50, -50, 150, -50, 150, 150, -50, 150});
    Paths64 sol = Clipper.rectClip(rect, path);

    assertEquals(1, sol.size());
    assertEquals(10000.0, Math.abs(Clipper.area(sol)), 1.0);
  }

  @Test
  public void testRectClipPathInsideRect() {
    // Path completely inside rect → result is the path unchanged, area = 80*80 = 6400
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 path = Clipper.makePath(new long[]{10, 10, 90, 10, 90, 90, 10, 90});
    Paths64 sol = Clipper.rectClip(rect, path);

    assertEquals(1, sol.size());
    assertEquals(6400.0, Math.abs(Clipper.area(sol)), 1.0);
  }

  @Test
  public void testRectClipPathOutsideRect() {
    // Path entirely outside rect → empty result
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 path = Clipper.makePath(new long[]{200, 200, 300, 200, 300, 300, 200, 300});
    Paths64 sol = Clipper.rectClip(rect, path);

    assertTrue(sol.isEmpty());
  }

  @Test
  public void testRectClipPathPartialOverlap() {
    // Path overlaps only the left half of the rect → area = 50*100 = 5000
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 path = Clipper.makePath(new long[]{-50, 0, 50, 0, 50, 100, -50, 100});
    Paths64 sol = Clipper.rectClip(rect, path);

    assertEquals(1, sol.size());
    assertEquals(5000.0, Math.abs(Clipper.area(sol)), 1.0);
  }

  // -------------------------------------------------------------------------
  // rectClip(Rect64, Paths64)
  // -------------------------------------------------------------------------

  @Test
  public void testRectClipMultiplePaths() {
    // Two non-overlapping paths each cover half the rect
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Paths64 paths = new Paths64();
    paths.add(Clipper.makePath(new long[]{0, 0, 50, 0, 50, 100, 0, 100}));   // left half
    paths.add(Clipper.makePath(new long[]{50, 0, 100, 0, 100, 100, 50, 100})); // right half

    Paths64 sol = Clipper.rectClip(rect, paths);

    assertEquals(2, sol.size());
    double totalArea = 0;
    for (Path64 p : sol) totalArea += Math.abs(Clipper.area(p));
    assertEquals(10000.0, totalArea, 1.0);
  }

  @Test
  public void testRectClipEmptyPaths() {
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Paths64 sol = Clipper.rectClip(rect, new Paths64());
    assertTrue(sol.isEmpty());
  }

  // -------------------------------------------------------------------------
  // rectClip(RectD, PathD) and rectClip(RectD, PathsD)
  // -------------------------------------------------------------------------

  @Test
  public void testRectClipPathD() {
    RectD rect = new RectD(0, 0, 100, 100);
    PathD path = Clipper.makePath(new double[]{-50, -50, 150, -50, 150, 150, -50, 150});
    PathsD sol = Clipper.rectClip(rect, path);

    assertEquals(1, sol.size());
    assertEquals(10000.0, Math.abs(Clipper.area(sol)), 1.0);
  }

  @Test
  public void testRectClipPathsD() {
    RectD rect = new RectD(0, 0, 100, 100);
    PathsD paths = new PathsD();
    paths.add(Clipper.makePath(new double[]{-50, -50, 150, -50, 150, 150, -50, 150}));
    PathsD sol = Clipper.rectClip(rect, paths);

    assertEquals(1, sol.size());
    assertEquals(10000.0, Math.abs(Clipper.area(sol)), 1.0);
  }

  // -------------------------------------------------------------------------
  // rectClipLines(Rect64, Path64)
  // -------------------------------------------------------------------------

  @Test
  public void testRectClipLinesHorizontalThrough() {
    // Horizontal line from (-50,50) to (150,50) → clipped to (0,50)→(100,50)
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 line = Clipper.makePath(new long[]{-50, 50, 150, 50});
    Paths64 sol = Clipper.rectClipLines(rect, line);

    assertEquals(1, sol.size());
    assertEquals(2, sol.get(0).size());
    assertEquals(0L, sol.get(0).get(0).x);
    assertEquals(50L, sol.get(0).get(0).y);
    assertEquals(100L, sol.get(0).get(1).x);
    assertEquals(50L, sol.get(0).get(1).y);
  }

  @Test
  public void testRectClipLinesEntirelyInside() {
    // Line entirely within rect → returned unchanged
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 line = Clipper.makePath(new long[]{10, 50, 90, 50});
    Paths64 sol = Clipper.rectClipLines(rect, line);

    assertEquals(1, sol.size());
    assertEquals(2, sol.get(0).size());
    assertEquals(10L, sol.get(0).get(0).x);
    assertEquals(90L, sol.get(0).get(1).x);
  }

  @Test
  public void testRectClipLinesEntirelyOutside() {
    // Line entirely outside rect → empty result
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Path64 line = Clipper.makePath(new long[]{-50, 50, -10, 50});
    Paths64 sol = Clipper.rectClipLines(rect, line);

    assertTrue(sol.isEmpty());
  }

  @Test
  public void testRectClipLinesMultiple() {
    // Two parallel horizontal lines through the rect
    Rect64 rect = new Rect64(0, 0, 100, 100);
    Paths64 lines = new Paths64();
    lines.add(Clipper.makePath(new long[]{-50, 25, 150, 25}));
    lines.add(Clipper.makePath(new long[]{-50, 75, 150, 75}));
    Paths64 sol = Clipper.rectClipLines(rect, lines);

    assertEquals(2, sol.size());
    // Each clipped line runs from x=0 to x=100
    for (Path64 p : sol) {
      assertEquals(2, p.size());
      assertEquals(0L, p.get(0).x);
      assertEquals(100L, p.get(1).x);
    }
  }

  @Test
  public void testRectClipLinesPathD() {
    RectD rect = new RectD(0, 0, 100, 100);
    PathD line = Clipper.makePath(new double[]{-50, 50, 150, 50});
    PathsD sol = Clipper.rectClipLines(rect, line);

    assertEquals(1, sol.size());
    assertEquals(2, sol.get(0).size());
    assertEquals(0.0, sol.get(0).get(0).x, 0.5);
    assertEquals(100.0, sol.get(0).get(1).x, 0.5);
  }
}
