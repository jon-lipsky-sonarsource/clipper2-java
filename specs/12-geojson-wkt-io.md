# Feature 12 — GeoJSON and WKT Import/Export

## Summary

This feature adds a new package `clipper2.io` containing two converter classes:

- `GeoJsonConverter` — parses GeoJSON geometry objects (`Polygon`,
  `MultiPolygon`, `GeometryCollection`) and serialises `Paths64`/`PathsD`
  back to GeoJSON strings.
- `WktConverter` — parses Well-Known Text (WKT) `POLYGON`, `MULTIPOLYGON`,
  `LINESTRING`, and `MULTILINESTRING` geometries and serialises back to WKT.

Both classes operate on standard `Paths64` / `PathsD` types and add no
runtime dependencies beyond the Java standard library.

## Justification

GeoJSON (RFC 7946) and WKT (ISO 19125) are the two dominant interchange
formats for 2D polygon data outside the SVG/graphics world:

- **GeoJSON** is the standard for geographic data in web APIs, mapping
  libraries (Leaflet, Mapbox), PostGIS, and virtually every modern GIS tool.
- **WKT** is used by SQL spatial extensions (`ST_GeomFromText`), QGIS, GDAL,
  the JTS Topology Suite, and desktop GIS applications.

Users working with geographic data must convert to/from these formats before
and after every clipping or offsetting operation. The converters are
straightforward text parsers and generators — no complex algorithms are
involved — and the value per line of code is very high for geo-focused users.

---

## Technical Specification

### Package

`clipper2.io`

---

## 12.1 WKT Converter

### Supported Types

| WKT Type | Clipper2 type | Notes |
|----------|---------------|-------|
| `POINT (x y)` | `Path64` (single vertex) | |
| `LINESTRING (x y, x y, ...)` | `Path64` / `PathD` | Open path |
| `POLYGON ((x y, ...), (x y, ...), ...)` | `Paths64` | First ring = outer; subsequent = holes |
| `MULTIPOLYGON (((...),...), ...)` | `Paths64` | All rings flattened |
| `MULTILINESTRING ((x y,...), ...)` | `Paths64` | |
| `GEOMETRYCOLLECTION (...)` | `Paths64` | Recurse into each geometry |

WKT 3D variants (`POINT Z`, `POLYGON Z (...)`) are accepted; the Z coordinate
is silently discarded (or, if Z callbacks are implemented per Feature 1,
optionally stored).

### New Class: `WktConverter`

```java
package clipper2.io;

import clipper2.core.Path64;
import clipper2.core.Paths64;
import clipper2.core.PathsD;
import clipper2.core.PathD;

/**
 * Converts between Well-Known Text (WKT) geometry strings and Clipper2 path
 * types.
 *
 * <p>Supports: POINT, LINESTRING, POLYGON, MULTIPOLYGON, MULTILINESTRING,
 * GEOMETRYCOLLECTION (and their 3D variants with Z coordinates, which are
 * silently dropped).
 *
 * <p>Coordinate order: WKT uses (x y) order, which is (longitude latitude)
 * for geographic data. No coordinate transformation is applied; callers are
 * responsible for any axis swapping.
 */
public final class WktConverter {

    private WktConverter() {}

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /**
     * Parses a WKT string and returns all ring/line geometries as integer
     * paths. Coordinates are rounded to the nearest {@code long}.
     *
     * @param wkt   the WKT string; must not be {@code null}
     * @param scale factor applied to each coordinate before rounding;
     *              use 1.0 for no scaling, or e.g. 1e7 to convert decimal
     *              degrees to fixed-point integer representation
     * @return all rings and line strings as a {@link Paths64}; never
     *         {@code null}; empty for EMPTY geometries
     * @throws WktParseException if the WKT syntax is invalid
     */
    public static Paths64 parse64(String wkt, double scale) { ... }

    /** Convenience overload with {@code scale = 1.0}. */
    public static Paths64 parse64(String wkt) { return parse64(wkt, 1.0); }

    /**
     * Parses a WKT string and returns all ring/line geometries as
     * floating-point paths (no rounding).
     *
     * @param wkt   the WKT string
     * @param scale factor applied to each coordinate
     */
    public static PathsD parseD(String wkt, double scale) { ... }

    /** Convenience overload with {@code scale = 1.0}. */
    public static PathsD parseD(String wkt) { return parseD(wkt, 1.0); }

    // -----------------------------------------------------------------------
    // Generation
    // -----------------------------------------------------------------------

    /**
     * Serialises {@code paths} to a WKT string.
     *
     * <p>A single path is emitted as a {@code LINESTRING} (open) or as the
     * outer ring of a {@code POLYGON} (closed, when {@code closedPaths} is
     * {@code true}). Multiple paths are emitted as a {@code MULTIPOLYGON}
     * or {@code MULTILINESTRING} as appropriate.
     *
     * @param paths         paths to serialise
     * @param decimalPlaces number of decimal places for coordinates
     * @param closedPaths   if {@code true}, treat each path as a closed
     *                      polygon ring; if {@code false}, as a line string
     * @return WKT string; "GEOMETRYCOLLECTION EMPTY" if {@code paths} is
     *         empty or null
     */
    public static String toWkt(PathsD paths, int decimalPlaces, boolean closedPaths) { ... }

    /** Integer overload (no decimal places). */
    public static String toWkt(Paths64 paths, boolean closedPaths) { ... }

    /**
     * Serialises a single polygon with holes to WKT {@code POLYGON} format.
     *
     * @param outer  the outer boundary
     * @param holes  the hole rings (may be empty or null)
     */
    public static String toWktPolygon(Path64 outer, Paths64 holes) { ... }
    public static String toWktPolygon(PathD outer, PathsD holes,
                                       int decimalPlaces) { ... }
}
```

### Exception Type

```java
package clipper2.io;

/** Thrown when a WKT string cannot be parsed. */
public class WktParseException extends RuntimeException {
    public WktParseException(String message) { super(message); }
    public WktParseException(String message, Throwable cause) { super(message, cause); }
}
```

### Parser Detail

The WKT grammar is simple enough to parse with a hand-written recursive descent
parser:

```
geometry    := type_keyword  ws? geometry_body
type_keyword := "POINT" | "LINESTRING" | "POLYGON" | "MULTIPOLYGON"
              | "MULTILINESTRING" | "GEOMETRYCOLLECTION"
              | (same with " Z" or "Z" suffix)
geometry_body := "EMPTY"
              |  "(" coordinate_sequence ")"  -- for POINT/LINESTRING
              |  "(" ring_list ")"            -- for POLYGON
              |  "(" polygon_list ")"         -- for MULTIPOLYGON
              |  "(" geometry_list ")"        -- for GEOMETRYCOLLECTION
coordinate   := number ws number (ws number)?  -- optional Z
coordinate_sequence := coordinate ("," ws? coordinate)*
ring_list    := "(" coordinate_sequence ")" ("," ws? "(" coordinate_sequence ")")*
polygon_list := "(" ring_list ")" ("," ws? "(" ring_list ")")*
```

Numbers are parsed with `Double.parseDouble()`. Whitespace is normalised. The
parser is case-insensitive for keywords.

For `POLYGON`, the first ring is the outer boundary; subsequent rings are
holes. All rings are returned as separate paths in the output `Paths64`
(matching the library's existing convention for polygon-with-holes).

---

## 12.2 GeoJSON Converter

### Supported Types

| GeoJSON Type | Clipper2 type |
|-------------|---------------|
| `"Point"` | `Path64` / `PathD` (single vertex) |
| `"LineString"` | `Path64` / `PathD` (open path) |
| `"Polygon"` | `Paths64` (first = outer, rest = holes) |
| `"MultiPolygon"` | `Paths64` (all rings flattened) |
| `"MultiLineString"` | `Paths64` |
| `"GeometryCollection"` | `Paths64` (all geometries flattened) |
| `"Feature"` | delegates to the `"geometry"` field |
| `"FeatureCollection"` | collects geometry from all features |

GeoJSON uses longitude/latitude order (x = longitude, y = latitude). No
transformation is applied.

### New Class: `GeoJsonConverter`

```java
package clipper2.io;

import clipper2.core.Paths64;
import clipper2.core.PathsD;

/**
 * Converts between GeoJSON geometry strings/objects and Clipper2 path types.
 *
 * <p>Only the geometry is extracted; GeoJSON properties (attributes) are
 * discarded.
 *
 * <p>GeoJSON coordinate arrays use [longitude, latitude] order, which maps
 * directly to [x, y] in Clipper2. No coordinate transformation is applied.
 */
public final class GeoJsonConverter {

    private GeoJsonConverter() {}

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /**
     * Parses a GeoJSON string (Geometry, Feature, or FeatureCollection) and
     * returns all ring and line coordinates as integer paths.
     *
     * @param geojson the GeoJSON string; must not be {@code null}
     * @param scale   coordinate scale factor applied before rounding to
     *                {@code long}; use 1e7 to convert decimal degrees to
     *                a common integer representation
     * @return all rings and lines; never {@code null}
     * @throws GeoJsonParseException if the JSON is malformed or an
     *         unsupported geometry type is encountered
     */
    public static Paths64 parse64(String geojson, double scale) { ... }

    public static Paths64 parse64(String geojson) { return parse64(geojson, 1.0); }

    /** Returns floating-point paths (no rounding). */
    public static PathsD parseD(String geojson, double scale) { ... }

    public static PathsD parseD(String geojson) { return parseD(geojson, 1.0); }

    // -----------------------------------------------------------------------
    // Generation
    // -----------------------------------------------------------------------

    /**
     * Serialises {@code paths} to a GeoJSON {@code MultiPolygon} string if
     * {@code closedPaths} is true, or {@code MultiLineString} if false.
     *
     * @param paths         the paths to serialise
     * @param decimalPlaces coordinate decimal places (1–15)
     * @param closedPaths   whether to treat paths as closed polygon rings
     * @return a GeoJSON geometry object string
     */
    public static String toGeoJson(PathsD paths, int decimalPlaces, boolean closedPaths) { ... }

    /** Integer overload. */
    public static String toGeoJson(Paths64 paths, boolean closedPaths) { ... }

    /**
     * Serialises a single polygon with holes to a GeoJSON {@code Polygon}.
     */
    public static String toGeoJsonPolygon(PathsD polygonWithHoles, int decimalPlaces) { ... }
}
```

### Exception Type

```java
package clipper2.io;

public class GeoJsonParseException extends RuntimeException {
    public GeoJsonParseException(String message) { super(message); }
    public GeoJsonParseException(String message, Throwable cause) { super(message, cause); }
}
```

### JSON Parser Detail

To avoid adding a runtime dependency (e.g., Jackson or Gson), a minimal
hand-written JSON parser is sufficient for GeoJSON geometry, which has a
predictable and limited structure. The parser needs only:

- Object navigation by string key.
- Array iteration.
- Number parsing.
- String value extraction (for type fields).

A ~200-line recursive descent JSON parser is the appropriate scope. It need not
handle all JSON (escape sequences in strings, arbitrary nesting depth); it only
needs to handle valid GeoJSON geometry.

Alternatively, document that the input string must be a valid GeoJSON geometry
and use `javax.script.ScriptEngine` with `JSON.parse` (Nashorn/Graal) if
available, falling back to the hand-written parser. However, the simplest and
most portable approach is the hand-written parser.

---

### Output Format (GeoJSON `Polygon`)

```json
{
  "type": "Polygon",
  "coordinates": [
    [[x1, y1], [x2, y2], ..., [x1, y1]],
    [[hx1, hy1], ..., [hx1, hy1]]
  ]
}
```

GeoJSON requires the first and last coordinate of each ring to be identical
(closed ring). This is handled automatically by the generator; when emitting
a `Path64`, the first vertex is repeated at the end.

GeoJSON winding convention (RFC 7946): outer rings are counter-clockwise;
holes are clockwise. The generator uses `Clipper.isPositive()` to determine
winding and reverses the ring if it does not match the expected convention.

---

## Scale Factor Convention

Both converters accept a `scale` parameter. For geographic data in decimal
degrees, a common fixed-point convention is:

| Scale | Precision | Use case |
|-------|-----------|---------|
| `1.0` | native precision | floating-point output |
| `1e5` | ~1 metre at equator | web mapping |
| `1e7` | ~1 cm at equator | high-precision geo |

Document this table in the Javadoc of the `scale` parameter.

---

## Testing Strategy

### WKT
- **Parse POLYGON**: `"POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))"` → 1 path with
  5 vertices (first == last; the parser should keep or remove the duplicate;
  document the choice).
- **Parse with hole**: `"POLYGON ((0 0, 20 0, 20 20, 0 20, 0 0), (5 5, 15 5, 15 15, 5 15, 5 5))"` → 2 paths.
- **Parse MULTIPOLYGON**: verify correct number of paths.
- **Parse EMPTY**: returns empty `Paths64`.
- **Round-trip**: parse, then serialise, then parse again; assert geometry is
  identical.
- **Scale**: parse `"POINT (1.5 2.5)"` with `scale = 10`; assert result is
  `(15, 25)` (rounded).
- **Invalid WKT**: `"CIRCLE (0 0, 5)"` → `WktParseException`.

### GeoJSON
- **Parse Polygon**: valid GeoJSON `Polygon` with one hole → 2 paths.
- **Parse Feature**: `{"type":"Feature","geometry":{"type":"Polygon",...},"properties":{}}` → geometry extracted.
- **Parse FeatureCollection**: 3 features → all geometries collected.
- **Generate Polygon**: assert output is valid JSON; parse it back and
  compare geometry.
- **Winding order**: generated outer ring is CCW; generated holes are CW.
- **Round-trip**: parse → serialise → parse; geometry identical.
- **Invalid JSON**: `"{type: Polygon}"` (unquoted key) → `GeoJsonParseException`.
