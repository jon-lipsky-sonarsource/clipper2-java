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
 * Pre-clipping data structure. Used to separate polygons into ascending and
 * descending 'bounds' (or sides) that start at local minima and ascend to a
 * local maxima, before descending again.
 */
class Vertex {
  Point64 pt;
  Vertex next;
  Vertex prev;
  int flags; // bitfield using VertexFlags constants

  Vertex(Point64 pt, int flags, Vertex prev) {
    this.pt = pt;
    this.flags = flags;
    this.next = null;
    this.prev = prev;
  }
}
