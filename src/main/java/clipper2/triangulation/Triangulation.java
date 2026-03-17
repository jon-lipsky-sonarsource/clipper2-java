/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  13 December 2025                                                *
 * Release   :  BETA RELEASE                                                    *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Constrained Delaunay Triangulation                              *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.triangulation;

import clipper2.core.InternalClipper;
import clipper2.core.Path64;
import clipper2.core.PathD;
import clipper2.core.PathsD;
import clipper2.core.Paths64;
import clipper2.core.Point64;
import clipper2.core.PointD;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Triangulation {

  // -------------------------------------------------------------------------
  // Public result enum
  // -------------------------------------------------------------------------

  public enum TriangulateResult {
    success, fail, noPolygons, pathsIntersect
  }

  // -------------------------------------------------------------------------
  // Internal enums
  // -------------------------------------------------------------------------

  private enum EdgeKind {
    loose, ascend, descend // ascend & descend are 'fixed' edges
  }

  private enum IntersectKind {
    none, collinear, intersect
  }

  private enum EdgeContainsResult {
    neither, left, right
  }

  // -------------------------------------------------------------------------
  // Internal data classes
  // -------------------------------------------------------------------------

  private static class Vertex2 {
    Point64 pt;
    List<Edge> edges = new ArrayList<>();
    boolean innerLM = false;

    Vertex2(Point64 p64) {
      pt = p64;
    }
  }

  private static class Edge {
    Vertex2 vL;
    Vertex2 vR;
    Vertex2 vB;
    Vertex2 vT;
    EdgeKind kind = EdgeKind.loose;
    Triangle triA = null;
    Triangle triB = null;
    boolean isActive = false;
    Edge nextE = null;
    Edge prevE = null;
  }

  private static class Triangle {
    Edge[] edges = new Edge[3];

    Triangle(Edge e1, Edge e2, Edge e3) {
      edges[0] = e1;
      edges[1] = e2;
      edges[2] = e3;
    }
  }

  // -------------------------------------------------------------------------
  // Delaunay class
  // -------------------------------------------------------------------------

  private static class Delaunay {

    private final List<Vertex2> allVertices = new ArrayList<>();
    private final List<Edge> allEdges = new ArrayList<>();
    private final List<Triangle> allTriangles = new ArrayList<>();
    private final Deque<Edge> pendingDelaunayStack = new ArrayDeque<>();
    private final Deque<Edge> horzEdgeStack = new ArrayDeque<>();
    private final Deque<Vertex2> locMinStack = new ArrayDeque<>();
    private final boolean useDelaunay;
    private Edge firstActive = null;
    private Vertex2 lowermostVertex = null;

    Delaunay(boolean delaunay) {
      useDelaunay = delaunay;
    }

    private void addPath(Path64 path) {
      int len = path.size();
      if (len == 0) return;

      int i0 = 0;
      int[] i0Ref = new int[]{i0};
      if (!findLocMinIdx(path, len, i0Ref)) return;
      i0 = i0Ref[0];

      int iPrev = prev(i0, len);
      while (path.get(iPrev).equals(path.get(i0)))
        iPrev = prev(iPrev, len);

      int iNext = next(i0, len);

      int i = i0;
      while (InternalClipper.crossProductSign(path.get(iPrev), path.get(i), path.get(iNext)) == 0) {
        int[] iRef = new int[]{i};
        findLocMinIdx(path, len, iRef);
        i = iRef[0];
        if (i == i0) return; // entirely collinear path
        iPrev = prev(i, len);
        while (path.get(iPrev).equals(path.get(i)))
          iPrev = prev(iPrev, len);
        iNext = next(i, len);
      }

      int vert_cnt = allVertices.size();
      Vertex2 v0 = new Vertex2(path.get(i));
      allVertices.add(v0);

      if (leftTurning(path.get(iPrev), path.get(i), path.get(iNext)))
        v0.innerLM = true;

      Vertex2 vPrev = v0;
      i = iNext;

      for (;;) {
        // vPrev is a locMin here
        locMinStack.push(vPrev);
        // update lowermostVertex ...
        if (lowermostVertex == null ||
            vPrev.pt.y > lowermostVertex.pt.y ||
            (vPrev.pt.y == lowermostVertex.pt.y &&
             vPrev.pt.x < lowermostVertex.pt.x)) {
          lowermostVertex = vPrev;
        }

        iNext = next(i, len);
        if (InternalClipper.crossProductSign(vPrev.pt, path.get(i), path.get(iNext)) == 0) {
          i = iNext;
          continue;
        }

        // ascend up next bound to LocMax
        while (path.get(i).y <= vPrev.pt.y) {
          Vertex2 v = new Vertex2(path.get(i));
          allVertices.add(v);
          createEdge(vPrev, v, EdgeKind.ascend);
          vPrev = v;
          i = iNext;
          iNext = next(i, len);

          while (InternalClipper.crossProductSign(vPrev.pt, path.get(i), path.get(iNext)) == 0) {
            i = iNext;
            iNext = next(i, len);
          }
        }

        // Now at a locMax, so descend to next locMin
        Vertex2 vPrevPrev = vPrev;
        while (i != i0 && path.get(i).y >= vPrev.pt.y) {
          Vertex2 v = new Vertex2(path.get(i));
          allVertices.add(v);
          createEdge(v, vPrev, EdgeKind.descend);
          vPrevPrev = vPrev;
          vPrev = v;
          i = iNext;
          iNext = next(i, len);

          while (InternalClipper.crossProductSign(vPrev.pt, path.get(i), path.get(iNext)) == 0) {
            i = iNext;
            iNext = next(i, len);
          }
        }

        // now at the next locMin
        if (i == i0) break;
        if (leftTurning(vPrevPrev.pt, vPrev.pt, path.get(i)))
          vPrev.innerLM = true;
      }

      createEdge(v0, vPrev, EdgeKind.descend);

      // finally, ignore this path if is not a polygon or too small
      len = allVertices.size() - vert_cnt;
      int idx = vert_cnt;
      if (len < 3 || (len == 3 &&
          ((distSqr(allVertices.get(idx).pt, allVertices.get(idx + 1).pt) <= 1) ||
           (distSqr(allVertices.get(idx + 1).pt, allVertices.get(idx + 2).pt) <= 1) ||
           (distSqr(allVertices.get(idx + 2).pt, allVertices.get(idx).pt) <= 1)))) {
        for (int j = vert_cnt; j < allVertices.size(); ++j)
          allVertices.get(j).edges.clear(); // flag to ignore
      }
    }

    private boolean addPaths(Paths64 paths) {
      int totalVertexCount = 0;
      for (Path64 path : paths)
        totalVertexCount += path.size();
      if (totalVertexCount == 0) return false;

      for (Path64 path : paths)
        addPath(path);

      return allVertices.size() > 2;
    }

    private void cleanUp() {
      allVertices.clear();
      allEdges.clear();
      allTriangles.clear();
      pendingDelaunayStack.clear();
      horzEdgeStack.clear();
      locMinStack.clear();
      firstActive = null;
      lowermostVertex = null;
    }

    private boolean fixupEdgeIntersects() {
      // precondition - edgeList must be sorted - ascending on edge.vL.pt.x

      for (int i1 = 0; i1 < allEdges.size(); ++i1) {
        Edge e1 = allEdges.get(i1);
        for (int i2 = i1 + 1; i2 < allEdges.size(); ++i2) {
          Edge e2 = allEdges.get(i2);
          if (e2.vL.pt.x >= e1.vR.pt.x)
            break;

          if (e2.vT.pt.y < e1.vB.pt.y && e2.vB.pt.y > e1.vT.pt.y &&
              segsIntersect(e2.vL.pt, e2.vR.pt, e1.vL.pt, e1.vR.pt) == IntersectKind.intersect) {
            if (!removeIntersection(e2, e1))
              return false;
          }
        }
      }
      return true;
    }

    private void mergeDupOrCollinearVertices() {
      if (allVertices.size() < 2) return;

      int v1Index = 0;
      for (int v2Index = 1; v2Index < allVertices.size(); ++v2Index) {
        Vertex2 v1 = allVertices.get(v1Index);
        Vertex2 v2 = allVertices.get(v2Index);

        if (!v1.pt.equals(v2.pt)) {
          v1Index = v2Index;
          continue;
        }

        // merge v1 & v2
        if (!v1.innerLM || !v2.innerLM)
          v1.innerLM = false;

        for (Edge e : v2.edges) {
          if (e.vB == v2) e.vB = v1; else e.vT = v1;
          if (e.vL == v2) e.vL = v1; else e.vR = v1;
        }

        v1.edges.addAll(v2.edges);
        v2.edges.clear();

        // excluding horizontals, if v1.edges contains two edges
        // that are collinear and share the same bottom coords
        // but have different lengths, split the longer edge at
        // the top of the shorter edge ...
        for (int iE = 0; iE < v1.edges.size(); ++iE) {
          Edge e1 = v1.edges.get(iE);
          if (isHorizontal(e1) || e1.vB != v1) continue;

          for (int iE2 = iE + 1; iE2 < v1.edges.size(); ++iE2) {
            Edge e2 = v1.edges.get(iE2);
            if (e2.vB != v1 || e1.vT.pt.y == e2.vT.pt.y ||
                InternalClipper.crossProductSign(e1.vT.pt, v1.pt, e2.vT.pt) != 0)
              continue;

            // parallel edges from v1 up
            if (e1.vT.pt.y < e2.vT.pt.y) splitEdge(e1, e2);
            else splitEdge(e2, e1);
            break; // only two can be collinear
          }
        }
      }
    }

    private void splitEdge(Edge longE, Edge shortE) {
      Vertex2 oldT = longE.vT;
      Vertex2 newT = shortE.vT;

      removeEdgeFromVertex(oldT, longE);

      longE.vT = newT;
      if (longE.vL == oldT) longE.vL = newT; else longE.vR = newT;

      newT.edges.add(longE);

      createEdge(newT, oldT, longE.kind);
    }

    private boolean removeIntersection(Edge e1, Edge e2) {
      Vertex2 v = e1.vL;
      Edge tmpE = e2;

      double d = shortestDistFromSegment(e1.vL.pt, e2.vL.pt, e2.vR.pt);
      double d2 = shortestDistFromSegment(e1.vR.pt, e2.vL.pt, e2.vR.pt);
      if (d2 < d) { d = d2; v = e1.vR; }

      d2 = shortestDistFromSegment(e2.vL.pt, e1.vL.pt, e1.vR.pt);
      if (d2 < d) { d = d2; tmpE = e1; v = e2.vL; }

      d2 = shortestDistFromSegment(e2.vR.pt, e1.vL.pt, e1.vR.pt);
      if (d2 < d) { d = d2; tmpE = e1; v = e2.vR; }

      if (d > 1.0)
        return false; // not a simple rounding intersection

      Vertex2 v2 = tmpE.vT;
      removeEdgeFromVertex(v2, tmpE);

      if (tmpE.vL == v2) tmpE.vL = v; else tmpE.vR = v;
      tmpE.vT = v;
      v.edges.add(tmpE);
      v.innerLM = false;

      if (tmpE.vB.innerLM && getLocMinAngle(tmpE.vB) <= 0)
        tmpE.vB.innerLM = false;

      createEdge(v, v2, tmpE.kind);
      return true;
    }

    private Edge createEdge(Vertex2 v1, Vertex2 v2, EdgeKind k) {
      Edge res = new Edge();
      allEdges.add(res);

      if (v1.pt.y == v2.pt.y) {
        res.vB = v1;
        res.vT = v2;
      } else if (v1.pt.y < v2.pt.y) {
        res.vB = v2;
        res.vT = v1;
      } else {
        res.vB = v1;
        res.vT = v2;
      }

      if (v1.pt.x <= v2.pt.x) {
        res.vL = v1;
        res.vR = v2;
      } else {
        res.vL = v2;
        res.vR = v1;
      }

      res.kind = k;
      v1.edges.add(res);
      v2.edges.add(res);

      if (k == EdgeKind.loose) {
        pendingDelaunayStack.push(res);
        addEdgeToActives(res);
      }

      return res;
    }

    private Triangle createTriangle(Edge e1, Edge e2, Edge e3) {
      Triangle tri = new Triangle(e1, e2, e3);
      allTriangles.add(tri);

      for (int i = 0; i < 3; ++i) {
        Edge e = tri.edges[i];
        if (e.triA != null) {
          e.triB = tri;
          removeEdgeFromActives(e);
        } else {
          e.triA = tri;
          if (!isLooseEdge(e))
            removeEdgeFromActives(e);
        }
      }
      return tri;
    }

    private void forceLegal(Edge edge) {
      if (edge.triA == null || edge.triB == null) return;

      Vertex2 vertA = null;
      Vertex2 vertB = null;

      Edge[] edgesA = new Edge[3];
      Edge[] edgesB = new Edge[3];

      for (int i = 0; i < 3; ++i) {
        if (edge.triA.edges[i] == edge) continue;
        Edge e = edge.triA.edges[i];
        switch (edgeContains(e, edge.vL)) {
          case left:
            edgesA[1] = e;
            vertA = e.vR;
            break;
          case right:
            edgesA[1] = e;
            vertA = e.vL;
            break;
          default:
            edgesB[1] = e;
            break;
        }
      }

      for (int i = 0; i < 3; ++i) {
        if (edge.triB.edges[i] == edge) continue;
        Edge e = edge.triB.edges[i];
        switch (edgeContains(e, edge.vL)) {
          case left:
            edgesA[2] = e;
            vertB = e.vR;
            break;
          case right:
            edgesA[2] = e;
            vertB = e.vL;
            break;
          default:
            edgesB[2] = e;
            break;
        }
      }

      if (vertA == null || vertB == null) return;

      if (InternalClipper.crossProductSign(vertA.pt, edge.vL.pt, edge.vR.pt) == 0)
        return;

      double ictResult = inCircleTest(vertA.pt, edge.vL.pt, edge.vR.pt, vertB.pt);
      if (ictResult == 0 ||
          (rightTurning(vertA.pt, edge.vL.pt, edge.vR.pt) == (ictResult < 0)))
        return;

      edge.vL = vertA;
      edge.vR = vertB;

      edge.triA.edges[0] = edge;
      for (int i = 1; i < 3; ++i) {
        Edge eAi = edgesA[i];
        edge.triA.edges[i] = eAi;
        if (isLooseEdge(eAi))
          pendingDelaunayStack.push(eAi);

        if (eAi.triA == edge.triA || eAi.triB == edge.triA) continue;

        if (eAi.triA == edge.triB)
          eAi.triA = edge.triA;
        else if (eAi.triB == edge.triB)
          eAi.triB = edge.triA;
        else
          throw new IllegalStateException("oops");
      }

      edge.triB.edges[0] = edge;
      for (int i = 1; i < 3; ++i) {
        Edge eBi = edgesB[i];
        edge.triB.edges[i] = eBi;
        if (isLooseEdge(eBi))
          pendingDelaunayStack.push(eBi);

        if (eBi.triA == edge.triB || eBi.triB == edge.triB) continue;

        if (eBi.triA == edge.triA)
          eBi.triA = edge.triB;
        else if (eBi.triB == edge.triA)
          eBi.triB = edge.triB;
        else
          throw new IllegalStateException("oops");
      }
    }

    private Edge createInnerLocMinLooseEdge(Vertex2 vAbove) {
      if (firstActive == null) return null;

      long xAbove = vAbove.pt.x;
      long yAbove = vAbove.pt.y;

      Edge e = firstActive;
      Edge eBelow = null;
      double bestD = -1.0;

      while (e != null) {
        if (e.vL.pt.x <= xAbove && e.vR.pt.x >= xAbove &&
            e.vB.pt.y >= yAbove && e.vB != vAbove && e.vT != vAbove &&
            !leftTurning(e.vL.pt, vAbove.pt, e.vR.pt)) {
          double d = shortestDistFromSegment(vAbove.pt, e.vL.pt, e.vR.pt);
          if (eBelow == null || d < bestD) {
            eBelow = e;
            bestD = d;
          }
        }
        e = e.nextE;
      }

      if (eBelow == null) return null;

      Vertex2 vBest = (eBelow.vT.pt.y <= yAbove) ? eBelow.vB : eBelow.vT;
      long xBest = vBest.pt.x;
      long yBest = vBest.pt.y;

      e = firstActive;
      if (xBest < xAbove) {
        while (e != null) {
          if (e.vR.pt.x > xBest && e.vL.pt.x < xAbove &&
              e.vB.pt.y > yAbove && e.vT.pt.y < yBest &&
              segsIntersect(e.vB.pt, e.vT.pt, vBest.pt, vAbove.pt) == IntersectKind.intersect) {
            vBest = (e.vT.pt.y > yAbove) ? e.vT : e.vB;
            xBest = vBest.pt.x;
            yBest = vBest.pt.y;
          }
          e = e.nextE;
        }
      } else {
        while (e != null) {
          if (e.vR.pt.x < xBest && e.vL.pt.x > xAbove &&
              e.vB.pt.y > yAbove && e.vT.pt.y < yBest &&
              segsIntersect(e.vB.pt, e.vT.pt, vBest.pt, vAbove.pt) == IntersectKind.intersect) {
            vBest = (e.vT.pt.y > yAbove) ? e.vT : e.vB;
            xBest = vBest.pt.x;
            yBest = vBest.pt.y;
          }
          e = e.nextE;
        }
      }

      return createEdge(vBest, vAbove, EdgeKind.loose);
    }

    private Edge horizontalBetween(Vertex2 v1, Vertex2 v2) {
      long y = v1.pt.y;
      long l, r;

      if (v1.pt.x > v2.pt.x) {
        l = v2.pt.x;
        r = v1.pt.x;
      } else {
        l = v1.pt.x;
        r = v2.pt.x;
      }

      Edge res = firstActive;
      while (res != null) {
        if (res.vL.pt.y == y && res.vR.pt.y == y &&
            res.vL.pt.x >= l && res.vR.pt.x <= r &&
            (res.vL.pt.x != l || res.vL.pt.x != r))
          break;
        res = res.nextE;
      }
      return res;
    }

    private void doTriangulateLeft(Edge edge, Vertex2 pivot, long minY) {
      Vertex2 vAlt = null;
      Edge eAlt = null;

      Vertex2 v = (edge.vB == pivot) ? edge.vT : edge.vB;

      for (Edge e : pivot.edges) {
        if (e == edge || !e.isActive) continue;

        Vertex2 vX = (e.vT == pivot) ? e.vB : e.vT;
        if (vX == v) continue;

        int cps = InternalClipper.crossProductSign(v.pt, pivot.pt, vX.pt);
        if (cps == 0) {
          if ((v.pt.x > pivot.pt.x) == (pivot.pt.x > vX.pt.x)) continue;
        } else if (cps > 0 || (vAlt != null && !leftTurning(vX.pt, pivot.pt, vAlt.pt)))
          continue;

        vAlt = vX;
        eAlt = e;
      }

      if (vAlt == null || vAlt.pt.y < minY || eAlt == null) return;

      if (vAlt.pt.y < pivot.pt.y) {
        if (isLeftEdge(eAlt)) return;
      } else if (vAlt.pt.y > pivot.pt.y) {
        if (isRightEdge(eAlt)) return;
      }

      Edge eX = findLinkingEdge(vAlt, v, (vAlt.pt.y < v.pt.y));
      if (eX == null) {
        if (vAlt.pt.y == v.pt.y && v.pt.y == minY &&
            horizontalBetween(vAlt, v) != null)
          return;

        eX = createEdge(vAlt, v, EdgeKind.loose);
      }

      createTriangle(edge, eAlt, eX);

      if (!edgeCompleted(eX))
        doTriangulateLeft(eX, vAlt, minY);
    }

    private void doTriangulateRight(Edge edge, Vertex2 pivot, long minY) {
      Vertex2 vAlt = null;
      Edge eAlt = null;

      Vertex2 v = (edge.vB == pivot) ? edge.vT : edge.vB;

      for (Edge e : pivot.edges) {
        if (e == edge || !e.isActive) continue;

        Vertex2 vX = (e.vT == pivot) ? e.vB : e.vT;
        if (vX == v) continue;

        int cps = InternalClipper.crossProductSign(v.pt, pivot.pt, vX.pt);
        if (cps == 0) {
          if ((v.pt.x > pivot.pt.x) == (pivot.pt.x > vX.pt.x)) continue;
        } else if (cps < 0 || (vAlt != null && !rightTurning(vX.pt, pivot.pt, vAlt.pt)))
          continue;

        vAlt = vX;
        eAlt = e;
      }

      if (vAlt == null || vAlt.pt.y < minY || eAlt == null) return;

      if (vAlt.pt.y < pivot.pt.y) {
        if (isRightEdge(eAlt)) return;
      } else if (vAlt.pt.y > pivot.pt.y) {
        if (isLeftEdge(eAlt)) return;
      }

      Edge eX = findLinkingEdge(vAlt, v, (vAlt.pt.y > v.pt.y));
      if (eX == null) {
        if (vAlt.pt.y == v.pt.y && v.pt.y == minY &&
            horizontalBetween(vAlt, v) != null)
          return;

        eX = createEdge(vAlt, v, EdgeKind.loose);
      }

      createTriangle(edge, eX, eAlt);

      if (!edgeCompleted(eX))
        doTriangulateRight(eX, vAlt, minY);
    }

    private void addEdgeToActives(Edge edge) {
      if (edge.isActive) return;

      edge.prevE = null;
      edge.nextE = firstActive;
      edge.isActive = true;

      if (firstActive != null)
        firstActive.prevE = edge;

      firstActive = edge;
    }

    private void removeEdgeFromActives(Edge edge) {
      removeEdgeFromVertex(edge.vB, edge);
      removeEdgeFromVertex(edge.vT, edge);

      Edge prev = edge.prevE;
      Edge next = edge.nextE;

      if (next != null) next.prevE = prev;
      if (prev != null) prev.nextE = next;

      edge.isActive = false;
      if (firstActive == edge) firstActive = next;
    }

    TriangulateResult execute(Paths64 paths, Paths64[] sol) {
      sol[0] = new Paths64();

      if (!addPaths(paths)) {
        return TriangulateResult.noPolygons;
      }

      // if necessary fix path orientation because the algorithm
      // expects clockwise outer paths and counter-clockwise inner paths
      if (lowermostVertex.innerLM) {
        // the orientation of added paths must be wrong, so
        // 1. reverse innerLM flags ...
        while (!locMinStack.isEmpty()) {
          Vertex2 lm = locMinStack.pop();
          lm.innerLM = !lm.innerLM;
        }
        // 2. swap edge kinds
        for (Edge e : allEdges) {
          if (e.kind == EdgeKind.ascend)
            e.kind = EdgeKind.descend;
          else if (e.kind == EdgeKind.descend)
            e.kind = EdgeKind.ascend;
        }
      } else {
        // path orientation is fine so ...
        locMinStack.clear();
      }

      allEdges.sort((a, b) -> Long.compare(a.vL.pt.x, b.vL.pt.x));

      if (!fixupEdgeIntersects()) {
        cleanUp();
        return TriangulateResult.pathsIntersect;
      }

      allVertices.sort((a, b) -> {
        if (a.pt.y == b.pt.y)
          return Long.compare(a.pt.x, b.pt.x);
        return Long.compare(b.pt.y, a.pt.y);
      });

      mergeDupOrCollinearVertices();

      long currY = allVertices.get(0).pt.y;

      for (Vertex2 v : allVertices) {
        if (v.edges.isEmpty()) continue;

        if (v.pt.y != currY) {
          while (!locMinStack.isEmpty()) {
            Vertex2 lm = locMinStack.pop();
            Edge e = createInnerLocMinLooseEdge(lm);
            if (e == null) {
              cleanUp();
              return TriangulateResult.fail;
            }

            if (isHorizontal(e)) {
              if (e.vL == e.vB)
                doTriangulateLeft(e, e.vB, currY);
              else
                doTriangulateRight(e, e.vB, currY);
            } else {
              doTriangulateLeft(e, e.vB, currY);
              if (!edgeCompleted(e))
                doTriangulateRight(e, e.vB, currY);
            }

            addEdgeToActives(lm.edges.get(0));
            addEdgeToActives(lm.edges.get(1));
          }

          while (!horzEdgeStack.isEmpty()) {
            Edge e = horzEdgeStack.pop();
            if (edgeCompleted(e)) continue;

            if (e.vB == e.vL) {
              if (isLeftEdge(e))
                doTriangulateLeft(e, e.vB, currY);
            } else {
              if (isRightEdge(e))
                doTriangulateRight(e, e.vB, currY);
            }
          }

          currY = v.pt.y;
        }

        for (int i = v.edges.size() - 1; i >= 0; --i) {
          if (i >= v.edges.size()) continue;

          Edge e = v.edges.get(i);
          if (edgeCompleted(e) || isLooseEdge(e)) continue;

          if (v == e.vB) {
            if (isHorizontal(e))
              horzEdgeStack.push(e);

            if (!v.innerLM)
              addEdgeToActives(e);
          } else {
            if (isHorizontal(e))
              horzEdgeStack.push(e);
            else if (isLeftEdge(e))
              doTriangulateLeft(e, e.vB, v.pt.y);
            else
              doTriangulateRight(e, e.vB, v.pt.y);
          }
        }

        if (v.innerLM)
          locMinStack.push(v);
      }

      while (!horzEdgeStack.isEmpty()) {
        Edge e = horzEdgeStack.pop();
        if (!edgeCompleted(e) && e.vB == e.vL)
          doTriangulateLeft(e, e.vB, currY);
      }

      if (useDelaunay) {
        while (!pendingDelaunayStack.isEmpty()) {
          Edge e = pendingDelaunayStack.pop();
          forceLegal(e);
        }
      }

      sol[0] = new Paths64(allTriangles.size());
      for (Triangle tri : allTriangles) {
        Path64 p = pathFromTriangle(tri);
        int cps = InternalClipper.crossProductSign(p.get(0), p.get(1), p.get(2));
        if (cps == 0) continue;
        if (cps < 0) java.util.Collections.reverse(p);
        sol[0].add(p);
      }

      cleanUp();
      return TriangulateResult.success;
    }

    // -----------------------------------------------------------------------
    // Static / helper methods
    // -----------------------------------------------------------------------

    private static boolean isLooseEdge(Edge e) {
      return e.kind == EdgeKind.loose;
    }

    private static boolean isLeftEdge(Edge e) {
      return e.kind == EdgeKind.ascend;
    }

    private static boolean isRightEdge(Edge e) {
      return e.kind == EdgeKind.descend;
    }

    private static boolean isHorizontal(Edge e) {
      return e.vB.pt.y == e.vT.pt.y;
    }

    private static boolean leftTurning(Point64 p1, Point64 p2, Point64 p3) {
      return InternalClipper.crossProductSign(p1, p2, p3) < 0;
    }

    private static boolean rightTurning(Point64 p1, Point64 p2, Point64 p3) {
      return InternalClipper.crossProductSign(p1, p2, p3) > 0;
    }

    private static boolean edgeCompleted(Edge edge) {
      if (edge.triA == null) return false;
      if (edge.triB != null) return true;
      return edge.kind != EdgeKind.loose;
    }

    private static EdgeContainsResult edgeContains(Edge edge, Vertex2 v) {
      if (edge.vL == v) return EdgeContainsResult.left;
      if (edge.vR == v) return EdgeContainsResult.right;
      return EdgeContainsResult.neither;
    }

    private static double getAngle(Point64 a, Point64 b, Point64 c) {
      double abx = (double) (b.x - a.x);
      double aby = (double) (b.y - a.y);
      double bcx = (double) (b.x - c.x);
      double bcy = (double) (b.y - c.y);
      double dp = abx * bcx + aby * bcy;
      double cp = abx * bcy - aby * bcx;
      return Math.atan2(cp, dp);
    }

    private static double getLocMinAngle(Vertex2 v) {
      int asc, des;
      if (v.edges.get(0).kind == EdgeKind.ascend) {
        asc = 0;
        des = 1;
      } else {
        des = 0;
        asc = 1;
      }
      return getAngle(v.edges.get(des).vT.pt, v.pt, v.edges.get(asc).vT.pt);
    }

    private static void removeEdgeFromVertex(Vertex2 vert, Edge edge) {
      int idx = vert.edges.indexOf(edge);
      if (idx < 0) throw new IllegalStateException("oops!");
      vert.edges.remove(idx);
    }

    private static boolean findLocMinIdx(Path64 path, int len, int[] idx) {
      if (len < 3) return false;
      int i0 = idx[0];
      int n = (idx[0] + 1) % len;

      while (path.get(n).y <= path.get(idx[0]).y) {
        idx[0] = n;
        n = (n + 1) % len;
        if (idx[0] == i0) return false;
      }

      while (path.get(n).y >= path.get(idx[0]).y) {
        idx[0] = n;
        n = (n + 1) % len;
      }

      return true;
    }

    private static int prev(int idx, int len) {
      if (idx == 0) return len - 1;
      return idx - 1;
    }

    private static int next(int idx, int len) {
      return (idx + 1) % len;
    }

    private static Edge findLinkingEdge(Vertex2 vert1, Vertex2 vert2, boolean preferAscending) {
      Edge res = null;
      for (Edge e : vert1.edges) {
        if (e.vL == vert2 || e.vR == vert2) {
          if (e.kind == EdgeKind.loose ||
              ((e.kind == EdgeKind.ascend) == preferAscending))
            return e;
          res = e;
        }
      }
      return res;
    }

    private static Path64 pathFromTriangle(Triangle tri) {
      Path64 res = new Path64(3);
      res.add(tri.edges[0].vL.pt);
      res.add(tri.edges[0].vR.pt);
      Edge e = tri.edges[1];
      if (e.vL.pt.equals(res.get(0)) || e.vL.pt.equals(res.get(1)))
        res.add(e.vR.pt);
      else
        res.add(e.vL.pt);
      return res;
    }

    private static double inCircleTest(Point64 ptA, Point64 ptB, Point64 ptC, Point64 ptD) {
      double m00 = (double) (ptA.x - ptD.x);
      double m01 = (double) (ptA.y - ptD.y);
      double m02 = sqr(m00) + sqr(m01);

      double m10 = (double) (ptB.x - ptD.x);
      double m11 = (double) (ptB.y - ptD.y);
      double m12 = sqr(m10) + sqr(m11);

      double m20 = (double) (ptC.x - ptD.x);
      double m21 = (double) (ptC.y - ptD.y);
      double m22 = sqr(m20) + sqr(m21);

      return m00 * (m11 * m22 - m21 * m12) -
             m10 * (m01 * m22 - m21 * m02) +
             m20 * (m01 * m12 - m11 * m02);
    }

    private static double shortestDistFromSegment(Point64 pt, Point64 segPt1, Point64 segPt2) {
      double dx = (double) (segPt2.x - segPt1.x);
      double dy = (double) (segPt2.y - segPt1.y);

      double ax = (double) (pt.x - segPt1.x);
      double ay = (double) (pt.y - segPt1.y);

      double qNum = ax * dx + ay * dy;
      double denom = sqr(dx) + sqr(dy);

      if (qNum < 0)
        return distanceSqr(pt, segPt1);
      if (qNum > denom)
        return distanceSqr(pt, segPt2);

      return sqr(ax * dy - dx * ay) / denom;
    }

    private static IntersectKind segsIntersect(Point64 s1a, Point64 s1b, Point64 s2a, Point64 s2b) {
      // ignore segments sharing an end-point
      if (s1a.equals(s2a) || s1a.equals(s2b) || s1b.equals(s2b)) return IntersectKind.none;

      double dy1 = (double) (s1b.y - s1a.y);
      double dx1 = (double) (s1b.x - s1a.x);
      double dy2 = (double) (s2b.y - s2a.y);
      double dx2 = (double) (s2b.x - s2a.x);

      double cp = dy1 * dx2 - dy2 * dx1;
      if (cp == 0) return IntersectKind.collinear;

      double t = ((double) (s1a.x - s2a.x) * dy2 -
                  (double) (s1a.y - s2a.y) * dx2);
      if (t >= 0) {
        if (cp < 0 || t >= cp) return IntersectKind.none;
      } else {
        if (cp > 0 || t <= cp) return IntersectKind.none;
      }

      t = ((double) (s1a.x - s2a.x) * dy1 -
           (double) (s1a.y - s2a.y) * dx1);
      if (t >= 0) {
        if (cp > 0 && t < cp) return IntersectKind.intersect;
      } else {
        if (cp < 0 && t > cp) return IntersectKind.intersect;
      }

      return IntersectKind.none;
    }

    private static double distSqr(Point64 pt1, Point64 pt2) {
      return sqr((double) (pt1.x - pt2.x)) + sqr((double) (pt1.y - pt2.y));
    }

    private static double sqr(double v) {
      return v * v;
    }

    private static double distanceSqr(Point64 a, Point64 b) {
      double dx = (double) (a.x - b.x);
      double dy = (double) (a.y - b.y);
      return dx * dx + dy * dy;
    }

  } // Delaunay

  // -------------------------------------------------------------------------
  // Public API — mirrors Clipper.Triangulate static methods in C#
  // -------------------------------------------------------------------------

  /**
   * Triangulates the supplied paths using Constrained Delaunay Triangulation.
   *
   * @param paths      input polygons (Paths64)
   * @param solution   output array of length 1; receives the triangulated result
   * @param useDelaunay if true, applies Delaunay legalization (default: true)
   * @return a TriangulateResult value indicating success or failure
   */
  public static TriangulateResult triangulate(Paths64 paths, Paths64[] solution, boolean useDelaunay) {
    Delaunay d = new Delaunay(useDelaunay);
    return d.execute(paths, solution);
  }

  /** Overload with useDelaunay defaulting to true. */
  public static TriangulateResult triangulate(Paths64 paths, Paths64[] solution) {
    return triangulate(paths, solution, true);
  }

  /**
   * Triangulates PathsD (floating-point) by scaling to integer space.
   *
   * @param paths      input polygons (PathsD)
   * @param decPlaces  number of decimal places to preserve via scaling
   * @param solution   output array of length 1; receives the triangulated PathsD result
   * @param useDelaunay if true, applies Delaunay legalization (default: true)
   * @return a TriangulateResult value indicating success or failure
   */
  public static TriangulateResult triangulate(PathsD paths, int decPlaces, PathsD[] solution, boolean useDelaunay) {
    double scale;
    if (decPlaces <= 0) scale = 1.0;
    else if (decPlaces > 8) scale = Math.pow(10.0, 8.0);
    else scale = Math.pow(10.0, decPlaces);

    Paths64 pp64 = scalePaths64(paths, scale);

    Delaunay d = new Delaunay(useDelaunay);
    Paths64[] sol64 = new Paths64[1];
    TriangulateResult result = d.execute(pp64, sol64);
    if (result == TriangulateResult.success)
      solution[0] = scalePathsD(sol64[0], 1.0 / scale);
    else
      solution[0] = new PathsD();
    return result;
  }

  /** Overload with useDelaunay defaulting to true. */
  public static TriangulateResult triangulate(PathsD paths, int decPlaces, PathsD[] solution) {
    return triangulate(paths, decPlaces, solution, true);
  }

  // -------------------------------------------------------------------------
  // Scaling helpers (equivalent to Clipper.ScalePaths64 / ScalePathsD in C#)
  // -------------------------------------------------------------------------

  private static Paths64 scalePaths64(PathsD paths, double scale) {
    Paths64 result = new Paths64(paths.size());
    for (PathD path : paths) {
      Path64 p = new Path64(path.size());
      for (PointD pt : path) {
        p.add(new Point64(
            InternalClipper.roundAwayFromZero(pt.x * scale),
            InternalClipper.roundAwayFromZero(pt.y * scale)));
      }
      result.add(p);
    }
    return result;
  }

  private static PathsD scalePathsD(Paths64 paths, double scale) {
    PathsD result = new PathsD(paths.size());
    for (Path64 path : paths) {
      PathD p = new PathD(path.size());
      for (Point64 pt : path) {
        p.add(new PointD(pt.x * scale, pt.y * scale));
      }
      result.add(p);
    }
    return result;
  }

}
