/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.Clipper;
import clipper2.core.Path64;
import clipper2.core.PathD;

/**
 * PolyPathD — one node in a floating-point coordinate polygon tree.
 */
public class PolyPathD extends PolyPathBase {

  private double _scale = 1.0;
  private PathD _polygon; // null for tree root

  public PolyPathD() {
    super();
  }

  public PolyPathD(PolyPathBase parent) {
    super(parent);
  }

  double getScale() { return _scale; }
  void setScale(double scale) { _scale = scale; }

  public PathD getPolygon() { return _polygon; }

  /** Adds a child given an integer path (will be scaled to doubles). */
  @Override
  public PolyPathBase addChild(Path64 p) {
    PolyPathD newChild = new PolyPathD(this);
    newChild._scale = _scale;
    newChild._polygon = Clipper.scalePathD(p, 1.0 / _scale);
    _childs.add(newChild);
    return newChild;
  }

  /** Adds a child given an already-scaled double path. */
  public PolyPathBase addChild(PathD p) {
    PolyPathD newChild = new PolyPathD(this);
    newChild._scale = _scale;
    newChild._polygon = p;
    _childs.add(newChild);
    return newChild;
  }

  public PolyPathD getChild(int index) {
    if (index < 0 || index >= _childs.size())
      throw new IndexOutOfBoundsException("Index: " + index);
    return (PolyPathD) _childs.get(index);
  }

  public double area() {
    double result = _polygon == null ? 0.0 : Clipper.area(_polygon);
    for (PolyPathBase child : _childs)
      result += ((PolyPathD) child).area();
    return result;
  }
}
