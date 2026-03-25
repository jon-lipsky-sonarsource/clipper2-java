# Feature 10 — Path Interpolation and Resampling

## Summary

This feature adds a new class `PathMorph` in the `clipper2.curves` package
that provides two related operations:

1. **Resampling** — redistributes the vertices of a path to achieve a target
   vertex count or a target segment length, using arc-length parameterisation.
2. **Interpolation** — linearly blends between two paths of equal vertex count
   to produce a path at a fractional position `t ∈ [0, 1]`.

Together, these two operations enable smooth shape morphing between any two
paths.

## Justification

Path morphing and resampling appear frequently in:

- **Animation**: transitioning between two polygon shapes over time (e.g., a
  morphing logo, a growing/shrinking region, an animated map boundary).
- **Generative graphics**: blending between template shapes to produce
  intermediate forms.
- **Mesh generation**: distributing sample points evenly along a boundary
  before meshing.
- **Signal processing on curves**: many curve-analysis algorithms require
  uniformly-spaced samples.
- **Tool-path planning**: CNC and robotics require evenly-spaced waypoints
  along a path.

The interpolation operation is trivial once both paths have the same vertex
count, and resampling is the necessary pre-step. Combined, they provide a
complete morphing pipeline.

---

## Technical Specification

### Package

`clipper2.curves`

---

### New Class: `PathMorph`

```java
package clipper2.curves;

import clipper2.core.PathD;
import clipper2.core.PathsD;
import clipper2.core.PointD;

/**
 * Utilities for resampling and interpolating between paths.
 *
 * <p>Path morphing workflow:
 * <pre>
 *   PathD from = ...;
 *   PathD to   = ...;
 *   int    n   = Math.max(from.size(), to.size());
 *   PathD resampledFrom = PathMorph.resample(from, n, true);
 *   PathD resampledTo   = PathMorph.resample(to,   n, true);
 *   PathD midShape      = PathMorph.interpolate(resampledFrom, resampledTo, 0.5);
 * </pre>
 */
public final class PathMorph {

    private PathMorph() {}

    // -----------------------------------------------------------------------
    // Resampling
    // -----------------------------------------------------------------------

    /**
     * Resamples {@code path} to exactly {@code targetCount} evenly-spaced
     * vertices using arc-length parameterisation.
     *
     * <p>Arc-length parameterisation maps each original vertex to a position
     * on the interval [0, totalLength]. New vertices are placed at equal
     * arc-length intervals and computed by linear interpolation along the
     * edges of the original path.
     *
     * @param path         the source path; must have at least 2 vertices
     * @param targetCount  number of vertices in the output; must be ≥ 2
     * @param closedPath   if {@code true}, the path is treated as a closed
     *                     polygon: the edge from the last to the first vertex
     *                     is included in the arc-length calculation, and the
     *                     output does not repeat the first vertex at the end
     * @return a new {@link PathD} with exactly {@code targetCount} vertices
     * @throws IllegalArgumentException if path has fewer than 2 vertices or
     *         {@code targetCount < 2}
     */
    public static PathD resample(PathD path, int targetCount, boolean closedPath) { ... }

    /**
     * Resamples {@code path} so that each segment has approximately
     * {@code targetSegmentLength} length.
     *
     * <p>The number of output vertices is computed as
     * {@code max(2, round(totalLength / targetSegmentLength))} for open
     * paths, or {@code max(3, round(totalLength / targetSegmentLength))}
     * for closed paths.
     *
     * @param path                the source path
     * @param targetSegmentLength desired approximate segment length; must
     *                            be &gt; 0
     * @param closedPath          whether the path is closed
     * @return a resampled {@link PathD}
     * @throws IllegalArgumentException if {@code targetSegmentLength <= 0}
     */
    public static PathD resample(PathD path, double targetSegmentLength, boolean closedPath) { ... }

    // -----------------------------------------------------------------------
    // Interpolation
    // -----------------------------------------------------------------------

    /**
     * Linearly interpolates between two paths of equal vertex count.
     *
     * <p>The result path has the same vertex count as the inputs. Each
     * output vertex is computed as:
     * <pre>
     *   result[i] = from[i] * (1 - t) + to[i] * t
     * </pre>
     *
     * @param from the source shape at {@code t = 0}; must not be {@code null}
     * @param to   the target shape at {@code t = 1}; must not be {@code null}
     * @param t    interpolation factor; 0.0 returns a copy of {@code from},
     *             1.0 returns a copy of {@code to}; values outside [0, 1]
     *             extrapolate (not clamped, to allow over/under-shoot)
     * @return a new interpolated {@link PathD}
     * @throws IllegalArgumentException if {@code from} and {@code to} have
     *         different vertex counts
     */
    public static PathD interpolate(PathD from, PathD to, double t) { ... }

    /**
     * Generates an animation sequence of {@code frameCount} intermediate
     * shapes between {@code from} and {@code to}.
     *
     * <p>The t values are evenly spaced. If {@code includeEndpoints} is
     * {@code true}, the sequence is
     * {@code [t=0, t=1/(n-1), ..., t=1]}, giving {@code frameCount}
     * paths. If {@code false}, endpoints are excluded and t values are
     * {@code [1/n, 2/n, ..., (n-1)/n]}, also giving {@code frameCount} paths.
     *
     * @param from             start shape (already resampled to match
     *                         {@code to})
     * @param to               end shape
     * @param frameCount       number of frames; must be ≥ 2 if
     *                         {@code includeEndpoints} is true, ≥ 1 otherwise
     * @param includeEndpoints whether to include frames at t=0 and t=1
     * @return a {@link PathsD} (list of frames)
     */
    public static PathsD animationSequence(PathD from, PathD to,
                                            int frameCount, boolean includeEndpoints) { ... }
}
```

---

### Resampling Algorithm

#### Arc-Length Parameterisation

```
1. Compute cumulative arc-length at each vertex:
   arcLen[0] = 0
   arcLen[i] = arcLen[i-1] + dist(path[i-1], path[i])
   For closed paths, also add dist(path[last], path[0]) to get totalLength.
   For open paths, totalLength = arcLen[path.size()-1].

2. Compute target sample positions:
   For a closed path with targetCount n:
       samplePos[k] = k * totalLength / n   for k = 0, ..., n-1
   For an open path with targetCount n:
       samplePos[k] = k * totalLength / (n-1)  for k = 0, ..., n-1

3. For each sample position s:
   Find the segment [i, i+1] such that arcLen[i] <= s <= arcLen[i+1].
   Compute t = (s - arcLen[i]) / (arcLen[i+1] - arcLen[i]).
   result[k] = lerp(path[i], path[i+1], t).
```

Binary search can be used to find the segment in O(log n) per sample. The
total complexity is O(n + m log n) where n is the original vertex count and
m is the target count.

**Closed-path wrapping**: the "virtual" edge from `path[last]` to `path[0]`
is treated as segment `[last, last+1]` with `path[last+1] = path[0]`.

#### Point Interpolation

```java
private static PointD lerp(PointD a, PointD b, double t) {
    return new PointD(
        a.x + t * (b.x - a.x),
        a.y + t * (b.y - a.y));
}
```

---

### Interpolation Algorithm

```java
public static PathD interpolate(PathD from, PathD to, double t) {
    if (from.size() != to.size())
        throw new IllegalArgumentException(
            "Paths must have equal vertex count: " + from.size() + " vs " + to.size());
    PathD result = new PathD(from.size());
    double oneMinusT = 1.0 - t;
    for (int i = 0; i < from.size(); i++) {
        PointD a = from.get(i), b = to.get(i);
        result.add(new PointD(
            a.x * oneMinusT + b.x * t,
            a.y * oneMinusT + b.y * t));
    }
    return result;
}
```

---

### Vertex Alignment for Morphing

When morphing between two shapes that represent the "same" polygon (e.g.,
two versions of a country border), the user should align the starting vertex
of each resampled path so that corresponding vertices are geometrically close.
This is outside the scope of this feature (it is a higher-level concern), but
the following helper is provided:

```java
/**
 * Returns a cyclic rotation of {@code path} that minimises the total
 * squared distance to {@code reference}, used to align the start vertex
 * before interpolation.
 *
 * <p>This is an O(n²) algorithm. For large paths, consider only checking
 * a subset of candidate start offsets.
 *
 * @param path      the path to rotate; must be the same size as
 *                  {@code reference}
 * @param reference the reference path
 * @return a new {@link PathD} that is a cyclic rotation of {@code path}
 */
public static PathD alignStartVertex(PathD path, PathD reference) { ... }
```

---

## Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| `targetCount == path.size()` | Returns a copy of the path (all arc-length samples coincide with originals — minor floating-point differences possible) |
| Degenerate path (all vertices equal) | `totalLength == 0`; all resampled vertices equal the single point |
| `targetCount == 1` | `IllegalArgumentException` (minimum is 2) |
| `t == 0` | Returns a copy of `from` |
| `t == 1` | Returns a copy of `to` |
| `t < 0` or `t > 1` | Extrapolation; no error thrown; documented as valid |
| Unequal vertex counts in `interpolate` | `IllegalArgumentException` |

---

## Testing Strategy

- **Identity resample**: resample a path to its own vertex count; result
  should be nearly identical to input (within floating-point tolerance).
- **Halving**: resample a 10-vertex open path to 5 vertices; verify the new
  vertices lie on the original path segments.
- **Doubling**: resample a 5-vertex open path to 10 vertices; verify even
  spacing by checking all segment lengths are equal.
- **Closed path wrapping**: for a closed path, the resampled path should not
  repeat the first vertex, and all segment lengths should be equal.
- **Interpolation t=0.5**: interpolate a unit square and a double-size square;
  result should be a 1.5× square.
- **`animationSequence`**: verify frame count, that first frame == `from`
  (when `includeEndpoints = true`), last frame == `to`.
- **`alignStartVertex`**: construct a path and a shifted version; verify
  alignment returns the correctly-rotated path.
- **Degenerate input**: zero-length path handled without exception.
