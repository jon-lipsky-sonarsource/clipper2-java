/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

class HorzJoin {
  OutPt op1;
  OutPt op2;

  HorzJoin(OutPt ltor, OutPt rtol) {
    this.op1 = ltor;
    this.op2 = rtol;
  }
}
