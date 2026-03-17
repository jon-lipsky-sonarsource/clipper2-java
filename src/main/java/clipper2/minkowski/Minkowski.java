/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  10 October 2024                                                 *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2024                                         *
 * Purpose   :  Minkowski Sum and Difference                                    *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.minkowski;

import clipper2.Clipper;
import clipper2.core.*;

public final class Minkowski {

  private Minkowski() {}

  private static Paths64 minkowskiInternal(Path64 pattern, Path64 path,
      boolean isSum, boolean isClosed) {
    int delta = isClosed ? 0 : 1;
    int patLen = pattern.size();
    int pathLen = path.size();
    Paths64 tmp = new Paths64(pathLen);

    for (Point64 pathPt : path) {
      Path64 path2 = new Path64(patLen);
      if (isSum) {
        for (Point64 basePt : pattern) {
          path2.add(pathPt.add(basePt));
        }
      } else {
        for (Point64 basePt : pattern) {
          path2.add(pathPt.subtract(basePt));
        }
      }
      tmp.add(path2);
    }

    Paths64 result = new Paths64((pathLen - delta) * patLen);
    int g = isClosed ? pathLen - 1 : 0;

    int h = patLen - 1;
    for (int i = delta; i < pathLen; i++) {
      for (int j = 0; j < patLen; j++) {
        Path64 quad = new Path64(4);
        quad.add(tmp.get(g).get(h));
        quad.add(tmp.get(i).get(h));
        quad.add(tmp.get(i).get(j));
        quad.add(tmp.get(g).get(j));
        if (!Clipper.isPositive(quad)) {
          result.add(Clipper.reversePath(quad));
        } else {
          result.add(quad);
        }
        h = j;
      }
      g = i;
    }
    return result;
  }

  public static Paths64 sum(Path64 pattern, Path64 path, boolean isClosed) {
    return Clipper.union(minkowskiInternal(pattern, path, true, isClosed), FillRule.NonZero);
  }

  public static PathsD sum(PathD pattern, PathD path, boolean isClosed, int decimalPlaces) {
    double scale = Math.pow(10, decimalPlaces);
    Paths64 tmp = Clipper.union(
        minkowskiInternal(
            Clipper.scalePath64(pattern, scale),
            Clipper.scalePath64(path, scale),
            true, isClosed),
        FillRule.NonZero);
    return Clipper.scalePathsD(tmp, 1.0 / scale);
  }

  public static PathsD sum(PathD pattern, PathD path, boolean isClosed) {
    return sum(pattern, path, isClosed, 2);
  }

  public static Paths64 diff(Path64 pattern, Path64 path, boolean isClosed) {
    return Clipper.union(minkowskiInternal(pattern, path, false, isClosed), FillRule.NonZero);
  }

  public static PathsD diff(PathD pattern, PathD path, boolean isClosed, int decimalPlaces) {
    double scale = Math.pow(10, decimalPlaces);
    Paths64 tmp = Clipper.union(
        minkowskiInternal(
            Clipper.scalePath64(pattern, scale),
            Clipper.scalePath64(path, scale),
            false, isClosed),
        FillRule.NonZero);
    return Clipper.scalePathsD(tmp, 1.0 / scale);
  }

  public static PathsD diff(PathD pattern, PathD path, boolean isClosed) {
    return diff(pattern, path, isClosed, 2);
  }

}
