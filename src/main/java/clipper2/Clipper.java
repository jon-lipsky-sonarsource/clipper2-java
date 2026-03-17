/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  14 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Static convenience methods for the Clipper2 library             *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2;

import clipper2.core.ClipType;
import clipper2.core.FillRule;
import clipper2.core.InternalClipper;
import clipper2.core.Path64;
import clipper2.core.PathD;
import clipper2.core.PathType;
import clipper2.core.Paths64;
import clipper2.core.PathsD;
import clipper2.core.Point64;
import clipper2.core.PointD;
import clipper2.core.Rect64;
import clipper2.core.RectD;
import clipper2.engine.ClipperD;
import clipper2.engine.Clipper64;
import clipper2.engine.PointInPolygonResult;
import clipper2.engine.PolyPath64;
import clipper2.engine.PolyPathD;
import clipper2.engine.PolyTree64;
import clipper2.engine.PolyTreeD;
import clipper2.minkowski.Minkowski;
import clipper2.triangulation.Triangulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Static utility class providing convenience wrappers for clipping, offsetting,
 * scaling, simplification, and other path operations.
 *
 * <p>Methods that reference {@code ClipperOffset}, {@code RectClip64}, and
 * {@code RectClipLines64} will compile once those classes are added to the project.
 */
public final class Clipper {

  private Clipper() {}

  // -------------------------------------------------------------------------
  // Invalid-rect sentinels
  // -------------------------------------------------------------------------

  private static final Rect64 INVALID_RECT64 = new Rect64(false);
  private static final RectD INVALID_RECT_D = new RectD(false);

  public static Rect64 invalidRect64() { return INVALID_RECT64; }
  public static RectD invalidRectD() { return INVALID_RECT_D; }

  // -------------------------------------------------------------------------
  // Boolean clipping — integer paths
  // -------------------------------------------------------------------------

  public static Paths64 intersect(Paths64 subject, Paths64 clip, FillRule fillRule) {
    return booleanOp(ClipType.Intersection, subject, clip, fillRule);
  }

  public static Paths64 union(Paths64 subject, FillRule fillRule) {
    return booleanOp(ClipType.Union, subject, null, fillRule);
  }

  public static Paths64 union(Paths64 subject, Paths64 clip, FillRule fillRule) {
    return booleanOp(ClipType.Union, subject, clip, fillRule);
  }

  public static Paths64 difference(Paths64 subject, Paths64 clip, FillRule fillRule) {
    return booleanOp(ClipType.Difference, subject, clip, fillRule);
  }

  public static Paths64 xor(Paths64 subject, Paths64 clip, FillRule fillRule) {
    return booleanOp(ClipType.Xor, subject, clip, fillRule);
  }

  public static Paths64 booleanOp(ClipType clipType,
      Paths64 subject, Paths64 clip, FillRule fillRule) {
    Paths64 solution = new Paths64();
    if (subject == null) return solution;
    Clipper64 c = new Clipper64();
    c.addPaths(subject, PathType.Subject);
    if (clip != null) c.addPaths(clip, PathType.Clip);
    c.execute(clipType, fillRule, solution);
    return solution;
  }

  public static void booleanOp(ClipType clipType,
      Paths64 subject, Paths64 clip,
      PolyTree64 polytree, FillRule fillRule) {
    if (subject == null) return;
    Clipper64 c = new Clipper64();
    c.addPaths(subject, PathType.Subject);
    if (clip != null) c.addPaths(clip, PathType.Clip);
    c.execute(clipType, fillRule, polytree);
  }

  // -------------------------------------------------------------------------
  // Boolean clipping — double paths
  // -------------------------------------------------------------------------

  public static PathsD intersect(PathsD subject, PathsD clip,
      FillRule fillRule, int precision) {
    return booleanOp(ClipType.Intersection, subject, clip, fillRule, precision);
  }

  public static PathsD intersect(PathsD subject, PathsD clip, FillRule fillRule) {
    return intersect(subject, clip, fillRule, 2);
  }

  public static PathsD union(PathsD subject, FillRule fillRule) {
    return booleanOp(ClipType.Union, subject, null, fillRule, 2);
  }

  public static PathsD union(PathsD subject, PathsD clip,
      FillRule fillRule, int precision) {
    return booleanOp(ClipType.Union, subject, clip, fillRule, precision);
  }

  public static PathsD union(PathsD subject, PathsD clip, FillRule fillRule) {
    return union(subject, clip, fillRule, 2);
  }

  public static PathsD difference(PathsD subject, PathsD clip,
      FillRule fillRule, int precision) {
    return booleanOp(ClipType.Difference, subject, clip, fillRule, precision);
  }

  public static PathsD difference(PathsD subject, PathsD clip, FillRule fillRule) {
    return difference(subject, clip, fillRule, 2);
  }

  public static PathsD xor(PathsD subject, PathsD clip,
      FillRule fillRule, int precision) {
    return booleanOp(ClipType.Xor, subject, clip, fillRule, precision);
  }

  public static PathsD xor(PathsD subject, PathsD clip, FillRule fillRule) {
    return xor(subject, clip, fillRule, 2);
  }

  public static PathsD booleanOp(ClipType clipType, PathsD subject, PathsD clip,
      FillRule fillRule, int precision) {
    PathsD solution = new PathsD();
    ClipperD c = new ClipperD(precision);
    c.addSubject(subject);
    if (clip != null) c.addClip(clip);
    c.execute(clipType, fillRule, solution);
    return solution;
  }

  public static PathsD booleanOp(ClipType clipType, PathsD subject, PathsD clip,
      FillRule fillRule) {
    return booleanOp(clipType, subject, clip, fillRule, 2);
  }

  public static void booleanOp(ClipType clipType,
      PathsD subject, PathsD clip,
      PolyTreeD polytree, FillRule fillRule, int precision) {
    if (subject == null) return;
    ClipperD c = new ClipperD(precision);
    c.addPaths(subject, PathType.Subject);
    if (clip != null) c.addPaths(clip, PathType.Clip);
    c.execute(clipType, fillRule, polytree);
  }

  public static void booleanOp(ClipType clipType,
      PathsD subject, PathsD clip,
      PolyTreeD polytree, FillRule fillRule) {
    booleanOp(clipType, subject, clip, polytree, fillRule, 2);
  }

  // -------------------------------------------------------------------------
  // InflatePaths — references ClipperOffset (to be added later)
  // -------------------------------------------------------------------------

  /**
   * Inflates (offsets) the given paths.
   * Requires {@code clipper2.offset.ClipperOffset}.
   */
  public static Paths64 inflatePaths(Paths64 paths, double delta,
      clipper2.offset.JoinType joinType,
      clipper2.offset.EndType endType,
      double miterLimit, double arcTolerance) {
    clipper2.offset.ClipperOffset co =
        new clipper2.offset.ClipperOffset(miterLimit, arcTolerance, false, false);
    co.addPaths(paths, joinType, endType);
    Paths64 solution = new Paths64();
    co.execute(delta, solution);
    return solution;
  }

  public static Paths64 inflatePaths(Paths64 paths, double delta,
      clipper2.offset.JoinType joinType, clipper2.offset.EndType endType) {
    return inflatePaths(paths, delta, joinType, endType, 2.0, 0.0);
  }

  public static PathsD inflatePaths(PathsD paths, double delta,
      clipper2.offset.JoinType joinType, clipper2.offset.EndType endType,
      double miterLimit, int precision, double arcTolerance) {
    InternalClipper.checkPrecision(precision);
    double scale = Math.pow(10, precision);
    Paths64 tmp = scalePaths64(paths, scale);
    clipper2.offset.ClipperOffset co =
        new clipper2.offset.ClipperOffset(miterLimit, scale * arcTolerance, false, false);
    co.addPaths(tmp, joinType, endType);
    co.execute(delta * scale, tmp);
    return scalePathsD(tmp, 1.0 / scale);
  }

  public static PathsD inflatePaths(PathsD paths, double delta,
      clipper2.offset.JoinType joinType, clipper2.offset.EndType endType) {
    return inflatePaths(paths, delta, joinType, endType, 2.0, 2, 0.0);
  }

  // -------------------------------------------------------------------------
  // RectClip — references RectClip64 / RectClipLines64 (to be added later)
  // -------------------------------------------------------------------------

  public static Paths64 rectClip(Rect64 rect, Paths64 paths) {
    if (rect.isEmpty() || paths.isEmpty()) return new Paths64();
    clipper2.rectclip.RectClip64 rc = new clipper2.rectclip.RectClip64(rect);
    return rc.execute(paths);
  }

  public static Paths64 rectClip(Rect64 rect, Path64 path) {
    if (rect.isEmpty() || path.isEmpty()) return new Paths64();
    Paths64 tmp = new Paths64();
    tmp.add(path);
    return rectClip(rect, tmp);
  }

  public static PathsD rectClip(RectD rect, PathsD paths, int precision) {
    InternalClipper.checkPrecision(precision);
    if (rect.isEmpty() || paths.isEmpty()) return new PathsD();
    double scale = Math.pow(10, precision);
    Rect64 r = scaleRect(rect, scale);
    Paths64 tmpPath = scalePaths64(paths, scale);
    clipper2.rectclip.RectClip64 rc = new clipper2.rectclip.RectClip64(r);
    tmpPath = rc.execute(tmpPath);
    return scalePathsD(tmpPath, 1.0 / scale);
  }

  public static PathsD rectClip(RectD rect, PathsD paths) {
    return rectClip(rect, paths, 2);
  }

  public static PathsD rectClip(RectD rect, PathD path, int precision) {
    if (rect.isEmpty() || path.isEmpty()) return new PathsD();
    PathsD tmp = new PathsD();
    tmp.add(path);
    return rectClip(rect, tmp, precision);
  }

  public static PathsD rectClip(RectD rect, PathD path) {
    return rectClip(rect, path, 2);
  }

  public static Paths64 rectClipLines(Rect64 rect, Paths64 paths) {
    if (rect.isEmpty() || paths.isEmpty()) return new Paths64();
    clipper2.rectclip.RectClipLines64 rc = new clipper2.rectclip.RectClipLines64(rect);
    return rc.execute(paths);
  }

  public static Paths64 rectClipLines(Rect64 rect, Path64 path) {
    if (rect.isEmpty() || path.isEmpty()) return new Paths64();
    Paths64 tmp = new Paths64();
    tmp.add(path);
    return rectClipLines(rect, tmp);
  }

  public static PathsD rectClipLines(RectD rect, PathsD paths, int precision) {
    InternalClipper.checkPrecision(precision);
    if (rect.isEmpty() || paths.isEmpty()) return new PathsD();
    double scale = Math.pow(10, precision);
    Rect64 r = scaleRect(rect, scale);
    Paths64 tmpPath = scalePaths64(paths, scale);
    clipper2.rectclip.RectClipLines64 rc = new clipper2.rectclip.RectClipLines64(r);
    tmpPath = rc.execute(tmpPath);
    return scalePathsD(tmpPath, 1.0 / scale);
  }

  public static PathsD rectClipLines(RectD rect, PathsD paths) {
    return rectClipLines(rect, paths, 2);
  }

  public static PathsD rectClipLines(RectD rect, PathD path, int precision) {
    if (rect.isEmpty() || path.isEmpty()) return new PathsD();
    PathsD tmp = new PathsD();
    tmp.add(path);
    return rectClipLines(rect, tmp, precision);
  }

  public static PathsD rectClipLines(RectD rect, PathD path) {
    return rectClipLines(rect, path, 2);
  }

  // -------------------------------------------------------------------------
  // Area
  // -------------------------------------------------------------------------

  public static double area(Path64 path) {
    double a = 0.0;
    int cnt = path.size();
    if (cnt < 3) return 0.0;
    Point64 prevPt = path.get(cnt - 1);
    for (Point64 pt : path) {
      a += (double) (prevPt.y + pt.y) * (prevPt.x - pt.x);
      prevPt = pt;
    }
    return a * 0.5;
  }

  public static double area(Paths64 paths) {
    double a = 0.0;
    for (Path64 path : paths) a += area(path);
    return a;
  }

  public static double area(PathD path) {
    double a = 0.0;
    int cnt = path.size();
    if (cnt < 3) return 0.0;
    PointD prevPt = path.get(cnt - 1);
    for (PointD pt : path) {
      a += (prevPt.y + pt.y) * (prevPt.x - pt.x);
      prevPt = pt;
    }
    return a * 0.5;
  }

  public static double area(PathsD paths) {
    double a = 0.0;
    for (PathD path : paths) a += area(path);
    return a;
  }

  // -------------------------------------------------------------------------
  // IsPositive
  // -------------------------------------------------------------------------

  public static boolean isPositive(Path64 poly) { return area(poly) >= 0; }

  public static boolean isPositive(PathD poly) { return area(poly) >= 0; }

  // -------------------------------------------------------------------------
  // Path string helpers
  // -------------------------------------------------------------------------

  public static String path64ToString(Path64 path) {
    StringBuilder sb = new StringBuilder();
    for (Point64 pt : path) sb.append(pt.toString());
    return sb.append('\n').toString();
  }

  public static String paths64ToString(Paths64 paths) {
    StringBuilder sb = new StringBuilder();
    for (Path64 path : paths) sb.append(path64ToString(path));
    return sb.toString();
  }

  public static String pathDToString(PathD path) {
    StringBuilder sb = new StringBuilder();
    for (PointD pt : path) sb.append(pt.toString());
    return sb.append('\n').toString();
  }

  public static String pathsDToString(PathsD paths) {
    StringBuilder sb = new StringBuilder();
    for (PathD path : paths) sb.append(pathDToString(path));
    return sb.toString();
  }

  // -------------------------------------------------------------------------
  // OffsetPath / TranslatePath
  // -------------------------------------------------------------------------

  public static Path64 offsetPath(Path64 path, long dx, long dy) {
    Path64 result = new Path64(path.size());
    for (Point64 pt : path) result.add(new Point64(pt.x + dx, pt.y + dy));
    return result;
  }

  public static Path64 translatePath(Path64 path, long dx, long dy) {
    return offsetPath(path, dx, dy);
  }

  public static Paths64 translatePaths(Paths64 paths, long dx, long dy) {
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) result.add(offsetPath(path, dx, dy));
    return result;
  }

  public static PathD translatePath(PathD path, double dx, double dy) {
    PathD result = new PathD(path.size());
    for (PointD pt : path) result.add(new PointD(pt.x + dx, pt.y + dy));
    return result;
  }

  public static PathsD translatePaths(PathsD paths, double dx, double dy) {
    PathsD result = new PathsD(paths.size());
    for (PathD path : paths) result.add(translatePath(path, dx, dy));
    return result;
  }

  // -------------------------------------------------------------------------
  // Scale helpers
  // -------------------------------------------------------------------------

  public static Point64 scalePoint64(Point64 pt, double scale) {
    return new Point64(
        InternalClipper.roundAwayFromZero(pt.x * scale),
        InternalClipper.roundAwayFromZero(pt.y * scale));
  }

  public static PointD scalePointD(Point64 pt, double scale) {
    return new PointD(pt.x * scale, pt.y * scale);
  }

  public static Rect64 scaleRect(RectD rec, double scale) {
    return new Rect64(
        (long) (rec.left * scale),
        (long) (rec.top * scale),
        (long) (rec.right * scale),
        (long) (rec.bottom * scale));
  }

  public static Path64 scalePath(Path64 path, double scale) {
    if (InternalClipper.isAlmostZero(scale - 1)) return path;
    Path64 result = new Path64(path.size());
    for (Point64 pt : path)
      result.add(new Point64((long)(pt.x * scale), (long)(pt.y * scale)));
    return result;
  }

  public static Paths64 scalePaths(Paths64 paths, double scale) {
    if (InternalClipper.isAlmostZero(scale - 1)) return paths;
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) result.add(scalePath(path, scale));
    return result;
  }

  public static PathD scalePath(PathD path, double scale) {
    if (InternalClipper.isAlmostZero(scale - 1)) return path;
    PathD result = new PathD(path.size());
    for (PointD pt : path) result.add(new PointD(pt, scale));
    return result;
  }

  public static PathsD scalePaths(PathsD paths, double scale) {
    if (InternalClipper.isAlmostZero(scale - 1)) return paths;
    PathsD result = new PathsD(paths.size());
    for (PathD path : paths) result.add(scalePath(path, scale));
    return result;
  }

  /** Converts PathD to Path64 with scaling (involves type conversion). */
  public static Path64 scalePath64(PathD path, double scale) {
    Path64 res = new Path64(path.size());
    for (PointD pt : path) res.add(new Point64(pt, scale));
    return res;
  }

  public static Paths64 scalePaths64(PathsD paths, double scale) {
    Paths64 res = new Paths64(paths.size());
    for (PathD path : paths) res.add(scalePath64(path, scale));
    return res;
  }

  /** Converts Path64 to PathD with scaling. */
  public static PathD scalePathD(Path64 path, double scale) {
    PathD res = new PathD(path.size());
    for (Point64 pt : path) res.add(new PointD(pt, scale));
    return res;
  }

  public static PathsD scalePathsD(Paths64 paths, double scale) {
    PathsD res = new PathsD(paths.size());
    for (Path64 path : paths) res.add(scalePathD(path, scale));
    return res;
  }

  // -------------------------------------------------------------------------
  // Type-conversion without scaling
  // -------------------------------------------------------------------------

  public static Path64 toPath64(PathD path) {
    Path64 result = new Path64(path.size());
    for (PointD pt : path) result.add(new Point64(pt));
    return result;
  }

  public static Paths64 toPaths64(PathsD paths) {
    Paths64 result = new Paths64(paths.size());
    for (PathD path : paths) result.add(toPath64(path));
    return result;
  }

  public static PathD toPathD(Path64 path) {
    PathD result = new PathD(path.size());
    for (Point64 pt : path) result.add(new PointD(pt));
    return result;
  }

  public static PathsD toPathsD(Paths64 paths) {
    PathsD result = new PathsD(paths.size());
    for (Path64 path : paths) result.add(toPathD(path));
    return result;
  }

  // -------------------------------------------------------------------------
  // ReversePath / ReversePaths
  // -------------------------------------------------------------------------

  public static Path64 reversePath(Path64 path) {
    Path64 result = new Path64(path);
    java.util.Collections.reverse(result);
    return result;
  }

  public static PathD reversePath(PathD path) {
    PathD result = new PathD(path);
    java.util.Collections.reverse(result);
    return result;
  }

  public static Paths64 reversePaths(Paths64 paths) {
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) result.add(reversePath(path));
    return result;
  }

  public static PathsD reversePaths(PathsD paths) {
    PathsD result = new PathsD(paths.size());
    for (PathD path : paths) result.add(reversePath(path));
    return result;
  }

  // -------------------------------------------------------------------------
  // GetBounds
  // -------------------------------------------------------------------------

  public static Rect64 getBounds(Path64 path) {
    Rect64 result = new Rect64(false);
    for (Point64 pt : path) {
      if (pt.x < result.left) result.left = pt.x;
      if (pt.x > result.right) result.right = pt.x;
      if (pt.y < result.top) result.top = pt.y;
      if (pt.y > result.bottom) result.bottom = pt.y;
    }
    return result.left == Long.MAX_VALUE ? new Rect64() : result;
  }

  public static Rect64 getBounds(Paths64 paths) {
    Rect64 result = new Rect64(false);
    for (Path64 path : paths)
      for (Point64 pt : path) {
        if (pt.x < result.left) result.left = pt.x;
        if (pt.x > result.right) result.right = pt.x;
        if (pt.y < result.top) result.top = pt.y;
        if (pt.y > result.bottom) result.bottom = pt.y;
      }
    return result.left == Long.MAX_VALUE ? new Rect64() : result;
  }

  public static RectD getBounds(PathD path) {
    RectD result = new RectD(false);
    for (PointD pt : path) {
      if (pt.x < result.left) result.left = pt.x;
      if (pt.x > result.right) result.right = pt.x;
      if (pt.y < result.top) result.top = pt.y;
      if (pt.y > result.bottom) result.bottom = pt.y;
    }
    return Math.abs(result.left - Double.MAX_VALUE) < InternalClipper.FLOATING_POINT_TOLERANCE
        ? new RectD() : result;
  }

  public static RectD getBounds(PathsD paths) {
    RectD result = new RectD(false);
    for (PathD path : paths)
      for (PointD pt : path) {
        if (pt.x < result.left) result.left = pt.x;
        if (pt.x > result.right) result.right = pt.x;
        if (pt.y < result.top) result.top = pt.y;
        if (pt.y > result.bottom) result.bottom = pt.y;
      }
    return Math.abs(result.left - Double.MAX_VALUE) < InternalClipper.FLOATING_POINT_TOLERANCE
        ? new RectD() : result;
  }

  // -------------------------------------------------------------------------
  // MakePath helpers
  // -------------------------------------------------------------------------

  public static Path64 makePath(int[] arr) {
    int len = arr.length / 2;
    Path64 p = new Path64(len);
    for (int i = 0; i < len; i++) p.add(new Point64(arr[i * 2], arr[i * 2 + 1]));
    return p;
  }

  public static Path64 makePath(long[] arr) {
    int len = arr.length / 2;
    Path64 p = new Path64(len);
    for (int i = 0; i < len; i++) p.add(new Point64(arr[i * 2], arr[i * 2 + 1]));
    return p;
  }

  public static PathD makePath(double[] arr) {
    int len = arr.length / 2;
    PathD p = new PathD(len);
    for (int i = 0; i < len; i++) p.add(new PointD(arr[i * 2], arr[i * 2 + 1]));
    return p;
  }

  // -------------------------------------------------------------------------
  // Math helpers
  // -------------------------------------------------------------------------

  public static double sqr(double val) { return val * val; }
  public static double sqr(long val)   { return (double) val * (double) val; }

  public static double distanceSqr(Point64 pt1, Point64 pt2) {
    return sqr(pt1.x - pt2.x) + sqr(pt1.y - pt2.y);
  }

  public static Point64 midPoint(Point64 pt1, Point64 pt2) {
    return new Point64((pt1.x + pt2.x) / 2, (pt1.y + pt2.y) / 2);
  }

  public static PointD midPoint(PointD pt1, PointD pt2) {
    return new PointD((pt1.x + pt2.x) / 2, (pt1.y + pt2.y) / 2);
  }

  public static void inflateRect(Rect64 rec, int dx, int dy) {
    rec.left -= dx; rec.right += dx; rec.top -= dy; rec.bottom += dy;
  }

  public static void inflateRect(RectD rec, double dx, double dy) {
    rec.left -= dx; rec.right += dx; rec.top -= dy; rec.bottom += dy;
  }

  public static boolean pointsNearEqual(PointD pt1, PointD pt2, double distanceSqrd) {
    return sqr(pt1.x - pt2.x) + sqr(pt1.y - pt2.y) < distanceSqrd;
  }

  // -------------------------------------------------------------------------
  // Strip duplicates / near-duplicates
  // -------------------------------------------------------------------------

  public static PathD stripNearDuplicates(PathD path,
      double minEdgeLenSqrd, boolean isClosedPath) {
    int cnt = path.size();
    PathD result = new PathD(cnt);
    if (cnt == 0) return result;
    PointD lastPt = path.get(0);
    result.add(lastPt);
    for (int i = 1; i < cnt; i++) {
      if (!pointsNearEqual(lastPt, path.get(i), minEdgeLenSqrd)) {
        lastPt = path.get(i);
        result.add(lastPt);
      }
    }
    if (isClosedPath && pointsNearEqual(lastPt, result.get(0), minEdgeLenSqrd))
      result.remove(result.size() - 1);
    return result;
  }

  public static Path64 stripDuplicates(Path64 path, boolean isClosedPath) {
    int cnt = path.size();
    Path64 result = new Path64(cnt);
    if (cnt == 0) return result;
    Point64 lastPt = path.get(0);
    result.add(lastPt);
    for (int i = 1; i < cnt; i++) {
      if (!lastPt.equals(path.get(i))) {
        lastPt = path.get(i);
        result.add(lastPt);
      }
    }
    if (isClosedPath && lastPt.equals(result.get(0)))
      result.remove(result.size() - 1);
    return result;
  }

  // -------------------------------------------------------------------------
  // PolyTree helpers
  // -------------------------------------------------------------------------

  private static void addPolyNodeToPaths(PolyPath64 polyPath, Paths64 paths) {
    if (polyPath.getPolygon() != null && !polyPath.getPolygon().isEmpty())
      paths.add(polyPath.getPolygon());
    for (int i = 0; i < polyPath.count(); i++)
      addPolyNodeToPaths((PolyPath64) polyPath._childs.get(i), paths);
  }

  public static Paths64 polyTreeToPaths64(PolyTree64 polyTree) {
    Paths64 result = new Paths64();
    for (int i = 0; i < polyTree.count(); i++)
      addPolyNodeToPaths((PolyPath64) polyTree._childs.get(i), result);
    return result;
  }

  public static void addPolyNodeToPathsD(PolyPathD polyPath, PathsD paths) {
    if (polyPath.getPolygon() != null && !polyPath.getPolygon().isEmpty())
      paths.add(polyPath.getPolygon());
    for (int i = 0; i < polyPath.count(); i++)
      addPolyNodeToPathsD((PolyPathD) polyPath._childs.get(i), paths);
  }

  public static PathsD polyTreeToPathsD(PolyTreeD polyTree) {
    PathsD result = new PathsD();
    for (clipper2.engine.PolyPathBase child : polyTree) {
      addPolyNodeToPathsD((PolyPathD) child, result);
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // Perpendicular distance (squared) from a point to a line
  // -------------------------------------------------------------------------

  public static double perpendicDistFromLineSqrd(PointD pt, PointD line1, PointD line2) {
    double a = pt.x - line1.x;
    double b = pt.y - line1.y;
    double c = line2.x - line1.x;
    double d = line2.y - line1.y;
    if (c == 0 && d == 0) return 0;
    return sqr(a * d - c * b) / (c * c + d * d);
  }

  public static double perpendicDistFromLineSqrd(Point64 pt, Point64 line1, Point64 line2) {
    double a = (double) pt.x - line1.x;
    double b = (double) pt.y - line1.y;
    double c = (double) line2.x - line1.x;
    double d = (double) line2.y - line1.y;
    if (c == 0 && d == 0) return 0;
    return sqr(a * d - c * b) / (c * c + d * d);
  }

  // -------------------------------------------------------------------------
  // Ramer-Douglas-Peucker simplification
  // -------------------------------------------------------------------------

  private static void rdp(Path64 path, int begin, int end,
      double epsSqrd, boolean[] flags) {
    while (true) {
      int idx = 0;
      double max_d = 0;
      while (end > begin && path.get(begin).equals(path.get(end)))
        flags[end--] = false;
      for (int i = begin + 1; i < end; ++i) {
        double d = perpendicDistFromLineSqrd(path.get(i), path.get(begin), path.get(end));
        if (d <= max_d) continue;
        max_d = d; idx = i;
      }
      if (max_d <= epsSqrd) return;
      flags[idx] = true;
      if (idx > begin + 1) rdp(path, begin, idx, epsSqrd, flags);
      if (idx < end - 1) { begin = idx; continue; }
      break;
    }
  }

  public static Path64 ramerDouglasPeucker(Path64 path, double epsilon) {
    int len = path.size();
    if (len < 5) return path;
    boolean[] flags = new boolean[len];
    flags[0] = true; flags[len - 1] = true;
    rdp(path, 0, len - 1, sqr(epsilon), flags);
    Path64 result = new Path64(len);
    for (int i = 0; i < len; ++i) if (flags[i]) result.add(path.get(i));
    return result;
  }

  public static Paths64 ramerDouglasPeucker(Paths64 paths, double epsilon) {
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) result.add(ramerDouglasPeucker(path, epsilon));
    return result;
  }

  private static void rdp(PathD path, int begin, int end,
      double epsSqrd, boolean[] flags) {
    while (true) {
      int idx = 0;
      double max_d = 0;
      while (end > begin && path.get(begin).equals(path.get(end)))
        flags[end--] = false;
      for (int i = begin + 1; i < end; ++i) {
        double d = perpendicDistFromLineSqrd(path.get(i), path.get(begin), path.get(end));
        if (d <= max_d) continue;
        max_d = d; idx = i;
      }
      if (max_d <= epsSqrd) return;
      flags[idx] = true;
      if (idx > begin + 1) rdp(path, begin, idx, epsSqrd, flags);
      if (idx < end - 1) { begin = idx; continue; }
      break;
    }
  }

  public static PathD ramerDouglasPeucker(PathD path, double epsilon) {
    int len = path.size();
    if (len < 5) return path;
    boolean[] flags = new boolean[len];
    flags[0] = true; flags[len - 1] = true;
    rdp(path, 0, len - 1, sqr(epsilon), flags);
    PathD result = new PathD(len);
    for (int i = 0; i < len; ++i) if (flags[i]) result.add(path.get(i));
    return result;
  }

  public static PathsD ramerDouglasPeucker(PathsD paths, double epsilon) {
    PathsD result = new PathsD(paths.size());
    for (PathD path : paths) result.add(ramerDouglasPeucker(path, epsilon));
    return result;
  }

  // -------------------------------------------------------------------------
  // SimplifyPath
  // -------------------------------------------------------------------------

  private static int getNext(int current, int high, boolean[] flags) {
    ++current;
    while (current <= high && flags[current]) ++current;
    if (current <= high) return current;
    current = 0;
    while (flags[current]) ++current;
    return current;
  }

  private static int getPrior(int current, int high, boolean[] flags) {
    if (current == 0) current = high;
    else --current;
    while (current > 0 && flags[current]) --current;
    if (!flags[current]) return current;
    current = high;
    while (flags[current]) --current;
    return current;
  }

  public static Path64 simplifyPath(Path64 path, double epsilon, boolean isClosedPath) {
    int len = path.size(), high = len - 1;
    double epsSqr = sqr(epsilon);
    if (len < 4) return path;
    boolean[] flags = new boolean[len];
    double[] dsq = new double[len];
    int curr = 0;
    if (isClosedPath) {
      dsq[0] = perpendicDistFromLineSqrd(path.get(0), path.get(high), path.get(1));
      dsq[high] = perpendicDistFromLineSqrd(path.get(high), path.get(0), path.get(high - 1));
    } else {
      dsq[0] = Double.MAX_VALUE;
      dsq[high] = Double.MAX_VALUE;
    }
    for (int i = 1; i < high; ++i)
      dsq[i] = perpendicDistFromLineSqrd(path.get(i), path.get(i - 1), path.get(i + 1));
    for (;;) {
      if (dsq[curr] > epsSqr) {
        int start = curr;
        do { curr = getNext(curr, high, flags); }
        while (curr != start && dsq[curr] > epsSqr);
        if (curr == start) break;
      }
      int prev = getPrior(curr, high, flags);
      int next = getNext(curr, high, flags);
      if (next == prev) break;
      int prior2;
      if (dsq[next] < dsq[curr]) {
        prior2 = prev; prev = curr; curr = next;
        next = getNext(next, high, flags);
      } else prior2 = getPrior(prev, high, flags);
      flags[curr] = true;
      curr = next;
      next = getNext(next, high, flags);
      if (isClosedPath || (curr != high && curr != 0))
        dsq[curr] = perpendicDistFromLineSqrd(path.get(curr), path.get(prev), path.get(next));
      if (isClosedPath || (prev != 0 && prev != high))
        dsq[prev] = perpendicDistFromLineSqrd(path.get(prev), path.get(prior2), path.get(curr));
    }
    Path64 result = new Path64(len);
    for (int i = 0; i < len; i++) if (!flags[i]) result.add(path.get(i));
    return result;
  }

  public static Path64 simplifyPath(Path64 path, double epsilon) {
    return simplifyPath(path, epsilon, true);
  }

  public static Paths64 simplifyPaths(Paths64 paths, double epsilon, boolean isClosedPaths) {
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) result.add(simplifyPath(path, epsilon, isClosedPaths));
    return result;
  }

  public static Paths64 simplifyPaths(Paths64 paths, double epsilon) {
    return simplifyPaths(paths, epsilon, true);
  }

  public static PathD simplifyPath(PathD path, double epsilon, boolean isClosedPath) {
    int len = path.size(), high = len - 1;
    double epsSqr = sqr(epsilon);
    if (len < 4) return path;
    boolean[] flags = new boolean[len];
    double[] dsq = new double[len];
    int curr = 0;
    if (isClosedPath) {
      dsq[0] = perpendicDistFromLineSqrd(path.get(0), path.get(high), path.get(1));
      dsq[high] = perpendicDistFromLineSqrd(path.get(high), path.get(0), path.get(high - 1));
    } else {
      dsq[0] = Double.MAX_VALUE;
      dsq[high] = Double.MAX_VALUE;
    }
    for (int i = 1; i < high; ++i)
      dsq[i] = perpendicDistFromLineSqrd(path.get(i), path.get(i - 1), path.get(i + 1));
    for (;;) {
      if (dsq[curr] > epsSqr) {
        int start = curr;
        do { curr = getNext(curr, high, flags); }
        while (curr != start && dsq[curr] > epsSqr);
        if (curr == start) break;
      }
      int prev = getPrior(curr, high, flags);
      int next = getNext(curr, high, flags);
      if (next == prev) break;
      int prior2;
      if (dsq[next] < dsq[curr]) {
        prior2 = prev; prev = curr; curr = next;
        next = getNext(next, high, flags);
      } else prior2 = getPrior(prev, high, flags);
      flags[curr] = true;
      curr = next;
      next = getNext(next, high, flags);
      if (isClosedPath || (curr != high && curr != 0))
        dsq[curr] = perpendicDistFromLineSqrd(path.get(curr), path.get(prev), path.get(next));
      if (isClosedPath || (prev != 0 && prev != high))
        dsq[prev] = perpendicDistFromLineSqrd(path.get(prev), path.get(prior2), path.get(curr));
    }
    PathD result = new PathD(len);
    for (int i = 0; i < len; i++) if (!flags[i]) result.add(path.get(i));
    return result;
  }

  public static PathD simplifyPath(PathD path, double epsilon) {
    return simplifyPath(path, epsilon, true);
  }

  public static PathsD simplifyPaths(PathsD paths, double epsilon, boolean isClosedPath) {
    PathsD result = new PathsD(paths.size());
    for (PathD path : paths) result.add(simplifyPath(path, epsilon, isClosedPath));
    return result;
  }

  public static PathsD simplifyPaths(PathsD paths, double epsilon) {
    return simplifyPaths(paths, epsilon, true);
  }

  // -------------------------------------------------------------------------
  // TrimCollinear
  // -------------------------------------------------------------------------

  public static Path64 trimCollinear(Path64 path, boolean isOpen) {
    int len = path.size();
    int i = 0;
    if (!isOpen) {
      while (i < len - 1
          && InternalClipper.isCollinear(path.get(len - 1), path.get(i), path.get(i + 1)))
        i++;
      while (i < len - 1
          && InternalClipper.isCollinear(path.get(len - 2), path.get(len - 1), path.get(i)))
        len--;
    }
    if (len - i < 3) {
      if (!isOpen || len < 2 || path.get(0).equals(path.get(1))) return new Path64();
      return path;
    }
    Path64 result = new Path64(len - i);
    Point64 last = path.get(i);
    result.add(last);
    for (i++; i < len - 1; i++) {
      if (InternalClipper.isCollinear(last, path.get(i), path.get(i + 1))) continue;
      last = path.get(i);
      result.add(last);
    }
    if (isOpen) {
      result.add(path.get(len - 1));
    } else if (!InternalClipper.isCollinear(last, path.get(len - 1), result.get(0))) {
      result.add(path.get(len - 1));
    } else {
      while (result.size() > 2 && InternalClipper.isCollinear(
          result.get(result.size() - 1), result.get(result.size() - 2), result.get(0)))
        result.remove(result.size() - 1);
      if (result.size() < 3) result.clear();
    }
    return result;
  }

  public static Path64 trimCollinear(Path64 path) {
    return trimCollinear(path, false);
  }

  public static PathD trimCollinear(PathD path, int precision, boolean isOpen) {
    InternalClipper.checkPrecision(precision);
    double scale = Math.pow(10, precision);
    Path64 p = scalePath64(path, scale);
    p = trimCollinear(p, isOpen);
    return scalePathD(p, 1.0 / scale);
  }

  public static PathD trimCollinear(PathD path, int precision) {
    return trimCollinear(path, precision, false);
  }

  // -------------------------------------------------------------------------
  // PointInPolygon
  // -------------------------------------------------------------------------

  public static PointInPolygonResult pointInPolygon(Point64 pt, Path64 polygon) {
    return InternalClipper.pointInPolygon(pt, polygon);
  }

  public static PointInPolygonResult pointInPolygon(PointD pt, PathD polygon, int precision) {
    InternalClipper.checkPrecision(precision);
    double scale = Math.pow(10, precision);
    Point64 p = new Point64(pt, scale);
    Path64 path = scalePath64(polygon, scale);
    return InternalClipper.pointInPolygon(p, path);
  }

  public static PointInPolygonResult pointInPolygon(PointD pt, PathD polygon) {
    return pointInPolygon(pt, polygon, 2);
  }

  // -------------------------------------------------------------------------
  // Ellipse
  // -------------------------------------------------------------------------

  public static Path64 ellipse(Point64 center, double radiusX, double radiusY, int steps) {
    if (radiusX <= 0) return new Path64();
    if (radiusY <= 0) radiusY = radiusX;
    if (steps <= 2)
      steps = (int) Math.ceil(Math.PI * Math.sqrt((radiusX + radiusY) / 2));
    double si = Math.sin(2 * Math.PI / steps);
    double co = Math.cos(2 * Math.PI / steps);
    double dx = co, dy = si;
    Path64 result = new Path64(steps);
    result.add(new Point64(center.x + radiusX, center.y));
    for (int i = 1; i < steps; ++i) {
      result.add(new Point64(center.x + radiusX * dx, center.y + radiusY * dy));
      double x = dx * co - dy * si;
      dy = dy * co + dx * si;
      dx = x;
    }
    return result;
  }

  public static Path64 ellipse(Point64 center, double radiusX, double radiusY) {
    return ellipse(center, radiusX, radiusY, 0);
  }

  public static Path64 ellipse(Point64 center, double radiusX) {
    return ellipse(center, radiusX, 0, 0);
  }

  public static PathD ellipse(PointD center, double radiusX, double radiusY, int steps) {
    if (radiusX <= 0) return new PathD();
    if (radiusY <= 0) radiusY = radiusX;
    if (steps <= 2)
      steps = (int) Math.ceil(Math.PI * Math.sqrt((radiusX + radiusY) / 2));
    double si = Math.sin(2 * Math.PI / steps);
    double co = Math.cos(2 * Math.PI / steps);
    double dx = co, dy = si;
    PathD result = new PathD(steps);
    result.add(new PointD(center.x + radiusX, center.y));
    for (int i = 1; i < steps; ++i) {
      result.add(new PointD(center.x + radiusX * dx, center.y + radiusY * dy));
      double x = dx * co - dy * si;
      dy = dy * co + dx * si;
      dx = x;
    }
    return result;
  }

  public static PathD ellipse(PointD center, double radiusX, double radiusY) {
    return ellipse(center, radiusX, radiusY, 0);
  }

  public static PathD ellipse(PointD center, double radiusX) {
    return ellipse(center, radiusX, 0, 0);
  }

  // -------------------------------------------------------------------------
  // Minkowski Sum and Difference
  // -------------------------------------------------------------------------

  public static Paths64 minkowskiSum(Path64 pattern, Path64 path, boolean isClosed) {
    return Minkowski.sum(pattern, path, isClosed);
  }

  public static PathsD minkowskiSum(PathD pattern, PathD path, boolean isClosed) {
    return Minkowski.sum(pattern, path, isClosed);
  }

  public static Paths64 minkowskiDiff(Path64 pattern, Path64 path, boolean isClosed) {
    return Minkowski.diff(pattern, path, isClosed);
  }

  public static PathsD minkowskiDiff(PathD pattern, PathD path, boolean isClosed) {
    return Minkowski.diff(pattern, path, isClosed);
  }

  // -------------------------------------------------------------------------
  // Triangulation
  // -------------------------------------------------------------------------

  public static Triangulation.TriangulateResult triangulate(
      Paths64 paths, Paths64[] solution, boolean useDelaunay) {
    return Triangulation.triangulate(paths, solution, useDelaunay);
  }

  public static Triangulation.TriangulateResult triangulate(
      Paths64 paths, Paths64[] solution) {
    return Triangulation.triangulate(paths, solution);
  }

  public static Triangulation.TriangulateResult triangulate(
      PathsD paths, int decPlaces, PathsD[] solution, boolean useDelaunay) {
    return Triangulation.triangulate(paths, decPlaces, solution, useDelaunay);
  }

  public static Triangulation.TriangulateResult triangulate(
      PathsD paths, int decPlaces, PathsD[] solution) {
    return Triangulation.triangulate(paths, decPlaces, solution);
  }

  // -------------------------------------------------------------------------
  // ShowPolyTreeStructure (console)
  // -------------------------------------------------------------------------

  private static void showPolyPathStructure(PolyPath64 pp, int level) {
    String spaces = "  ".repeat(level);
    String caption = pp.isHole() ? "Hole " : "Outer ";
    if (pp.count() == 0) {
      System.out.println(spaces + caption);
    } else {
      System.out.println(spaces + caption + "(" + pp.count() + ")");
      for (clipper2.engine.PolyPathBase child : pp)
        showPolyPathStructure((PolyPath64) child, level + 1);
    }
  }

  public static void showPolyTreeStructure(PolyTree64 polytree) {
    System.out.println("Polytree Root");
    for (clipper2.engine.PolyPathBase child : polytree)
      showPolyPathStructure((PolyPath64) child, 1);
  }

  private static void showPolyPathStructure(PolyPathD pp, int level) {
    String spaces = "  ".repeat(level);
    String caption = pp.isHole() ? "Hole " : "Outer ";
    if (pp.count() == 0) {
      System.out.println(spaces + caption);
    } else {
      System.out.println(spaces + caption + "(" + pp.count() + ")");
      for (clipper2.engine.PolyPathBase child : pp)
        showPolyPathStructure((PolyPathD) child, level + 1);
    }
  }

  public static void showPolyTreeStructure(PolyTreeD polytree) {
    System.out.println("Polytree Root");
    for (clipper2.engine.PolyPathBase child : polytree)
      showPolyPathStructure((PolyPathD) child, 1);
  }
}
