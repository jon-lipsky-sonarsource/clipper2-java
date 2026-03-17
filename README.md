# Clipper2 — Java Port

A Java port of [Clipper2](https://github.com/AngusJohnson/Clipper2), the polygon clipping and offsetting library by Angus Johnson.

> Original C# library: https://github.com/AngusJohnson/Clipper2
> License: [Boost Software License 1.0](https://www.boost.org/LICENSE_1_0.txt)

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jon-lipsky-sonarsource_clipper2-java&metric=alert_status&token=c72e28f3198a9949ee462f1d3712b8cace2d16dc)](https://sonarcloud.io/summary/new_code?id=jon-lipsky-sonarsource_clipper2-java)

---

## What is Clipper2?

Clipper2 performs **boolean polygon clipping** (intersection, union, difference, xor) and **polygon offsetting** on 2D polygons. It handles complex cases including self-intersecting polygons, holes, and open paths. This Java port provides the same functionality under the same Boost license.

---

## Features

- Boolean clipping: Intersection, Union, Difference, Xor
- Polygon offsetting with Miter, Round, and Square join types
- Open path offsetting with Butt, Round, and Square end caps
- Rectangle clipping (`rectClip`, `rectClipLines`)
- Path simplification (`simplifyPath`, `ramerDouglasPeucker`, `trimCollinear`)
- Minkowski Sum and Difference
- Polygon triangulation
- Both integer (`Path64`) and floating-point (`PathD`) coordinate types
- `PolyTree` output for hierarchical hole/outer polygon relationships

---

## Requirements

- Java 17 or later
- [Gradle](https://gradle.org/) (tested with 9.4.0)

---

## Build & Test

```bash
gradle test
```

All 50 unit tests should pass.

---

## Project Structure

```
clipper2-java/
├── src/
│   ├── main/java/clipper2/
│   │   ├── Clipper.java                  # Static convenience facade
│   │   ├── core/                         # Point64, PointD, Path64, Rect64, etc.
│   │   ├── engine/                       # ClipperBase, Clipper64, ClipperD, PolyTree, ...
│   │   ├── offset/                       # ClipperOffset, JoinType, EndType
│   │   ├── rectclip/                     # RectClip64, RectClipLines64
│   │   ├── minkowski/                    # Minkowski sum/difference
│   │   └── triangulation/               # Triangulation
│   └── test/java/clipper2/
│       ├── TestPolygons.java
│       ├── TestLines.java
│       ├── TestPolytree.java
│       ├── TestOffset.java
│       ├── TestRectClip.java
│       ├── TestMinkowski.java
│       ├── TestSimplify.java
│       └── utils/ClipperFileIO.java
└── Tests/                                # Shared test data (*.txt)
```

---

## Quick Example

```java
import clipper2.Clipper;
import clipper2.core.*;
import clipper2.engine.*;

// Create subject and clip polygons
Paths64 subj = new Paths64();
subj.add(Clipper.makePath(new long[]{0, 0, 100, 0, 100, 100, 0, 100}));

Paths64 clip = new Paths64();
clip.add(Clipper.makePath(new long[]{50, 50, 150, 50, 150, 150, 50, 150}));

// Intersect
Paths64 solution = Clipper.intersect(subj, clip, FillRule.NonZero);

// Offset (expand by 10 units)
Paths64 inflated = Clipper.inflatePaths(subj, 10,
    clipper2.offset.JoinType.Miter,
    clipper2.offset.EndType.Polygon);
```

---

## Differences from the C# Version

- Field names use Java conventions: `x`/`y` instead of `X`/`Y` on `Point64`/`PointD`
- No USINGZ (z-callback) support
- `ReuseableDataContainer64` is a top-level public class (in `clipper2.engine`)

---

## License

[Boost Software License 1.0](https://www.boost.org/LICENSE_1_0.txt) — same as the original Clipper2 library.
