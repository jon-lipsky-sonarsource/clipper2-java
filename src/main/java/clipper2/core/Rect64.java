/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

public final class Rect64 {

  public long left;
  public long top;
  public long right;
  public long bottom;

  public Rect64() {
    left = 0;
    top = 0;
    right = 0;
    bottom = 0;
  }

  public Rect64(long left, long top, long right, long bottom) {
    this.left = left;
    this.top = top;
    this.right = right;
    this.bottom = bottom;
  }

  /** @param isValid if false, creates an "invalid" sentinel rect with inverted extremes */
  public Rect64(boolean isValid) {
    if (isValid) {
      left = 0;
      top = 0;
      right = 0;
      bottom = 0;
    } else {
      left = Long.MAX_VALUE;
      top = Long.MAX_VALUE;
      right = Long.MIN_VALUE;
      bottom = Long.MIN_VALUE;
    }
  }

  public Rect64(Rect64 rec) {
    left = rec.left;
    top = rec.top;
    right = rec.right;
    bottom = rec.bottom;
  }

  public long getWidth() {
    return right - left;
  }

  public void setWidth(long value) {
    right = left + value;
  }

  public long getHeight() {
    return bottom - top;
  }

  public void setHeight(long value) {
    bottom = top + value;
  }

  public boolean isEmpty() {
    return bottom <= top || right <= left;
  }

  public boolean isValid() {
    return left < Long.MAX_VALUE;
  }

  public Point64 midPoint() {
    return new Point64((left + right) / 2, (top + bottom) / 2);
  }

  public boolean contains(Point64 pt) {
    return pt.x > left && pt.x < right && pt.y > top && pt.y < bottom;
  }

  public boolean contains(Rect64 rec) {
    return rec.left >= left && rec.right <= right
        && rec.top >= top && rec.bottom <= bottom;
  }

  public boolean intersects(Rect64 rec) {
    return (Math.max(left, rec.left) <= Math.min(right, rec.right))
        && (Math.max(top, rec.top) <= Math.min(bottom, rec.bottom));
  }

  public Path64 asPath() {
    Path64 result = new Path64(4);
    result.add(new Point64(left, top));
    result.add(new Point64(right, top));
    result.add(new Point64(right, bottom));
    result.add(new Point64(left, bottom));
    return result;
  }

}
