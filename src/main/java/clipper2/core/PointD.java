/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

public final class PointD {

  public double x;
  public double y;

  public PointD() {
    x = 0;
    y = 0;
  }

  public PointD(PointD pt) {
    x = pt.x;
    y = pt.y;
  }

  public PointD(Point64 pt) {
    x = pt.x;
    y = pt.y;
  }

  public PointD(Point64 pt, double scale) {
    x = pt.x * scale;
    y = pt.y * scale;
  }

  public PointD(PointD pt, double scale) {
    x = pt.x * scale;
    y = pt.y * scale;
  }

  public PointD(long x, long y) {
    this.x = x;
    this.y = y;
  }

  public PointD(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public void negate() {
    x = -x;
    y = -y;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PointD other) {
      return InternalClipper.isAlmostZero(x - other.x)
          && InternalClipper.isAlmostZero(y - other.y);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(x) * 31 + Double.hashCode(y);
  }

  public String toString(int precision) {
    String fmt = "%." + precision + "f";
    return String.format(fmt + "," + fmt, x, y);
  }

  @Override
  public String toString() {
    return toString(2);
  }

}
