# Feature 11 — PolyTree Traversal Utilities

## Summary

This feature adds ergonomic traversal, filtering, and collection helpers for
`PolyTree64` and `PolyTreeD` to the `Clipper` facade and a new companion
class `PolyTreeUtils`. The existing `polyTreeToPaths64()` method collects all
paths, but there is no way to filter by depth, iterate with a visitor, or
collect only outer polygons or only holes.

## Justification

`PolyTree64` is the output type for hierarchical clipping results where the
caller needs to know which polygons are outer boundaries and which are holes.
The tree structure correctly encodes this (odd-depth nodes are holes, even-depth
are outer), but working with it is cumbersome:

- There is no iterator or visitor API; callers must write their own recursive
  traversal.
- Getting "all outer polygons" or "all holes" requires understanding the
  depth-based convention and writing boilerplate.
- There is no way to map a tree into a flat list with associated metadata
  (depth, parent polygon, etc.).

These utilities are low-effort and make the `PolyTree` result type
significantly more approachable.

---

## Technical Specification

Methods are added both directly to `Clipper` (for the most common operations)
and to a new `PolyTreeUtils` class for the more specialised ones.

---

### Methods Added to `Clipper`

```java
// clipper2/Clipper.java  (additions)

/**
 * Returns all outer polygons (even-depth nodes) from {@code tree} as a
 * flat {@link Paths64}.
 *
 * <p>The root node itself (depth -1) is skipped. Depth-0 nodes are outer
 * polygons; depth-2 are "outer" polygons inside a hole; etc.
 *
 * @param tree the poly tree to collect from; must not be {@code null}
 * @return all even-depth paths; never {@code null}
 */
public static Paths64 getOuterPolygons(PolyTree64 tree) { ... }

/**
 * Returns all hole polygons (odd-depth nodes) from {@code tree} as a flat
 * {@link Paths64}.
 *
 * @param tree the poly tree; must not be {@code null}
 * @return all odd-depth paths; never {@code null}
 */
public static Paths64 getHoles(PolyTree64 tree) { ... }

/** Double-precision equivalents. */
public static PathsD getOuterPolygons(PolyTreeD tree) { ... }
public static PathsD getHoles(PolyTreeD tree) { ... }
```

---

### New Class: `PolyTreeUtils`

```java
package clipper2.engine;

import clipper2.core.Path64;
import clipper2.core.PathD;
import clipper2.core.Paths64;
import clipper2.core.PathsD;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility methods for traversing and querying {@link PolyTree64} and
 * {@link PolyTreeD} structures.
 */
public final class PolyTreeUtils {

    private PolyTreeUtils() {}

    // -----------------------------------------------------------------------
    // Traversal
    // -----------------------------------------------------------------------

    /**
     * Performs a depth-first traversal of {@code tree}, invoking
     * {@code visitor} for each {@link PolyPath64} node (excluding the root).
     *
     * <p>Children are visited in the order they appear in the parent's child
     * list. The visitor receives each node after its parent has been visited
     * (pre-order traversal).
     *
     * @param tree    the poly tree to traverse; must not be {@code null}
     * @param visitor the action to perform on each node
     */
    public static void walk(PolyTree64 tree, Consumer<PolyPath64> visitor) { ... }

    /** Double-precision overload. */
    public static void walk(PolyTreeD tree, Consumer<PolyPathD> visitor) { ... }

    /**
     * Performs a breadth-first traversal of {@code tree}.
     */
    public static void walkBreadthFirst(PolyTree64 tree, Consumer<PolyPath64> visitor) { ... }

    // -----------------------------------------------------------------------
    // Filtering and collection
    // -----------------------------------------------------------------------

    /**
     * Collects all nodes that satisfy {@code predicate} into a list.
     *
     * <p>Example — collect only nodes at depth 2:
     * <pre>
     *   List&lt;PolyPath64&gt; depth2 = PolyTreeUtils.collect(tree,
     *       node -&gt; node.getLevel() == 2);
     * </pre>
     *
     * @param tree      the poly tree
     * @param predicate the filter condition; receives each non-root node
     * @return a list of matching nodes in depth-first order
     */
    public static List<PolyPath64> collect(PolyTree64 tree,
                                            Predicate<PolyPath64> predicate) { ... }

    /**
     * Collects all paths at exactly {@code depth} levels below the root.
     *
     * @param tree  the poly tree
     * @param depth 0-based depth (0 = direct children of root)
     * @return paths at that depth; empty if none
     */
    public static Paths64 pathsAtDepth(PolyTree64 tree, int depth) { ... }

    // -----------------------------------------------------------------------
    // Structured result
    // -----------------------------------------------------------------------

    /**
     * A pairing of an outer polygon with its direct holes.
     */
    public record PolygonWithHoles(Path64 outer, Paths64 holes) {

        /**
         * Returns the outer polygon and all its holes as a {@link Paths64}
         * suitable for use with APIs that expect the outer boundary first.
         */
        public Paths64 toPathsWithHoles() {
            Paths64 result = new Paths64(1 + holes.size());
            result.add(outer);
            result.addAll(holes);
            return result;
        }
    }

    /**
     * Returns a list of {@link PolygonWithHoles} records for each top-level
     * outer polygon (depth-0 node) in the tree, with its direct hole children
     * included.
     *
     * <p>This is a convenience for the common pattern of processing each
     * outer polygon with its immediate holes as a unit.
     *
     * @param tree the poly tree
     * @return a list with one entry per top-level outer polygon
     */
    public static List<PolygonWithHoles> topLevelPolygonsWithHoles(PolyTree64 tree) { ... }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    /**
     * Returns the total number of non-root nodes in {@code tree}.
     *
     * <p>This is equivalent to the total number of polygons (outer + holes).
     */
    public static int totalNodeCount(PolyTree64 tree) { ... }

    /**
     * Returns the maximum depth of any node in {@code tree} (root has
     * depth -1 by the library's convention; direct children have depth 0).
     */
    public static int maxDepth(PolyTree64 tree) { ... }
}
```

---

### Implementation Notes

#### `walk` (depth-first)

```java
public static void walk(PolyTree64 tree, Consumer<PolyPath64> visitor) {
    for (int i = 0; i < tree.count(); i++) {
        walkNode(tree.get(i), visitor);
    }
}

private static void walkNode(PolyPath64 node, Consumer<PolyPath64> visitor) {
    visitor.accept(node);
    for (int i = 0; i < node.count(); i++) {
        walkNode(node.get(i), visitor);
    }
}
```

#### `getOuterPolygons` / `getHoles`

```java
public static Paths64 getOuterPolygons(PolyTree64 tree) {
    Paths64 result = new Paths64();
    walk(tree, node -> { if (!node.isHole()) result.add(node.getPolygon()); });
    return result;
}

public static Paths64 getHoles(PolyTree64 tree) {
    Paths64 result = new Paths64();
    walk(tree, node -> { if (node.isHole()) result.add(node.getPolygon()); });
    return result;
}
```

Where `node.getPolygon()` refers to the existing path stored in the
`PolyPath64` node (the existing API calls this `getPath()` or exposes it as
a field — use the actual field name from the codebase).

#### `topLevelPolygonsWithHoles`

```java
public static List<PolygonWithHoles> topLevelPolygonsWithHoles(PolyTree64 tree) {
    List<PolygonWithHoles> result = new ArrayList<>();
    for (int i = 0; i < tree.count(); i++) {
        PolyPath64 outer = tree.get(i);       // depth-0 = outer
        Paths64 holes = new Paths64();
        for (int j = 0; j < outer.count(); j++) {
            holes.add(outer.get(j).getPolygon());  // depth-1 = holes
        }
        result.add(new PolygonWithHoles(outer.getPolygon(), holes));
    }
    return result;
}
```

---

## Relation to Existing API

The existing `Clipper.polyTreeToPaths64(PolyTree64)` collects all paths
regardless of depth or hole status. The new methods complement rather than
replace it:

| Method | What it returns |
|--------|----------------|
| `polyTreeToPaths64` | All paths (outer + holes) |
| `getOuterPolygons` | Only outer (non-hole) paths |
| `getHoles` | Only hole paths |
| `pathsAtDepth(tree, 0)` | Top-level outer boundaries only |
| `topLevelPolygonsWithHoles` | Top-level outers paired with their holes |

---

## Testing Strategy

- **Simple tree**: build a tree with one outer polygon containing one hole;
  verify `getOuterPolygons` returns 1 path, `getHoles` returns 1 path.
- **Deep nesting**: outer → hole → outer → hole structure; verify
  `getOuterPolygons` returns 2 paths, `getHoles` returns 2 paths.
- **`walk` order**: verify depth-first pre-order visit sequence against
  expected node order.
- **`pathsAtDepth`**: verify correct paths returned for depth 0, 1, and an
  out-of-range depth (empty result).
- **`topLevelPolygonsWithHoles`**: verify the record correctly pairs outer with
  its holes; verify `toPathsWithHoles()` has outer as first element.
- **Empty tree**: all methods return empty collections without throwing.
- **`totalNodeCount`** and **`maxDepth`**: verify against hand-constructed
  trees of known structure.
