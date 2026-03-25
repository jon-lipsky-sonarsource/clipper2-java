# Feature 3 — SVG Path Parsing and Generation

## Summary

This feature adds a new class `SvgPathConverter` in the `clipper2.curves`
package that can parse an SVG `<path>` element's `d` attribute string into
`PathsD` and serialize `PathsD` / `Paths64` back to a `d` string. Curve
segments (arcs, quadratic and cubic Beziers) are flattened using Feature 2
(`BezierFlattener`) during parsing.

## Justification

SVG is the dominant interchange format for 2D vector graphics. Web developers,
data-visualization engineers, and CAD-adjacent tools all work with SVG paths.
Without the ability to read SVG `d` strings, a user who wants to clip two SVG
shapes must:

1. Parse the `d` attribute themselves.
2. Convert arcs and curves to polylines.
3. Convert to `PathsD`.
4. Call the library.
5. Convert the result back to a `d` string.

Steps 1–3 and 5 are non-trivial and must be implemented from scratch.
Providing this adapter makes the library immediately usable for SVG workflows
with a single static call.

**Prerequisite**: Feature 2 (Bezier Curve Flattening).

---

## Technical Specification

### Package

`clipper2.curves`

### New Class: `SvgPathConverter`

```java
package clipper2.curves;

import clipper2.core.PathD;
import clipper2.core.PathsD;
import clipper2.core.Paths64;
import clipper2.core.PointD;

/**
 * Converts between SVG path {@code d} attribute strings and Clipper2 path
 * types.
 *
 * <p>All curve segments (quadratic Bezier, cubic Bezier, elliptical arc) are
 * approximated as polylines during parsing. The {@code tolerance} parameter
 * controls the maximum permissible deviation from the true curve.
 *
 * <p>Coordinate system: SVG uses a Y-down coordinate system (origin at
 * top-left). Clipper2 is coordinate-system agnostic. No flip is applied by
 * this class; callers are responsible for any desired Y-axis transformation.
 */
public final class SvgPathConverter {

    private SvgPathConverter() {}

    // -----------------------------------------------------------------------
    // Parsing — SVG d string → PathsD
    // -----------------------------------------------------------------------

    /**
     * Parses an SVG path {@code d} attribute and returns the resulting paths.
     *
     * <p>Each {@code M} (moveto) command begins a new sub-path. Sub-paths are
     * returned as separate {@link PathD} elements. If a sub-path is closed
     * (ends with {@code Z}), it is returned as a closed polygon (start point
     * not repeated). Open sub-paths are returned as-is.
     *
     * @param d         the SVG path data string; must not be {@code null}
     * @param tolerance maximum perpendicular deviation for curve approximation;
     *                  typically 0.1–1.0 for screen rendering
     * @return a {@link PathsD} containing one element per sub-path; never
     *         {@code null}; empty if {@code d} is blank
     * @throws SvgParseException if {@code d} contains invalid syntax
     */
    public static PathsD parse(String d, double tolerance) { ... }

    /**
     * Parses using the default tolerance of {@link BezierFlattener#DEFAULT_TOLERANCE}.
     */
    public static PathsD parse(String d) {
        return parse(d, BezierFlattener.DEFAULT_TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // Generation — PathsD / Paths64 → SVG d string
    // -----------------------------------------------------------------------

    /**
     * Serializes {@link PathsD} to an SVG path {@code d} attribute string
     * using straight-line {@code L} commands only (no curve reconstruction).
     *
     * <p>Each path is represented as {@code M x,y L x,y ... Z} (closed) or
     * {@code M x,y L x,y ...} (open). All paths are concatenated into a
     * single string separated by spaces.
     *
     * @param paths         the paths to serialize; {@code null} is treated as
     *                      empty
     * @param decimalPlaces number of decimal places for coordinates (1–8)
     * @param closePaths    if {@code true}, append {@code Z} to each sub-path
     * @return SVG path {@code d} string; empty string if {@code paths} is
     *         empty or null
     */
    public static String toSvgPath(PathsD paths, int decimalPlaces, boolean closePaths) { ... }

    /**
     * Overload for integer paths.
     */
    public static String toSvgPath(Paths64 paths, boolean closePaths) { ... }

    /**
     * Convenience overload using 2 decimal places and closed paths.
     */
    public static String toSvgPath(PathsD paths) {
        return toSvgPath(paths, 2, true);
    }
}
```

### Exception Type

```java
package clipper2.curves;

/**
 * Thrown when an SVG path {@code d} string cannot be parsed.
 */
public class SvgParseException extends RuntimeException {
    private final int position;  // character offset in the d string where parsing failed

    public SvgParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }

    public int getPosition() { return position; }
}
```

---

### Parser Implementation Detail

#### Tokeniser

The `d` string is tokenised into a stream of command characters and numeric
values. The SVG grammar allows numbers to be separated by commas, whitespace,
or (for negative numbers following a positive number) by the `-` sign itself.

Tokenise in two passes:

1. **Lexical pass**: extract a list of `Token` objects — either a command
   character (`M`, `m`, `L`, `l`, `H`, `h`, `V`, `v`, `C`, `c`, `S`, `s`,
   `Q`, `q`, `T`, `t`, `A`, `a`, `Z`, `z`) or a `double` number.
2. **Command pass**: consume tokens sequentially, maintaining parser state.

#### Parser State

```
currentX, currentY   — current pen position
startX, startY       — start of current sub-path (for Z close-path)
lastCtrlX, lastCtrlY — last Bezier control point (for S/s and T/t reflection)
lastCommand          — last command character (for implicit repeat)
```

#### Commands

| Command | Parameters | Description |
|---------|-----------|-------------|
| `M x y` | 2 | Absolute moveto; starts new sub-path |
| `m dx dy` | 2 | Relative moveto |
| `L x y` | 2 | Absolute lineto |
| `l dx dy` | 2 | Relative lineto |
| `H x` | 1 | Absolute horizontal line |
| `h dx` | 1 | Relative horizontal line |
| `V y` | 1 | Absolute vertical line |
| `v dy` | 1 | Relative vertical line |
| `C x1 y1 x2 y2 x y` | 6 | Absolute cubic Bezier |
| `c dx1 dy1 dx2 dy2 dx dy` | 6 | Relative cubic Bezier |
| `S x2 y2 x y` | 4 | Absolute smooth cubic (reflects prev ctrl2) |
| `s dx2 dy2 dx dy` | 4 | Relative smooth cubic |
| `Q x1 y1 x y` | 4 | Absolute quadratic Bezier |
| `q dx1 dy1 dx dy` | 4 | Relative quadratic Bezier |
| `T x y` | 2 | Absolute smooth quadratic (reflects prev ctrl) |
| `t dx dy` | 2 | Relative smooth quadratic |
| `A rx ry xRot laf sf x y` | 7 | Absolute elliptical arc |
| `a rx ry xRot laf sf dx dy` | 7 | Relative elliptical arc |
| `Z` / `z` | 0 | Close path |

**Implicit command repetition**: if a command is followed immediately by
another set of parameters without a command letter, the same command is
repeated (except `M`/`m` which repeats as `L`/`l`).

#### Arc Conversion

SVG arcs use an endpoint parameterization `(x1, y1) → (x2, y2)` with radii
`(rx, ry)`, rotation `xRot`, large-arc-flag `laf`, and sweep-flag `sf`.

Convert to center parameterization following the algorithm in the SVG
specification §B.2.4, then approximate with polyline points:

```
Given center (cx, cy), start angle φ1, delta angle dφ, semi-axes rx, ry:

For t from 0 to 1 in steps of (tolerance / max(rx, ry)):
    angle = φ1 + t * dφ
    x = cx + rx * cos(angle) * cos(xRot) − ry * sin(angle) * sin(xRot)
    y = cy + rx * cos(angle) * sin(xRot) + ry * sin(angle) * cos(xRot)
    append (x, y) to output path
```

The step size `tolerance / max(rx, ry)` ensures the chord-height of each arc
segment is bounded by approximately `tolerance`. For a full circle this produces
about `2π * max(rx,ry) / tolerance` points, which is the same order as
`Clipper.ellipse()`.

#### Smooth Bezier Reflection

`S`/`s` reflects the previous cubic control point `ctrl2` through the current
endpoint. If the previous command was not `C`, `c`, `S`, or `s`, the
reflection point equals the current position.

`T`/`t` reflects the previous quadratic control point. If the previous command
was not `Q`, `q`, `T`, or `t`, the reflected point equals the current position.

---

### Generator Implementation Detail

The generator iterates over each `PathD` in `PathsD`:

```
For each PathD path:
    emit "M " + format(path[0])
    for each subsequent point path[i]:
        emit " L " + format(path[i])
    if closePaths:
        emit " Z"
```

Number formatting uses `String.format("%.Nf", value)` where N is
`decimalPlaces`. Trailing zeros are stripped from fractional parts (e.g.,
`1.50` → `1.5`).

---

## Edge Cases and Error Handling

| Scenario | Behaviour |
|----------|-----------|
| Empty `d` string | Returns empty `PathsD` |
| Sub-path with fewer than 2 points | Sub-path is omitted |
| `Z` with no preceding `M` | Ignored |
| Arc with `rx == 0` or `ry == 0` | Treated as a straight line to the endpoint |
| Arc where start == end | Treated as a full ellipse if `laf == 1`, otherwise zero-length segment |
| Numbers out of range (e.g., very large) | Parsed as `double`; no clamping |
| Invalid token (e.g., letter `X`) | `SvgParseException` thrown with offset |

---

## Testing Strategy

- **Simple square**: `"M 0 0 L 100 0 L 100 100 L 0 100 Z"` → 4-vertex closed
  path.
- **Relative commands**: `"M 0 0 l 100 0 l 0 100 l -100 0 z"` → same square.
- **Cubic Bezier**: parse a known cubic; measure maximum distance from
  polyline to true curve; assert < tolerance.
- **Arc**: parse `"M 100 200 A 25 25 0 1 1 50 100"` (large arc); verify vertex
  count and that all vertices lie on the ellipse within tolerance.
- **Smooth cubic `S`**: verify control point reflection is correct.
- **Implicit repetition**: `"M 0 0 L 10 10 20 20 30 30"` → three line segments.
- **Multiple sub-paths**: `"M 0 0 L 10 0 Z M 20 0 L 30 0 Z"` → two separate
  `PathD` elements returned.
- **Roundtrip**: parse a `d` string, serialize back with `toSvgPath`, parse
  again; assert the geometry is identical within floating-point precision.
- **`toSvgPath` on `Paths64`**: verify integer coordinates are emitted without
  decimal points.
- **Invalid input**: assert `SvgParseException` for `"M 0 0 X 10 10"`.
