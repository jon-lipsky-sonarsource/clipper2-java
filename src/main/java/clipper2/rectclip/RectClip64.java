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
import clipper2.engine.PointInPolygonResult;

import java.util.ArrayList;
import java.util.List;

public class RectClip64 {

  protected final Rect64 rect_;
  protected final Point64 mp_;
  protected final Path64 rectPath_;
  protected Rect64 pathBounds_;
  protected ArrayList<OutPt2> results_;
  @SuppressWarnings("unchecked")
  protected ArrayList<OutPt2>[] edges_;

  public RectClip64(Rect64 rect) {
    rect_ = rect;
    mp_ = rect.midPoint();
    rectPath_ = rect_.asPath();
    results_ = new ArrayList<>();
    edges_ = new ArrayList[8];
    for (int i = 0; i < 8; i++) {
      edges_[i] = new ArrayList<>();
    }
  }

  OutPt2 add(Point64 pt, boolean startingNewPath) {
    // this method is only called by executeInternal.
    // Later splitting and rejoining won't create additional op's,
    // though they will change the (non-storage) results count.
    int currIdx = results_.size();
    OutPt2 result;
    if (currIdx == 0 || startingNewPath) {
      result = new OutPt2(pt);
      results_.add(result);
      result.ownerIdx = currIdx;
      result.prev = result;
      result.next = result;
    } else {
      currIdx--;
      OutPt2 prevOp = results_.get(currIdx);
      if (prevOp.pt.equals(pt)) return prevOp;
      result = new OutPt2(pt);
      result.ownerIdx = currIdx;
      result.next = prevOp.next;
      prevOp.next.prev = result;
      prevOp.next = result;
      result.prev = prevOp;
      results_.set(currIdx, result);
    }
    return result;
  }

  OutPt2 add(Point64 pt) {
    return add(pt, false);
  }

  private static boolean path1ContainsPath2(Path64 path1, Path64 path2) {
    // nb: occasionally, due to rounding, path1 may
    // appear (momentarily) inside or outside path2.
    int ioCount = 0;
    for (Point64 pt : path2) {
      PointInPolygonResult pip = InternalClipper.pointInPolygon(pt, path1);
      switch (pip) {
        case IsInside:
          ioCount--;
          break;
        case IsOutside:
          ioCount++;
          break;
        default:
          break;
      }
      if (Math.abs(ioCount) > 1) break;
    }
    return ioCount <= 0;
  }

  private static boolean isClockwise(Location prev, Location curr,
      Point64 prevPt, Point64 currPt, Point64 rectMidPoint) {
    if (areOpposites(prev, curr)) {
      return InternalClipper.crossProductSign(prevPt, rectMidPoint, currPt) < 0;
    }
    return headingClockwise(prev, curr);
  }

  private static boolean areOpposites(Location prev, Location curr) {
    return Math.abs(prev.ordinal() - curr.ordinal()) == 2;
  }

  private static boolean headingClockwise(Location prev, Location curr) {
    return (prev.ordinal() + 1) % 4 == curr.ordinal();
  }

  private static Location getAdjacentLocation(Location loc, boolean isClockwise) {
    int delta = isClockwise ? 1 : 3;
    return Location.values()[(loc.ordinal() + delta) % 4];
  }

  private static OutPt2 unlinkOp(OutPt2 op) {
    if (op.next == op) return null;
    op.prev.next = op.next;
    op.next.prev = op.prev;
    return op.next;
  }

  private static OutPt2 unlinkOpBack(OutPt2 op) {
    if (op.next == op) return null;
    op.prev.next = op.next;
    op.next.prev = op.prev;
    return op.prev;
  }

  private static int getEdgesForPt(Point64 pt, Rect64 rec) {
    int result = 0;
    if (pt.x == rec.left) result = 1;
    else if (pt.x == rec.right) result = 4;
    if (pt.y == rec.top) result += 2;
    else if (pt.y == rec.bottom) result += 8;
    return result;
  }

  private static boolean isHeadingClockwise(Point64 pt1, Point64 pt2, int edgeIdx) {
    switch (edgeIdx) {
      case 0: return pt2.y < pt1.y;
      case 1: return pt2.x > pt1.x;
      case 2: return pt2.y > pt1.y;
      default: return pt2.x < pt1.x;
    }
  }

  private static boolean hasHorzOverlap(Point64 left1, Point64 right1,
      Point64 left2, Point64 right2) {
    return (left1.x < right2.x) && (right1.x > left2.x);
  }

  private static boolean hasVertOverlap(Point64 top1, Point64 bottom1,
      Point64 top2, Point64 bottom2) {
    return (top1.y < bottom2.y) && (bottom1.y > top2.y);
  }

  private static void addToEdge(List<OutPt2> edge, OutPt2 op) {
    if (op.edge != null) return;
    op.edge = edge;
    edge.add(op);
  }

  private static void uncoupleEdge(OutPt2 op) {
    if (op.edge == null) return;
    for (int i = 0; i < op.edge.size(); i++) {
      OutPt2 op2 = op.edge.get(i);
      if (op2 != op) continue;
      op.edge.set(i, null);
      break;
    }
    op.edge = null;
  }

  private static void setNewOwner(OutPt2 op, int newIdx) {
    op.ownerIdx = newIdx;
    OutPt2 op2 = op.next;
    while (op2 != op) {
      op2.ownerIdx = newIdx;
      op2 = op2.next;
    }
  }

  private void addCorner(Location prev, Location curr) {
    add(headingClockwise(prev, curr) ? rectPath_.get(prev.ordinal()) : rectPath_.get(curr.ordinal()));
  }

  // In C# this takes "ref Location loc" — Java returns the new Location value.
  // Callers must assign: loc = addCorner(loc, isClockwise);
  private Location addCornerAndAdvance(Location loc, boolean isClockwise) {
    if (isClockwise) {
      add(rectPath_.get(loc.ordinal()));
      return getAdjacentLocation(loc, true);
    } else {
      loc = getAdjacentLocation(loc, false);
      add(rectPath_.get(loc.ordinal()));
      return loc;
    }
  }

  protected static boolean getLocation(Rect64 rec, Point64 pt, Location[] loc) {
    if (pt.x == rec.left && pt.y >= rec.top && pt.y <= rec.bottom) {
      loc[0] = Location.left;
      return false; // pt on rec
    }
    if (pt.x == rec.right && pt.y >= rec.top && pt.y <= rec.bottom) {
      loc[0] = Location.right;
      return false; // pt on rec
    }
    if (pt.y == rec.top && pt.x >= rec.left && pt.x <= rec.right) {
      loc[0] = Location.top;
      return false; // pt on rec
    }
    if (pt.y == rec.bottom && pt.x >= rec.left && pt.x <= rec.right) {
      loc[0] = Location.bottom;
      return false; // pt on rec
    }
    if (pt.x < rec.left) loc[0] = Location.left;
    else if (pt.x > rec.right) loc[0] = Location.right;
    else if (pt.y < rec.top) loc[0] = Location.top;
    else if (pt.y > rec.bottom) loc[0] = Location.bottom;
    else loc[0] = Location.inside;
    return true;
  }

  private static boolean isHorizontal(Point64 pt1, Point64 pt2) {
    return pt1.y == pt2.y;
  }

  private static boolean getSegmentIntersection(Point64 p1,
      Point64 p2, Point64 p3, Point64 p4, Point64[] ip) {
    int res1 = InternalClipper.crossProductSign(p1, p3, p4);
    int res2 = InternalClipper.crossProductSign(p2, p3, p4);
    if (res1 == 0) {
      ip[0] = new Point64(p1);
      if (res2 == 0) return false; // segments are collinear
      if (p1.equals(p3) || p1.equals(p4)) return true;
      if (isHorizontal(p3, p4)) return ((p1.x > p3.x) == (p1.x < p4.x));
      return ((p1.y > p3.y) == (p1.y < p4.y));
    }
    if (res2 == 0) {
      ip[0] = new Point64(p2);
      if (p2.equals(p3) || p2.equals(p4)) return true;
      if (isHorizontal(p3, p4)) return ((p2.x > p3.x) == (p2.x < p4.x));
      return ((p2.y > p3.y) == (p2.y < p4.y));
    }

    if ((res1 > 0) == (res2 > 0)) {
      ip[0] = new Point64(0, 0);
      return false;
    }

    int res3 = InternalClipper.crossProductSign(p3, p1, p2);
    int res4 = InternalClipper.crossProductSign(p4, p1, p2);
    if (res3 == 0) {
      ip[0] = new Point64(p3);
      if (p3.equals(p1) || p3.equals(p2)) return true;
      if (isHorizontal(p1, p2)) return ((p3.x > p1.x) == (p3.x < p2.x));
      return ((p3.y > p1.y) == (p3.y < p2.y));
    }
    if (res4 == 0) {
      ip[0] = new Point64(p4);
      if (p4.equals(p1) || p4.equals(p2)) return true;
      if (isHorizontal(p1, p2)) return ((p4.x > p1.x) == (p4.x < p2.x));
      return ((p4.y > p1.y) == (p4.y < p2.y));
    }
    if ((res3 > 0) == (res4 > 0)) {
      ip[0] = new Point64(0, 0);
      return false;
    }

    // segments must intersect to get here
    Point64 result = InternalClipper.getLineIntersectPt(p1, p2, p3, p4);
    if (result == null) {
      ip[0] = new Point64(0, 0);
      return false;
    }
    ip[0] = result;
    return true;
  }

  protected static boolean getIntersection(Path64 rectPath, Point64 p, Point64 p2,
      Location[] loc, Point64[] ip) {
    // gets the pt of intersection between rectPath and segment(p, p2) that's closest to 'p'
    // when result == false, loc will remain unchanged
    ip[0] = new Point64();
    switch (loc[0]) {
      case left:
        if (getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(3), ip))
          return true;
        if (p.y < rectPath.get(0).y && getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(1), ip)) {
          loc[0] = Location.top;
          return true;
        }
        if (!getSegmentIntersection(p, p2, rectPath.get(2), rectPath.get(3), ip)) return false;
        loc[0] = Location.bottom;
        return true;

      case right:
        if (getSegmentIntersection(p, p2, rectPath.get(1), rectPath.get(2), ip))
          return true;
        if (p.y < rectPath.get(0).y && getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(1), ip)) {
          loc[0] = Location.top;
          return true;
        }
        if (!getSegmentIntersection(p, p2, rectPath.get(2), rectPath.get(3), ip)) return false;
        loc[0] = Location.bottom;
        return true;

      case top:
        if (getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(1), ip))
          return true;
        if (p.x < rectPath.get(0).x && getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(3), ip)) {
          loc[0] = Location.left;
          return true;
        }
        if (p.x <= rectPath.get(1).x || !getSegmentIntersection(p, p2, rectPath.get(1), rectPath.get(2), ip))
          return false;
        loc[0] = Location.right;
        return true;

      case bottom:
        if (getSegmentIntersection(p, p2, rectPath.get(2), rectPath.get(3), ip))
          return true;
        if (p.x < rectPath.get(3).x && getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(3), ip)) {
          loc[0] = Location.left;
          return true;
        }
        if (p.x <= rectPath.get(2).x || !getSegmentIntersection(p, p2, rectPath.get(1), rectPath.get(2), ip))
          return false;
        loc[0] = Location.right;
        return true;

      default: // inside
        if (getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(3), ip)) {
          loc[0] = Location.left;
          return true;
        }
        if (getSegmentIntersection(p, p2, rectPath.get(0), rectPath.get(1), ip)) {
          loc[0] = Location.top;
          return true;
        }
        if (getSegmentIntersection(p, p2, rectPath.get(1), rectPath.get(2), ip)) {
          loc[0] = Location.right;
          return true;
        }
        if (!getSegmentIntersection(p, p2, rectPath.get(2), rectPath.get(3), ip)) return false;
        loc[0] = Location.bottom;
        return true;
    }
  }

  protected void getNextLocation(Path64 path,
      Location[] loc, int[] i, int highI) {
    switch (loc[0]) {
      case left: {
        while (i[0] <= highI && path.get(i[0]).x <= rect_.left) i[0]++;
        if (i[0] > highI) break;
        if (path.get(i[0]).x >= rect_.right) loc[0] = Location.right;
        else if (path.get(i[0]).y <= rect_.top) loc[0] = Location.top;
        else if (path.get(i[0]).y >= rect_.bottom) loc[0] = Location.bottom;
        else loc[0] = Location.inside;
        break;
      }
      case top: {
        while (i[0] <= highI && path.get(i[0]).y <= rect_.top) i[0]++;
        if (i[0] > highI) break;
        if (path.get(i[0]).y >= rect_.bottom) loc[0] = Location.bottom;
        else if (path.get(i[0]).x <= rect_.left) loc[0] = Location.left;
        else if (path.get(i[0]).x >= rect_.right) loc[0] = Location.right;
        else loc[0] = Location.inside;
        break;
      }
      case right: {
        while (i[0] <= highI && path.get(i[0]).x >= rect_.right) i[0]++;
        if (i[0] > highI) break;
        if (path.get(i[0]).x <= rect_.left) loc[0] = Location.left;
        else if (path.get(i[0]).y <= rect_.top) loc[0] = Location.top;
        else if (path.get(i[0]).y >= rect_.bottom) loc[0] = Location.bottom;
        else loc[0] = Location.inside;
        break;
      }
      case bottom: {
        while (i[0] <= highI && path.get(i[0]).y >= rect_.bottom) i[0]++;
        if (i[0] > highI) break;
        if (path.get(i[0]).y <= rect_.top) loc[0] = Location.top;
        else if (path.get(i[0]).x <= rect_.left) loc[0] = Location.left;
        else if (path.get(i[0]).x >= rect_.right) loc[0] = Location.right;
        else loc[0] = Location.inside;
        break;
      }
      case inside: {
        while (i[0] <= highI) {
          if (path.get(i[0]).x < rect_.left) loc[0] = Location.left;
          else if (path.get(i[0]).x > rect_.right) loc[0] = Location.right;
          else if (path.get(i[0]).y > rect_.bottom) loc[0] = Location.bottom;
          else if (path.get(i[0]).y < rect_.top) loc[0] = Location.top;
          else {
            add(path.get(i[0]));
            i[0]++;
            continue;
          }
          break;
        }
        break;
      }
    }
  }

  private static boolean startLocsAreClockwise(List<Location> startLocs) {
    int result = 0;
    for (int i = 1; i < startLocs.size(); i++) {
      int d = startLocs.get(i).ordinal() - startLocs.get(i - 1).ordinal();
      switch (d) {
        case -1: result -= 1; break;
        case 1:  result += 1; break;
        case -3: result += 1; break;
        case 3:  result -= 1; break;
        default: break;
      }
    }
    return result > 0;
  }

  private void executeInternal(Path64 path) {
    if (path.size() < 3 || rect_.isEmpty()) return;
    List<Location> startLocs = new ArrayList<>();

    Location firstCross = Location.inside;
    Location crossingLoc = firstCross;
    Location prev = firstCross;

    int highI = path.size() - 1;
    Location[] locArr = new Location[1];
    Location[] prevArr = new Location[1];

    if (!getLocation(rect_, path.get(highI), locArr)) {
      int i = highI - 1;
      while (i >= 0 && !getLocation(rect_, path.get(i), prevArr)) i--;
      if (i < 0) {
        for (Point64 pt : path) add(pt);
        return;
      }
      if (prevArr[0] == Location.inside) locArr[0] = Location.inside;
    }
    Location startingLoc = locArr[0];
    Location loc = locArr[0];

    ///////////////////////////////////////////////////
    int[] iArr = new int[]{0};
    while (iArr[0] <= highI) {
      prev = loc;
      Location prevCrossLoc = crossingLoc;
      Location[] locRef = new Location[]{loc};
      getNextLocation(path, locRef, iArr, highI);
      loc = locRef[0];
      if (iArr[0] > highI) break;

      Point64 prevPt = (iArr[0] == 0) ? path.get(highI) : path.get(iArr[0] - 1);
      crossingLoc = loc;
      Location[] crossingLocRef = new Location[]{crossingLoc};
      Point64[] ipArr = new Point64[]{new Point64()};
      if (!getIntersection(rectPath_, path.get(iArr[0]), prevPt, crossingLocRef, ipArr)) {
        crossingLoc = crossingLocRef[0];
        // ie remaining outside
        if (prevCrossLoc == Location.inside) {
          boolean isClockw = isClockwise(prev, loc, prevPt, path.get(iArr[0]), mp_);
          do {
            startLocs.add(prev);
            prev = getAdjacentLocation(prev, isClockw);
          } while (prev != loc);
          crossingLoc = prevCrossLoc; // still not crossed
        } else if (prev != Location.inside && prev != loc) {
          boolean isClockw = isClockwise(prev, loc, prevPt, path.get(iArr[0]), mp_);
          do {
            prev = addCornerAndAdvance(prev, isClockw);
          } while (prev != loc);
        }
        iArr[0]++;
        continue;
      }
      crossingLoc = crossingLocRef[0];
      Point64 ip = ipArr[0];

      ////////////////////////////////////////////////////
      // we must be crossing the rect boundary to get here
      ////////////////////////////////////////////////////

      if (loc == Location.inside) { // path must be entering rect
        if (firstCross == Location.inside) {
          firstCross = crossingLoc;
          startLocs.add(prev);
        } else if (prev != crossingLoc) {
          boolean isClockw = isClockwise(prev, crossingLoc, prevPt, path.get(iArr[0]), mp_);
          do {
            prev = addCornerAndAdvance(prev, isClockw);
          } while (prev != crossingLoc);
        }
      } else if (prev != Location.inside) {
        // passing right through rect. 'ip' here will be the second
        // intersect pt but we'll also need the first intersect pt (ip2)
        loc = prev;
        Location[] locRef2 = new Location[]{loc};
        Point64[] ip2Arr = new Point64[]{new Point64()};
        getIntersection(rectPath_, prevPt, path.get(iArr[0]), locRef2, ip2Arr);
        loc = locRef2[0];
        Point64 ip2 = ip2Arr[0];
        if (prevCrossLoc != Location.inside && prevCrossLoc != loc) { // #597
          addCorner(prevCrossLoc, loc);
        }

        if (firstCross == Location.inside) {
          firstCross = loc;
          startLocs.add(prev);
        }

        loc = crossingLoc;
        add(ip2);
        if (ip.equals(ip2)) {
          // it's very likely that path[i] is on rect
          Location[] locTmp = new Location[1];
          getLocation(rect_, path.get(iArr[0]), locTmp);
          loc = locTmp[0];
          addCorner(crossingLoc, loc);
          crossingLoc = loc;
          iArr[0]++;
          continue;
        }
      } else { // path must be exiting rect
        loc = crossingLoc;
        if (firstCross == Location.inside)
          firstCross = crossingLoc;
      }

      add(ip);
      iArr[0]++;
    } // while iArr[0] <= highI
    ///////////////////////////////////////////////////

    if (firstCross == Location.inside) {
      // path never intersects
      if (startingLoc == Location.inside) return;
      if (!pathBounds_.contains(rect_) ||
          !path1ContainsPath2(path, rectPath_)) return;
      boolean startLocsClockwise = startLocsAreClockwise(startLocs);
      for (int j = 0; j < 4; j++) {
        int k = startLocsClockwise ? j : 3 - j; // ie reverse result path
        add(rectPath_.get(k));
        addToEdge(edges_[k * 2], results_.get(0));
      }
    } else if (loc != Location.inside &&
        (loc != firstCross || startLocs.size() > 2)) {
      if (startLocs.size() > 0) {
        prev = loc;
        for (Location loc2 : startLocs) {
          if (prev == loc2) continue;
          prev = addCornerAndAdvance(prev, headingClockwise(prev, loc2));
        }
        loc = prev;
      }
      if (loc != firstCross) {
        loc = addCornerAndAdvance(loc, headingClockwise(loc, firstCross));
      }
    }
  }

  public Paths64 execute(Paths64 paths) {
    Paths64 result = new Paths64();
    if (rect_.isEmpty()) return result;
    for (Path64 path : paths) {
      if (path.size() < 3) continue;
      pathBounds_ = InternalClipper.getBounds(path);
      if (!rect_.intersects(pathBounds_))
        continue; // the path must be completely outside rect_
      if (rect_.contains(pathBounds_)) {
        // the path must be completely inside rect_
        result.add(path);
        continue;
      }
      executeInternal(path);
      checkEdges();
      for (int i = 0; i < 4; ++i)
        tidyEdgePair(i, edges_[i * 2], edges_[i * 2 + 1]);

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

  private void checkEdges() {
    for (int i = 0; i < results_.size(); i++) {
      OutPt2 op = results_.get(i);
      OutPt2 op2 = op;
      if (op == null) continue;
      do {
        if (InternalClipper.isCollinear(op2.prev.pt, op2.pt, op2.next.pt)) {
          if (op2 == op) {
            op2 = unlinkOpBack(op2);
            if (op2 == null) break;
            op = op2.prev;
          } else {
            op2 = unlinkOpBack(op2);
            if (op2 == null) break;
          }
        } else {
          op2 = op2.next;
        }
      } while (op2 != op);

      if (op2 == null) {
        results_.set(i, null);
        continue;
      }
      results_.set(i, op2); // safety first

      int edgeSet1 = getEdgesForPt(op.prev.pt, rect_);
      op2 = op;
      do {
        int edgeSet2 = getEdgesForPt(op2.pt, rect_);
        if (edgeSet2 != 0 && op2.edge == null) {
          int combinedSet = (edgeSet1 & edgeSet2);
          for (int j = 0; j < 4; ++j) {
            if ((combinedSet & (1 << j)) == 0) continue;
            if (isHeadingClockwise(op2.prev.pt, op2.pt, j))
              addToEdge(edges_[j * 2], op2);
            else
              addToEdge(edges_[j * 2 + 1], op2);
          }
        }
        edgeSet1 = edgeSet2;
        op2 = op2.next;
      } while (op2 != op);
    }
  }

  private void tidyEdgePair(int idx, ArrayList<OutPt2> cw, ArrayList<OutPt2> ccw) {
    if (ccw.size() == 0) return;
    boolean isHorz = ((idx == 1) || (idx == 3));
    boolean cwIsTowardLarger = ((idx == 1) || (idx == 2));
    int i = 0, j = 0;

    while (i < cw.size()) {
      OutPt2 p1 = cw.get(i);
      if (p1 == null || p1.next == p1.prev) {
        cw.set(i++, null);
        j = 0;
        continue;
      }

      int jLim = ccw.size();
      while (j < jLim &&
          (ccw.get(j) == null || ccw.get(j).next == ccw.get(j).prev)) ++j;

      if (j == jLim) {
        ++i;
        j = 0;
        continue;
      }

      OutPt2 p2;
      OutPt2 p1a;
      OutPt2 p2a;
      if (cwIsTowardLarger) {
        // p1 >>>> p1a;
        // p2 <<<< p2a;
        p1 = cw.get(i).prev;
        p1a = cw.get(i);
        p2 = ccw.get(j);
        p2a = ccw.get(j).prev;
      } else {
        // p1 <<<< p1a;
        // p2 >>>> p2a;
        p1 = cw.get(i);
        p1a = cw.get(i).prev;
        p2 = ccw.get(j).prev;
        p2a = ccw.get(j);
      }

      if ((isHorz && !hasHorzOverlap(p1.pt, p1a.pt, p2.pt, p2a.pt)) ||
          (!isHorz && !hasVertOverlap(p1.pt, p1a.pt, p2.pt, p2a.pt))) {
        ++j;
        continue;
      }

      // to get here we're either splitting or rejoining
      boolean isRejoining = cw.get(i).ownerIdx != ccw.get(j).ownerIdx;

      if (isRejoining) {
        results_.set(p2.ownerIdx, null);
        setNewOwner(p2, p1.ownerIdx);
      }

      // do the split or re-join
      if (cwIsTowardLarger) {
        // p1 >> | >> p1a;
        // p2 << | << p2a;
        p1.next = p2;
        p2.prev = p1;
        p1a.prev = p2a;
        p2a.next = p1a;
      } else {
        // p1 << | << p1a;
        // p2 >> | >> p2a;
        p1.prev = p2;
        p2.next = p1;
        p1a.next = p2a;
        p2a.prev = p1a;
      }

      if (!isRejoining) {
        int new_idx = results_.size();
        results_.add(p1a);
        setNewOwner(p1a, new_idx);
      }

      OutPt2 op;
      OutPt2 op2;
      if (cwIsTowardLarger) {
        op = p2;
        op2 = p1a;
      } else {
        op = p1;
        op2 = p2a;
      }
      results_.set(op.ownerIdx, op);
      results_.set(op2.ownerIdx, op2);

      // and now lots of work to get ready for the next loop

      boolean opIsLarger, op2IsLarger;
      if (isHorz) { // X
        opIsLarger = op.pt.x > op.prev.pt.x;
        op2IsLarger = op2.pt.x > op2.prev.pt.x;
      } else { // Y
        opIsLarger = op.pt.y > op.prev.pt.y;
        op2IsLarger = op2.pt.y > op2.prev.pt.y;
      }

      if ((op.next == op.prev) || (op.pt.equals(op.prev.pt))) {
        if (op2IsLarger == cwIsTowardLarger) {
          cw.set(i, op2);
          ccw.set(j++, null);
        } else {
          ccw.set(j, op2);
          cw.set(i++, null);
        }
      } else if ((op2.next == op2.prev) || (op2.pt.equals(op2.prev.pt))) {
        if (opIsLarger == cwIsTowardLarger) {
          cw.set(i, op);
          ccw.set(j++, null);
        } else {
          ccw.set(j, op);
          cw.set(i++, null);
        }
      } else if (opIsLarger == op2IsLarger) {
        if (opIsLarger == cwIsTowardLarger) {
          cw.set(i, op);
          uncoupleEdge(op2);
          addToEdge(cw, op2);
          ccw.set(j++, null);
        } else {
          cw.set(i++, null);
          ccw.set(j, op2);
          uncoupleEdge(op);
          addToEdge(ccw, op);
          j = 0;
        }
      } else {
        if (opIsLarger == cwIsTowardLarger)
          cw.set(i, op);
        else
          ccw.set(j, op);
        if (op2IsLarger == cwIsTowardLarger)
          cw.set(i, op2);
        else
          ccw.set(j, op2);
      }
    }
  }

  private static Path64 getPath(OutPt2 op) {
    Path64 result = new Path64();
    if (op == null || op.prev == op.next) return result;
    OutPt2 op2 = op.next;
    while (op2 != null && op2 != op) {
      if (InternalClipper.isCollinear(op2.prev.pt, op2.pt, op2.next.pt)) {
        op = op2.prev;
        op2 = unlinkOp(op2);
      } else {
        op2 = op2.next;
      }
    }
    if (op2 == null) return new Path64();

    result.add(op.pt);
    op2 = op.next;
    while (op2 != op) {
      result.add(op2.pt);
      op2 = op2.next;
    }
    return result;
  }

}
