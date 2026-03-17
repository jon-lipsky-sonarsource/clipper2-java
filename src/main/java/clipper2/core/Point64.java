/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

public final class Point64 {

  public long x;
  public long y;

  public Point64() {
    x = 0;
    y = 0;
  }

  public Point64(Point64 pt) {
    x = pt.x;
    y = pt.y;
  }

  public Point64(Point64 pt, double scale) {
    x = Math.round(pt.x * scale);
    y = Math.round(pt.y * scale);
  }

  public Point64(long x, long y) {
    this.x = x;
    this.y = y;
  }

  public Point64(double x, double y) {
    this.x = InternalClipper.roundAwayFromZero(x);
    this.y = InternalClipper.roundAwayFromZero(y);
  }

  public Point64(PointD pt) {
    x = InternalClipper.roundAwayFromZero(pt.x);
    y = InternalClipper.roundAwayFromZero(pt.y);
  }

  public Point64(PointD pt, double scale) {
    x = InternalClipper.roundAwayFromZero(pt.x * scale);
    y = InternalClipper.roundAwayFromZero(pt.y * scale);
  }

  public Point64 add(Point64 rhs) {
    return new Point64(x + rhs.x, y + rhs.y);
  }

  public Point64 subtract(Point64 rhs) {
    return new Point64(x - rhs.x, y - rhs.y);
  }

  public Point64 negate() {
    return new Point64(-x, -y);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Point64 other) {
      return x == other.x && y == other.y;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(x) * 31 + Long.hashCode(y);
  }

  @Override
  public String toString() {
    // trailing space matches C# format
    return x + "," + y + " ";
  }

}
