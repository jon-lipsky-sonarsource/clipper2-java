/*******************************************************************************
 * Java port of C# Clipper2 tests                                               *
 * Original Author :  Angus Johnson                                             *
 * License         :  https://www.boost.org/LICENSE_1_0.txt                    *
 *******************************************************************************/

package clipper2;

import clipper2.core.Path64;
import clipper2.core.Paths64;
import clipper2.core.Point64;
import clipper2.offset.ClipperOffset;
import clipper2.offset.EndType;
import clipper2.offset.JoinType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestOffset {

  @Test
  public void testOffsetEmpty() {
    // Should not throw on empty input
    Paths64 solution = new Paths64();
    ClipperOffset offset = new ClipperOffset();
    offset.execute(10, solution);
  }

  @Test
  public void testSquareMiterExpandPolygon() {
    // 100×100 square, Miter join, expand by 10 → 120×120 = 14400
    Path64 square = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Paths64 subj = new Paths64();
    subj.add(square);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Miter, EndType.Polygon);
    Paths64 solution = new Paths64();
    co.execute(10, solution);

    assertEquals(1, solution.size());
    assertEquals(14400.0, Math.abs(Clipper.area(solution)), 1.0);
  }

  @Test
  public void testSquareMiterShrinkPolygon() {
    // 100×100 square, Miter join, shrink by 10 → 80×80 = 6400
    Path64 square = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Paths64 subj = new Paths64();
    subj.add(square);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Miter, EndType.Polygon);
    Paths64 solution = new Paths64();
    co.execute(-10, solution);

    assertEquals(1, solution.size());
    assertEquals(6400.0, Math.abs(Clipper.area(solution)), 1.0);
  }

  @Test
  public void testRoundJoinPolygon() {
    // ~100-vertex circle with r=50, Round join, expand by 10 → r≈60, area ≈ π*60² ≈ 11310
    Path64 circle = Clipper.ellipse(new Point64(0, 0), 50, 50, 100);
    Paths64 subj = new Paths64();
    subj.add(circle);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Round, EndType.Polygon);
    Paths64 solution = new Paths64();
    co.execute(10, solution);

    assertEquals(1, solution.size());
    double area = Math.abs(Clipper.area(solution));
    // π*60² ≈ 11310; allow ±3% for polygon approximation
    assertTrue(area > 10900 && area < 11700,
        "Round-join polygon area out of range: " + area);
  }

  @Test
  public void testOpenPathButtEnds() {
    // Horizontal line (0,0)→(100,0), Square join, Butt end, delta=10
    // Result: rectangle (0,-10)→(100,10), area = 100*20 = 2000
    Path64 line = Clipper.makePath(new long[]{0, 0, 100, 0});
    Paths64 subj = new Paths64();
    subj.add(line);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Square, EndType.Butt);
    Paths64 solution = new Paths64();
    co.execute(10, solution);

    assertEquals(1, solution.size());
    assertEquals(2000.0, Math.abs(Clipper.area(solution)), 1.0);
  }

  @Test
  public void testOpenPathSquareEnds() {
    // Horizontal line (0,0)→(100,0), Square join, Square end, delta=10
    // Result: rectangle (-10,-10)→(110,10), area = 120*20 = 2400
    Path64 line = Clipper.makePath(new long[]{0, 0, 100, 0});
    Paths64 subj = new Paths64();
    subj.add(line);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Square, EndType.Square);
    Paths64 solution = new Paths64();
    co.execute(10, solution);

    assertEquals(1, solution.size());
    assertEquals(2400.0, Math.abs(Clipper.area(solution)), 1.0);
  }

  @Test
  public void testOpenPathRoundEnds() {
    // Horizontal line (0,0)→(100,0), Round join, Round end, delta=10
    // Body: 100*20 = 2000; two semicircles of r=10 add π*r² = π*100 ≈ 314
    // Total ≈ 2314; allow generous tolerance for polygon approximation
    Path64 line = Clipper.makePath(new long[]{0, 0, 100, 0});
    Paths64 subj = new Paths64();
    subj.add(line);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Round, EndType.Round);
    Paths64 solution = new Paths64();
    co.execute(10, solution);

    assertEquals(1, solution.size());
    double area = Math.abs(Clipper.area(solution));
    assertTrue(area > 2200 && area < 2450,
        "Round-end open path area out of range: " + area);
  }

  @Test
  public void testInflatePathsFacade() {
    // Verify Clipper.inflatePaths convenience method produces same result as ClipperOffset
    Path64 square = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Paths64 subj = new Paths64();
    subj.add(square);

    Paths64 solution = Clipper.inflatePaths(subj, 10, JoinType.Miter, EndType.Polygon);

    assertEquals(1, solution.size());
    assertEquals(14400.0, Math.abs(Clipper.area(solution)), 1.0);
  }

  @Test
  public void testDeltaCallbackConstant() {
    // DeltaCallback64 returning a constant should give the same result as execute(delta)
    Path64 square = Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100});
    Paths64 subj = new Paths64();
    subj.add(square);

    ClipperOffset co = new ClipperOffset();
    co.addPaths(subj, JoinType.Miter, EndType.Polygon);
    Paths64 solution = new Paths64();
    co.execute((path, norms, j, k) -> 10.0, solution);

    assertEquals(1, solution.size());
    assertEquals(14400.0, Math.abs(Clipper.area(solution)), 1.0);
  }
}
