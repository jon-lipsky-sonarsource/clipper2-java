# Clipper2-Java Enhancement Specifications — Overview

This directory contains detailed technical specifications for a set of proposed
enhancements to the Clipper2-Java library. Each feature has its own file with
a written justification and a full implementation specification.

---

## Background

Clipper2-Java is a Java port of Angus Johnson's Clipper2 library. It provides
boolean polygon clipping, polygon offsetting, rectangular clipping, Minkowski
sum/difference, constrained Delaunay triangulation, and path simplification.
The port is feature-complete relative to the core C# library with one
explicitly noted exception (USINGZ / Z-coordinate callbacks).

The enhancements described here fall into three categories:

1. **Parity with the C# original** — features that exist upstream but were not
   ported.
2. **Natural geometric extensions** — operations that fit naturally alongside
   the existing API and are commonly needed by the same users.
3. **Ecosystem integration** — I/O adapters and interchange formats that allow
   the library to connect with other tools and pipelines.

---

## Feature Index

| # | Feature | Category | Priority | Effort | Spec File |
|---|---------|----------|----------|--------|-----------|
| 1 | Z-Coordinate / Vertex Attribute Callbacks | Parity | High | Medium | [01-z-coordinate-callbacks.md](01-z-coordinate-callbacks.md) |
| 2 | Bezier Curve Flattening | Extension | High | Medium | [02-bezier-curve-flattening.md](02-bezier-curve-flattening.md) |
| 3 | SVG Path Parsing and Generation | Integration | High | Medium | [03-svg-path-parsing.md](03-svg-path-parsing.md) |
| 4 | Affine Transformations | Extension | High | Low | [04-affine-transformations.md](04-affine-transformations.md) |
| 5 | Additional Geometric Utilities | Extension | High | Low | [05-geometric-utilities.md](05-geometric-utilities.md) |
| 6 | Visvalingam–Whyatt Simplification | Extension | Medium | Low | [06-visvalingam-whyatt.md](06-visvalingam-whyatt.md) |
| 7 | Convex Decomposition | Extension | Medium | Medium | [07-convex-decomposition.md](07-convex-decomposition.md) |
| 8 | Extended Shape Constructors | Extension | Medium | Low | [08-shape-constructors.md](08-shape-constructors.md) |
| 9 | Coordinate Snapping and Fuzzy Merging | Extension | Medium | Low | [09-coordinate-snapping.md](09-coordinate-snapping.md) |
| 10 | Path Interpolation and Resampling | Extension | Medium | Medium | [10-path-interpolation.md](10-path-interpolation.md) |
| 11 | PolyTree Traversal Utilities | Extension | Low | Low | [11-polytree-traversal.md](11-polytree-traversal.md) |
| 12 | GeoJSON and WKT Import/Export | Integration | Low | Low | [12-geojson-wkt-io.md](12-geojson-wkt-io.md) |

---

## Design Principles

All specifications follow these conventions to maintain consistency with the
existing codebase.

### Coordinate type convention
- `Path64` / `Paths64` — 64-bit signed integer coordinates. Use where exact
  arithmetic is important or when the caller is already working in integer
  space.
- `PathD` / `PathsD` — `double` coordinates. Use for operations that are
  inherently floating-point (e.g., rotation, Bezier flattening).
- Where an operation exists in both forms, name them identically and rely on
  overload resolution.
- Where an operation is inherently floating-point (trigonometry, arc-length),
  accept `Path64` input but always return `PathD`.

### Package layout
New packages are added under `clipper2.*` following the existing structure:

```
clipper2/
├── Clipper.java            (existing facade — extended with new static methods)
├── core/                   (existing)
├── engine/                 (existing)
├── offset/                 (existing)
├── rectclip/               (existing)
├── minkowski/              (existing)
├── triangulation/          (existing)
├── curves/                 (NEW — Features 2, 3)
├── transform/              (NEW — Feature 4)
├── decomposition/          (NEW — Feature 7)
└── io/                     (NEW — Feature 12)
```

Features 1, 5, 6, 8, 9, 10, and 11 add methods directly to `Clipper.java`
or to a small utility class in an appropriate existing or new package.

### Nullability and error handling
- Return empty collections (`new Paths64()`, etc.) rather than `null` on
  degenerate input (matching existing library convention).
- Throw `IllegalArgumentException` for clearly invalid arguments (e.g.,
  negative radius, empty matrix array of wrong length).
- Document all pre-conditions in Javadoc.

### Testing
Each feature spec includes a testing strategy. New tests belong in
`src/test/java/clipper2/` following the `Test<Feature>.java` naming convention.

### No new runtime dependencies
All implementations must rely only on the Java standard library (Java 17+).
The existing test dependency on JUnit 5 is retained.

---

## Dependency Graph

Some features depend on or naturally layer on top of others:

```
Feature 2 (Bezier Flattening)
    └── Feature 3 (SVG Parsing) depends on Feature 2

Feature 7 (Convex Decomposition)
    └── depends on existing Triangulation module

Feature 4 (Affine Transforms)
    └── Feature 10 (Path Interpolation) benefits from Feature 4
```

All other features are independent.

---

## Suggested Implementation Order

Given priorities, effort, and dependencies:

1. Feature 5 — Geometric Utilities (low effort, high value, no deps)
2. Feature 4 — Affine Transformations (low effort, high value, no deps)
3. Feature 8 — Shape Constructors (low effort, medium value, no deps)
4. Feature 9 — Coordinate Snapping (low effort, medium value, no deps)
5. Feature 6 — Visvalingam–Whyatt (low effort, medium value, no deps)
6. Feature 11 — PolyTree Traversal (low effort, low value, no deps)
7. Feature 1 — Z-Coordinate Callbacks (medium effort, high value, requires
   Point64/PointD changes — review carefully)
8. Feature 2 — Bezier Flattening (medium effort, high value, no deps)
9. Feature 3 — SVG Parsing (medium effort, high value, depends on Feature 2)
10. Feature 10 — Path Interpolation (medium effort, medium value, no deps)
11. Feature 7 — Convex Decomposition (medium effort, medium value, depends on
    existing Triangulation)
12. Feature 12 — GeoJSON / WKT I/O (low effort, low value, no deps)
