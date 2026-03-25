# Feature 8 — Extended Shape Constructors

## Summary

This feature adds static factory methods to the `Clipper` facade for
constructing common geometric shapes as `Path64` or `PathD` values. The
existing `ellipse()` method is the pattern; this feature completes the set
with regular polygons, stars, rounded rectangles, and arc segments.

## Justification

The library already provides `ellipse(Point64 center, double radiusX, double
radiusY, int steps)`. The following shapes are equally common in graphics and
computational geometry work and should be constructable without caller-side
boilerplate:

- **Regular polygon**: circles approximated at a specific vertex count, used
  in physics (octagonal bounding shapes), art tools, and game development.
- **Star polygon**: used in graphics, game objects, and UI icons.
- **Rounded rectangle**: the single most common shape in UI design; building
  it from arcs is non-trivial.
- **Arc segment**: a partial ellipse used in pie charts, gauge instruments,
  and architectural profiles.
- **Annulus sector (wedge)**: a "pie slice" with inner and outer radii; used
  in charts and mechanical parts.

All of these are pure trigonometry — no new data structures or dependencies are
required.

---

## Technical Specification

All methods are `public static` on `clipper2.Clipper`.

---

### 8.1 Regular Polygon

```java
/**
 * Returns a regular {@code n}-gon centred at {@code centre} with the given
 * circumscribed radius.
 *
 * <p>The first vertex is at angle {@code startAngle} from the centre, with
 * subsequent vertices at equal angular intervals counter-clockwise (positive
 * Y-up; clockwise in SVG/screen Y-down convention).
 *
 * @param centre      centre point of the polygon
 * @param radius      circumscribed radius (distance from centre to vertex)
 * @param n           number of sides; must be ≥ 3
 * @param startAngle  angle in radians of the first vertex; 0 = right (3 o'clock)
 * @return a closed {@link Path64} with {@code n} vertices
 * @throws IllegalArgumentException if {@code n < 3} or {@code radius <= 0}
 */
public static Path64 makeRegularPolygon(Point64 centre, double radius, int n, double startAngle) { ... }

/** Convenience overload with {@code startAngle = -Math.PI / 2} (first vertex at top). */
public static Path64 makeRegularPolygon(Point64 centre, double radius, int n) {
    return makeRegularPolygon(centre, radius, n, -Math.PI / 2);
}

/** Double-precision overload. */
public static PathD makeRegularPolygon(PointD centre, double radius, int n, double startAngle) { ... }
public static PathD makeRegularPolygon(PointD centre, double radius, int n) { ... }
```

**Implementation**:
```java
for (int i = 0; i < n; i++) {
    double angle = startAngle + i * (2 * Math.PI / n);
    vertices[i] = new Point64(
        Math.round(centre.x + radius * Math.cos(angle)),
        Math.round(centre.y + radius * Math.sin(angle)));
}
```

---

### 8.2 Star Polygon

```java
/**
 * Returns a star polygon with {@code points} points, centred at
 * {@code centre}, with outer radius {@code outerRadius} and inner
 * (concave) radius {@code innerRadius}.
 *
 * <p>The star has {@code 2 * points} vertices alternating between the outer
 * and inner circles.
 *
 * @param centre       centre point
 * @param outerRadius  distance from centre to the tips of the star; must be
 *                     &gt; 0
 * @param innerRadius  distance from centre to the concave vertices; must be
 *                     &gt; 0 and ≤ {@code outerRadius}
 * @param points       number of star points; must be ≥ 3
 * @param startAngle   angle of the first outer vertex (radians)
 * @return a closed {@link Path64} with {@code 2 * points} vertices
 * @throws IllegalArgumentException if constraints on radii or points are
 *         violated
 */
public static Path64 makeStar(Point64 centre, double outerRadius, double innerRadius,
                               int points, double startAngle) { ... }

/** Convenience overload with first tip at top. */
public static Path64 makeStar(Point64 centre, double outerRadius, double innerRadius,
                               int points) {
    return makeStar(centre, outerRadius, innerRadius, points, -Math.PI / 2);
}

/** Double-precision overloads. */
public static PathD makeStar(PointD centre, double outerRadius, double innerRadius,
                              int points, double startAngle) { ... }
```

**Implementation**:
```java
double angleStep = Math.PI / points;  // half the full angular step
for (int i = 0; i < 2 * points; i++) {
    double r = (i % 2 == 0) ? outerRadius : innerRadius;
    double angle = startAngle + i * angleStep;
    vertices[i] = new Point64(
        Math.round(centre.x + r * Math.cos(angle)),
        Math.round(centre.y + r * Math.sin(angle)));
}
```

---

### 8.3 Rounded Rectangle

A rounded rectangle replaces each of the four 90° corners with a circular arc.
The arc at each corner has the given radius and is approximated with
`stepsPerCorner` line segments.

```java
/**
 * Returns a rounded rectangle with the given corner radius.
 *
 * <p>The rectangle is axis-aligned. All four corners use the same radius.
 * The path is wound counter-clockwise.
 *
 * @param rect           the bounding rectangle of the rounded rect
 * @param cornerRadius   radius of the corner arcs; clamped to
 *                       {@code min(rect.width(), rect.height()) / 2} if
 *                       larger
 * @param stepsPerCorner number of line segments per 90° corner arc; must
 *                       be ≥ 1; use 8 for screen rendering, 24 for print
 * @return a closed {@link PathD}
 * @throws IllegalArgumentException if {@code cornerRadius < 0} or
 *         {@code stepsPerCorner < 1}
 */
public static PathD makeRoundedRect(RectD rect, double cornerRadius, int stepsPerCorner) { ... }

/** Convenience overload using {@code stepsPerCorner = 8}. */
public static PathD makeRoundedRect(RectD rect, double cornerRadius) {
    return makeRoundedRect(rect, cornerRadius, 8);
}

/**
 * Per-corner radius variant. Corner order: top-left, top-right,
 * bottom-right, bottom-left.
 *
 * @param radii  four corner radii in the order: [tlRadius, trRadius,
 *               brRadius, blRadius]; each must be ≥ 0
 */
public static PathD makeRoundedRect(RectD rect, double[] radii, int stepsPerCorner) { ... }
```

**Algorithm**: Trace the rectangle perimeter counter-clockwise. At each corner,
emit an arc of 90° centred at the corner's inset circle centre:

```
Corner order (CCW, Y-up): bottom-left, bottom-right, top-right, top-left.

For bottom-left corner (example):
    arc centre = (rect.left + r, rect.bottom + r)
    start angle = 180° (pointing left)
    end angle   = 270° (pointing down)
    sweep       = 90° CCW
    emit stepsPerCorner + 1 points from start to end angle inclusive
```

Straight edges connect the arc endpoints. The total vertex count is
`4 * stepsPerCorner + 4` (not counting the duplicate of the start/end vertex,
since the path is returned without repeating the first vertex).

**Radius clamping**: if a radius exceeds `min(width, height) / 2`, it is
reduced to that maximum. For the per-corner variant, adjacent corners' radii
are reduced proportionally if their sum exceeds the shared edge length.

---

### 8.4 Arc Segment

An arc is a partial path along an ellipse — useful as a standalone open path
or as a building block for pie charts, gauges, and annulus sectors.

```java
/**
 * Returns a polygonal approximation of an elliptical arc as an open
 * {@link PathD}.
 *
 * @param centre      centre of the ellipse
 * @param radiusX     horizontal semi-axis; must be &gt; 0
 * @param radiusY     vertical semi-axis; must be &gt; 0
 * @param startAngle  start angle in radians (0 = rightward, positive = CCW
 *                    in Y-up convention)
 * @param sweepAngle  angular extent of the arc in radians; positive = CCW;
 *                    may be negative for clockwise arcs; magnitude clamped
 *                    to 2π (a full ellipse)
 * @param steps       number of line segments; must be ≥ 1; the arc will
 *                    have {@code steps + 1} vertices
 * @return an open {@link PathD} with {@code steps + 1} vertices
 * @throws IllegalArgumentException if {@code steps < 1} or radii ≤ 0
 */
public static PathD makeArc(PointD centre, double radiusX, double radiusY,
                             double startAngle, double sweepAngle, int steps) { ... }

/** Integer-centre convenience overload. */
public static PathD makeArc(Point64 centre, double radiusX, double radiusY,
                             double startAngle, double sweepAngle, int steps) { ... }
```

**Implementation**:
```java
PathD arc = new PathD(steps + 1);
double angleStep = sweepAngle / steps;
for (int i = 0; i <= steps; i++) {
    double angle = startAngle + i * angleStep;
    arc.add(new PointD(
        centre.x + radiusX * Math.cos(angle),
        centre.y + radiusY * Math.sin(angle)));
}
return arc;
```

---

### 8.5 Annulus Sector (Wedge)

A "pie slice" with optional inner hole — useful for doughnut charts and
mechanical sectors.

```java
/**
 * Returns a closed polygon representing an annulus sector (a "wedge" or
 * "pie slice" with an optional inner cut-out).
 *
 * <p>The shape is bounded by:
 * <ul>
 *   <li>Two straight radial edges from the inner to the outer radius.</li>
 *   <li>An outer arc from {@code startAngle} to {@code startAngle + sweepAngle}.</li>
 *   <li>An inner arc (reversed) at {@code innerRadius}. If {@code innerRadius
 *       == 0}, the inner arc collapses to a single centre point.</li>
 * </ul>
 *
 * @param centre       centre point
 * @param innerRadius  inner radius; use 0 for a solid pie slice
 * @param outerRadius  outer radius; must be &gt; {@code innerRadius}
 * @param startAngle   start angle in radians
 * @param sweepAngle   angular extent; positive = CCW; must not be 0
 * @param steps        number of segments per arc; must be ≥ 1
 * @return a closed {@link PathD}
 */
public static PathD makeAnnulusSector(PointD centre, double innerRadius,
                                       double outerRadius, double startAngle,
                                       double sweepAngle, int steps) { ... }
```

---

## Notes on `ellipse()` Consistency

The existing `ellipse(Point64 center, double radiusX, double radiusY, int steps)`
method generates a closed ellipse. The new `makeArc()` is the open-path
counterpart. `makeRegularPolygon()` with a large `n` is approximately equivalent
to `ellipse()` but uses an exact vertex count rather than a derived step count.

---

## Testing Strategy

- **Regular polygon**: `makeRegularPolygon(origin, 10, 4, 0)` should give a
  square with vertices at `(10,0), (0,10), (-10,0), (0,-10)`.
- **Regular polygon vertex count**: result has exactly `n` vertices.
- **Star vertex count**: `makeStar(..., 5)` has exactly 10 vertices.
- **Star outer/inner radii**: all odd-indexed vertices are at distance
  `innerRadius` from centre; all even-indexed at `outerRadius`.
- **Rounded rectangle corners**: for `stepsPerCorner = 1`, corner should be a
  single midpoint at 45°; for `stepsPerCorner = 0`, should throw.
- **Rounded rectangle area**: for large `stepsPerCorner`, area should approach
  `rect.width * rect.height - (4 - π) * r²`.
- **Rounded rectangle corner radius clamping**: radius > half-min-dimension
  should not throw, should clamp.
- **Arc start/end points**: first vertex of `makeArc(centre, r, r, 0, π/2, n)`
  should be `(centre.x + r, centre.y)`; last vertex should be
  `(centre.x, centre.y + r)`.
- **Arc with sweep = 2π**: should trace a complete circle and first/last
  vertices should be identical.
- **Annulus sector**: `area ≈ (outerRadius² - innerRadius²) * sweepAngle / 2`
  within 1% for large `steps`.
- **`IllegalArgumentException`**: verify n < 3, radius ≤ 0, steps < 1 all throw.
