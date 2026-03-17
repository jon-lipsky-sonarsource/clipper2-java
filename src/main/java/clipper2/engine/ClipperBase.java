/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * Purpose   :  This is the main polygon clipping module                        *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.ClipType;
import clipper2.core.FillRule;
import clipper2.core.InternalClipper;
import clipper2.core.Path64;
import clipper2.core.PathType;
import clipper2.core.Paths64;
import clipper2.core.Point64;
import clipper2.core.Rect64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Package-private utility class containing static helpers shared between
 * ClipperBase and ReuseableDataContainer64.
 */
class ClipperEngine {

  static void addLocMin(Vertex vert, PathType polytype, boolean isOpen,
      List<LocalMinima> minimaList) {
    if ((vert.flags & VertexFlags.LocalMin) != VertexFlags.None) return;
    vert.flags |= VertexFlags.LocalMin;
    minimaList.add(new LocalMinima(vert, polytype, isOpen));
  }

  static void addPathsToVertexList(Paths64 paths, PathType polytype, boolean isOpen,
      List<LocalMinima> minimaList, List<Vertex> vertexList) {

    for (Path64 path : paths) {
      Vertex v0 = null, prev_v = null, curr_v;
      for (Point64 pt : path) {
        if (v0 == null) {
          v0 = new Vertex(pt, VertexFlags.None, null);
          vertexList.add(v0);
          prev_v = v0;
        } else if (!prev_v.pt.equals(pt)) { // skip duplicates
          curr_v = new Vertex(pt, VertexFlags.None, prev_v);
          vertexList.add(curr_v);
          prev_v.next = curr_v;
          prev_v = curr_v;
        }
      }
      if (prev_v == null || prev_v.prev == null) continue;
      if (!isOpen && prev_v.pt.equals(v0.pt)) prev_v = prev_v.prev;
      prev_v.next = v0;
      v0.prev = prev_v;
      if (!isOpen && prev_v.next == prev_v) continue;

      // Valid path — determine direction
      boolean going_up;
      if (isOpen) {
        curr_v = v0.next;
        while (curr_v != v0 && curr_v.pt.y == v0.pt.y)
          curr_v = curr_v.next;
        going_up = curr_v.pt.y <= v0.pt.y;
        if (going_up) {
          v0.flags = VertexFlags.OpenStart;
          addLocMin(v0, polytype, true, minimaList);
        } else {
          v0.flags = VertexFlags.OpenStart | VertexFlags.LocalMax;
        }
      } else {
        Vertex prev_vv = v0.prev;
        while (prev_vv != v0 && prev_vv.pt.y == v0.pt.y)
          prev_vv = prev_vv.prev;
        if (prev_vv == v0) continue; // only open paths can be completely flat
        going_up = prev_vv.pt.y > v0.pt.y;
      }

      boolean going_up0 = going_up;
      Vertex pv = v0;
      curr_v = v0.next;
      while (curr_v != v0) {
        if (curr_v.pt.y > pv.pt.y && going_up) {
          pv.flags |= VertexFlags.LocalMax;
          going_up = false;
        } else if (curr_v.pt.y < pv.pt.y && !going_up) {
          going_up = true;
          addLocMin(pv, polytype, isOpen, minimaList);
        }
        pv = curr_v;
        curr_v = curr_v.next;
      }

      if (isOpen) {
        pv.flags |= VertexFlags.OpenEnd;
        if (going_up) pv.flags |= VertexFlags.LocalMax;
        else addLocMin(pv, polytype, isOpen, minimaList);
      } else if (going_up != going_up0) {
        if (going_up0) addLocMin(pv, polytype, false, minimaList);
        else pv.flags |= VertexFlags.LocalMax;
      }
    }
  }
}

/**
 * Base class for all Clipper clipping engines.
 */
public class ClipperBase {

  private ClipType _cliptype;
  private FillRule _fillrule;
  private Active _actives;
  private Active _sel;
  private final List<Active> _freeActives = new ArrayList<>();
  private final List<LocalMinima> _minimaList = new ArrayList<>();
  private final List<IntersectNode> _intersectList = new ArrayList<>();
  private final List<Vertex> _vertexList = new ArrayList<>();
  private final List<OutRec> _outrecList = new ArrayList<>();
  private final List<Long> _scanlineList = new ArrayList<>();
  private final List<HorzSegment> _horzSegList = new ArrayList<>();
  private final List<HorzJoin> _horzJoinList = new ArrayList<>();
  private int _currentLocMin;
  private long _currentBotY;
  private boolean _isSortedMinimaList;
  private boolean _hasOpenPaths;
  boolean _using_polytree;
  boolean _succeeded;
  public boolean preserveCollinear = true;
  public boolean reverseSolution;

  // Getters/setters for JavaBean style
  public boolean isPreserveCollinear() { return preserveCollinear; }
  public void setPreserveCollinear(boolean v) { preserveCollinear = v; }
  public boolean isReverseSolution() { return reverseSolution; }
  public void setReverseSolution(boolean v) { reverseSolution = v; }

  public ClipperBase() {}

  // -----------------------------------------------------------------------
  // Static helpers
  // -----------------------------------------------------------------------

  private static boolean isOdd(int val) {
    return (val & 1) != 0;
  }

  private static boolean isHotEdge(Active ae) {
    return ae.outrec != null;
  }

  private static boolean isOpen(Active ae) {
    return ae.localMin.isOpen;
  }

  private static boolean isOpenEnd(Active ae) {
    return ae.localMin.isOpen && isOpenEnd(ae.vertexTop);
  }

  private static boolean isOpenEnd(Vertex v) {
    return (v.flags & (VertexFlags.OpenStart | VertexFlags.OpenEnd)) != VertexFlags.None;
  }

  private static Active getPrevHotEdge(Active ae) {
    Active prev = ae.prevInAEL;
    while (prev != null && (isOpen(prev) || !isHotEdge(prev)))
      prev = prev.prevInAEL;
    return prev;
  }

  private static boolean isFront(Active ae) {
    return ae == ae.outrec.frontEdge;
  }

  private static double getDx(Point64 pt1, Point64 pt2) {
    double dy = pt2.y - pt1.y;
    if (dy != 0) return (pt2.x - pt1.x) / dy;
    return pt2.x > pt1.x ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
  }

  private static long topX(Active ae, long currentY) {
    if (currentY == ae.top.y || ae.top.x == ae.bot.x) return ae.top.x;
    if (currentY == ae.bot.y) return ae.bot.x;
    // banker's rounding (MidpointRounding.ToEven) to match C++ nearbyint
    return ae.bot.x + (long) Math.rint(ae.dx * (currentY - ae.bot.y));
  }

  private static boolean isHorizontal(Active ae) {
    return ae.top.y == ae.bot.y;
  }

  private static boolean isHeadingRightHorz(Active ae) {
    return Double.isInfinite(ae.dx) && ae.dx < 0; // NEGATIVE_INFINITY
  }

  private static boolean isHeadingLeftHorz(Active ae) {
    return Double.isInfinite(ae.dx) && ae.dx > 0; // POSITIVE_INFINITY
  }

  private static PathType getPolyType(Active ae) {
    return ae.localMin.polytype;
  }

  private static boolean isSamePolyType(Active ae1, Active ae2) {
    return ae1.localMin.polytype == ae2.localMin.polytype;
  }

  private static void setDx(Active ae) {
    ae.dx = getDx(ae.bot, ae.top);
  }

  private static Vertex nextVertex(Active ae) {
    return ae.windDx > 0 ? ae.vertexTop.next : ae.vertexTop.prev;
  }

  private static Vertex prevPrevVertex(Active ae) {
    return ae.windDx > 0 ? ae.vertexTop.prev.prev : ae.vertexTop.next.next;
  }

  private static boolean isMaxima(Vertex vertex) {
    return (vertex.flags & VertexFlags.LocalMax) != VertexFlags.None;
  }

  private static boolean isMaxima(Active ae) {
    return isMaxima(ae.vertexTop);
  }

  private static Active getMaximaPair(Active ae) {
    Active ae2 = ae.nextInAEL;
    while (ae2 != null) {
      if (ae2.vertexTop == ae.vertexTop) return ae2;
      ae2 = ae2.nextInAEL;
    }
    return null;
  }

  private static Vertex getCurrYMaximaVertex_Open(Active ae) {
    Vertex result = ae.vertexTop;
    if (ae.windDx > 0) {
      while (result.next.pt.y == result.pt.y
          && (result.flags & (VertexFlags.OpenEnd | VertexFlags.LocalMax)) == VertexFlags.None)
        result = result.next;
    } else {
      while (result.prev.pt.y == result.pt.y
          && (result.flags & (VertexFlags.OpenEnd | VertexFlags.LocalMax)) == VertexFlags.None)
        result = result.prev;
    }
    if (!isMaxima(result)) result = null;
    return result;
  }

  private static Vertex getCurrYMaximaVertex(Active ae) {
    Vertex result = ae.vertexTop;
    if (ae.windDx > 0)
      while (result.next.pt.y == result.pt.y) result = result.next;
    else
      while (result.prev.pt.y == result.pt.y) result = result.prev;
    if (!isMaxima(result)) result = null;
    return result;
  }

  private static final Comparator<IntersectNode> INTERSECT_LIST_SORT =
      (a, b) -> {
        if (a.pt.y != b.pt.y) return (a.pt.y > b.pt.y) ? -1 : 1;
        if (a.pt.x == b.pt.x) return 0;
        return (a.pt.x < b.pt.x) ? -1 : 1;
      };

  private static void setSides(OutRec outrec, Active startEdge, Active endEdge) {
    outrec.frontEdge = startEdge;
    outrec.backEdge = endEdge;
  }

  private static void swapOutrecs(Active ae1, Active ae2) {
    OutRec or1 = ae1.outrec;
    OutRec or2 = ae2.outrec;
    if (or1 == or2) {
      Active ae = or1.frontEdge;
      or1.frontEdge = or1.backEdge;
      or1.backEdge = ae;
      return;
    }
    if (or1 != null) {
      if (ae1 == or1.frontEdge) or1.frontEdge = ae2;
      else or1.backEdge = ae2;
    }
    if (or2 != null) {
      if (ae2 == or2.frontEdge) or2.frontEdge = ae1;
      else or2.backEdge = ae1;
    }
    ae1.outrec = or2;
    ae2.outrec = or1;
  }

  private static void setOwner(OutRec outrec, OutRec newOwner) {
    while (newOwner.owner != null && newOwner.owner.pts == null)
      newOwner.owner = newOwner.owner.owner;
    OutRec tmp = newOwner;
    while (tmp != null && tmp != outrec) tmp = tmp.owner;
    if (tmp != null) newOwner.owner = outrec.owner;
    outrec.owner = newOwner;
  }

  private static double areaOfOutPtList(OutPt op) {
    double area = 0.0;
    OutPt op2 = op;
    do {
      area += (double) (op2.prev.pt.y + op2.pt.y) * (op2.prev.pt.x - op2.pt.x);
      op2 = op2.next;
    } while (op2 != op);
    return area * 0.5;
  }

  private static double areaTriangle(Point64 pt1, Point64 pt2, Point64 pt3) {
    return (double) (pt3.y + pt1.y) * (pt3.x - pt1.x)
        + (double) (pt1.y + pt2.y) * (pt1.x - pt2.x)
        + (double) (pt2.y + pt3.y) * (pt2.x - pt3.x);
  }

  private static OutRec getRealOutRec(OutRec outRec) {
    while (outRec != null && outRec.pts == null)
      outRec = outRec.owner;
    return outRec;
  }

  private static boolean isValidOwner(OutRec outRec, OutRec testOwner) {
    while (testOwner != null && testOwner != outRec)
      testOwner = testOwner.owner;
    return testOwner == null;
  }

  private static void uncoupleOutRec(Active ae) {
    OutRec outrec = ae.outrec;
    if (outrec == null) return;
    outrec.frontEdge.outrec = null;
    outrec.backEdge.outrec = null;
    outrec.frontEdge = null;
    outrec.backEdge = null;
  }

  private static boolean outrecIsAscending(Active hotEdge) {
    return hotEdge == hotEdge.outrec.frontEdge;
  }

  private static void swapFrontBackSides(OutRec outrec) {
    Active ae2 = outrec.frontEdge;
    outrec.frontEdge = outrec.backEdge;
    outrec.backEdge = ae2;
    outrec.pts = outrec.pts.next;
  }

  private static boolean edgesAdjacentInAEL(IntersectNode inode) {
    return (inode.edge1.nextInAEL == inode.edge2) || (inode.edge1.prevInAEL == inode.edge2);
  }

  protected void clearSolutionOnly() {
    while (_actives != null) deleteFromAEL(_actives);
    _scanlineList.clear();
    disposeIntersectNodes();
    _outrecList.clear();
    _horzSegList.clear();
    _horzJoinList.clear();
    _freeActives.clear();
  }

  public void clear() {
    clearSolutionOnly();
    _minimaList.clear();
    _vertexList.clear();
    _currentLocMin = 0;
    _isSortedMinimaList = false;
    _hasOpenPaths = false;
  }

  protected void reset() {
    if (!_isSortedMinimaList) {
      _minimaList.sort(Comparator.comparingLong((LocalMinima lm) -> lm.vertex.pt.y).reversed());
      _isSortedMinimaList = true;
    }
    for (int i = _minimaList.size() - 1; i >= 0; i--)
      _scanlineList.add(_minimaList.get(i).vertex.pt.y);
    _currentBotY = 0;
    _currentLocMin = 0;
    _actives = null;
    _sel = null;
    _succeeded = true;
  }

  // -----------------------------------------------------------------------
  // Scanline management
  // -----------------------------------------------------------------------

  private void insertScanline(long y) {
    // Binary-search insertion so list stays sorted ascending.
    // PopScanline takes from the end (largest value first).
    int lo = 0, hi = _scanlineList.size() - 1;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      long v = _scanlineList.get(mid);
      if (v == y) return; // already present — duplicates handled by PopScanline
      if (v < y) lo = mid + 1; // y is greater, search right
      else hi = mid - 1;       // y is smaller, search left
    }
    _scanlineList.add(lo, y);
  }

  /** Returns false if the list is empty; fills y[0] with the largest y. */
  private boolean popScanline(long[] y) {
    int cnt = _scanlineList.size() - 1;
    if (cnt < 0) {
      y[0] = 0;
      return false;
    }
    y[0] = _scanlineList.get(cnt);
    _scanlineList.remove(cnt--);
    while (cnt >= 0 && y[0] == _scanlineList.get(cnt))
      _scanlineList.remove(cnt--);
    return true;
  }

  private boolean hasLocMinAtY(long y) {
    return _currentLocMin < _minimaList.size()
        && _minimaList.get(_currentLocMin).vertex.pt.y == y;
  }

  private LocalMinima popLocalMinima() {
    return _minimaList.get(_currentLocMin++);
  }

  // -----------------------------------------------------------------------
  // Public add-path API
  // -----------------------------------------------------------------------

  public void addSubject(Path64 path) {
    addPath(path, PathType.Subject);
  }

  public void addOpenSubject(Path64 path) {
    addPath(path, PathType.Subject, true);
  }

  public void addClip(Path64 path) {
    addPath(path, PathType.Clip);
  }

  protected void addPath(Path64 path, PathType polytype) {
    addPath(path, polytype, false);
  }

  protected void addPath(Path64 path, PathType polytype, boolean isOpen) {
    Paths64 tmp = new Paths64();
    tmp.add(path);
    addPaths(tmp, polytype, isOpen);
  }

  protected void addPaths(Paths64 paths, PathType polytype) {
    addPaths(paths, polytype, false);
  }

  protected void addPaths(Paths64 paths, PathType polytype, boolean isOpen) {
    if (isOpen) _hasOpenPaths = true;
    _isSortedMinimaList = false;
    ClipperEngine.addPathsToVertexList(paths, polytype, isOpen, _minimaList, _vertexList);
  }

  protected void addReuseableData(ReuseableDataContainer64 reuseableData) {
    if (reuseableData._minimaList.isEmpty()) return;
    _isSortedMinimaList = false;
    for (LocalMinima lm : reuseableData._minimaList) {
      _minimaList.add(new LocalMinima(lm.vertex, lm.polytype, lm.isOpen));
      if (lm.isOpen) _hasOpenPaths = true;
    }
  }

  // -----------------------------------------------------------------------
  // Wind-count helpers
  // -----------------------------------------------------------------------

  private boolean isContributingClosed(Active ae) {
    switch (_fillrule) {
      case Positive: if (ae.windCount != 1) return false; break;
      case Negative: if (ae.windCount != -1) return false; break;
      case NonZero:  if (Math.abs(ae.windCount) != 1) return false; break;
      default: break;
    }
    switch (_cliptype) {
      case Intersection:
        switch (_fillrule) {
          case Positive: return ae.windCount2 > 0;
          case Negative: return ae.windCount2 < 0;
          default: return ae.windCount2 != 0;
        }
      case Union:
        switch (_fillrule) {
          case Positive: return ae.windCount2 <= 0;
          case Negative: return ae.windCount2 >= 0;
          default: return ae.windCount2 == 0;
        }
      case Difference: {
        boolean result;
        switch (_fillrule) {
          case Positive: result = ae.windCount2 <= 0; break;
          case Negative: result = ae.windCount2 >= 0; break;
          default: result = ae.windCount2 == 0; break;
        }
        return (getPolyType(ae) == PathType.Subject) ? result : !result;
      }
      case Xor: return true;
      default: return false;
    }
  }

  private boolean isContributingOpen(Active ae) {
    boolean isInSubj, isInClip;
    switch (_fillrule) {
      case Positive:
        isInSubj = ae.windCount > 0;
        isInClip = ae.windCount2 > 0;
        break;
      case Negative:
        isInSubj = ae.windCount < 0;
        isInClip = ae.windCount2 < 0;
        break;
      default:
        isInSubj = ae.windCount != 0;
        isInClip = ae.windCount2 != 0;
        break;
    }
    switch (_cliptype) {
      case Intersection: return isInClip;
      case Union: return !isInSubj && !isInClip;
      default: return !isInClip; // Difference and others
    }
  }

  private void setWindCountForClosedPathEdge(Active ae) {
    Active ae2 = ae.prevInAEL;
    PathType pt = getPolyType(ae);
    while (ae2 != null && (getPolyType(ae2) != pt || isOpen(ae2)))
      ae2 = ae2.prevInAEL;

    if (ae2 == null) {
      ae.windCount = ae.windDx;
      ae2 = _actives;
    } else if (_fillrule == FillRule.EvenOdd) {
      ae.windCount = ae.windDx;
      ae.windCount2 = ae2.windCount2;
      ae2 = ae2.nextInAEL;
    } else {
      if (ae2.windCount * ae2.windDx < 0) {
        if (Math.abs(ae2.windCount) > 1) {
          if (ae2.windDx * ae.windDx < 0) ae.windCount = ae2.windCount;
          else ae.windCount = ae2.windCount + ae.windDx;
        } else {
          ae.windCount = isOpen(ae) ? 1 : ae.windDx;
        }
      } else {
        if (ae2.windDx * ae.windDx < 0) ae.windCount = ae2.windCount;
        else ae.windCount = ae2.windCount + ae.windDx;
      }
      ae.windCount2 = ae2.windCount2;
      ae2 = ae2.nextInAEL;
    }

    if (_fillrule == FillRule.EvenOdd) {
      while (ae2 != ae) {
        if (getPolyType(ae2) != pt && !isOpen(ae2))
          ae.windCount2 = (ae.windCount2 == 0 ? 1 : 0);
        ae2 = ae2.nextInAEL;
      }
    } else {
      while (ae2 != ae) {
        if (getPolyType(ae2) != pt && !isOpen(ae2))
          ae.windCount2 += ae2.windDx;
        ae2 = ae2.nextInAEL;
      }
    }
  }

  private void setWindCountForOpenPathEdge(Active ae) {
    Active ae2 = _actives;
    if (_fillrule == FillRule.EvenOdd) {
      int cnt1 = 0, cnt2 = 0;
      while (ae2 != ae) {
        if (getPolyType(ae2) == PathType.Clip) cnt2++;
        else if (!isOpen(ae2)) cnt1++;
        ae2 = ae2.nextInAEL;
      }
      ae.windCount = isOdd(cnt1) ? 1 : 0;
      ae.windCount2 = isOdd(cnt2) ? 1 : 0;
    } else {
      while (ae2 != ae) {
        if (getPolyType(ae2) == PathType.Clip) ae.windCount2 += ae2.windDx;
        else if (!isOpen(ae2)) ae.windCount += ae2.windDx;
        ae2 = ae2.nextInAEL;
      }
    }
  }

  // -----------------------------------------------------------------------
  // AEL ordering
  // -----------------------------------------------------------------------

  private static boolean isValidAelOrder(Active resident, Active newcomer) {
    if (newcomer.curX != resident.curX) return newcomer.curX > resident.curX;

    int d = InternalClipper.crossProductSign(resident.top, newcomer.bot, newcomer.top);
    if (d != 0) return d < 0;

    if (!isMaxima(resident) && resident.top.y > newcomer.top.y) {
      return InternalClipper.crossProductSign(newcomer.bot,
          resident.top, nextVertex(resident).pt) <= 0;
    }
    if (!isMaxima(newcomer) && newcomer.top.y > resident.top.y) {
      return InternalClipper.crossProductSign(newcomer.bot,
          newcomer.top, nextVertex(newcomer).pt) >= 0;
    }

    long y = newcomer.bot.y;
    boolean newcomerIsLeft = newcomer.isLeftBound;
    if (resident.bot.y != y || resident.localMin.vertex.pt.y != y)
      return newcomer.isLeftBound;
    if (resident.isLeftBound != newcomerIsLeft) return newcomerIsLeft;
    if (InternalClipper.isCollinear(prevPrevVertex(resident).pt, resident.bot, resident.top))
      return true;
    return (InternalClipper.crossProductSign(prevPrevVertex(resident).pt,
        newcomer.bot, prevPrevVertex(newcomer).pt) > 0) == newcomerIsLeft;
  }

  private void insertLeftEdge(Active ae) {
    if (_actives == null) {
      ae.prevInAEL = null;
      ae.nextInAEL = null;
      _actives = ae;
    } else if (!isValidAelOrder(_actives, ae)) {
      ae.prevInAEL = null;
      ae.nextInAEL = _actives;
      _actives.prevInAEL = ae;
      _actives = ae;
    } else {
      Active ae2 = _actives;
      while (ae2.nextInAEL != null && isValidAelOrder(ae2.nextInAEL, ae))
        ae2 = ae2.nextInAEL;
      if (ae2.joinWith == JoinWith.Right) ae2 = ae2.nextInAEL;
      ae.nextInAEL = ae2.nextInAEL;
      if (ae2.nextInAEL != null) ae2.nextInAEL.prevInAEL = ae;
      ae.prevInAEL = ae2;
      ae2.nextInAEL = ae;
    }
  }

  private static void insertRightEdge(Active ae, Active ae2) {
    ae2.nextInAEL = ae.nextInAEL;
    if (ae.nextInAEL != null) ae.nextInAEL.prevInAEL = ae2;
    ae2.prevInAEL = ae;
    ae.nextInAEL = ae2;
  }

  private void insertLocalMinimaIntoAEL(long botY) {
    while (hasLocMinAtY(botY)) {
      LocalMinima localMinima = popLocalMinima();
      Active leftBound;
      if ((localMinima.vertex.flags & VertexFlags.OpenStart) != VertexFlags.None) {
        leftBound = null;
      } else {
        leftBound = newActive();
        leftBound.bot = localMinima.vertex.pt;
        leftBound.curX = localMinima.vertex.pt.x;
        leftBound.windDx = -1;
        leftBound.vertexTop = localMinima.vertex.prev;
        leftBound.top = localMinima.vertex.prev.pt;
        leftBound.outrec = null;
        leftBound.localMin = localMinima;
        setDx(leftBound);
      }

      Active rightBound;
      if ((localMinima.vertex.flags & VertexFlags.OpenEnd) != VertexFlags.None) {
        rightBound = null;
      } else {
        rightBound = newActive();
        rightBound.bot = localMinima.vertex.pt;
        rightBound.curX = localMinima.vertex.pt.x;
        rightBound.windDx = 1;
        rightBound.vertexTop = localMinima.vertex.next;
        rightBound.top = localMinima.vertex.next.pt;
        rightBound.outrec = null;
        rightBound.localMin = localMinima;
        setDx(rightBound);
      }

      // Determine which is left/right
      if (leftBound != null && rightBound != null) {
        if (isHorizontal(leftBound)) {
          if (isHeadingRightHorz(leftBound)) {
            Active tmp = leftBound; leftBound = rightBound; rightBound = tmp;
          }
        } else if (isHorizontal(rightBound)) {
          if (isHeadingLeftHorz(rightBound)) {
            Active tmp = leftBound; leftBound = rightBound; rightBound = tmp;
          }
        } else if (leftBound.dx < rightBound.dx) {
          Active tmp = leftBound; leftBound = rightBound; rightBound = tmp;
        }
      } else if (leftBound == null) {
        leftBound = rightBound;
        rightBound = null;
      }

      boolean contributing;
      leftBound.isLeftBound = true;
      insertLeftEdge(leftBound);

      if (isOpen(leftBound)) {
        setWindCountForOpenPathEdge(leftBound);
        contributing = isContributingOpen(leftBound);
      } else {
        setWindCountForClosedPathEdge(leftBound);
        contributing = isContributingClosed(leftBound);
      }

      if (rightBound != null) {
        rightBound.windCount = leftBound.windCount;
        rightBound.windCount2 = leftBound.windCount2;
        insertRightEdge(leftBound, rightBound);

        if (contributing) {
          addLocalMinPoly(leftBound, rightBound, leftBound.bot, true);
          if (!isHorizontal(leftBound)) checkJoinLeft(leftBound, leftBound.bot, false);
        }

        while (rightBound.nextInAEL != null
            && isValidAelOrder(rightBound.nextInAEL, rightBound)) {
          intersectEdges(rightBound, rightBound.nextInAEL, rightBound.bot);
          swapPositionsInAEL(rightBound, rightBound.nextInAEL);
        }

        if (isHorizontal(rightBound)) pushHorz(rightBound);
        else {
          checkJoinRight(rightBound, rightBound.bot, false);
          insertScanline(rightBound.top.y);
        }
      } else if (contributing) {
        startOpenPath(leftBound, leftBound.bot);
      }

      if (isHorizontal(leftBound)) pushHorz(leftBound);
      else insertScanline(leftBound.top.y);
    }
  }

  private void pushHorz(Active ae) {
    ae.nextInSEL = _sel;
    _sel = ae;
  }

  /** Returns false when there are no more horizontals. Fills ae[0]. */
  private boolean popHorz(Active[] ae) {
    ae[0] = _sel;
    if (_sel == null) return false;
    _sel = _sel.nextInSEL;
    return true;
  }

  private OutPt addLocalMinPoly(Active ae1, Active ae2, Point64 pt, boolean isNew) {
    OutRec outrec = newOutRec();
    ae1.outrec = outrec;
    ae2.outrec = outrec;

    if (isOpen(ae1)) {
      outrec.owner = null;
      outrec.isOpen = true;
      if (ae1.windDx > 0) setSides(outrec, ae1, ae2);
      else setSides(outrec, ae2, ae1);
    } else {
      outrec.isOpen = false;
      Active prevHotEdge = getPrevHotEdge(ae1);
      if (prevHotEdge != null) {
        if (_using_polytree) setOwner(outrec, prevHotEdge.outrec);
        outrec.owner = prevHotEdge.outrec;
        if (outrecIsAscending(prevHotEdge) == isNew) setSides(outrec, ae2, ae1);
        else setSides(outrec, ae1, ae2);
      } else {
        outrec.owner = null;
        if (isNew) setSides(outrec, ae1, ae2);
        else setSides(outrec, ae2, ae1);
      }
    }

    OutPt op = newOutPt(pt, outrec);
    outrec.pts = op;
    return op;
  }

  private OutPt addLocalMinPoly(Active ae1, Active ae2, Point64 pt) {
    return addLocalMinPoly(ae1, ae2, pt, false);
  }

  private OutPt addLocalMaxPoly(Active ae1, Active ae2, Point64 pt) {
    if (isJoined(ae1)) split(ae1, pt);
    if (isJoined(ae2)) split(ae2, pt);

    if (isFront(ae1) == isFront(ae2)) {
      if (isOpenEnd(ae1)) swapFrontBackSides(ae1.outrec);
      else if (isOpenEnd(ae2)) swapFrontBackSides(ae2.outrec);
      else { _succeeded = false; return null; }
    }

    OutPt result = addOutPt(ae1, pt);
    if (ae1.outrec == ae2.outrec) {
      OutRec outrec = ae1.outrec;
      outrec.pts = result;
      if (_using_polytree) {
        Active e = getPrevHotEdge(ae1);
        if (e == null) outrec.owner = null;
        else setOwner(outrec, e.outrec);
      }
      uncoupleOutRec(ae1);
    } else if (isOpen(ae1)) {
      if (ae1.windDx < 0) joinOutrecPaths(ae1, ae2);
      else joinOutrecPaths(ae2, ae1);
    } else if (ae1.outrec.idx < ae2.outrec.idx) {
      joinOutrecPaths(ae1, ae2);
    } else {
      joinOutrecPaths(ae2, ae1);
    }
    return result;
  }

  private static void joinOutrecPaths(Active ae1, Active ae2) {
    OutPt p1Start = ae1.outrec.pts;
    OutPt p2Start = ae2.outrec.pts;
    OutPt p1End = p1Start.next;
    OutPt p2End = p2Start.next;
    if (isFront(ae1)) {
      p2End.prev = p1Start;
      p1Start.next = p2End;
      p2Start.next = p1End;
      p1End.prev = p2Start;
      ae1.outrec.pts = p2Start;
      ae1.outrec.frontEdge = ae2.outrec.frontEdge;
      if (ae1.outrec.frontEdge != null) ae1.outrec.frontEdge.outrec = ae1.outrec;
    } else {
      p1End.prev = p2Start;
      p2Start.next = p1End;
      p1Start.next = p2End;
      p2End.prev = p1Start;
      ae1.outrec.backEdge = ae2.outrec.backEdge;
      if (ae1.outrec.backEdge != null) ae1.outrec.backEdge.outrec = ae1.outrec;
    }
    ae2.outrec.frontEdge = null;
    ae2.outrec.backEdge = null;
    ae2.outrec.pts = null;
    ae1.outrec.outPtCount += ae2.outrec.outPtCount;
    setOwner(ae2.outrec, ae1.outrec);

    if (isOpenEnd(ae1)) {
      ae2.outrec.pts = ae1.outrec.pts;
      ae1.outrec.pts = null;
    }
    ae1.outrec = null;
    ae2.outrec = null;
  }

  private OutPt addOutPt(Active ae, Point64 pt) {
    OutRec outrec = ae.outrec;
    boolean toFront = isFront(ae);
    OutPt opFront = outrec.pts;
    OutPt opBack = opFront.next;

    if (toFront && pt.equals(opFront.pt)) return opFront;
    if (!toFront && pt.equals(opBack.pt)) return opBack;

    OutPt newOp = newOutPt(pt, outrec);
    opBack.prev = newOp;
    newOp.prev = opFront;
    newOp.next = opBack;
    opFront.next = newOp;
    if (toFront) outrec.pts = newOp;
    return newOp;
  }

  private OutRec newOutRec() {
    OutRec result = new OutRec();
    result.idx = _outrecList.size();
    _outrecList.add(result);
    return result;
  }

  private OutPt newOutPt(Point64 pt, OutRec outrec) {
    OutPt op = new OutPt(pt, outrec);
    outrec.outPtCount++;
    return op;
  }

  private OutPt startOpenPath(Active ae, Point64 pt) {
    OutRec outrec = newOutRec();
    outrec.isOpen = true;
    if (ae.windDx > 0) {
      outrec.frontEdge = ae;
      outrec.backEdge = null;
    } else {
      outrec.frontEdge = null;
      outrec.backEdge = ae;
    }
    ae.outrec = outrec;
    OutPt op = newOutPt(pt, outrec);
    outrec.pts = op;
    return op;
  }

  private void updateEdgeIntoAEL(Active ae) {
    ae.bot = ae.top;
    ae.vertexTop = nextVertex(ae);
    ae.top = ae.vertexTop.pt;
    ae.curX = ae.bot.x;
    setDx(ae);
    if (isJoined(ae)) split(ae, ae.bot);
    if (isHorizontal(ae)) {
      if (!isOpen(ae)) trimHorz(ae, preserveCollinear);
      return;
    }
    insertScanline(ae.top.y);
    checkJoinLeft(ae, ae.bot, false);
    checkJoinRight(ae, ae.bot, true);
  }

  private static Active findEdgeWithMatchingLocMin(Active e) {
    Active result = e.nextInAEL;
    while (result != null) {
      if (result.localMin.equals(e.localMin)) return result;
      if (!isHorizontal(result) && !e.bot.equals(result.bot)) result = null;
      else result = result.nextInAEL;
    }
    result = e.prevInAEL;
    while (result != null) {
      if (result.localMin.equals(e.localMin)) return result;
      if (!isHorizontal(result) && !e.bot.equals(result.bot)) return null;
      result = result.prevInAEL;
    }
    return null;
  }

  private void intersectEdges(Active ae1, Active ae2, Point64 pt) {
    OutPt resultOp = null;

    // Manage open path intersections separately
    if (_hasOpenPaths && (isOpen(ae1) || isOpen(ae2))) {
      if (isOpen(ae1) && isOpen(ae2)) return;
      if (isOpen(ae2)) { Active tmp = ae1; ae1 = ae2; ae2 = tmp; }
      if (isJoined(ae2)) split(ae2, pt);

      if (_cliptype == ClipType.Union) {
        if (!isHotEdge(ae2)) return;
      } else if (ae2.localMin.polytype == PathType.Subject) return;

      switch (_fillrule) {
        case Positive: if (ae2.windCount != 1) return; break;
        case Negative: if (ae2.windCount != -1) return; break;
        default: if (Math.abs(ae2.windCount) != 1) return; break;
      }

      if (isHotEdge(ae1)) {
        resultOp = addOutPt(ae1, pt);
        if (isFront(ae1)) ae1.outrec.frontEdge = null;
        else ae1.outrec.backEdge = null;
        ae1.outrec = null;
      } else if (pt.equals(ae1.localMin.vertex.pt) && !isOpenEnd(ae1.localMin.vertex)) {
        Active ae3 = findEdgeWithMatchingLocMin(ae1);
        if (ae3 != null && isHotEdge(ae3)) {
          ae1.outrec = ae3.outrec;
          if (ae1.windDx > 0) setSides(ae3.outrec, ae1, ae3);
          else setSides(ae3.outrec, ae3, ae1);
          return;
        }
        resultOp = startOpenPath(ae1, pt);
      } else {
        resultOp = startOpenPath(ae1, pt);
      }
      return;
    }

    // Managing closed paths
    if (isJoined(ae1)) split(ae1, pt);
    if (isJoined(ae2)) split(ae2, pt);

    // Update winding counts
    int oldE1WindCount, oldE2WindCount;
    if (ae1.localMin.polytype == ae2.localMin.polytype) {
      if (_fillrule == FillRule.EvenOdd) {
        oldE1WindCount = ae1.windCount;
        ae1.windCount = ae2.windCount;
        ae2.windCount = oldE1WindCount;
      } else {
        if (ae1.windCount + ae2.windDx == 0) ae1.windCount = -ae1.windCount;
        else ae1.windCount += ae2.windDx;
        if (ae2.windCount - ae1.windDx == 0) ae2.windCount = -ae2.windCount;
        else ae2.windCount -= ae1.windDx;
      }
    } else {
      if (_fillrule != FillRule.EvenOdd) ae1.windCount2 += ae2.windDx;
      else ae1.windCount2 = (ae1.windCount2 == 0 ? 1 : 0);
      if (_fillrule != FillRule.EvenOdd) ae2.windCount2 -= ae1.windDx;
      else ae2.windCount2 = (ae2.windCount2 == 0 ? 1 : 0);
    }

    switch (_fillrule) {
      case Positive:
        oldE1WindCount = ae1.windCount;
        oldE2WindCount = ae2.windCount;
        break;
      case Negative:
        oldE1WindCount = -ae1.windCount;
        oldE2WindCount = -ae2.windCount;
        break;
      default:
        oldE1WindCount = Math.abs(ae1.windCount);
        oldE2WindCount = Math.abs(ae2.windCount);
        break;
    }

    boolean e1WindCountIs0or1 = oldE1WindCount == 0 || oldE1WindCount == 1;
    boolean e2WindCountIs0or1 = oldE2WindCount == 0 || oldE2WindCount == 1;

    if ((!isHotEdge(ae1) && !e1WindCountIs0or1)
        || (!isHotEdge(ae2) && !e2WindCountIs0or1)) return;

    // Both edges are 'hot'
    if (isHotEdge(ae1) && isHotEdge(ae2)) {
      if ((oldE1WindCount != 0 && oldE1WindCount != 1)
          || (oldE2WindCount != 0 && oldE2WindCount != 1)
          || (ae1.localMin.polytype != ae2.localMin.polytype && _cliptype != ClipType.Xor)) {
        resultOp = addLocalMaxPoly(ae1, ae2, pt);
      } else if (isFront(ae1) || ae1.outrec == ae2.outrec) {
        resultOp = addLocalMaxPoly(ae1, ae2, pt);
        addLocalMinPoly(ae1, ae2, pt);
      } else {
        resultOp = addOutPt(ae1, pt);
        addOutPt(ae2, pt);
        swapOutrecs(ae1, ae2);
      }
    } else if (isHotEdge(ae1)) {
      resultOp = addOutPt(ae1, pt);
      swapOutrecs(ae1, ae2);
    } else if (isHotEdge(ae2)) {
      resultOp = addOutPt(ae2, pt);
      swapOutrecs(ae1, ae2);
    } else {
      // Neither edge is hot
      long e1Wc2, e2Wc2;
      switch (_fillrule) {
        case Positive: e1Wc2 = ae1.windCount2; e2Wc2 = ae2.windCount2; break;
        case Negative: e1Wc2 = -ae1.windCount2; e2Wc2 = -ae2.windCount2; break;
        default: e1Wc2 = Math.abs(ae1.windCount2); e2Wc2 = Math.abs(ae2.windCount2); break;
      }

      if (!isSamePolyType(ae1, ae2)) {
        resultOp = addLocalMinPoly(ae1, ae2, pt);
      } else if (oldE1WindCount == 1 && oldE2WindCount == 1) {
        switch (_cliptype) {
          case Union:
            if (e1Wc2 > 0 && e2Wc2 > 0) return;
            resultOp = addLocalMinPoly(ae1, ae2, pt);
            break;
          case Difference:
            if (((getPolyType(ae1) == PathType.Clip) && e1Wc2 > 0 && e2Wc2 > 0)
                || ((getPolyType(ae1) == PathType.Subject) && e1Wc2 <= 0 && e2Wc2 <= 0))
              resultOp = addLocalMinPoly(ae1, ae2, pt);
            break;
          case Xor:
            resultOp = addLocalMinPoly(ae1, ae2, pt);
            break;
          default: // Intersection
            if (e1Wc2 <= 0 || e2Wc2 <= 0) return;
            resultOp = addLocalMinPoly(ae1, ae2, pt);
            break;
        }
      }
    }
  }

  private void deleteFromAEL(Active ae) {
    Active prev = ae.prevInAEL;
    Active next = ae.nextInAEL;
    if (prev == null && next == null && ae != _actives) return;
    if (prev != null) prev.nextInAEL = next;
    else _actives = next;
    if (next != null) next.prevInAEL = prev;
    poolDeletedActive(ae);
  }

  private void poolDeletedActive(Active ae) {
    ae.bot = new Point64();
    ae.top = new Point64();
    ae.dx = 0.0;
    ae.windCount = 0;
    ae.windCount2 = 0;
    ae.outrec = null;
    ae.prevInAEL = null;
    ae.nextInAEL = null;
    ae.prevInSEL = null;
    ae.nextInSEL = null;
    ae.jump = null;
    ae.vertexTop = null;
    ae.localMin = new LocalMinima();
    ae.isLeftBound = false;
    ae.joinWith = JoinWith.None;
    _freeActives.add(ae);
  }

  private Active newActive() {
    if (_freeActives.isEmpty()) return new Active();
    return _freeActives.remove(_freeActives.size() - 1);
  }

  private void adjustCurrXAndCopyToSEL(long topY) {
    Active ae = _actives;
    _sel = ae;
    while (ae != null) {
      ae.prevInSEL = ae.prevInAEL;
      ae.nextInSEL = ae.nextInAEL;
      ae.jump = ae.nextInSEL;
      ae.curX = topX(ae, topY);
      ae = ae.nextInAEL;
    }
  }

  protected void executeInternal(ClipType ct, FillRule fillRule) {
    if (ct == ClipType.NoClip) return;
    _fillrule = fillRule;
    _cliptype = ct;
    reset();
    long[] y = new long[1];
    if (!popScanline(y)) return;
    Active[] ae = new Active[1];
    while (_succeeded) {
      insertLocalMinimaIntoAEL(y[0]);
      while (popHorz(ae)) doHorizontal(ae[0]);
      if (!_horzSegList.isEmpty()) {
        convertHorzSegsToJoins();
        _horzSegList.clear();
      }
      _currentBotY = y[0];
      if (!popScanline(y)) break;
      doIntersections(y[0]);
      doTopOfScanbeam(y[0]);
      while (popHorz(ae)) doHorizontal(ae[0]);
    }
    if (_succeeded) processHorzJoins();
  }

  private void doIntersections(long topY) {
    if (!buildIntersectList(topY)) return;
    processIntersectList();
    disposeIntersectNodes();
  }

  private void disposeIntersectNodes() {
    _intersectList.clear();
  }

  private void addNewIntersectNode(Active ae1, Active ae2, long topY) {
    Point64 ip = InternalClipper.getLineIntersectPt(ae1.bot, ae1.top, ae2.bot, ae2.top);
    if (ip == null) ip = new Point64(ae1.curX, topY);

    if (ip.y > _currentBotY || ip.y < topY) {
      double absDx1 = Math.abs(ae1.dx);
      double absDx2 = Math.abs(ae2.dx);
      if (absDx1 > 100 && absDx2 > 100) {
        ip = (absDx1 > absDx2)
            ? InternalClipper.getClosestPtOnSegment(ip, ae1.bot, ae1.top)
            : InternalClipper.getClosestPtOnSegment(ip, ae2.bot, ae2.top);
      } else if (absDx1 > 100) {
        ip = InternalClipper.getClosestPtOnSegment(ip, ae1.bot, ae1.top);
      } else if (absDx2 > 100) {
        ip = InternalClipper.getClosestPtOnSegment(ip, ae2.bot, ae2.top);
      } else {
        if (ip.y < topY) ip.y = topY;
        else ip.y = _currentBotY;
        if (absDx1 < absDx2) ip.x = topX(ae1, ip.y);
        else ip.x = topX(ae2, ip.y);
      }
    }
    _intersectList.add(new IntersectNode(ip, ae1, ae2));
  }

  private static Active extractFromSEL(Active ae) {
    Active res = ae.nextInSEL;
    if (res != null) res.prevInSEL = ae.prevInSEL;
    ae.prevInSEL.nextInSEL = res;
    return res;
  }

  private static void insert1Before2InSEL(Active ae1, Active ae2) {
    ae1.prevInSEL = ae2.prevInSEL;
    if (ae1.prevInSEL != null) ae1.prevInSEL.nextInSEL = ae1;
    ae1.nextInSEL = ae2;
    ae2.prevInSEL = ae1;
  }

  private boolean buildIntersectList(long topY) {
    if (_actives == null || _actives.nextInAEL == null) return false;
    adjustCurrXAndCopyToSEL(topY);

    Active left = _sel;
    while (left.jump != null) {
      Active prevBase = null;
      while (left != null && left.jump != null) {
        Active currBase = left;
        Active right = left.jump;
        Active lEnd = right;
        Active rEnd = right.jump;
        left.jump = rEnd;
        while (left != lEnd && right != rEnd) {
          if (right.curX < left.curX) {
            Active tmp = right.prevInSEL;
            for (;;) {
              addNewIntersectNode(tmp, right, topY);
              if (tmp == left) break;
              tmp = tmp.prevInSEL;
            }
            tmp = right;
            right = extractFromSEL(tmp);
            lEnd = right;
            insert1Before2InSEL(tmp, left);
            if (left == currBase) {
              currBase = tmp;
              currBase.jump = rEnd;
              if (prevBase == null) _sel = currBase;
              else prevBase.jump = currBase;
            }
          } else {
            left = left.nextInSEL;
          }
        }
        prevBase = currBase;
        left = rEnd;
      }
      left = _sel;
    }
    return !_intersectList.isEmpty();
  }

  private void processIntersectList() {
    _intersectList.sort(INTERSECT_LIST_SORT);
    for (int i = 0; i < _intersectList.size(); ++i) {
      if (!edgesAdjacentInAEL(_intersectList.get(i))) {
        int j = i + 1;
        while (!edgesAdjacentInAEL(_intersectList.get(j))) j++;
        IntersectNode tmp = _intersectList.get(j);
        _intersectList.set(j, _intersectList.get(i));
        _intersectList.set(i, tmp);
      }
      IntersectNode node = _intersectList.get(i);
      intersectEdges(node.edge1, node.edge2, node.pt);
      swapPositionsInAEL(node.edge1, node.edge2);
      node.edge1.curX = node.pt.x;
      node.edge2.curX = node.pt.x;
      checkJoinLeft(node.edge2, node.pt, true);
      checkJoinRight(node.edge1, node.pt, true);
    }
  }

  private void swapPositionsInAEL(Active ae1, Active ae2) {
    Active next = ae2.nextInAEL;
    if (next != null) next.prevInAEL = ae1;
    Active prev = ae1.prevInAEL;
    if (prev != null) prev.nextInAEL = ae2;
    ae2.prevInAEL = prev;
    ae2.nextInAEL = ae1;
    ae1.prevInAEL = ae2;
    ae1.nextInAEL = next;
    if (ae2.prevInAEL == null) _actives = ae2;
  }

  /**
   * Resets horizontal direction; fills leftX[0] and rightX[0].
   * Returns true if going left-to-right.
   */
  private static boolean resetHorzDirection(Active horz, Vertex vertexMax,
      long[] leftX, long[] rightX) {
    if (horz.bot.x == horz.top.x) {
      leftX[0] = horz.curX;
      rightX[0] = horz.curX;
      Active ae = horz.nextInAEL;
      while (ae != null && ae.vertexTop != vertexMax) ae = ae.nextInAEL;
      return ae != null;
    }
    if (horz.curX < horz.top.x) {
      leftX[0] = horz.curX;
      rightX[0] = horz.top.x;
      return true;
    }
    leftX[0] = horz.top.x;
    rightX[0] = horz.curX;
    return false;
  }

  private static void trimHorz(Active horzEdge, boolean preserveCollinear) {
    boolean wasTrimmed = false;
    Point64 pt = nextVertex(horzEdge).pt;
    while (pt.y == horzEdge.top.y) {
      if (preserveCollinear
          && ((pt.x < horzEdge.top.x) != (horzEdge.bot.x < horzEdge.top.x))) break;
      horzEdge.vertexTop = nextVertex(horzEdge);
      horzEdge.top = pt;
      wasTrimmed = true;
      if (isMaxima(horzEdge)) break;
      pt = nextVertex(horzEdge).pt;
    }
    if (wasTrimmed) setDx(horzEdge);
  }

  private void addToHorzSegList(OutPt op) {
    if (op.outrec.isOpen) return;
    _horzSegList.add(new HorzSegment(op));
  }

  private static OutPt getLastOp(Active hotEdge) {
    OutRec outrec = hotEdge.outrec;
    return (hotEdge == outrec.frontEdge) ? outrec.pts : outrec.pts.next;
  }

  private void doHorizontal(Active horz) {
    boolean horzIsOpen = isOpen(horz);
    long Y = horz.bot.y;

    Vertex vertex_max = horzIsOpen
        ? getCurrYMaximaVertex_Open(horz)
        : getCurrYMaximaVertex(horz);

    long[] leftX = new long[1], rightX = new long[1];
    boolean isLeftToRight = resetHorzDirection(horz, vertex_max, leftX, rightX);

    if (isHotEdge(horz)) {
      OutPt op = addOutPt(horz, new Point64(horz.curX, Y));
      addToHorzSegList(op);
    }

    for (;;) {
      Active ae = isLeftToRight ? horz.nextInAEL : horz.prevInAEL;

      while (ae != null) {
        if (ae.vertexTop == vertex_max) {
          if (isHotEdge(horz) && isJoined(ae)) split(ae, ae.top);
          if (isHotEdge(horz)) {
            while (horz.vertexTop != vertex_max) {
              addOutPt(horz, horz.top);
              updateEdgeIntoAEL(horz);
            }
            if (isLeftToRight) addLocalMaxPoly(horz, ae, horz.top);
            else addLocalMaxPoly(ae, horz, horz.top);
          }
          deleteFromAEL(ae);
          deleteFromAEL(horz);
          return;
        }

        Point64 pt;
        if (vertex_max != horz.vertexTop || isOpenEnd(horz)) {
          if ((isLeftToRight && ae.curX > rightX[0])
              || (!isLeftToRight && ae.curX < leftX[0])) break;

          if (ae.curX == horz.top.x && !isHorizontal(ae)) {
            pt = nextVertex(horz).pt;
            if (isOpen(ae) && !isSamePolyType(ae, horz) && !isHotEdge(ae)) {
              if ((isLeftToRight && topX(ae, pt.y) > pt.x)
                  || (!isLeftToRight && topX(ae, pt.y) < pt.x)) break;
            } else if ((isLeftToRight && topX(ae, pt.y) >= pt.x)
                || (!isLeftToRight && topX(ae, pt.y) <= pt.x)) break;
          }
        }

        pt = new Point64(ae.curX, Y);
        if (isLeftToRight) {
          intersectEdges(horz, ae, pt);
          swapPositionsInAEL(horz, ae);
          checkJoinLeft(ae, pt, false);
          horz.curX = ae.curX;
          ae = horz.nextInAEL;
        } else {
          intersectEdges(ae, horz, pt);
          swapPositionsInAEL(ae, horz);
          checkJoinRight(ae, pt, false);
          horz.curX = ae.curX;
          ae = horz.prevInAEL;
        }
        if (isHotEdge(horz)) addToHorzSegList(getLastOp(horz));
      }

      // Check if finished looping through consecutive horizontals
      if (horzIsOpen && isOpenEnd(horz)) {
        if (isHotEdge(horz)) {
          addOutPt(horz, horz.top);
          if (isFront(horz)) horz.outrec.frontEdge = null;
          else horz.outrec.backEdge = null;
          horz.outrec = null;
        }
        deleteFromAEL(horz);
        return;
      }
      if (nextVertex(horz).pt.y != horz.top.y) break;

      if (isHotEdge(horz)) addOutPt(horz, horz.top);
      updateEdgeIntoAEL(horz);
      isLeftToRight = resetHorzDirection(horz, vertex_max, leftX, rightX);
    }

    if (isHotEdge(horz)) {
      OutPt op = addOutPt(horz, horz.top);
      addToHorzSegList(op);
    }
    updateEdgeIntoAEL(horz);
  }

  private void doTopOfScanbeam(long y) {
    _sel = null;
    Active ae = _actives;
    while (ae != null) {
      if (ae.top.y == y) {
        ae.curX = ae.top.x;
        if (isMaxima(ae)) {
          ae = doMaxima(ae);
          continue;
        }
        if (isHotEdge(ae)) addOutPt(ae, ae.top);
        updateEdgeIntoAEL(ae);
        if (isHorizontal(ae)) pushHorz(ae);
      } else {
        ae.curX = topX(ae, y);
      }
      ae = ae.nextInAEL;
    }
  }

  private Active doMaxima(Active ae) {
    Active prevE = ae.prevInAEL;
    Active nextE = ae.nextInAEL;

    if (isOpenEnd(ae)) {
      if (isHotEdge(ae)) addOutPt(ae, ae.top);
      if (isHorizontal(ae)) return nextE;
      if (isHotEdge(ae)) {
        if (isFront(ae)) ae.outrec.frontEdge = null;
        else ae.outrec.backEdge = null;
        ae.outrec = null;
      }
      deleteFromAEL(ae);
      return nextE;
    }

    Active maxPair = getMaximaPair(ae);
    if (maxPair == null) return nextE;

    if (isJoined(ae)) split(ae, ae.top);
    if (isJoined(maxPair)) split(maxPair, maxPair.top);

    while (nextE != maxPair) {
      intersectEdges(ae, nextE, ae.top);
      swapPositionsInAEL(ae, nextE);
      nextE = ae.nextInAEL;
    }

    if (isOpen(ae)) {
      if (isHotEdge(ae)) addLocalMaxPoly(ae, maxPair, ae.top);
      deleteFromAEL(maxPair);
      deleteFromAEL(ae);
      return prevE != null ? prevE.nextInAEL : _actives;
    }

    if (isHotEdge(ae)) addLocalMaxPoly(ae, maxPair, ae.top);
    deleteFromAEL(ae);
    deleteFromAEL(maxPair);
    return prevE != null ? prevE.nextInAEL : _actives;
  }

  private static boolean isJoined(Active e) {
    return e.joinWith != JoinWith.None;
  }

  private void split(Active e, Point64 currPt) {
    if (e.joinWith == JoinWith.Right) {
      e.joinWith = JoinWith.None;
      e.nextInAEL.joinWith = JoinWith.None;
      addLocalMinPoly(e, e.nextInAEL, currPt, true);
    } else {
      e.joinWith = JoinWith.None;
      e.prevInAEL.joinWith = JoinWith.None;
      addLocalMinPoly(e.prevInAEL, e, currPt, true);
    }
  }

  private void checkJoinLeft(Active e, Point64 pt, boolean checkCurrX) {
    Active prev = e.prevInAEL;
    if (prev == null || !isHotEdge(e) || !isHotEdge(prev)
        || isHorizontal(e) || isHorizontal(prev) || isOpen(e) || isOpen(prev)) return;
    if ((pt.y < e.top.y + 2 || pt.y < prev.top.y + 2)
        && (e.bot.y > pt.y || prev.bot.y > pt.y)) return;

    if (checkCurrX) {
      if (perpendicDistFromLineSqrd(pt, prev.bot, prev.top) > 0.25) return;
    } else if (e.curX != prev.curX) return;
    if (!InternalClipper.isCollinear(e.top, pt, prev.top)) return;

    if (e.outrec.idx == prev.outrec.idx) addLocalMaxPoly(prev, e, pt);
    else if (e.outrec.idx < prev.outrec.idx) joinOutrecPaths(e, prev);
    else joinOutrecPaths(prev, e);
    prev.joinWith = JoinWith.Right;
    e.joinWith = JoinWith.Left;
  }

  private void checkJoinRight(Active e, Point64 pt, boolean checkCurrX) {
    Active next = e.nextInAEL;
    if (next == null || !isHotEdge(e) || !isHotEdge(next)
        || isHorizontal(e) || isHorizontal(next) || isOpen(e) || isOpen(next)) return;
    if ((pt.y < e.top.y + 2 || pt.y < next.top.y + 2)
        && (e.bot.y > pt.y || next.bot.y > pt.y)) return;

    if (checkCurrX) {
      if (perpendicDistFromLineSqrd(pt, next.bot, next.top) > 0.25) return;
    } else if (e.curX != next.curX) return;
    if (!InternalClipper.isCollinear(e.top, pt, next.top)) return;

    if (e.outrec.idx == next.outrec.idx) addLocalMaxPoly(e, next, pt);
    else if (e.outrec.idx < next.outrec.idx) joinOutrecPaths(e, next);
    else joinOutrecPaths(next, e);
    e.joinWith = JoinWith.Right;
    next.joinWith = JoinWith.Left;
  }

  private static void fixOutRecPts(OutRec outrec) {
    OutPt op = outrec.pts;
    do {
      op.outrec = outrec;
      op = op.next;
    } while (op != outrec.pts);
  }

  private static boolean setHorzSegHeadingForward(HorzSegment hs, OutPt opP, OutPt opN) {
    if (opP.pt.x == opN.pt.x) return false;
    if (opP.pt.x < opN.pt.x) {
      hs.leftOp = opP; hs.rightOp = opN; hs.leftToRight = true;
    } else {
      hs.leftOp = opN; hs.rightOp = opP; hs.leftToRight = false;
    }
    return true;
  }

  private static boolean updateHorzSegment(HorzSegment hs) {
    OutPt op = hs.leftOp;
    OutRec outrec = getRealOutRec(op.outrec);
    boolean outrecHasEdges = outrec.frontEdge != null;
    long curr_y = op.pt.y;
    OutPt opP = op, opN = op;
    if (outrecHasEdges) {
      OutPt opA = outrec.pts, opZ = opA.next;
      while (opP != opZ && opP.prev.pt.y == curr_y) opP = opP.prev;
      while (opN != opA && opN.next.pt.y == curr_y) opN = opN.next;
    } else {
      while (opP.prev != opN && opP.prev.pt.y == curr_y) opP = opP.prev;
      while (opN.next != opP && opN.next.pt.y == curr_y) opN = opN.next;
    }
    boolean result = setHorzSegHeadingForward(hs, opP, opN) && hs.leftOp.horz == null;
    if (result) hs.leftOp.horz = hs;
    else hs.rightOp = null;
    return result;
  }

  private OutPt duplicateOp(OutPt op, boolean insertAfter) {
    OutPt result = newOutPt(op.pt, op.outrec);
    if (insertAfter) {
      result.next = op.next;
      result.next.prev = result;
      result.prev = op;
      op.next = result;
    } else {
      result.prev = op.prev;
      result.prev.next = result;
      result.next = op;
      op.prev = result;
    }
    return result;
  }

  private static int horzSegSort(HorzSegment hs1, HorzSegment hs2) {
    if (hs1 == null || hs2 == null) return 0;
    if (hs1.rightOp == null) return hs2.rightOp == null ? 0 : 1;
    if (hs2.rightOp == null) return -1;
    return Long.compare(hs1.leftOp.pt.x, hs2.leftOp.pt.x);
  }

  private void convertHorzSegsToJoins() {
    int k = 0;
    for (HorzSegment hs : _horzSegList) if (updateHorzSegment(hs)) k++;
    if (k < 2) return;
    _horzSegList.sort(ClipperBase::horzSegSort);

    for (int i = 0; i < k - 1; i++) {
      HorzSegment hs1 = _horzSegList.get(i);
      for (int j = i + 1; j < k; j++) {
        HorzSegment hs2 = _horzSegList.get(j);
        if (hs2.leftOp.pt.x >= hs1.rightOp.pt.x
            || hs2.leftToRight == hs1.leftToRight
            || hs2.rightOp.pt.x <= hs1.leftOp.pt.x) continue;
        long curr_y = hs1.leftOp.pt.y;
        if (hs1.leftToRight) {
          while (hs1.leftOp.next.pt.y == curr_y
              && hs1.leftOp.next.pt.x <= hs2.leftOp.pt.x)
            hs1.leftOp = hs1.leftOp.next;
          while (hs2.leftOp.prev.pt.y == curr_y
              && hs2.leftOp.prev.pt.x <= hs1.leftOp.pt.x)
            hs2.leftOp = hs2.leftOp.prev;
          _horzJoinList.add(new HorzJoin(
              duplicateOp(hs1.leftOp, true),
              duplicateOp(hs2.leftOp, false)));
        } else {
          while (hs1.leftOp.prev.pt.y == curr_y
              && hs1.leftOp.prev.pt.x <= hs2.leftOp.pt.x)
            hs1.leftOp = hs1.leftOp.prev;
          while (hs2.leftOp.next.pt.y == curr_y
              && hs2.leftOp.next.pt.x <= hs1.leftOp.pt.x)
            hs2.leftOp = hs2.leftOp.next;
          _horzJoinList.add(new HorzJoin(
              duplicateOp(hs2.leftOp, true),
              duplicateOp(hs1.leftOp, false)));
        }
      }
    }
  }

  private static Path64 getCleanPath(OutPt op) {
    Path64 result = new Path64();
    OutPt op2 = op;
    while (op2.next != op
        && ((op2.pt.x == op2.next.pt.x && op2.pt.x == op2.prev.pt.x)
            || (op2.pt.y == op2.next.pt.y && op2.pt.y == op2.prev.pt.y)))
      op2 = op2.next;
    result.add(op2.pt);
    OutPt prevOp = op2;
    op2 = op2.next;
    while (op2 != op) {
      if ((op2.pt.x != op2.next.pt.x || op2.pt.x != prevOp.pt.x)
          && (op2.pt.y != op2.next.pt.y || op2.pt.y != prevOp.pt.y)) {
        result.add(op2.pt);
        prevOp = op2;
      }
      op2 = op2.next;
    }
    return result;
  }

  private static PointInPolygonResult pointInOpPolygon(Point64 pt, OutPt op) {
    if (op == op.next || op.prev == op.next) return PointInPolygonResult.IsOutside;
    OutPt op2 = op;
    do {
      if (op.pt.y != pt.y) break;
      op = op.next;
    } while (op != op2);
    if (op.pt.y == pt.y) return PointInPolygonResult.IsOutside;

    boolean isAbove = op.pt.y < pt.y, startingAbove = isAbove;
    int val = 0;
    op2 = op.next;
    while (op2 != op) {
      if (isAbove) while (op2 != op && op2.pt.y < pt.y) op2 = op2.next;
      else while (op2 != op && op2.pt.y > pt.y) op2 = op2.next;
      if (op2 == op) break;

      if (op2.pt.y == pt.y) {
        if (op2.pt.x == pt.x || (op2.pt.y == op2.prev.pt.y
            && (pt.x < op2.prev.pt.x) != (pt.x < op2.pt.x)))
          return PointInPolygonResult.IsOn;
        op2 = op2.next;
        if (op2 == op) break;
        continue;
      }

      if (op2.pt.x <= pt.x || op2.prev.pt.x <= pt.x) {
        if (op2.prev.pt.x < pt.x && op2.pt.x < pt.x) {
          val = 1 - val;
        } else {
          int d = InternalClipper.crossProductSign(op2.prev.pt, op2.pt, pt);
          if (d == 0) return PointInPolygonResult.IsOn;
          if ((d < 0) == isAbove) val = 1 - val;
        }
      }
      isAbove = !isAbove;
      op2 = op2.next;
    }

    if (isAbove == startingAbove)
      return val == 0 ? PointInPolygonResult.IsOutside : PointInPolygonResult.IsInside;
    int d = InternalClipper.crossProductSign(op2.prev.pt, op2.pt, pt);
    if (d == 0) return PointInPolygonResult.IsOn;
    if ((d < 0) == isAbove) val = 1 - val;
    return val == 0 ? PointInPolygonResult.IsOutside : PointInPolygonResult.IsInside;
  }

  private static boolean path1InsidePath2(OutPt op1, OutPt op2) {
    PointInPolygonResult pip = PointInPolygonResult.IsOn;
    OutPt op = op1;
    do {
      PointInPolygonResult r = pointInOpPolygon(op.pt, op2);
      if (r == PointInPolygonResult.IsOutside) {
        if (pip == PointInPolygonResult.IsOutside) return false;
        pip = PointInPolygonResult.IsOutside;
      } else if (r == PointInPolygonResult.IsInside) {
        if (pip == PointInPolygonResult.IsInside) return true;
        pip = PointInPolygonResult.IsInside;
      }
      op = op.next;
    } while (op != op1);
    return InternalClipper.path2ContainsPath1(getCleanPath(op1), getCleanPath(op2));
  }

  private static void moveSplits(OutRec fromOr, OutRec toOr) {
    if (fromOr.splits == null) return;
    if (toOr.splits == null) toOr.splits = new ArrayList<>();
    for (int i : fromOr.splits)
      if (i != toOr.idx) toOr.splits.add(i);
    fromOr.splits = null;
  }

  private void processHorzJoins() {
    for (HorzJoin j : _horzJoinList) {
      OutRec or1 = getRealOutRec(j.op1.outrec);
      OutRec or2 = getRealOutRec(j.op2.outrec);

      OutPt op1b = j.op1.next;
      OutPt op2b = j.op2.prev;
      j.op1.next = j.op2;
      j.op2.prev = j.op1;
      op1b.prev = op2b;
      op2b.next = op1b;

      if (or1 == or2) {
        or2 = newOutRec();
        or2.pts = op1b;
        fixOutRecPts(or2);
        if (or1.pts.outrec == or2) {
          or1.pts = j.op1;
          or1.pts.outrec = or1;
        }
        if (_using_polytree) {
          if (path1InsidePath2(or1.pts, or2.pts)) {
            OutPt tmp = or2.pts; or2.pts = or1.pts; or1.pts = tmp;
            fixOutRecPts(or1);
            fixOutRecPts(or2);
            or2.owner = or1;
          } else if (path1InsidePath2(or2.pts, or1.pts)) {
            or2.owner = or1;
          } else {
            or2.owner = or1.owner;
          }
          if (or1.splits == null) or1.splits = new ArrayList<>();
          or1.splits.add(or2.idx);
        } else {
          or2.owner = or1;
        }
      } else {
        or2.pts = null;
        if (_using_polytree) {
          setOwner(or2, or1);
          moveSplits(or2, or1);
        } else {
          or2.owner = or1;
        }
      }
    }
  }

  private static boolean ptsReallyClose(Point64 pt1, Point64 pt2) {
    return Math.abs(pt1.x - pt2.x) < 2 && Math.abs(pt1.y - pt2.y) < 2;
  }

  private static boolean isVerySmallTriangle(OutPt op) {
    return op.next.next == op.prev
        && (ptsReallyClose(op.prev.pt, op.next.pt)
            || ptsReallyClose(op.pt, op.next.pt)
            || ptsReallyClose(op.pt, op.prev.pt));
  }

  private static boolean isValidClosedPath(OutPt op) {
    return op != null && op.next != op
        && (op.next != op.prev || !isVerySmallTriangle(op));
  }

  private static OutPt disposeOutPt(OutPt op) {
    OutPt result = (op.next == op ? null : op.next);
    op.prev.next = op.next;
    op.next.prev = op.prev;
    return result;
  }

  private void cleanCollinear(OutRec outrec) {
    outrec = getRealOutRec(outrec);
    if (outrec == null || outrec.isOpen) return;
    if (!isValidClosedPath(outrec.pts)) { outrec.pts = null; return; }

    OutPt startOp = outrec.pts;
    OutPt op2 = startOp;
    for (;;) {
      if (InternalClipper.isCollinear(op2.prev.pt, op2.pt, op2.next.pt)
          && (op2.pt.equals(op2.prev.pt) || op2.pt.equals(op2.next.pt) || !preserveCollinear
              || InternalClipper.dotProduct(op2.prev.pt, op2.pt, op2.next.pt) < 0)) {
        if (op2 == outrec.pts) outrec.pts = op2.prev;
        op2 = disposeOutPt(op2);
        if (!isValidClosedPath(op2)) { outrec.pts = null; return; }
        startOp = op2;
        continue;
      }
      op2 = op2.next;
      if (op2 == startOp) break;
    }
    fixSelfIntersects(outrec);
  }

  private void doSplitOp(OutRec outrec, OutPt splitOp) {
    OutPt prevOp = splitOp.prev;
    OutPt nextNextOp = splitOp.next.next;
    outrec.pts = prevOp;

    Point64 ip = InternalClipper.getLineIntersectPt(
        prevOp.pt, splitOp.pt, splitOp.next.pt, nextNextOp.pt);
    if (ip == null) ip = new Point64();

    double area1 = areaOfOutPtList(prevOp);
    double absArea1 = Math.abs(area1);
    if (absArea1 < 2) { outrec.pts = null; return; }

    double area2 = areaTriangle(ip, splitOp.pt, splitOp.next.pt);
    double absArea2 = Math.abs(area2);

    if (ip.equals(prevOp.pt) || ip.equals(nextNextOp.pt)) {
      nextNextOp.prev = prevOp;
      prevOp.next = nextNextOp;
    } else {
      OutPt newOp2 = newOutPt(ip, outrec);
      newOp2.prev = prevOp;
      newOp2.next = nextNextOp;
      nextNextOp.prev = newOp2;
      prevOp.next = newOp2;
    }

    if (!(absArea2 > 1)
        || (!(absArea2 > absArea1) && (area2 > 0) != (area1 > 0))) return;

    OutRec newOutRec = newOutRec();
    newOutRec.owner = outrec.owner;
    splitOp.outrec = newOutRec;
    splitOp.next.outrec = newOutRec;
    OutPt newOp = newOutPt(ip, newOutRec);
    newOp.prev = splitOp.next;
    newOp.next = splitOp;
    newOutRec.pts = newOp;
    splitOp.prev = newOp;
    splitOp.next.next = newOp;

    if (!_using_polytree) return;
    if (path1InsidePath2(prevOp, newOp)) {
      if (newOutRec.splits == null) newOutRec.splits = new ArrayList<>();
      newOutRec.splits.add(outrec.idx);
    } else {
      if (outrec.splits == null) outrec.splits = new ArrayList<>();
      outrec.splits.add(newOutRec.idx);
    }
  }

  private void fixSelfIntersects(OutRec outrec) {
    OutPt op2 = outrec.pts;
    if (op2.prev == op2.next.next) return;
    for (;;) {
      if (InternalClipper.segsIntersect(op2.prev.pt, op2.pt, op2.next.pt, op2.next.next.pt, false)) {
        if (op2 == outrec.pts || op2.next == outrec.pts)
          outrec.pts = outrec.pts.prev;
        doSplitOp(outrec, op2);
        if (outrec.pts == null) return;
        op2 = outrec.pts;
        if (op2.prev == op2.next.next) break;
        continue;
      }
      op2 = op2.next;
      if (op2 == outrec.pts) break;
    }
  }

  static boolean buildPath(OutPt op, boolean reverse, boolean isOpen, Path64 path) {
    if (op == null || op.next == op || (!isOpen && op.next == op.prev)) return false;
    path.clear();

    Point64 lastPt;
    OutPt op2;
    if (reverse) {
      lastPt = op.pt;
      op2 = op.prev;
    } else {
      op = op.next;
      lastPt = op.pt;
      op2 = op.next;
    }
    path.add(lastPt);
    while (op2 != op) {
      if (!op2.pt.equals(lastPt)) {
        lastPt = op2.pt;
        path.add(lastPt);
      }
      op2 = reverse ? op2.prev : op2.next;
    }
    return path.size() != 3 || isOpen || !isVerySmallTriangle(op2);
  }

  protected boolean buildPaths(Paths64 solutionClosed, Paths64 solutionOpen) {
    solutionClosed.clear();
    solutionOpen.clear();
    int i = 0;
    while (i < _outrecList.size()) {
      OutRec outrec = _outrecList.get(i++);
      if (outrec.pts == null) continue;
      Path64 path = new Path64(outrec.outPtCount);
      if (outrec.isOpen) {
        if (buildPath(outrec.pts, reverseSolution, true, path))
          solutionOpen.add(path);
      } else {
        cleanCollinear(outrec);
        if (buildPath(outrec.pts, reverseSolution, false, path))
          solutionClosed.add(path);
      }
    }
    return true;
  }

  private boolean checkBounds(OutRec outrec) {
    if (outrec.pts == null) return false;
    if (!outrec.bounds.isEmpty()) return true;
    cleanCollinear(outrec);
    if (outrec.pts == null
        || !buildPath(outrec.pts, reverseSolution, false, outrec.path))
      return false;
    outrec.bounds = InternalClipper.getBounds(outrec.path);
    return true;
  }

  private boolean checkSplitOwner(OutRec outrec, List<Integer> splits) {
    for (int i = 0; i < splits.size(); i++) {
      OutRec split = _outrecList.get(splits.get(i));
      if (split.pts == null && split.splits != null
          && checkSplitOwner(outrec, split.splits)) return true;
      split = getRealOutRec(split);
      if (split == null || split == outrec || split.recursiveSplit == outrec) continue;
      split.recursiveSplit = outrec;
      if (split.splits != null && checkSplitOwner(outrec, split.splits)) return true;
      if (!checkBounds(split) || !split.bounds.contains(outrec.bounds)
          || !path1InsidePath2(outrec.pts, split.pts)) continue;
      if (!isValidOwner(outrec, split)) split.owner = outrec.owner;
      outrec.owner = split;
      return true;
    }
    return false;
  }

  private void recursiveCheckOwners(OutRec outrec, PolyPathBase polypath) {
    if (outrec.polypath != null || outrec.bounds.isEmpty()) return;
    while (outrec.owner != null) {
      if (outrec.owner.splits != null
          && checkSplitOwner(outrec, outrec.owner.splits)) break;
      if (outrec.owner.pts != null && checkBounds(outrec.owner)
          && path1InsidePath2(outrec.pts, outrec.owner.pts)) break;
      outrec.owner = outrec.owner.owner;
    }
    if (outrec.owner != null) {
      if (outrec.owner.polypath == null)
        recursiveCheckOwners(outrec.owner, polypath);
      outrec.polypath = outrec.owner.polypath.addChild(outrec.path);
    } else {
      outrec.polypath = polypath.addChild(outrec.path);
    }
  }

  protected void buildTree(PolyPathBase polytree, Paths64 solutionOpen) {
    polytree.clear();
    solutionOpen.clear();
    int i = 0;
    while (i < _outrecList.size()) {
      OutRec outrec = _outrecList.get(i++);
      if (outrec.pts == null) continue;
      if (outrec.isOpen) {
        Path64 open_path = new Path64(outrec.outPtCount);
        if (buildPath(outrec.pts, reverseSolution, true, open_path))
          solutionOpen.add(open_path);
        continue;
      }
      if (checkBounds(outrec)) recursiveCheckOwners(outrec, polytree);
    }
  }

  public Rect64 getBounds() {
    Rect64 bounds = new Rect64(false); // invalid
    for (Vertex t : _vertexList) {
      Vertex v = t;
      do {
        if (v.pt.x < bounds.left) bounds.left = v.pt.x;
        if (v.pt.x > bounds.right) bounds.right = v.pt.x;
        if (v.pt.y < bounds.top) bounds.top = v.pt.y;
        if (v.pt.y > bounds.bottom) bounds.bottom = v.pt.y;
        v = v.next;
      } while (v != t);
    }
    return bounds.isEmpty() ? new Rect64(0, 0, 0, 0) : bounds;
  }

  // -----------------------------------------------------------------------
  // Perpendicular distance helper (used by CheckJoin*)
  // -----------------------------------------------------------------------

  static double perpendicDistFromLineSqrd(Point64 pt, Point64 line1, Point64 line2) {
    double a = (double) pt.x - line1.x;
    double b = (double) pt.y - line1.y;
    double c = (double) line2.x - line1.x;
    double d = (double) line2.y - line1.y;
    if (c == 0 && d == 0) return 0;
    double val = a * d - c * b;
    return val * val / (c * c + d * d);
  }
}
