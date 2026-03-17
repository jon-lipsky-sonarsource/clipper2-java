/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.Path64;
import clipper2.core.Rect64;
import java.util.ArrayList;
import java.util.List;

/**
 * Path data structure for clipping solutions.
 */
class OutRec {
  int idx;
  int outPtCount;
  OutRec owner;
  Active frontEdge;
  Active backEdge;
  OutPt pts;
  PolyPathBase polypath;
  Rect64 bounds = new Rect64();
  Path64 path = new Path64();
  boolean isOpen;
  List<Integer> splits;
  OutRec recursiveSplit;

  OutRec() {}
}
