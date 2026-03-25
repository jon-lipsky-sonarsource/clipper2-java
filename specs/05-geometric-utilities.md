# Feature 5 — Additional Geometric Utilities

## Summary

This feature adds several geometric measurement and analysis methods to the
`Clipper` facade. The new methods complement the existing `area()`,
`isPositive()`, and `pointInPolygon()` methods.

The additions are:
- `centroid()` — true centre of mass of a polygon
- `perimeter()` — total arc length of a path
- `convexHull()` — smallest convex polygon enclosing a point set or paths
- `isConvex()` — test whether a polygon is convex
- `hausdorffDistance()` — shape-similarity metric between two paths

## Justification

The existing `Rect64.midPoint()` computes the bounding-box centre, which is
not the geometric centroid. The library has no method for perimeter,
convex hull, or convexity testing. These are among the most requested
geometric queries in any polygon library:

- **Centroid**: needed for pivot-based scaling/rotation, computing moments of
  inertia, centre-of-mass layout, and labelling (place text at the centroid).
- **Perimeter**: needed for any stroke-length or path-cost calculation.
- **Convex hull**: used as a fast bounding approximation, a pre-filter before
  expensive clipping, and in its own right for collision detection and
  computational geometry.
- **Convexity test**: commonly needed before applying algorithms that only
  work on convex polygons (e.g., the GJK collision algorithm, Sutherland-Hodgman
  clipping).
- **Hausdorff distance**: used in shape matching, simplification quality
  assessment, and comparing two versions of the same polygon before and after
  an operation.

All five methods are pure mathematics — no new data structures are required —
making them very low effort relative to their value.

---

## Technical Specification

All methods are added as `public static` methods on the existing
`clipper2.Clipper` class. Each method exists in both `Path64`/`Paths64` and
`PathD`/`PathsD` variants where applicable.

---

### 5.1 Centroid

The centroid of a polygon with vertices `(x0,y0), ..., (xn-1,yn-1)` is
computed using the shoelace formula for signed area and its moment analogue:

```
A  = Σ (xi * y(i+1) - x(i+1) * yi) / 2
Cx = Σ (xi + x(i+1)) * (xi * y(i+1) - x(i+1) * yi) / (6 * A)
Cy = Σ (yi + y(i+1)) * (xi * y(i+1) - x(i+1) * yi) / (6 * A)
```

where indices are taken mod n (the polygon is treated as closed).

```java
// clipper2/Clipper.java  (additions)

/**
 * Returns the geometric centroid (centre of mass) of a polygon.
 *
 * <p>The path is treated as a closed polygon regardless of whether the first
 * and last vertices are equal. The result is in double precision even when
 * the input uses integer coordinates, because the centroid of an integer
 * polygon is generally not at an integer coordinate.
 *
 * @param path the polygon; must have at least 3 vertices
 * @return the centroid as a {@link PointD}; returns {@code (0, 0)} for
 *         degenerate input (fewer than 3 vertices or zero-area polygon)
 */
public static PointD centroid(Path64 path) { ... }

/** Double-precision overload. */
public static PointD centroid(PathD path) { ... }
```

**Implementation notes**:
- Degenerate case (collinear vertices, `A ≈ 0`): fall back to the arithmetic
  mean of all vertices.
- Self-intersecting polygons: the formula gives the centroid weighted by
  the signed area of each region; the result may lie outside the polygon.
  Document this behaviour; do not throw.

---

### 5.2 Perimeter

```java
/**
 * Returns the perimeter (total arc length) of a path.
 *
 * @param path       the path; must not be {@code null}
 * @param closedPath if {@code true}, includes the edge from the last vertex
 *                   back to the first vertex in the total length
 * @return the perimeter in the same units as the coordinates; 0.0 for paths
 *         with fewer than 2 vertices
 */
public static double perimeter(Path64 path, boolean closedPath) { ... }

/** Convenience overload treating the path as closed. */
public static double perimeter(Path64 path) { return perimeter(path, true); }

/** Double-precision overloads (same signatures). */
public static double perimeter(PathD path, boolean closedPath) { ... }
public static double perimeter(PathD path) { return perimeter(path, true); }

/**
 * Returns the total perimeter of all paths in a collection.
 */
public static double perimeter(Paths64 paths) { ... }
public static double perimeter(PathsD paths) { ... }
```

**Implementation**: sum Euclidean distances between consecutive vertices.
Uses `Math.hypot(dx, dy)` for numerical stability.

---

### 5.3 Convex Hull

Uses Andrew's Monotone Chain algorithm (O(n log n)):

1. Sort all input points lexicographically (primary: x ascending; secondary:
   y ascending).
2. Build the lower hull: iterate left to right, maintaining a stack; pop the
   last point while the last three points make a clockwise (non-left) turn.
3. Build the upper hull: iterate right to left with the same rule.
4. Concatenate and deduplicate the two hulls.

The cross product of vectors AB and AC: `(B.x - A.x) * (C.y - A.y) - (B.y - A.y) * (C.x - A.x)`.
A left turn (counter-clockwise) is a positive cross product; collinear points
give zero. Pop when the value is `<= 0` to exclude collinear hull points
(yields a minimal convex polygon).

```java
/**
 * Returns the convex hull of all points in {@code path} as a new
 * {@link Path64} in counter-clockwise order (assuming Y-up; clockwise in
 * SVG/screen Y-down).
 *
 * @param path input points; duplicates are handled correctly; must not be
 *             {@code null}
 * @return the convex hull; empty if fewer than 3 non-collinear points exist;
 *         a single-point path if all input points are equal; a two-point
 *         path if all points are collinear
 */
public static Path64 convexHull(Path64 path) { ... }

/**
 * Returns the convex hull of all points across all paths in {@code paths}.
 */
public static Path64 convexHull(Paths64 paths) { ... }

/** Double-precision overloads. */
public static PathD convexHull(PathD path) { ... }
public static PathD convexHull(PathsD paths) { ... }
```

**Note**: `isPositive()` returns `true` for counter-clockwise polygons in the
library's convention; the convex hull result follows the same winding
convention.

---

### 5.4 Convexity Test

```java
/**
 * Returns {@code true} if {@code path} is a convex polygon.
 *
 * <p>A polygon is convex if all cross products of consecutive edge pairs have
 * the same sign (i.e., all turns are in the same direction). Collinear
 * consecutive edges (cross product == 0) are permitted.
 *
 * <p>Paths with fewer than 3 vertices are considered convex by convention.
 *
 * @param path    the polygon to test; treated as closed
 * @param allowCollinear if {@code true}, allows collinear consecutive edges
 *                       without marking the polygon as non-convex
 */
public static boolean isConvex(Path64 path, boolean allowCollinear) { ... }

/** Overload allowing collinear edges by default. */
public static boolean isConvex(Path64 path) { return isConvex(path, true); }

/** Double-precision overloads. */
public static boolean isConvex(PathD path, boolean allowCollinear) { ... }
public static boolean isConvex(PathD path) { ... }
```

**Algorithm**:
```
sign = 0  // unknown yet
for each triple of consecutive vertices (A, B, C):
    cross = (B.x - A.x) * (C.y - A.y) - (B.y - A.y) * (C.x - A.x)
    if cross != 0:
        if sign == 0: sign = sign(cross)
        else if sign(cross) != sign: return false
return true
```

---

### 5.5 Hausdorff Distance

The Hausdorff distance between two point sets A and B is:

```
H(A, B) = max( directed_H(A, B), directed_H(B, A) )

where directed_H(A, B) = max over a in A of { min over b in B of dist(a, b) }
```

For polygon paths, the "point set" is all vertices of the path.

```java
/**
 * Returns the Hausdorff distance between two paths (vertex-to-vertex).
 *
 * <p>This is the discrete Hausdorff distance computed over the vertices of
 * each path. It is an upper bound on the true continuous Hausdorff distance
 * (which considers all points on the edges, not just vertices). For
 * well-sampled paths the difference is small.
 *
 * <p>Time complexity: O(m · n) where m and n are the vertex counts of the
 * two paths.
 *
 * @param a first path; must not be {@code null} or empty
 * @param b second path; must not be {@code null} or empty
 * @return the Hausdorff distance in the same units as the coordinates
 * @throws IllegalArgumentException if either path is empty
 */
public static double hausdorffDistance(Path64 a, Path64 b) { ... }

/** Double-precision overload. */
public static double hausdorffDistance(PathD a, PathD b) { ... }
```

**Implementation**:

```java
private static double directedHausdorff(Path64 a, Path64 b) {
    double maxMinDist = 0;
    for (Point64 pa : a) {
        double minDist = Double.MAX_VALUE;
        for (Point64 pb : b) {
            double dx = pa.x - pb.x, dy = pa.y - pb.y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < minDist) minDist = dist;
            if (minDist == 0) break;  // early exit
        }
        if (minDist > maxMinDist) maxMinDist = minDist;
    }
    return maxMinDist;
}
```

**Performance note**: The O(m·n) implementation is acceptable for typical
polygon vertex counts (hundreds to low thousands). For very large paths (tens
of thousands of vertices), a spatial index such as a k-d tree would reduce
this to O(m log n), but that is outside the scope of this feature.

---

## Testing Strategy

### Centroid
- Square `(0,0),(100,0),(100,100),(0,100)` → centroid `(50, 50)`.
- Right triangle `(0,0),(6,0),(0,4)` → centroid `(2, 4/3)`.
- Degenerate (2 vertices): returns mean of vertices without throwing.

### Perimeter
- Unit square (side 1): `perimeter = 4.0`.
- Equilateral triangle with side 10: `perimeter = 30.0`.
- Single point: `perimeter = 0.0`.
- Open vs. closed: a path `[(0,0),(10,0)]` should give 10.0 closed and 10.0
  open (same for a two-point path).

### Convex Hull
- Convex square: hull equals input (in correct order).
- Point set containing an interior point: interior point is absent from hull.
- Collinear points: hull is two endpoints.
- All identical points: hull is a single-point path.
- Large random set: hull is a subset; all non-hull points are inside the hull
  (verify with `pointInPolygon`).

### Convexity Test
- Regular polygon (n ≥ 3): `isConvex == true`.
- L-shaped polygon: `isConvex == false`.
- Triangle: always convex.
- Square with one indented corner: `isConvex == false`.

### Hausdorff Distance
- Identical paths: `hausdorffDistance == 0.0`.
- Two squares, one offset by (d, 0): `hausdorffDistance == d`.
- Asymmetric case: path A is a single point inside B; directed H(A,B) = 0,
  directed H(B,A) = max distance from B's vertices to A; the symmetric
  distance equals the latter.
