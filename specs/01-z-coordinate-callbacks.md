# Feature 1 — Z-Coordinate / Vertex Attribute Callbacks (USINGZ)

## Summary

The original C# Clipper2 library supports an optional `USINGZ` compilation
flag that attaches a third coordinate `Z` to every point and allows callers
to register a callback that is invoked whenever two edges intersect during
the sweep-line algorithm. The callback receives the four endpoints of the
two intersecting edges and the computed intersection point, and is responsible
for returning the `Z` value to assign to that new vertex.

The Java port explicitly omits this feature (noted in the README). This
specification describes how to add it in a backward-compatible way.

## Justification

The `Z` field is a general-purpose vertex attribute slot. Its primary uses are:

- **Texture coordinate interpolation**: When clipping a textured polygon, the
  caller can store UV coordinates in `Z` and interpolate them across clip
  intersections.
- **Material / layer tagging**: CAD and GIS applications tag vertices with
  layer identifiers and need those tags preserved after clipping.
- **Elevation data**: Terrain polygons carry elevation; clipping must
  interpolate the height at new intersection vertices.
- **Debugging and diagnostics**: Tracking which original edge contributed each
  output vertex.

Without this hook, callers who need vertex attributes must implement their own
intersection detection on top of the library, which partially duplicates the
sweep-line work and is error-prone.

---

## Technical Specification

### 1. Changes to `Point64`

Add a `z` field with a default value of `0`. All existing constructors remain
unchanged; the field is only populated when explicitly set or when the Z
callback system is active.

```java
// clipper2/core/Point64.java  (modified)
public class Point64 {
    public long x;
    public long y;
    public long z;           // NEW — defaults to 0; ignored unless Z callback is registered

    // Existing constructors unchanged — z defaults to 0 via field initialiser.

    // NEW convenience constructor
    public Point64(long x, long y, long z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point64(Point64 pt) {
        this.x = pt.x;
        this.y = pt.y;
        this.z = pt.z;   // copy z too
    }
}
```

**Backward compatibility**: the new field is `0` by default and all existing
code that ignores `z` continues to work unmodified. `equals()` and
`hashCode()` must be updated to include `z` so that point identity is
consistent; callers that do not use Z will see no behavioural difference since
all `z` values will be `0`.

### 2. Changes to `PointD`

Mirror the change for the double-precision type:

```java
// clipper2/core/PointD.java  (modified)
public class PointD {
    public double x;
    public double y;
    public double z;           // NEW — defaults to 0.0

    public PointD(double x, double y, double z) { ... }  // NEW
    public PointD(PointD pt) { this.x = pt.x; this.y = pt.y; this.z = pt.z; }
}
```

### 3. Z Callback Interface — `ZCallback64`

```java
// clipper2/engine/ZCallback64.java  (NEW)
package clipper2.engine;

import clipper2.core.Point64;

/**
 * Callback invoked by {@link Clipper64} each time two edges intersect during
 * the sweep-line algorithm. The callback must return the {@code z} value to
 * assign to the intersection point.
 *
 * <p>Parameter naming follows the C# Clipper2 convention:
 * {@code bot1}/{@code top1} are the bottom and top endpoints of the first edge;
 * {@code bot2}/{@code top2} are the bottom and top endpoints of the second edge;
 * {@code intersectPt} is the computed intersection point (with {@code z == 0}
 * on entry — the callback's return value is assigned to it after the call).
 *
 * <p>Implementations must be thread-safe if {@link Clipper64} instances are
 * shared across threads.
 */
@FunctionalInterface
public interface ZCallback64 {
    long zFill(Point64 bot1, Point64 top1,
               Point64 bot2, Point64 top2,
               Point64 intersectPt);
}
```

### 4. Z Callback Interface — `ZCallbackD`

```java
// clipper2/engine/ZCallbackD.java  (NEW)
package clipper2.engine;

import clipper2.core.PointD;

/**
 * Double-precision equivalent of {@link ZCallback64}, used with
 * {@link ClipperD}.
 */
@FunctionalInterface
public interface ZCallbackD {
    double zFill(PointD bot1, PointD top1,
                 PointD bot2, PointD top2,
                 PointD intersectPt);
}
```

### 5. Changes to `Clipper64`

```java
// clipper2/engine/Clipper64.java  (modified)
public class Clipper64 extends ClipperBase {

    private ZCallback64 zCallback;   // NEW — null by default

    /**
     * Registers a callback that will be invoked at every edge intersection
     * to determine the {@code z} value of the new intersection point.
     * Pass {@code null} to disable Z callbacks.
     */
    public void setZCallback(ZCallback64 callback) {   // NEW
        this.zCallback = callback;
    }

    public ZCallback64 getZCallback() { return zCallback; }  // NEW
}
```

### 6. Changes to `ClipperD`

Mirror the above for `ClipperD` using `ZCallbackD`.

### 7. Changes to `ClipperBase` — Intersection Point Computation

Inside `ClipperBase`, locate the internal method that computes intersection
points between two active edges (this is the method that produces new `Point64`
values during the sweep). After computing the intersection coordinates, check
whether a Z callback is registered and, if so, invoke it:

```java
// Inside ClipperBase (pseudo-code showing the injection point)
private Point64 getIntersectPoint(Active edge1, Active edge2) {
    // ... existing intersection arithmetic ...
    Point64 ip = new Point64(computedX, computedY);  // z defaults to 0

    // NEW — invoke Z callback if registered
    if (zCallback != null) {
        ip.z = zCallback.zFill(
            edge1.bot, edge1.top,
            edge2.bot, edge2.top,
            ip);
    }
    return ip;
}
```

The `zCallback` field in `ClipperBase` is `protected` so both `Clipper64`
and `ClipperD` can set it. Alternatively, expose a protected setter in
`ClipperBase` and let the concrete subclasses delegate to it.

### 8. Z Propagation for Non-Intersection Points

Points that are copied directly from input paths (not generated at
intersections) carry their `z` values unchanged. No additional work is needed
for these points because `Point64` construction already copies the `z` field.

### 9. Convenience Linear Interpolation Helper

A common Z callback is linear interpolation along the intersecting edge.
Provide this as a static method so callers do not have to reimplement it:

```java
// clipper2/engine/ZCallbackUtils.java  (NEW)
package clipper2.engine;

import clipper2.core.Point64;

/**
 * Factory methods for common {@link ZCallback64} implementations.
 */
public final class ZCallbackUtils {

    private ZCallbackUtils() {}

    /**
     * Returns a {@link ZCallback64} that linearly interpolates the {@code z}
     * value of the intersection point along the first edge ({@code bot1} to
     * {@code top1}).
     *
     * <p>This is the most common choice when {@code z} represents a scalar
     * attribute (elevation, texture coordinate component, etc.).
     */
    public static ZCallback64 linearInterpolateOnEdge1() {
        return (bot1, top1, bot2, top2, ip) -> {
            long dx = top1.x - bot1.x;
            long dy = top1.y - bot1.y;
            long lenSq = dx * dx + dy * dy;
            if (lenSq == 0) return bot1.z;
            double t = ((double)(ip.x - bot1.x) * dx
                      + (double)(ip.y - bot1.y) * dy) / lenSq;
            return bot1.z + Math.round(t * (top1.z - bot1.z));
        };
    }
}
```

### 10. Convenience Methods in `Clipper` facade

```java
// Clipper.java additions

/**
 * Intersects {@code subject} and {@code clip} paths, invoking {@code zCallback}
 * at each edge intersection to compute the {@code z} field of the new vertex.
 */
public static Paths64 intersect(Paths64 subject, Paths64 clip,
                                FillRule fillRule, ZCallback64 zCallback) { ... }

// Equivalent overloads for union, difference, xor, and booleanOp.
```

### 11. `equals()` and `hashCode()` Updates

```java
// Point64
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof Point64 other)) return false;
    return x == other.x && y == other.y && z == other.z;
}

@Override
public int hashCode() {
    return Long.hashCode(x) * 31 * 31 + Long.hashCode(y) * 31 + Long.hashCode(z);
}
```

Mirror for `PointD`, using a tolerance-based comparison for `z` (consistent
with existing `PointD.equals` which already uses `isAlmostZero`).

---

## Edge Cases and Error Handling

| Scenario | Behaviour |
|----------|-----------|
| No callback registered | `z` is always `0` on all output points (existing behaviour) |
| Callback returns for collinear overlap | The overlap endpoints are not generated via the intersection path; original `z` values are preserved |
| Input paths have `z == 0` everywhere | Output is identical to before this feature; `z` remains `0` |
| `ClipperD` scaling | Scale is applied only to `x` and `y`; `z` is passed through to the callback unscaled and the callback's return value is stored in `z` unscaled |

---

## Testing Strategy

- **Unit test**: construct two paths with distinct non-zero `z` values, clip
  them, assert that the intersection vertices have `z` values that match the
  expected linear interpolation.
- **No-callback regression**: run the full existing test suite with no callback
  registered; assert all `z` values on outputs are `0` and all existing
  geometry tests pass unchanged.
- **Identity callback**: register a callback that always returns `42`; assert
  every intersection vertex has `z == 42`.
- **`equals()` test**: verify that two `Point64` objects with the same `x`/`y`
  but different `z` are not equal.
