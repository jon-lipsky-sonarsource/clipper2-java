/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

/**
 * PolyTreeD — root of a floating-point coordinate polygon tree.
 * Extends PolyPathD and acts as the container/root node.
 */
public class PolyTreeD extends PolyPathD {
  public PolyTreeD() {
    super();
  }

  /**
   * Returns the scale factor used by ClipperD when building this tree.
   * The value is set by ClipperD.execute().
   */
  @Override
  public double getScale() {
    return super.getScale();
  }
}
