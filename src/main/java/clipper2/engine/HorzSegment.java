/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

class HorzSegment {
  OutPt leftOp;
  OutPt rightOp;
  boolean leftToRight;

  HorzSegment(OutPt op) {
    this.leftOp = op;
    this.rightOp = null;
    this.leftToRight = true;
  }
}
