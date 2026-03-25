# Feature 9 — Coordinate Snapping and Fuzzy Merging

## Summary

This feature adds static utility methods to the `Clipper` facade for two
related coordinate cleaning operations:

1. **Grid snapping** — rounds each vertex to the nearest multiple of a grid
   size, reducing precision to a fixed lattice.
2. **Fuzzy deduplication** — removes consecutive (or all) vertices that are
   within a given distance of each other, collapsing near-coincident points.

## Justification

In practice, polygon coordinates often arrive with minor imprecision:
floating-point arithmetic in upstream calculations, coordinate transforms,
or arc approximations produce vertices that are nearly — but not exactly —
coincident or collinear. These near-misses cause several problems:

- **Degenerate triangles** in triangulation (CDT).
- **Near-zero-area slivers** after boolean clipping.
- **Numerical instability** in the sweep-line algorithm with near-parallel,
  nearly-touching edges.

Grid snapping and fuzzy deduplication are standard pre-processing steps in
robust computational geometry pipelines. JTS calls this "snapping" and applies
it before computing overlays. The existing `trimCollinear()` method addresses
exactly-collinear points; these new methods address the floating-point case.

---

## Technical Specification

All methods are `public static` on `clipper2.Clipper`.

---

### 9.1 Grid Snapping

Snaps every vertex coordinate to the nearest integer multiple of `gridSize`.
After snapping, consecutive duplicate vertices (which arise when nearby
vertices snap to the same grid point) are removed.

```java
/**
 * Snaps every vertex of {@code path} to the nearest multiple of
 * {@code gridSize} and removes any consecutive duplicate vertices that
 * result.
 *
 * <p>Grid snapping reduces the effective precision of the coordinates to
 * the grid resolution. This can prevent numerical instability in downstream
 * clipping operations caused by near-coincident or near-parallel edges.
 *
 * @param path     the path to snap; must not be {@code null}
 * @param gridSize grid cell size; must be &gt; 0
 * @return a new {@link Path64} with snapped, deduplicated vertices; may be
 *         shorter than the input if vertices collapse onto the same grid
 *         point; returns an empty path if all vertices collapse
 * @throws IllegalArgumentException if {@code gridSize <= 0}
 */
public static Path64 snapToGrid(Path64 path, long gridSize) { ... }

/** Batch overload — snaps each path independently. */
public static Paths64 snapToGrid(Paths64 paths, long gridSize) { ... }

/**
 * Double-precision overload. Snaps to the nearest multiple of
 * {@code gridSize} (which need not be an integer).
 *
 * @param gridSize grid cell size; must be &gt; 0.0
 */
public static PathD snapToGrid(PathD path, double gridSize) { ... }

public static PathsD snapToGrid(PathsD paths, double gridSize) { ... }
```

**Snapping formula** for a single coordinate value `v`:
```java
long snapLong(long v, long gridSize) {
    // Integer rounding to nearest multiple
    return Math.round((double) v / gridSize) * gridSize;
}

double snapDouble(double v, double gridSize) {
    return Math.round(v / gridSize) * gridSize;
}
```

**Deduplication after snapping**: iterate the snapped vertices; skip any
vertex equal to the previous one. For a closed path, also compare the last
vertex to the first.

---

### 9.2 Fuzzy Vertex Deduplication

Removes consecutive vertices that are within `epsilon` distance of each other,
keeping the first vertex in each near-duplicate cluster.

```java
/**
 * Removes consecutive vertices from {@code path} that are closer than
 * {@code epsilon} to their predecessor.
 *
 * <p>The first vertex is always retained. Each subsequent vertex is retained
 * only if its Euclidean distance from the last retained vertex exceeds
 * {@code epsilon}. For a closed path, the last vertex is also compared to
 * the first.
 *
 * <p>This is distinct from {@link #trimCollinear(Path64, boolean)} which
 * removes collinear intermediate vertices but does not merge near-coincident
 * vertices.
 *
 * @param path      the path to clean; must not be {@code null}
 * @param epsilon   minimum permissible distance between consecutive retained
 *                  vertices; must be ≥ 0.0
 * @param closedPath if {@code true}, the path is treated as closed (the last
 *                   vertex is compared to the first)
 * @return a new {@link PathD} with near-coincident consecutive vertices
 *         removed; never {@code null}
 * @throws IllegalArgumentException if {@code epsilon < 0}
 */
public static PathD removeNearDuplicates(PathD path, double epsilon, boolean closedPath) { ... }

/** Convenience overload treating the path as closed. */
public static PathD removeNearDuplicates(PathD path, double epsilon) {
    return removeNearDuplicates(path, epsilon, true);
}

/** Batch overload. */
public static PathsD removeNearDuplicates(PathsD paths, double epsilon, boolean closedPath) { ... }
```

**Implementation**:
```java
PathD result = new PathD();
if (path.isEmpty()) return result;
result.add(path.get(0));
double epsilonSq = epsilon * epsilon;
for (int i = 1; i < path.size(); i++) {
    PointD prev = result.get(result.size() - 1);
    PointD curr = path.get(i);
    double dx = curr.x - prev.x, dy = curr.y - prev.y;
    if (dx * dx + dy * dy > epsilonSq) {
        result.add(curr);
    }
}
if (closedPath && result.size() > 1) {
    PointD last = result.get(result.size() - 1);
    PointD first = result.get(0);
    double dx = last.x - first.x, dy = last.y - first.y;
    if (dx * dx + dy * dy <= epsilonSq) {
        result.remove(result.size() - 1);
    }
}
return result;
```

**Uses squared epsilon** to avoid a `Math.sqrt` call per vertex.

---

### 9.3 Combined Clean Operation

A convenience method that applies both grid-snapping (or fuzzy dedup) followed
by collinear trimming, which is the full pre-processing pipeline for robust
clipping:

```java
/**
 * Applies a full cleaning pipeline to each path in {@code paths}:
 * <ol>
 *   <li>Snap to grid (using {@code gridSize}).</li>
 *   <li>Remove near-duplicate consecutive vertices with epsilon = 0 (exact
 *       deduplication after snapping).</li>
 *   <li>Remove collinear intermediate vertices (using existing
 *       {@link #trimCollinear(Path64, boolean)}).</li>
 * </ol>
 *
 * <p>Paths that become degenerate (fewer than 3 vertices after cleaning)
 * are removed from the result.
 *
 * @param paths    the paths to clean
 * @param gridSize snap grid cell size
 * @return cleaned paths; may have fewer paths than the input if some
 *         degenerate
 */
public static Paths64 cleanPaths(Paths64 paths, long gridSize) { ... }

public static PathsD cleanPaths(PathsD paths, double gridSize) { ... }
```

---

## Interaction with Existing `trimCollinear`

The existing `trimCollinear(Path64 path, boolean isOpen)` removes exactly
collinear points using the `InternalClipper.crossProduct` integer calculation.
The new `removeNearDuplicates` is a different operation that removes
near-coincident points (not collinear points). They are complementary and the
`cleanPaths` method chains them.

---

## Edge Cases

| Scenario | Behaviour |
|----------|-----------|
| All vertices collapse to one grid point | Returns empty path (or 1-vertex path) |
| `gridSize == 0` or `epsilon < 0` | `IllegalArgumentException` |
| Single-vertex path | Returns single-vertex path (after snapping, if applicable) |
| Path with all vertices already on the grid | Returns a copy unchanged |
| `epsilon == 0.0` | Removes only exactly-coincident consecutive vertices |
| Closed path where last vertex = first vertex | Last vertex is removed (dedup) |

---

## Testing Strategy

- **Basic snap**: `snapToGrid(path(3, 7), 5)` → point at `(5, 5)`.
- **Collapse**: snap two points close together with a coarse grid; verify they
  collapse to one grid point and the dedup produces a shorter path.
- **Square stays square**: a perfect square with vertices already on a fine
  grid snapped to that same grid should be unchanged.
- **Near-duplicate removal**: a path where every other vertex is within epsilon
  of its predecessor; verify only the first-in-each-pair survives.
- **Closed path last-vertex removal**: path where last vertex == first vertex;
  verify it is removed.
- **`cleanPaths` integration**: a path with slivers and collinear points should
  produce a clean minimal polygon.
- **`IllegalArgumentException`**: `gridSize = 0`, `epsilon = -1`.
