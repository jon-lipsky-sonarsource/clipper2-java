# Feature 6 — Visvalingam–Whyatt Path Simplification

## Summary

This feature adds the Visvalingam–Whyatt (VW) simplification algorithm as a
new static method on the `Clipper` facade. VW simplification iteratively
removes the vertex that contributes the smallest effective area to the path,
stopping when the smallest remaining area exceeds a given threshold.

## Justification

The library already provides `ramerDouglasPeucker()` (RDP) simplification.
The Visvalingam–Whyatt algorithm is the other major path simplification
technique and is complementary rather than redundant:

| Property | Ramer–Douglas–Peucker | Visvalingam–Whyatt |
|----------|-----------------------|-------------------|
| Criterion | Max perpendicular distance | Triangle area |
| Characteristic | Preserves peak points | Preserves visual shape |
| Typical use | Engineering / technical | Cartography / display |
| Behaviour on noisy data | Sensitive to spikes | Smoother results |

RDP tends to keep "spike" vertices that have high perpendicular deviation even
if they contribute little visual area. VW removes the least visually
significant vertices first, generally producing more aesthetically pleasing
results for geographic contour lines, street outlines, and organic shapes.

Offering both algorithms gives users the right tool for their specific data
type. The implementation is straightforward and adds no dependencies.

---

## Technical Specification

### Methods Added to `Clipper`

```java
// clipper2/Clipper.java  (additions)

/**
 * Simplifies {@code path} using the Visvalingam–Whyatt algorithm, removing
 * vertices whose effective triangle area is smaller than {@code areaEpsilon}.
 *
 * <p>The effective triangle area of a vertex V with predecessor U and
 * successor W is half the absolute value of the cross product of vectors
 * UV and UW:
 * <pre>
 *   area = |( (V.x - U.x) * (W.y - U.y) - (W.x - U.x) * (V.y - U.y) )| / 2
 * </pre>
 *
 * <p>When a vertex is removed, the effective areas of its two neighbours are
 * recomputed. The algorithm terminates when the vertex with the minimum
 * effective area has an area ≥ {@code areaEpsilon}, or when fewer than 3
 * vertices remain.
 *
 * @param path         the path to simplify; must not be {@code null}
 * @param areaEpsilon  minimum triangle area to retain; vertices with area
 *                     strictly less than this value are removed; must be ≥ 0
 * @param closedPath   if {@code true}, the path is treated as a closed
 *                     polygon (the predecessor of the first vertex is the
 *                     last vertex and vice versa); if {@code false}, the
 *                     first and last vertices are always retained
 * @return a new simplified {@link Path64}; if fewer than 3 vertices remain
 *         for a closed path, or fewer than 2 for an open path, returns the
 *         remaining vertices without further simplification
 * @throws IllegalArgumentException if {@code areaEpsilon < 0}
 */
public static Path64 visvalingamWhyatt(Path64 path, double areaEpsilon, boolean closedPath) { ... }

/** Convenience overload for closed paths. */
public static Path64 visvalingamWhyatt(Path64 path, double areaEpsilon) {
    return visvalingamWhyatt(path, areaEpsilon, true);
}

/** Double-precision overloads. */
public static PathD visvalingamWhyatt(PathD path, double areaEpsilon, boolean closedPath) { ... }
public static PathD visvalingamWhyatt(PathD path, double areaEpsilon) {
    return visvalingamWhyatt(path, areaEpsilon, true);
}

/**
 * Simplifies each path in {@code paths} independently.
 */
public static Paths64 visvalingamWhyatt(Paths64 paths, double areaEpsilon, boolean closedPath) { ... }
public static PathsD visvalingamWhyatt(PathsD paths, double areaEpsilon, boolean closedPath) { ... }
```

---

### Algorithm Detail

The algorithm uses a doubly-linked list overlaid on the input array to allow
O(1) vertex removal, combined with a min-heap (priority queue) for O(log n)
minimum-area extraction.

**Data structure**: A node array where each node holds:
- `index` — original index in the input path
- `area` — effective triangle area (computed from prev and next neighbours)
- `prev`, `next` — linked-list pointers (as array indices, -1 for removed)
- `heapIndex` — current position in the heap (for efficient update)

**Heap**: A min-heap keyed by `area`. When a vertex is removed and its
neighbours are updated, the heap is adjusted. Java's `PriorityQueue` does not
support decrease-key; use a "lazy deletion" approach: when updating a vertex,
insert a new entry and mark the old one as stale. Stale entries are skipped
on extraction.

**Alternatively (simpler, suitable for typical polygon sizes)**: Use a sorted
list rebuilt after each removal. For paths up to ~10,000 vertices the O(n²)
approach is fast enough in practice. The spec describes the O(n log n) approach
but the implementation may start with the simpler O(n²) version.

#### Step-by-step (O(n log n) approach)

```
1. Build doubly-linked list of all vertices.
2. For each vertex (except endpoints of open paths), compute area(prev, vertex, next).
3. Insert all vertices into a min-heap keyed by area.
4. Repeat:
   a. Peek at the heap's minimum.
   b. If min area >= areaEpsilon, STOP.
   c. Pop the vertex V with minimum area.
   d. If V has been removed (stale), skip it and go to (a).
   e. Remove V from the linked list (link prev.next = V.next, V.prev.next = V.next, etc.).
   f. Recompute area for V.prev (using V.prev.prev and V.next as neighbours).
   g. Recompute area for V.next (using V.prev and V.next.next as neighbours).
   h. Push updated entries for V.prev and V.next into the heap.
   i. If fewer than minVertices remain, STOP.
5. Collect remaining vertices from the linked list in order.
```

**Open path**: the first and last vertices have no effective area and are
never added to the heap. `minVertices = 2`.

**Closed path**: all vertices have effective areas (wrap-around indexing).
`minVertices = 3`.

**Area monotonicity**: to preserve the topological validity of the result, a
recomputed area for a neighbour is set to `max(newArea, removedArea)`. This
prevents a sequence of removals from creating self-intersections. (This is
the standard VW refinement described in Mike Bostock's implementation notes.)

#### Area Formula

For three consecutive vertices A, B, C:

```java
private static double triangleArea(double ax, double ay,
                                   double bx, double by,
                                   double cx, double cy) {
    return Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) * 0.5;
}
```

Using `double` arithmetic for the area computation even when the path uses
`long` coordinates avoids overflow for large coordinate values.

---

## Comparison with `ramerDouglasPeucker`

Both algorithms accept an epsilon parameter but with different units:

| Algorithm | Epsilon unit | Interpretation |
|-----------|-------------|----------------|
| RDP | Linear distance | Max deviation from chord |
| VW | Area | Min triangle area to retain |

A rough equivalence for a curve of radius R: `areaVW ≈ eps_RDP² * π / 4`.
This is only approximate; users should tune empirically for their data.

---

## Testing Strategy

- **Collinear points**: a straight line with many collinear intermediate
  points should reduce to 2 endpoints at any positive `areaEpsilon`.
- **Square with extra midpoints**: a 4-corner square with intermediate
  collinear points; VW should remove the intermediate points and preserve
  the 4 corners.
- **Triangle area threshold**: add a vertex that forms a very small triangle;
  set epsilon above that area; verify the vertex is removed. Set epsilon below
  it; verify the vertex is retained.
- **Minimum vertex count**: run VW on a triangle with a very large epsilon;
  verify the result is the triangle itself (3 vertices retained for closed
  path).
- **Open vs closed path**: verify that the first and last vertex of an open
  path are never removed regardless of epsilon.
- **Correctness vs O(n²) reference**: run both the O(n log n) and the naive
  O(n²) implementations on the same input; assert identical output.
- **RDP comparison**: for a smooth curve, compare vertex counts produced by
  both algorithms at comparable epsilon scales; document the typical ratio.
- **`Paths64` batch variant**: simplify a `Paths64`; verify each path is
  simplified independently.
