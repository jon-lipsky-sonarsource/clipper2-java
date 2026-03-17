/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

public final class RectD {

  public double left;
  public double top;
  public double right;
  public double bottom;

  public RectD() {
    left = 0;
    top = 0;
    right = 0;
    bottom = 0;
  }

  public RectD(double left, double top, double right, double bottom) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  public RectD(RectD rec) {
    left = rec.left;
    top = rec.top;
    right = rec.right;
    bottom = rec.bottom;
  }

  /** @param isValid if false, creates an "invalid" sentinel rect with inverted extremes */
  public RectD(boolean isValid) {
    if (isValid) {
      left = 0;
      top = 0;
      right = 0;
      bottom = 0;
    } else {
      left = Double.MAX_VALUE;
      top = Double.MAX_VALUE;
      right = -Double.MAX_VALUE;
      bottom = -Double.MAX_VALUE;
    }
  }

  public double getWidth() {
    return right - left;
  }

  public void setWidth(double value) {
    right = left + value;
  }

  public double getHeight() {
    return bottom - top;
  }

  public void setHeight(double value) {
    bottom = top + value;
  }

  public boolean isEmpty() {
    return bottom <= top || right <= left;
  }

  public PointD midPoint() {
    return new PointD((left + right) / 2, (top + bottom) / 2);
  }

  public boolean contains(PointD pt) {
    return pt.x > left && pt.x < right && pt.y > top && pt.y < bottom;
  }

  public boolean contains(RectD rec) {
    return rec.left >= left && rec.right <= right
        && rec.top >= top && rec.bottom <= bottom;
  }

  public boolean intersects(RectD rec) {
    return (Math.max(left, rec.left) < Math.min(right, rec.right))
        && (Math.max(top, rec.top) < Math.min(bottom, rec.bottom));
  }

  public PathD asPath() {
    PathD result = new PathD(4);
    result.add(new PointD(left, top));
    result.add(new PointD(right, top));
    result.add(new PointD(right, bottom));
    result.add(new PointD(left, bottom));
    return result;
  }

}
