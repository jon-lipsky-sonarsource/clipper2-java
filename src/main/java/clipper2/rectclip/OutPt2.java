/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  11 October 2025                                                 *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  FAST rectangular clipping                                       *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.rectclip;

import clipper2.core.Point64;
import java.util.ArrayList;
import java.util.List;

public class OutPt2 {

  public OutPt2 next;
  public OutPt2 prev;
  public Point64 pt;
  public int ownerIdx;
  public List<OutPt2> edge;

  public OutPt2(Point64 pt) {
    this.pt = pt;
  }

}
