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
 * Active edge entry.
 * <p>
 * UP and DOWN are premised on Y-axis positive down displays, which is the
 * orientation used in Clipper's development.
 */
class Active {
  Point64 bot = new Point64();
  Point64 top = new Point64();
  long curX;        // current (updated at every new scanline)
  double dx;
  int windDx;       // 1 or -1 depending on winding direction
  int windCount;
  int windCount2;   // winding count of the opposite polytype
  OutRec outrec;

  // AEL: 'active edge list'
  Active prevInAEL;
  Active nextInAEL;

  // SEL: 'sorted edge list'
  Active prevInSEL;
  Active nextInSEL;
  Active jump;
  Vertex vertexTop;
  LocalMinima localMin = new LocalMinima(); // the bottom of an edge 'bound'
  boolean isLeftBound;
  JoinWith joinWith = JoinWith.None;

  Active() {}
}
