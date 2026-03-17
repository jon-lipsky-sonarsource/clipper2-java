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

/**
 * PolyPath64 — one node in an integer-coordinate polygon tree.
 */
public class PolyPath64 extends PolyPathBase {

  private Path64 _polygon; // null for tree root

  public PolyPath64() {
    super();
  }

  public PolyPath64(PolyPathBase parent) {
    super(parent);
  }

  public Path64 getPolygon() { return _polygon; }

  @Override
  public PolyPathBase addChild(Path64 p) {
    PolyPath64 newChild = new PolyPath64(this);
    newChild._polygon = p;
    _childs.add(newChild);
    return newChild;
  }

  public PolyPath64 getChild(int index) {
    if (index < 0 || index >= _childs.size())
      throw new IndexOutOfBoundsException("Index: " + index);
    return (PolyPath64) _childs.get(index);
  }

  public double area() {
    double result = _polygon == null ? 0.0 : Clipper.area(_polygon);
    for (PolyPathBase child : _childs)
      result += ((PolyPath64) child).area();
    return result;
  }
}
