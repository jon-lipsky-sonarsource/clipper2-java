/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.Point64;

/**
 * Represents two intersecting edges. Intersections must be sorted so they are
 * processed from the largest Y coordinates to the smallest while keeping edges
 * adjacent.
 */
class IntersectNode {
  Point64 pt;
  Active edge1;
  Active edge2;

  IntersectNode(Point64 pt, Active edge1, Active edge2) {
    this.pt = pt;
    this.edge1 = edge1;
    this.edge2 = edge2;
  }
}
