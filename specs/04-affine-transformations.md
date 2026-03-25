# Feature 4 — Affine Transformations

## Summary

This feature adds a new class `AffineTransform` in the `clipper2.transform`
package that encapsulates a 2D affine transformation matrix and can apply it
to `Path64`, `PathD`, `Paths64`, and `PathsD` values. Static factory methods
in `Clipper` provide convenience access for common operations (translate,
scale, rotate, shear).

## Justification

Affine transformations are among the most frequent operations in any graphics
pipeline. Clipping and offsetting typically occur in a specific coordinate
space, but paths originate in model or world space and must be transformed
before and after. Without built-in support:

- Callers must write their own per-vertex loops.
- Combining transforms (e.g., scale then rotate) requires manual matrix
  multiplication.
- Off-by-one errors in pivot-point handling are common.

Virtually every comparable library — JTS, Shapely (Python), GEOS — provides
affine transform support. The implementation is pure arithmetic (no new
dependencies) and very low effort relative to its value.

---

## Technical Specification

### Package

`clipper2.transform`

---

### Matrix Representation

A 2D affine transformation in homogeneous coordinates:

```
[ x' ]   [ m00  m01  m02 ] [ x ]
[ y' ] = [ m10  m11  m12 ] [ y ]
[ 1  ]   [  0    0    1  ] [ 1 ]
```

The matrix is stored as a `double[6]` in row-major order:
`[m00, m01, m02, m10, m11, m12]`

Expanded:
```
x' = m00 * x + m01 * y + m02
y' = m10 * x + m11 * y + m12
```

This matches the convention used by Java2D's `AffineTransform`, Android's
`Matrix`, and SVG's `matrix(a,b,c,d,e,f)` (with `a=m00, b=m10, c=m01,
d=m11, e=m02, f=m12`).

---

### New Class: `AffineTransform`

```java
package clipper2.transform;

import clipper2.core.Path64;
import clipper2.core.PathD;
import clipper2.core.Paths64;
import clipper2.core.PathsD;
import clipper2.core.Point64;
import clipper2.core.PointD;

/**
 * Represents a 2D affine transformation and provides methods to apply it
 * to Clipper2 path types.
 *
 * <p>The transform is stored as a 3×3 homogeneous matrix (bottom row always
 * [0, 0, 1]) and represented as a {@code double[6]} in row-major order:
 * {@code [m00, m01, m02, m10, m11, m12]}.
 *
 * <p>Transforms are applied as column vectors: {@code p' = M * p}.
 * Combining transforms follows left-to-right composition via
 * {@link #then(AffineTransform)}: {@code a.then(b)} applies {@code a} first,
 * then {@code b}.
 */
public final class AffineTransform {

    private final double m00, m01, m02;
    private final double m10, m11, m12;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /** Identity transform. */
    public AffineTransform() {
        this(1, 0, 0,
             0, 1, 0);
    }

    /**
     * Creates a transform from six matrix elements.
     * Element order matches {@code [m00, m01, m02, m10, m11, m12]}.
     */
    public AffineTransform(double m00, double m01, double m02,
                           double m10, double m11, double m12) {
        this.m00 = m00; this.m01 = m01; this.m02 = m02;
        this.m10 = m10; this.m11 = m11; this.m12 = m12;
    }

    /**
     * Creates a transform from a {@code double[6]} array in row-major order.
     * @throws IllegalArgumentException if {@code matrix.length != 6}
     */
    public static AffineTransform of(double[] matrix) {
        if (matrix == null || matrix.length != 6)
            throw new IllegalArgumentException("matrix must have exactly 6 elements");
        return new AffineTransform(
            matrix[0], matrix[1], matrix[2],
            matrix[3], matrix[4], matrix[5]);
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    /** Returns a translation transform. */
    public static AffineTransform translation(double tx, double ty) {
        return new AffineTransform(1, 0, tx, 0, 1, ty);
    }

    /**
     * Returns a uniform scaling transform about the origin.
     */
    public static AffineTransform scale(double s) {
        return scale(s, s);
    }

    /**
     * Returns a non-uniform scaling transform about the origin.
     */
    public static AffineTransform scale(double sx, double sy) {
        return new AffineTransform(sx, 0, 0, 0, sy, 0);
    }

    /**
     * Returns a scaling transform about a pivot point.
     *
     * <p>Equivalent to: translate(-pivot), scale(sx, sy), translate(pivot).
     */
    public static AffineTransform scale(double sx, double sy, PointD pivot) {
        return translation(pivot.x, pivot.y)
            .then(scale(sx, sy))
            .then(translation(-pivot.x, -pivot.y));
    }

    /**
     * Returns a counter-clockwise rotation transform about the origin.
     *
     * @param radians rotation angle in radians; positive = counter-clockwise
     *                (assuming Y-up; negate for SVG Y-down convention)
     */
    public static AffineTransform rotation(double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new AffineTransform(cos, -sin, 0, sin, cos, 0);
    }

    /**
     * Returns a rotation transform about an arbitrary pivot point.
     *
     * @param radians rotation angle in radians
     * @param pivot   centre of rotation
     */
    public static AffineTransform rotation(double radians, PointD pivot) {
        return translation(pivot.x, pivot.y)
            .then(rotation(radians))
            .then(translation(-pivot.x, -pivot.y));
    }

    /**
     * Returns a shear transform.
     *
     * @param shearX horizontal shear factor (x' += shearX * y)
     * @param shearY vertical shear factor (y' += shearY * x)
     */
    public static AffineTransform shear(double shearX, double shearY) {
        return new AffineTransform(1, shearX, 0, shearY, 1, 0);
    }

    /**
     * Returns a reflection across the X axis (y → -y).
     */
    public static AffineTransform reflectX() {
        return new AffineTransform(1, 0, 0, 0, -1, 0);
    }

    /**
     * Returns a reflection across the Y axis (x → -x).
     */
    public static AffineTransform reflectY() {
        return new AffineTransform(-1, 0, 0, 0, 1, 0);
    }

    // -----------------------------------------------------------------------
    // Composition
    // -----------------------------------------------------------------------

    /**
     * Returns a new transform that applies {@code this} first, then
     * {@code other}.
     *
     * <p>Equivalent to matrix multiplication: {@code other.matrix * this.matrix}.
     */
    public AffineTransform then(AffineTransform other) {
        return new AffineTransform(
            other.m00 * m00 + other.m01 * m10,
            other.m00 * m01 + other.m01 * m11,
            other.m00 * m02 + other.m01 * m12 + other.m02,
            other.m10 * m00 + other.m11 * m10,
            other.m10 * m01 + other.m11 * m11,
            other.m10 * m02 + other.m11 * m12 + other.m12);
    }

    // -----------------------------------------------------------------------
    // Application
    // -----------------------------------------------------------------------

    /**
     * Applies this transform to a single double-precision point.
     */
    public PointD apply(PointD p) {
        return new PointD(
            m00 * p.x + m01 * p.y + m02,
            m10 * p.x + m11 * p.y + m12);
    }

    /**
     * Applies this transform to a single integer point, rounding to nearest
     * long.
     */
    public Point64 apply(Point64 p) {
        return new Point64(
            Math.round(m00 * p.x + m01 * p.y + m02),
            Math.round(m10 * p.x + m11 * p.y + m12));
    }

    /**
     * Returns a new {@link PathD} with every point transformed.
     */
    public PathD apply(PathD path) {
        PathD result = new PathD(path.size());
        for (PointD p : path) result.add(apply(p));
        return result;
    }

    /**
     * Returns a new {@link Path64} with every point transformed (rounded).
     */
    public Path64 apply(Path64 path) {
        Path64 result = new Path64(path.size());
        for (Point64 p : path) result.add(apply(p));
        return result;
    }

    /**
     * Returns a new {@link PathsD} with every path transformed.
     */
    public PathsD apply(PathsD paths) {
        PathsD result = new PathsD(paths.size());
        for (PathD p : paths) result.add(apply(p));
        return result;
    }

    /**
     * Returns a new {@link Paths64} with every path transformed (rounded).
     */
    public Paths64 apply(Paths64 paths) {
        Paths64 result = new Paths64(paths.size());
        for (Path64 p : paths) result.add(apply(p));
        return result;
    }

    // -----------------------------------------------------------------------
    // Matrix access
    // -----------------------------------------------------------------------

    /** Returns the six matrix elements as a new {@code double[6]} array. */
    public double[] toArray() {
        return new double[]{m00, m01, m02, m10, m11, m12};
    }

    @Override
    public String toString() {
        return String.format("AffineTransform[[%.4f, %.4f, %.4f], [%.4f, %.4f, %.4f]]",
            m00, m01, m02, m10, m11, m12);
    }
}
```

---

### Convenience Methods in `Clipper` Facade

These static methods are thin wrappers over `AffineTransform` for the most
common single-operation use cases:

```java
// clipper2/Clipper.java  (additions)

// --- Translation ---
public static Path64  translatePath(Path64  path, long dx, long dy)
public static PathD   translatePath(PathD   path, double dx, double dy)
public static Paths64 translatePaths(Paths64 paths, long dx, long dy)
public static PathsD  translatePaths(PathsD  paths, double dx, double dy)

// --- Scaling (about origin) ---
public static PathD   scalePath(PathD   path, double sx, double sy)
public static PathsD  scalePaths(PathsD  paths, double sx, double sy)

// --- Rotation (always returns PathD — rotation is inherently floating-point) ---
public static PathD   rotatePath(Path64  path, double radians, PointD pivot)
public static PathD   rotatePath(PathD   path, double radians, PointD pivot)
public static PathsD  rotatePaths(Paths64 paths, double radians, PointD pivot)
public static PathsD  rotatePaths(PathsD  paths, double radians, PointD pivot)
```

Implementation example:

```java
public static PathD rotatePath(PathD path, double radians, PointD pivot) {
    return AffineTransform.rotation(radians, pivot).apply(path);
}
```

---

## Numeric Precision Notes

- All `AffineTransform` operations use `double` arithmetic internally.
- When transforming `Path64` → `Path64`, coordinates are rounded via
  `Math.round()`. This is consistent with how `ClipperD` rounds internally.
- For rotation by multiples of 90°, callers should use exact multiples of
  `Math.PI / 2` and round the result back to integer coords if needed.
  A future enhancement could add a `rotateOrthogonal(int quarters)` factory
  that uses integer arithmetic.

---

## Testing Strategy

- **Identity**: apply identity transform to any path; assert output equals
  input (within floating-point precision for `PathD`; exactly for `Path64`
  with identity rounding).
- **Translation**: translate a square by (5, 10); assert all coordinates
  shifted.
- **Scale**: scale a unit square by (2, 3); assert axis-aligned dimensions
  doubled and tripled.
- **Scale about pivot**: scale a path about its centroid; assert centroid
  unchanged.
- **Rotation 90°**: rotate a unit square by π/2 about the origin; assert
  resulting corners are correct to 1e-10.
- **Rotation 180°**: rotation by π should negate both coordinates (about
  origin).
- **Reflection**: `reflectX().apply(p)` should negate `y`; `reflectY()`
  should negate `x`.
- **Shear**: apply shear (0.5, 0) to a vertical line; assert it becomes
  diagonal.
- **Composition**: `translation(5,5).then(scale(2,2))` applied to (1,1)
  should equal `translation(5,5).apply(scale(2,2).apply(pt(1,1)))`.
- **`toArray` / `of` roundtrip**: serialise and deserialise; assert equality.
- **Invalid matrix**: `AffineTransform.of(new double[5])` → `IllegalArgumentException`.
- **`rotatePath(Path64, ...)` in `Clipper`**: verify the delegate chain works.
