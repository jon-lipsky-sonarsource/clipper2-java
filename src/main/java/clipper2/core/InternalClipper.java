/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

import clipper2.engine.PointInPolygonResult;

public final class InternalClipper {

  public static final long MAX_INT64 = Long.MAX_VALUE;
  public static final long MAX_COORD = MAX_INT64 / 4;
  public static final double max_coord = MAX_COORD;
  public static final double min_coord = -MAX_COORD;
  public static final long INVALID64 = MAX_INT64;

  public static final double FLOATING_POINT_TOLERANCE = 1E-12;
  public static final double DEFAULT_MIN_EDGE_LENGTH = 0.1;

  private static final String PRECISION_RANGE_ERROR = "Error: Precision is out of range.";

  private InternalClipper() {}

  /**
   * Rounds away from zero — matches C# MidpointRounding.AwayFromZero.
   * Java's Math.round rounds half-up (differs for negative .5 values).
   */
  public static long roundAwayFromZero(double value) {
    return (value >= 0) ? (long) Math.floor(value + 0.5) : (long) Math.ceil(value - 0.5);
  }

  public static boolean isAlmostZero(double value) {
    return Math.abs(value) <= FLOATING_POINT_TOLERANCE;
  }

  public static int triSign(long x) {
    return (x < 0) ? -1 : (x > 0) ? 1 : 0;
  }

  // ---- 128-bit unsigned multiply ----
  // Stored as two longs representing lo64 and hi64 of a 128-bit unsigned result.
  // Java longs are signed, but bitwise operations treat them as unsigned here.

  /** Multiply two unsigned 64-bit values (passed as Java longs) into a 128-bit result. */
  public static long[] multiplyUInt64(long a, long b) {
    // Treat a and b as unsigned 64-bit. Split each into 32-bit halves.
    long aLo = a & 0xFFFFFFFFL;
    long aHi = (a >>> 32) & 0xFFFFFFFFL;
    long bLo = b & 0xFFFFFFFFL;
    long bHi = (b >>> 32) & 0xFFFFFFFFL;

    long x1 = aLo * bLo;
    long x2 = aHi * bLo + (x1 >>> 32);
    long x3 = aLo * bHi + (x2 & 0xFFFFFFFFL);

    long lo64 = ((x3 & 0xFFFFFFFFL) << 32) | (x1 & 0xFFFFFFFFL);
    long hi64 = aHi * bHi + (x2 >>> 32) + (x3 >>> 32);
    return new long[]{lo64, hi64};
  }

  /**
   * Compare two unsigned 64-bit longs (treating the Java longs as unsigned).
   * Returns negative if a < b, 0 if equal, positive if a > b.
   */
  private static int compareUnsigned(long a, long b) {
    return Long.compareUnsigned(a, b);
  }

  /** Returns true if a*b == c*d (signed). Uses 128-bit arithmetic to avoid overflow. */
  static boolean productsAreEqual(long a, long b, long c, long d) {
    long absA = Math.abs(a);
    long absB = Math.abs(b);
    long absC = Math.abs(c);
    long absD = Math.abs(d);

    long[] mulAB = multiplyUInt64(absA, absB);
    long[] mulCD = multiplyUInt64(absC, absD);

    int signAB = triSign(a) * triSign(b);
    int signCD = triSign(c) * triSign(d);

    return mulAB[0] == mulCD[0] && mulAB[1] == mulCD[1] && signAB == signCD;
  }

  public static boolean isCollinear(Point64 pt1, Point64 sharedPt, Point64 pt2) {
    long a = sharedPt.x - pt1.x;
    long b = pt2.y - sharedPt.y;
    long c = sharedPt.y - pt1.y;
    long d = pt2.x - sharedPt.x;
    return productsAreEqual(a, b, c, d);
  }

  public static double crossProduct(Point64 pt1, Point64 pt2, Point64 pt3) {
    return ((double) (pt2.x - pt1.x) * (pt3.y - pt2.y)
        - (double) (pt2.y - pt1.y) * (pt3.x - pt2.x));
  }

  public static int crossProductSign(Point64 pt1, Point64 pt2, Point64 pt3) {
    long a = pt2.x - pt1.x;
    long b = pt3.y - pt2.y;
    long c = pt2.y - pt1.y;
    long d = pt3.x - pt2.x;

    long[] ab = multiplyUInt64(Math.abs(a), Math.abs(b));
    long[] cd = multiplyUInt64(Math.abs(c), Math.abs(d));
    int signAB = triSign(a) * triSign(b);
    int signCD = triSign(c) * triSign(d);

    if (signAB == signCD) {
      int result;
      if (ab[1] == cd[1]) {
        if (ab[0] == cd[0]) return 0;
        result = Long.compareUnsigned(ab[0], cd[0]) > 0 ? 1 : -1;
      } else {
        result = Long.compareUnsigned(ab[1], cd[1]) > 0 ? 1 : -1;
      }
      return (signAB > 0) ? result : -result;
    }
    return (signAB > signCD) ? 1 : -1;
  }

  public static double dotProduct(Point64 pt1, Point64 pt2, Point64 pt3) {
    return ((double) (pt2.x - pt1.x) * (pt3.x - pt2.x)
        + (double) (pt2.y - pt1.y) * (pt3.y - pt2.y));
  }

  public static double crossProduct(PointD vec1, PointD vec2) {
    return vec1.y * vec2.x - vec2.y * vec1.x;
  }

  public static double dotProduct(PointD vec1, PointD vec2) {
    return vec1.x * vec2.x + vec1.y * vec2.y;
  }

  public static long checkCastInt64(double val) {
    if (val >= max_coord || val <= min_coord) return INVALID64;
    return roundAwayFromZero(val);
  }

  public static void checkPrecision(int precision) {
    if (precision < -8 || precision > 8) {
      throw new RuntimeException(PRECISION_RANGE_ERROR);
    }
  }

  /**
   * Gets the intersection point of two line segments.
   * Returns null if the lines are parallel (det == 0).
   * The result is constrained to lie within segment 1.
   */
  public static Point64 getLineIntersectPt(Point64 ln1a, Point64 ln1b,
      Point64 ln2a, Point64 ln2b) {
    double dy1 = ln1b.y - ln1a.y;
    double dx1 = ln1b.x - ln1a.x;
    double dy2 = ln2b.y - ln2a.y;
    double dx2 = ln2b.x - ln2a.x;
    double det = dy1 * dx2 - dy2 * dx1;
    if (det == 0.0) return null;

    double t = ((ln1a.x - ln2a.x) * dy2 - (ln1a.y - ln2a.y) * dx2) / det;
    if (t <= 0.0) return new Point64(ln1a);
    if (t >= 1.0) return new Point64(ln1b);
    // avoid rounding for performance (matches C# comment #664)
    return new Point64((long) (ln1a.x + t * dx1), (long) (ln1a.y + t * dy1));
  }

  public static PointD getLineIntersectPtD(PointD ln1a, PointD ln1b,
      PointD ln2a, PointD ln2b) {
    double dy1 = ln1b.y - ln1a.y;
    double dx1 = ln1b.x - ln1a.x;
    double dy2 = ln2b.y - ln2a.y;
    double dx2 = ln2b.x - ln2a.x;
    double det = dy1 * dx2 - dy2 * dx1;
    if (det == 0.0) return null;

    double t = ((ln1a.x - ln2a.x) * dy2 - (ln1a.y - ln2a.y) * dx2) / det;
    if (t <= 0.0) return new PointD(ln1a);
    if (t >= 1.0) return new PointD(ln1b);
    return new PointD(ln1a.x + t * dx1, ln1a.y + t * dy1);
  }

  public static boolean segsIntersect(Point64 seg1a, Point64 seg1b,
      Point64 seg2a, Point64 seg2b, boolean inclusive) {
    double dy1 = seg1b.y - seg1a.y;
    double dx1 = seg1b.x - seg1a.x;
    double dy2 = seg2b.y - seg2a.y;
    double dx2 = seg2b.x - seg2a.x;
    double cp = dy1 * dx2 - dy2 * dx1;
    if (cp == 0) return false;

    if (inclusive) {
      double t = (seg1a.x - seg2a.x) * dy2 - (seg1a.y - seg2a.y) * dx2;
      if (t == 0) return true;
      if (t > 0) {
        if (cp < 0 || t > cp) return false;
      } else if (cp > 0 || t < cp) return false;

      t = (seg1a.x - seg2a.x) * dy1 - (seg1a.y - seg2a.y) * dx1;
      if (t == 0) return true;
      if (t > 0) return cp > 0 && t <= cp;
      else return cp < 0 && t >= cp;
    } else {
      double t = (seg1a.x - seg2a.x) * dy2 - (seg1a.y - seg2a.y) * dx2;
      if (t == 0) return false;
      if (t > 0) {
        if (cp < 0 || t >= cp) return false;
      } else if (cp > 0 || t <= cp) return false;

      t = (seg1a.x - seg2a.x) * dy1 - (seg1a.y - seg2a.y) * dx1;
      if (t == 0) return false;
      if (t > 0) return cp > 0 && t < cp;
      else return cp < 0 && t > cp;
    }
  }

  public static Rect64 getBounds(Path64 path) {
    if (path.isEmpty()) return new Rect64();
    Rect64 result = new Rect64(false); // invalid (inverted extremes)
    for (Point64 pt : path) {
      if (pt.x < result.left) result.left = pt.x;
      if (pt.x > result.right) result.right = pt.x;
      if (pt.y < result.top) result.top = pt.y;
      if (pt.y > result.bottom) result.bottom = pt.y;
    }
    return result;
  }

  public static Point64 getClosestPtOnSegment(Point64 offPt, Point64 seg1, Point64 seg2) {
    if (seg1.x == seg2.x && seg1.y == seg2.y) return new Point64(seg1);
    double dx = seg2.x - seg1.x;
    double dy = seg2.y - seg1.y;
    double q = ((offPt.x - seg1.x) * dx + (offPt.y - seg1.y) * dy) / (dx * dx + dy * dy);
    if (q < 0) q = 0;
    else if (q > 1) q = 1;
    // use banker's rounding (ToEven) to match C++ nearbyint behaviour (#782)
    return new Point64(
        seg1.x + (long) Math.rint(q * dx),
        seg1.y + (long) Math.rint(q * dy));
  }

  public static PointInPolygonResult pointInPolygon(Point64 pt, Path64 polygon) {
    int len = polygon.size();
    int start = 0;
    if (len < 3) return PointInPolygonResult.IsOutside;

    while (start < len && polygon.get(start).y == pt.y) start++;
    if (start == len) return PointInPolygonResult.IsOutside;

    boolean isAbove = polygon.get(start).y < pt.y;
    boolean startingAbove = isAbove;
    int val = 0;
    int i = start + 1;
    int end = len;

    while (true) {
      if (i == end) {
        if (end == 0 || start == 0) break;
        end = start;
        i = 0;
      }

      if (isAbove) {
        while (i < end && polygon.get(i).y < pt.y) i++;
      } else {
        while (i < end && polygon.get(i).y > pt.y) i++;
      }

      if (i == end) continue;

      Point64 curr = polygon.get(i);
      Point64 prev = (i > 0) ? polygon.get(i - 1) : polygon.get(len - 1);

      if (curr.y == pt.y) {
        if (curr.x == pt.x || (curr.y == prev.y
            && ((pt.x < prev.x) != (pt.x < curr.x)))) {
          return PointInPolygonResult.IsOn;
        }
        i++;
        if (i == start) break;
        continue;
      }

      if (pt.x < curr.x && pt.x < prev.x) {
        // only interested in edges crossing on the left — do nothing
      } else if (pt.x > prev.x && pt.x > curr.x) {
        val = 1 - val;
      } else {
        int cps2 = crossProductSign(prev, curr, pt);
        if (cps2 == 0) return PointInPolygonResult.IsOn;
        if ((cps2 < 0) == isAbove) val = 1 - val;
      }
      isAbove = !isAbove;
      i++;
    }

    if (isAbove == startingAbove) {
      return val == 0 ? PointInPolygonResult.IsOutside : PointInPolygonResult.IsInside;
    }
    if (i == len) i = 0;
    int cps = (i == 0)
        ? crossProductSign(polygon.get(len - 1), polygon.get(0), pt)
        : crossProductSign(polygon.get(i - 1), polygon.get(i), pt);

    if (cps == 0) return PointInPolygonResult.IsOn;
    if ((cps < 0) == isAbove) val = 1 - val;
    return val == 0 ? PointInPolygonResult.IsOutside : PointInPolygonResult.IsInside;
  }

  public static boolean path2ContainsPath1(Path64 path1, Path64 path2) {
    PointInPolygonResult pip = PointInPolygonResult.IsOn;
    for (Point64 pt : path1) {
      PointInPolygonResult result = pointInPolygon(pt, path2);
      if (result == PointInPolygonResult.IsOutside) {
        if (pip == PointInPolygonResult.IsOutside) return false;
        pip = PointInPolygonResult.IsOutside;
      } else if (result == PointInPolygonResult.IsInside) {
        if (pip == PointInPolygonResult.IsInside) return true;
        pip = PointInPolygonResult.IsInside;
      }
    }
    Point64 mp = getBounds(path1).midPoint();
    return pointInPolygon(mp, path2) != PointInPolygonResult.IsOutside;
  }

}
