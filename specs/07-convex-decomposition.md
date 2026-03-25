# Feature 7 — Convex Decomposition

## Summary

This feature adds a `ConvexDecomposition` class in the `clipper2.decomposition`
package that decomposes a simple polygon (possibly with holes) into a set of
convex sub-polygons that exactly cover the original polygon's area. The
implementation uses the Hertel–Mehlhorn algorithm, which produces near-optimal
decompositions in O(n log n) time by merging triangles from an existing
triangulation.

## Justification

Clipper2-Java already contains a Constrained Delaunay Triangulation (CDT)
implementation (marked beta). A convex decomposition is a natural, higher-level
result that builds on the triangulation: it partitions the polygon into a small
number of convex parts rather than the maximum number of triangles.

Use cases:

- **Physics engines** (Box2D, JBullet, etc.) require convex shapes for
  collision geometry. Polygonal bodies that are not convex must be decomposed
  before use.
- **Collision detection** is significantly faster against convex shapes (GJK,
  SAT algorithms).
- **Rendering optimisation**: concave polygons often require convex
  decomposition for certain GPU primitives.
- **Path planning**: robot navigation, CNC toolpath generation, and obstacle
  avoidance algorithms work in terms of convex regions.

---

## Technical Specification

### Package

`clipper2.decomposition`

---

### New Class: `ConvexDecomposition`

```java
package clipper2.decomposition;

import clipper2.core.Path64;
import clipper2.core.Paths64;

/**
 * Decomposes a simple polygon (optionally with holes) into a set of convex
 * sub-polygons that exactly cover the original area.
 *
 * <p>The decomposition uses the Hertel–Mehlhorn algorithm: the input polygon
 * is first triangulated using the library's existing Constrained Delaunay
 * Triangulation, then adjacent triangles are greedily merged as long as the
 * merged shape remains convex. This produces a partition with at most 4 times
 * the optimal number of convex pieces.
 *
 * <p>The result is a set of counter-clockwise polygons (matching the
 * library's positive-area convention). No two result polygons overlap except
 * at shared edges.
 */
public final class ConvexDecomposition {

    private ConvexDecomposition() {}

    /**
     * Decomposes the given polygon into convex parts.
     *
     * @param polygon a simple polygon given as a closed {@link Path64};
     *                must have at least 3 vertices; must not self-intersect;
     *                winding direction does not matter (both CW and CCW are
     *                accepted)
     * @return a {@link Paths64} containing the convex sub-polygons;
     *         each sub-path is in counter-clockwise order; never {@code null};
     *         returns a one-element list containing the original polygon if
     *         the polygon is already convex
     * @throws IllegalArgumentException if {@code polygon} has fewer than 3
     *         vertices
     */
    public static Paths64 decompose(Path64 polygon) { ... }

    /**
     * Decomposes the given polygon with holes.
     *
     * <p>The first element of {@code polygonWithHoles} is the outer boundary;
     * subsequent elements are holes (wound in the opposite direction). Holes
     * are treated as obstacles — the resulting convex pieces do not cover
     * the hole regions.
     *
     * @param polygonWithHoles outer boundary followed by zero or more holes;
     *                         must not be {@code null}
     * @return convex decomposition of the polygon minus its holes
     * @throws IllegalArgumentException if the input is empty
     */
    public static Paths64 decompose(Paths64 polygonWithHoles) { ... }
}
```

---

### Algorithm: Hertel–Mehlhorn

The Hertel–Mehlhorn algorithm produces a convex partition from a triangulation
in O(n) time (after the O(n log n) triangulation step). It is not optimal —
the minimum convex partition is NP-hard — but guarantees at most 4× the optimal
number of pieces.

#### Step 1 — Triangulate

Call the existing `Triangulation.triangulate()` to obtain a set of triangles
covering the input polygon.

```java
Paths64 triangles = new Paths64();
Triangulation.TriangulateResult result = Triangulation.triangulate(input, triangles);
if (result != Triangulation.TriangulateResult.success) {
    // Fall back: return input as a single element
    return new Paths64(List.of(polygon));
}
```

#### Step 2 — Build Triangle Adjacency Graph

Two triangles are adjacent if they share exactly one edge. Build an adjacency
list:

```
For each triangle T:
    For each edge (Vi, Vj) of T:
        Find the (at most one) other triangle T' sharing this edge.
        Record adjacency(T, T') with shared edge (Vi, Vj).
```

Edges are represented as ordered pairs `(min(i,j), max(i,j))` of vertex
indices (using a `HashMap<EdgeKey, Integer>` where `EdgeKey` wraps the two
vertex indices) to find the shared edge efficiently.

#### Step 3 — Greedy Merge

```
remaining = all triangles (as mutable polygon lists)
changed = true
while changed:
    changed = false
    for each pair of adjacent polygons (P, Q) sharing edge (Vi, Vj):
        merged = merge(P, Q, Vi, Vj)
        if isConvex(merged):
            replace P and Q in remaining with merged
            update adjacency graph
            changed = true
            break  // restart inner loop
return remaining
```

The merge operation concatenates the two polygon vertex lists, removing the
shared edge vertices from each polygon and stitching the boundaries together.

#### Convexity Test for the Merge

Use the `isConvex(Path64)` method from Feature 5 to test the merged polygon.
This test runs in O(n) time.

#### Step 4 — Normalise Output

Ensure each result polygon:
- Has counter-clockwise winding (positive area per library convention).
- Has no duplicate consecutive vertices.
- Contains at least 3 vertices.

---

### Internal Data Structures

```java
// Internal — not public API
record EdgeKey(long v1, long v2) {
    // Canonical form: v1 < v2
    static EdgeKey of(long a, long b) { return a < b ? new EdgeKey(a, b) : new EdgeKey(b, a); }
}

// Mutable polygon node in the merge graph
class PolyNode {
    List<Integer> vertices;          // vertex indices into a global vertex list
    List<PolyNode> adjacencies;      // neighbouring PolyNodes and shared edge
    boolean removed;
}
```

The global vertex list is built from the triangulation output (deduplicate
vertices by exact coordinate match using a `HashMap<Long, Integer>` where the
key encodes `(x << 32 | y)` for integer coordinates).

---

### Complexity

| Phase | Complexity |
|-------|-----------|
| Triangulation | O(n log n) |
| Adjacency graph construction | O(n) |
| Greedy merge (worst case) | O(n²) |
| Total | O(n²) |

In practice, the merge phase is fast because the number of merge iterations
is small for typical input shapes.

---

### Limitations

- **Self-intersecting polygons**: undefined behaviour. The triangulation step
  may produce incorrect results for self-intersecting input; validate input
  before calling if necessary.
- **Very thin slivers**: may produce numerically unstable triangles. The
  existing CDT triangulation is responsible for handling these; this feature
  defers to the triangulation's robustness.
- **Accuracy of triangulation beta**: the existing `Triangulation` class is
  marked beta. If it returns a failure result, `decompose()` falls back to
  returning the original polygon as a single element.
- **Holes**: holes are passed to the triangulation as part of the constrained
  polygon; the triangulation handles them. The merge step treats hole edges as
  constraint edges that cannot be merged across.

---

## Testing Strategy

- **Convex input**: a regular hexagon should be returned unchanged (one piece).
- **L-shaped polygon**: should decompose into exactly 2 or 3 convex pieces.
- **Star polygon**: should decompose into the same number as the point count
  of the star.
- **Polygon with hole**: a square with a square hole should produce a valid
  decomposition that entirely covers the square minus the hole, with no piece
  overlapping the hole.
- **Area conservation**: for any input, `sum(area(piece) for piece in result)`
  should equal `area(input)` within a small tolerance.
- **No overlaps**: for any pair of result pieces, `intersect(piece_i, piece_j)`
  should be empty or a zero-area edge/point.
- **All pieces convex**: for each result piece, `isConvex(piece) == true`.
- **Minimum vertex count**: all result pieces have ≥ 3 vertices.
- **Fallback on triangulation failure**: mock a triangulation failure; verify
  the original polygon is returned.
