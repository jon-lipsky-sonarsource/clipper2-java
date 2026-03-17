/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  11 October 2025                                                 *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  FAST rectangular clipping                                       *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.rectclip;

import clipper2.core.InternalClipper;
import clipper2.core.Path64;
import clipper2.core.Paths64;
import clipper2.core.Point64;
import clipper2.core.Rect64;

public class RectClipLines64 extends RectClip64 {

  public RectClipLines64(Rect64 rect) {
    super(rect);
  }

  @Override
  public Paths64 execute(Paths64 paths) {
    Paths64 result = new Paths64();
    if (rect_.isEmpty()) return result;
    for (Path64 path : paths) {
      if (path.size() < 2) continue;
      pathBounds_ = InternalClipper.getBounds(path);
      if (!rect_.intersects(pathBounds_))
        continue; // the path must be completely outside rect_
      // Apart from that, we can't be sure whether the path
      // is completely outside or completely inside or intersects
      // rect_, simply by comparing path bounds with rect_.
      executeInternal(path);

      for (OutPt2 op : results_) {
        Path64 tmp = getPath(op);
        if (tmp.size() > 0) result.add(tmp);
      }

      // clean up after every loop
      results_.clear();
      for (int i = 0; i < 8; i++)
        edges_[i].clear();
    }
    return result;
  }

  private static Path64 getPath(OutPt2 op) {
    Path64 result = new Path64();
    if (op == null || op == op.next) return result;
    op = op.next; // starting at path beginning
    result.add(op.pt);
    OutPt2 op2 = op.next;
    while (op2 != op) {
      result.add(op2.pt);
      op2 = op2.next;
    }
    return result;
  }

  private void executeInternal(Path64 path) {
    results_.clear();
    if (path.size() < 2 || rect_.isEmpty()) return;

    Location[] locArr = new Location[1];
    Location[] prevArr = new Location[1];

    Location prev = Location.inside;
    int highI = path.size() - 1;
    if (!getLocation(rect_, path.get(0), locArr)) {
      int i = 1;
      while (i <= highI && !getLocation(rect_, path.get(i), prevArr)) i++;
      if (i > highI) {
        for (Point64 pt : path) add(pt);
        return;
      }
      if (prevArr[0] == Location.inside) locArr[0] = Location.inside;
      // reset i to 1 as per C# (i = 1)
    }
    Location loc = locArr[0];
    if (loc == Location.inside) add(path.get(0));

    ///////////////////////////////////////////////////
    int[] iArr = new int[]{1};
    while (iArr[0] <= highI) {
      prev = loc;
      Location[] locRef = new Location[]{loc};
      getNextLocation(path, locRef, iArr, highI);
      loc = locRef[0];
      if (iArr[0] > highI) break;
      Point64 prevPt = path.get(iArr[0] - 1);

      Location crossingLoc = loc;
      Location[] crossingLocRef = new Location[]{crossingLoc};
      Point64[] ipArr = new Point64[]{new Point64()};
      if (!getIntersection(rectPath_, path.get(iArr[0]), prevPt, crossingLocRef, ipArr)) {
        // ie remaining outside (& crossingLoc still == loc)
        iArr[0]++;
        continue;
      }
      crossingLoc = crossingLocRef[0];
      Point64 ip = ipArr[0];

      ////////////////////////////////////////////////////
      // we must be crossing the rect boundary to get here
      ////////////////////////////////////////////////////

      if (loc == Location.inside) { // path must be entering rect
        add(ip, true);
      } else if (prev != Location.inside) {
        // passing right through rect. 'ip' here will be the second
        // intersect pt but we'll also need the first intersect pt (ip2)
        crossingLoc = prev;
        Location[] crossingLocRef2 = new Location[]{crossingLoc};
        Point64[] ip2Arr = new Point64[]{new Point64()};
        getIntersection(rectPath_, prevPt, path.get(iArr[0]), crossingLocRef2, ip2Arr);
        Point64 ip2 = ip2Arr[0];
        add(ip2, true);
        add(ip);
      } else { // path must be exiting rect
        add(ip);
      }
      iArr[0]++;
    } // while iArr[0] <= highI
    ///////////////////////////////////////////////////
  }

}
