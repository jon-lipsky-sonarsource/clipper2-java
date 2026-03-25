# Feature 2 — Bezier Curve Flattening

## Summary

This feature adds a new package `clipper2.curves` containing utilities to
convert quadratic and cubic Bezier curve segments into polygonal approximations
(polylines) at a caller-specified tolerance. The resulting `PathD` objects can
then be fed directly into the existing clipping and offsetting APIs.

## Justification

Clipper2 operates exclusively on polygonal paths — sequences of straight-line
segments. Every real-world vector graphics pipeline (SVG, PDF, PostScript,
TrueType/OpenType fonts, drawing frameworks such as Android's `Path`,
JavaFX `Shape`, AWT `GeneralPath`) represents curves as Bezier segments.
Without a flattening step, callers must implement their own subdivision before
using this library.

Providing a high-quality, tolerance-controlled flattening implementation as a
first-class part of the library:

- Eliminates boilerplate in every consuming application.
- Ensures consistent quality (adaptive subdivision avoids both over- and
  under-tessellation).
- Forms the prerequisite for Feature 3 (SVG path parsing).
- Aligns the library with JTS Topology Suite and Shapely, both of which offer
  similar facilities.

---

## Technical Specification

### Package

`clipper2.curves`

### New Class: `BezierFlattener`

```java
package clipper2.curves;

import clipper2.core.PathD;
import clipper2.core.PointD;

/**
 * Converts quadratic and cubic Bezier curve segments to polygonal
 * approximations using adaptive de Casteljau subdivision.
 *
 * <p>The {@code tolerance} parameter controls the maximum permissible
 * perpendicular distance between the true curve and the approximating
 * polyline. A value of {@code 0.25} (quarter of a display pixel) is a
 * reasonable default for screen rendering; tighter values (e.g., {@code 0.01})
 * are appropriate for high-resolution output or print.
 */
public final class BezierFlattener {

    /** Default flatness tolerance in the same units as the coordinates. */
    public static final double DEFAULT_TOLERANCE = 0.25;

    /**
     * Maximum recursion depth for subdivision. Bounds stack usage and prevents
     * infinite loops on degenerate inputs. At depth 32 the subdivision
     * interval is 2^-32 ≈ 2.3e-10 of the original curve length, which is
     * well below any practical tolerance.
     */
    private static final int MAX_DEPTH = 32;

    private BezierFlattener() {}

    // -----------------------------------------------------------------------
    // Cubic Bezier (four control points)
    // -----------------------------------------------------------------------

    /**
     * Approximates the cubic Bezier curve defined by {@code p0}, {@code p1},
     * {@code p2}, {@code p3} and appends the approximation to {@code out}.
     *
     * <p>The start point {@code p0} is NOT appended (the caller is responsible
     * for having already added the previous endpoint). The end point
     * {@code p3} IS appended as the final point.
     *
     * @param p0        start point
     * @param p1        first control point
     * @param p2        second control point
     * @param p3        end point
     * @param tolerance maximum perpendicular deviation; must be &gt; 0
     * @param out       destination path; points are appended in order
     */
    public static void flattenCubic(PointD p0, PointD p1,
                                    PointD p2, PointD p3,
                                    double tolerance, PathD out) {
        if (tolerance <= 0) throw new IllegalArgumentException("tolerance must be > 0");
        subdivideCubic(p0.x, p0.y, p1.x, p1.y,
                       p2.x, p2.y, p3.x, p3.y,
                       tolerance * tolerance, out, 0);
    }

    /**
     * Convenience overload that creates and returns a new {@link PathD}.
     * The start point {@code p0} is included as the first element.
     */
    public static PathD flattenCubic(PointD p0, PointD p1,
                                     PointD p2, PointD p3,
                                     double tolerance) {
        PathD result = new PathD();
        result.add(new PointD(p0.x, p0.y));
        flattenCubic(p0, p1, p2, p3, tolerance, result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Quadratic Bezier (three control points)
    // -----------------------------------------------------------------------

    /**
     * Approximates the quadratic Bezier curve defined by {@code p0},
     * {@code p1}, {@code p2} and appends the result to {@code out}.
     *
     * <p>The start point {@code p0} is NOT appended. The end point
     * {@code p2} IS appended.
     *
     * @param p0        start point
     * @param p1        control point
     * @param p2        end point
     * @param tolerance maximum perpendicular deviation; must be &gt; 0
     * @param out       destination path
     */
    public static void flattenQuadratic(PointD p0, PointD p1, PointD p2,
                                        double tolerance, PathD out) {
        if (tolerance <= 0) throw new IllegalArgumentException("tolerance must be > 0");
        subdivideQuadratic(p0.x, p0.y, p1.x, p1.y, p2.x, p2.y,
                           tolerance * tolerance, out, 0);
    }

    /**
     * Convenience overload that creates and returns a new {@link PathD}.
     * The start point {@code p0} is included as the first element.
     */
    public static PathD flattenQuadratic(PointD p0, PointD p1, PointD p2,
                                         double tolerance) {
        PathD result = new PathD();
        result.add(new PointD(p0.x, p0.y));
        flattenQuadratic(p0, p1, p2, tolerance, result);
        return result;
    }

    // -----------------------------------------------------------------------
    // Private subdivision helpers
    // -----------------------------------------------------------------------

    /**
     * Recursive adaptive subdivision for a cubic Bezier.
     * Uses squared tolerance to avoid a square-root per iteration.
     */
    private static void subdivideCubic(
            double x0, double y0, double x1, double y1,
            double x2, double y2, double x3, double y3,
            double tolSq, PathD out, int depth) {

        if (depth >= MAX_DEPTH || isFlatCubic(x0, y0, x1, y1, x2, y2, x3, y3, tolSq)) {
            out.add(new PointD(x3, y3));
            return;
        }

        // de Casteljau subdivision at t = 0.5
        double mx01 = (x0 + x1) * 0.5, my01 = (y0 + y1) * 0.5;
        double mx12 = (x1 + x2) * 0.5, my12 = (y1 + y2) * 0.5;
        double mx23 = (x2 + x3) * 0.5, my23 = (y2 + y3) * 0.5;
        double mx012 = (mx01 + mx12) * 0.5, my012 = (my01 + my12) * 0.5;
        double mx123 = (mx12 + mx23) * 0.5, my123 = (my12 + my23) * 0.5;
        double mx0123 = (mx012 + mx123) * 0.5, my0123 = (my012 + my123) * 0.5;

        subdivideCubic(x0, y0, mx01, my01, mx012, my012, mx0123, my0123,
                       tolSq, out, depth + 1);
        subdivideCubic(mx0123, my0123, mx123, my123, mx23, my23, x3, y3,
                       tolSq, out, depth + 1);
    }

    /**
     * Flatness test for a cubic Bezier.
     *
     * <p>Uses the conservative criterion from the PostScript Language Reference
     * Manual: a cubic is considered flat when the sum of the squared distances
     * from the two control points to the chord P0-P3 is less than
     * {@code 16 * tolSq} (the factor 16 accounts for the worst-case deviation
     * from the chord in the cubic case).
     */
    private static boolean isFlatCubic(
            double x0, double y0, double x1, double y1,
            double x2, double y2, double x3, double y3,
            double tolSq) {
        double ux = 3.0 * x1 - 2.0 * x0 - x3;
        double uy = 3.0 * y1 - 2.0 * y0 - y3;
        double vx = 3.0 * x2 - 2.0 * x3 - x0;
        double vy = 3.0 * y2 - 2.0 * y3 - y0;
        double uSq = ux * ux + uy * uy;
        double vSq = vx * vx + vy * vy;
        return Math.max(uSq, vSq) <= 16.0 * tolSq;
    }

    /**
     * Recursive adaptive subdivision for a quadratic Bezier.
     */
    private static void subdivideQuadratic(
            double x0, double y0, double x1, double y1,
            double x2, double y2,
            double tolSq, PathD out, int depth) {

        if (depth >= MAX_DEPTH || isFlatQuadratic(x0, y0, x1, y1, x2, y2, tolSq)) {
            out.add(new PointD(x2, y2));
            return;
        }

        double mx01 = (x0 + x1) * 0.5, my01 = (y0 + y1) * 0.5;
        double mx12 = (x1 + x2) * 0.5, my12 = (y1 + y2) * 0.5;
        double mx012 = (mx01 + mx12) * 0.5, my012 = (my01 + my12) * 0.5;

        subdivideQuadratic(x0, y0, mx01, my01, mx012, my012, tolSq, out, depth + 1);
        subdivideQuadratic(mx012, my012, mx12, my12, x2, y2, tolSq, out, depth + 1);
    }

    /**
     * Flatness test for a quadratic Bezier.
     * The control point P1 must be within {@code tolSq} squared units of the
     * chord P0-P2.
     */
    private static boolean isFlatQuadratic(
            double x0, double y0, double x1, double y1,
            double x2, double y2, double tolSq) {
        double dx = x2 - x0, dy = y2 - y0;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-14) {
            // Degenerate: start == end — check P1 distance to P0
            double ex = x1 - x0, ey = y1 - y0;
            return ex * ex + ey * ey <= tolSq;
        }
        // Perpendicular distance from P1 to line P0-P2, squared
        double cross = (x1 - x0) * dy - (y1 - y0) * dx;
        return (cross * cross / lenSq) <= tolSq;
    }
}
```

### New Class: `CurveSegment` (Value Type for Mixed Paths)

When a caller has a mixed path containing both lines and curves (common in
SVG), a `CurveSegment` type captures one segment:

```java
package clipper2.curves;

import clipper2.core.PointD;

/**
 * Represents a single segment in a mixed path (line or Bezier curve).
 * Used as the element type for {@link CurvePath}.
 */
public sealed interface CurveSegment
        permits CurveSegment.Line,
                CurveSegment.QuadraticBezier,
                CurveSegment.CubicBezier {

    /**
     * Returns the endpoint of this segment (the point added to the path
     * after this segment).
     */
    PointD endPoint();

    /** Straight line to {@code end}. */
    record Line(PointD end) implements CurveSegment {
        public PointD endPoint() { return end; }
    }

    /** Quadratic Bezier with one control point {@code ctrl} to {@code end}. */
    record QuadraticBezier(PointD ctrl, PointD end) implements CurveSegment {
        public PointD endPoint() { return end; }
    }

    /** Cubic Bezier with two control points to {@code end}. */
    record CubicBezier(PointD ctrl1, PointD ctrl2, PointD end) implements CurveSegment {
        public PointD endPoint() { return end; }
    }
}
```

### New Class: `CurvePath`

```java
package clipper2.curves;

import clipper2.core.PathD;
import clipper2.core.PointD;
import java.util.ArrayList;
import java.util.List;

/**
 * A path that may contain straight-line segments and Bezier curve segments.
 *
 * <p>Call {@link #flatten(double)} to convert to a polygonal {@link PathD}
 * suitable for use with the clipping and offsetting APIs.
 */
public final class CurvePath {

    private final PointD startPoint;
    private final List<CurveSegment> segments = new ArrayList<>();
    private boolean closed;

    public CurvePath(PointD startPoint) {
        this.startPoint = startPoint;
    }

    public CurvePath lineTo(PointD end) {
        segments.add(new CurveSegment.Line(end));
        return this;
    }

    public CurvePath quadraticTo(PointD ctrl, PointD end) {
        segments.add(new CurveSegment.QuadraticBezier(ctrl, end));
        return this;
    }

    public CurvePath cubicTo(PointD ctrl1, PointD ctrl2, PointD end) {
        segments.add(new CurveSegment.CubicBezier(ctrl1, ctrl2, end));
        return this;
    }

    public CurvePath close() {
        this.closed = true;
        return this;
    }

    /**
     * Flattens this path to a polygonal {@link PathD}.
     *
     * @param tolerance maximum permitted perpendicular deviation
     * @return a new {@code PathD}; if {@link #close()} was called, the path
     *         is suitable as a closed polygon (the start point is not repeated
     *         at the end)
     */
    public PathD flatten(double tolerance) {
        PathD out = new PathD();
        out.add(new PointD(startPoint.x, startPoint.y));
        PointD prev = startPoint;
        for (CurveSegment seg : segments) {
            switch (seg) {
                case CurveSegment.Line(PointD end) ->
                    out.add(new PointD(end.x, end.y));
                case CurveSegment.QuadraticBezier(PointD ctrl, PointD end) ->
                    BezierFlattener.flattenQuadratic(prev, ctrl, end, tolerance, out);
                case CurveSegment.CubicBezier(PointD c1, PointD c2, PointD end) ->
                    BezierFlattener.flattenCubic(prev, c1, c2, end, tolerance, out);
            }
            prev = seg.endPoint();
        }
        return out;
    }
}
```

---

## Algorithm Notes

### Adaptive Subdivision (de Casteljau)

Adaptive subdivision recursively splits a curve at the midpoint until each
sub-curve is "flat" — indistinguishable from a straight line within the given
tolerance. The midpoint split is used (rather than a root-finding approach)
because it is numerically stable and produces evenly-distributed output points.

The flatness criterion used here is the PostScript/Skia criterion based on the
deviation of the control polygon from the chord. It is a conservative bound:
the curve will never deviate from the polyline by more than `tolerance`, but
in practice the deviation is usually much smaller.

### Complexity

- Per segment: O(n) where n is the number of output polyline vertices.
- The number of vertices n grows as O(1/tolerance) for a smooth curve.
- Stack depth is bounded by `MAX_DEPTH = 32`.

### t = 0.5 Splitting vs. Adaptive t

Splitting at t = 0.5 is used here because:
- It is simpler to implement and numerically stable.
- The de Casteljau algorithm at t = 0.5 uses only additions and bit shifts,
  making it fast in practice.
- Adaptive choice of split point provides marginal benefit for typical curves
  while adding significant complexity.

---

## Testing Strategy

- **Cubic flatness**: flatten a cubic Bezier with tolerance = 0.01; verify
  that every point in the output lies within 0.01 of the true curve
  (sample the true curve at 1000 points using the Bernstein form).
- **Quadratic flatness**: same for quadratic.
- **Degenerate curves**: a "curve" where all four control points are
  collinear should produce the same result as a straight line (two points).
- **Zero-length curve**: P0 == P1 == P2 == P3; should produce a single output
  point at P0.
- **Roundtrip**: flatten a circle approximated by 4 cubic Beziers; verify the
  output area matches π·r² within 0.01%.
- **MAX_DEPTH**: provide a pathological input (very high curvature, very tight
  tolerance) and verify the method returns rather than overflowing the stack.
- **`CurvePath` builder**: build a path with one line, one quadratic, one
  cubic; flatten and verify vertex count is reasonable and endpoints match.
