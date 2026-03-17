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
 * Vertex data structure for clipping solutions.
 */
class OutPt {
  Point64 pt;
  OutPt next;
  OutPt prev;
  OutRec outrec;
  HorzSegment horz;

  OutPt(Point64 pt, OutRec outrec) {
    this.pt = new Point64(pt);
    this.outrec = outrec;
    this.next = this;
    this.prev = this;
    this.horz = null;
  }
}
