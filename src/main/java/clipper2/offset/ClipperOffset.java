/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  11 October 2025                                                 *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Path Offset (Inflate/Shrink)                                    *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.offset;

import clipper2.Clipper;
import clipper2.core.*;
import clipper2.engine.Clipper64;
import clipper2.engine.PolyTree64;

import java.util.ArrayList;
import java.util.List;

public class ClipperOffset {

  @FunctionalInterface
  public interface DeltaCallback64 {
    double invoke(Path64 path, PathD pathNorms, int currPt, int prevPt);
  }

  // ---- Group ----

  private static class Group {
    Paths64 inPaths;
    JoinType joinType;
    EndType endType;
    boolean pathsReversed;
    int lowestPathIdx;

    Group(Paths64 paths, JoinType joinType, EndType endType) {
      this.joinType = joinType;
      this.endType = endType;

      boolean isJoined = (endType == EndType.Polygon || endType == EndType.Joined);
      inPaths = new Paths64(paths.size());
      for (Path64 path : paths) {
        inPaths.add(Clipper.stripDuplicates(path, isJoined));
      }

      if (endType == EndType.Polygon) {
        int[] idxOut = {-1};
        boolean[] isNegAreaOut = {false};
        getLowestPathInfo(inPaths, idxOut, isNegAreaOut);
        lowestPathIdx = idxOut[0];
        pathsReversed = (lowestPathIdx >= 0) && isNegAreaOut[0];
      } else {
        lowestPathIdx = -1;
        pathsReversed = false;
      }
    }
  }

  // ---- Constants ----

  private static final double TOLERANCE = 1.0E-12;
  private static final double ARC_CONST = 0.002; // 1/500

  // ---- Fields ----

  private final List<Group> groupList = new ArrayList<>();
  private Path64 pathOut = new Path64();
  private final PathD normals = new PathD();
  private Paths64 solution = new Paths64();
  private PolyTree64 solutionTree = null;

  private double groupDelta;
  private double delta;
  private double mitLimSqr;
  private double stepsPerRad;
  private double stepSin;
  private double stepCos;
  private JoinType joinType;
  private EndType endType;

  public double arcTolerance = 0.0;
  public boolean mergeGroups = true;
  public double miterLimit = 2.0;
  public boolean preserveCollinear = false;
  public boolean reverseSolution = false;
  public DeltaCallback64 deltaCallback = null;

  // ---- Constructors ----

  public ClipperOffset() {}

  public ClipperOffset(double miterLimit, double arcTolerance,
      boolean preserveCollinear, boolean reverseSolution) {
    this.miterLimit = miterLimit;
    this.arcTolerance = arcTolerance;
    this.preserveCollinear = preserveCollinear;
    this.reverseSolution = reverseSolution;
  }

  // ---- Public API ----

  public void clear() {
    groupList.clear();
  }

  public void addPath(Path64 path, JoinType joinType, EndType endType) {
    if (path.isEmpty()) return;
    Paths64 pp = new Paths64(1);
    pp.add(path);
    addPaths(pp, joinType, endType);
  }

  public void addPaths(Paths64 paths, JoinType joinType, EndType endType) {
    if (paths.isEmpty()) return;
    groupList.add(new Group(paths, joinType, endType));
  }

  public void execute(double delta, Paths64 solution) {
    solution.clear();
    this.solution = solution;
    executeInternal(delta);
  }

  public void execute(double delta, PolyTree64 solutionTree) {
    solutionTree.clear();
    this.solutionTree = solutionTree;
    this.solution.clear();
    executeInternal(delta);
  }

  public void execute(DeltaCallback64 deltaCallback, Paths64 solution) {
    this.deltaCallback = deltaCallback;
    execute(1.0, solution);
  }

  // ---- Static utility ----

  static PointD getUnitNormal(Point64 pt1, Point64 pt2) {
    double dx = pt2.x - pt1.x;
    double dy = pt2.y - pt1.y;
    if (dx == 0 && dy == 0) return new PointD();
    double f = 1.0 / Math.sqrt(dx * dx + dy * dy);
    return new PointD(dy * f, -dx * f);
  }

  static void getLowestPathInfo(Paths64 paths, int[] idxOut, boolean[] isNegAreaOut) {
    idxOut[0] = -1;
    isNegAreaOut[0] = false;
    Point64 botPt = new Point64(Long.MAX_VALUE, Long.MIN_VALUE);
    for (int i = 0; i < paths.size(); i++) {
      double a = Double.MAX_VALUE;
      for (Point64 pt : paths.get(i)) {
        if ((pt.y < botPt.y) || (pt.y == botPt.y && pt.x >= botPt.x)) continue;
        if (a == Double.MAX_VALUE) {
          a = Clipper.area(paths.get(i));
          if (a == 0) break;
          isNegAreaOut[0] = a < 0;
        }
        idxOut[0] = i;
        botPt = new Point64(pt.x, pt.y);
      }
    }
  }

  // ---- Private implementation ----

  private int calcSolutionCapacity() {
    int result = 0;
    for (Group g : groupList) {
      result += (g.endType == EndType.Joined) ? g.inPaths.size() * 2 : g.inPaths.size();
    }
    return result;
  }

  boolean checkPathsReversed() {
    for (Group g : groupList) {
      if (g.endType == EndType.Polygon) return g.pathsReversed;
    }
    return false;
  }

  private void executeInternal(double delta) {
    if (groupList.isEmpty()) return;

    if (Math.abs(delta) < 0.5) {
      for (Group group : groupList) {
        solution.addAll(group.inPaths);
      }
      return;
    }

    this.delta = delta;
    mitLimSqr = (miterLimit <= 1) ? 2.0 : 2.0 / Clipper.sqr(miterLimit);

    for (Group group : groupList) {
      doGroupOffset(group);
    }

    if (groupList.isEmpty()) return;

    boolean pathsReversed = checkPathsReversed();
    FillRule fillRule = pathsReversed ? FillRule.Negative : FillRule.Positive;

    Clipper64 c = new Clipper64();
    c.setPreserveCollinear(preserveCollinear);
    c.setReverseSolution(reverseSolution != pathsReversed);
    c.addSubject(solution);
    if (solutionTree != null) {
      c.execute(ClipType.Union, fillRule, solutionTree);
    } else {
      c.execute(ClipType.Union, fillRule, solution);
    }
  }

  private static PointD translatePoint(PointD pt, double dx, double dy) {
    return new PointD(pt.x + dx, pt.y + dy);
  }

  private static PointD reflectPoint(PointD pt, PointD pivot) {
    return new PointD(pivot.x + (pivot.x - pt.x), pivot.y + (pivot.y - pt.y));
  }

  private static boolean almostZero(double value) {
    return Math.abs(value) < 0.001;
  }

  private static double hypotenuse(double x, double y) {
    return Math.sqrt(x * x + y * y);
  }

  private static PointD normalizeVector(PointD vec) {
    double h = hypotenuse(vec.x, vec.y);
    if (almostZero(h)) return new PointD(0, 0);
    double inv = 1.0 / h;
    return new PointD(vec.x * inv, vec.y * inv);
  }

  private static PointD getAvgUnitVector(PointD vec1, PointD vec2) {
    return normalizeVector(new PointD(vec1.x + vec2.x, vec1.y + vec2.y));
  }

  private Point64 getPerpendic(Point64 pt, PointD norm) {
    return new Point64(pt.x + norm.x * groupDelta, pt.y + norm.y * groupDelta);
  }

  private PointD getPerpendicD(Point64 pt, PointD norm) {
    return new PointD(pt.x + norm.x * groupDelta, pt.y + norm.y * groupDelta);
  }

  private void doBevel(Path64 path, int j, int k) {
    Point64 pt1, pt2;
    if (j == k) {
      double absDelta = Math.abs(groupDelta);
      pt1 = new Point64(
          path.get(j).x - absDelta * normals.get(j).x,
          path.get(j).y - absDelta * normals.get(j).y);
      pt2 = new Point64(
          path.get(j).x + absDelta * normals.get(j).x,
          path.get(j).y + absDelta * normals.get(j).y);
    } else {
      pt1 = new Point64(
          path.get(j).x + groupDelta * normals.get(k).x,
          path.get(j).y + groupDelta * normals.get(k).y);
      pt2 = new Point64(
          path.get(j).x + groupDelta * normals.get(j).x,
          path.get(j).y + groupDelta * normals.get(j).y);
    }
    pathOut.add(pt1);
    pathOut.add(pt2);
  }

  private void doSquare(Path64 path, int j, int k) {
    PointD vec;
    if (j == k) {
      vec = new PointD(normals.get(j).y, -normals.get(j).x);
    } else {
      vec = getAvgUnitVector(
          new PointD(-normals.get(k).y, normals.get(k).x),
          new PointD(normals.get(j).y, -normals.get(j).x));
    }

    double absDelta = Math.abs(groupDelta);
    PointD ptQ = new PointD(path.get(j));
    ptQ = translatePoint(ptQ, absDelta * vec.x, absDelta * vec.y);

    PointD pt1 = translatePoint(ptQ, groupDelta * vec.y, groupDelta * -vec.x);
    PointD pt2 = translatePoint(ptQ, groupDelta * -vec.y, groupDelta * vec.x);
    PointD pt3 = getPerpendicD(path.get(k), normals.get(k));

    if (j == k) {
      PointD pt4 = new PointD(pt3.x + vec.x * groupDelta, pt3.y + vec.y * groupDelta);
      PointD pt = InternalClipper.getLineIntersectPtD(pt1, pt2, pt3, pt4);
      if (pt == null) pt = ptQ;
      pathOut.add(new Point64(reflectPoint(pt, ptQ)));
      pathOut.add(new Point64(pt));
    } else {
      PointD pt4 = getPerpendicD(path.get(j), normals.get(k));
      PointD pt = InternalClipper.getLineIntersectPtD(pt1, pt2, pt3, pt4);
      if (pt == null) pt = ptQ;
      pathOut.add(new Point64(pt));
      pathOut.add(new Point64(reflectPoint(pt, ptQ)));
    }
  }

  private void doMiter(Path64 path, int j, int k, double cosA) {
    double q = groupDelta / (cosA + 1);
    pathOut.add(new Point64(
        path.get(j).x + (normals.get(k).x + normals.get(j).x) * q,
        path.get(j).y + (normals.get(k).y + normals.get(j).y) * q));
  }

  private void doRound(Path64 path, int j, int k, double angle) {
    if (deltaCallback != null) {
      double absDelta = Math.abs(groupDelta);
      double arcTol = arcTolerance > 0.01 ? arcTolerance : absDelta * ARC_CONST;
      double stepsPer360 = Math.PI / Math.acos(1 - arcTol / absDelta);
      stepSin = Math.sin((2 * Math.PI) / stepsPer360);
      stepCos = Math.cos((2 * Math.PI) / stepsPer360);
      if (groupDelta < 0.0) stepSin = -stepSin;
      stepsPerRad = stepsPer360 / (2 * Math.PI);
    }

    Point64 pt = path.get(j);
    PointD offsetVec = new PointD(normals.get(k).x * groupDelta, normals.get(k).y * groupDelta);
    if (j == k) offsetVec.negate();

    pathOut.add(new Point64(pt.x + offsetVec.x, pt.y + offsetVec.y));
    int steps = (int) Math.ceil(stepsPerRad * Math.abs(angle));
    for (int i = 1; i < steps; i++) {
      offsetVec = new PointD(
          offsetVec.x * stepCos - stepSin * offsetVec.y,
          offsetVec.x * stepSin + offsetVec.y * stepCos);
      pathOut.add(new Point64(pt.x + offsetVec.x, pt.y + offsetVec.y));
    }
    pathOut.add(getPerpendic(pt, normals.get(j)));
  }

  private void buildNormals(Path64 path) {
    int cnt = path.size();
    normals.clear();
    if (cnt == 0) return;
    for (int i = 0; i < cnt - 1; i++) {
      normals.add(getUnitNormal(path.get(i), path.get(i + 1)));
    }
    normals.add(getUnitNormal(path.get(cnt - 1), path.get(0)));
  }

  // k is passed as int[] to simulate C# ref int
  private void offsetPoint(Group group, Path64 path, int j, int[] k) {
    if (path.get(j).equals(path.get(k[0]))) {
      k[0] = j;
      return;
    }

    double sinA = InternalClipper.crossProduct(normals.get(j), normals.get(k[0]));
    double cosA = InternalClipper.dotProduct(normals.get(j), normals.get(k[0]));
    if (sinA > 1.0) sinA = 1.0;
    else if (sinA < -1.0) sinA = -1.0;

    if (deltaCallback != null) {
      groupDelta = deltaCallback.invoke(path, normals, j, k[0]);
      if (group.pathsReversed) groupDelta = -groupDelta;
    }

    if (Math.abs(groupDelta) < TOLERANCE) {
      pathOut.add(path.get(j));
      k[0] = j;
      return;
    }

    if (cosA > -0.999 && (sinA * groupDelta < 0)) {
      // concave
      pathOut.add(getPerpendic(path.get(j), normals.get(k[0])));
      pathOut.add(new Point64(path.get(j)));
      pathOut.add(getPerpendic(path.get(j), normals.get(j)));
    } else if (cosA > 0.999 && joinType != JoinType.Round) {
      doMiter(path, j, k[0], cosA);
    } else {
      switch (joinType) {
        case Miter -> {
          if (cosA > mitLimSqr - 1) doMiter(path, j, k[0], cosA);
          else doSquare(path, j, k[0]);
        }
        case Round -> doRound(path, j, k[0], Math.atan2(sinA, cosA));
        case Bevel -> doBevel(path, j, k[0]);
        default -> doSquare(path, j, k[0]);
      }
    }
    k[0] = j;
  }

  private void offsetPolygon(Group group, Path64 path) {
    pathOut = new Path64();
    int cnt = path.size();
    int[] prev = {cnt - 1};
    for (int i = 0; i < cnt; i++) {
      offsetPoint(group, path, i, prev);
    }
    solution.add(pathOut);
  }

  private void offsetOpenJoined(Group group, Path64 path) {
    offsetPolygon(group, path);
    path = Clipper.reversePath(path);
    buildNormals(path);
    offsetPolygon(group, path);
  }

  private void offsetOpenPath(Group group, Path64 path) {
    pathOut = new Path64();
    int highI = path.size() - 1;

    if (deltaCallback != null) {
      groupDelta = deltaCallback.invoke(path, normals, 0, 0);
    }

    // start cap
    if (Math.abs(groupDelta) < TOLERANCE) {
      pathOut.add(path.get(0));
    } else {
      switch (endType) {
        case Butt -> doBevel(path, 0, 0);
        case Round -> doRound(path, 0, 0, Math.PI);
        default -> doSquare(path, 0, 0);
      }
    }

    // left side going forward
    int[] k = {0};
    for (int i = 1; i < highI; i++) {
      offsetPoint(group, path, i, k);
    }

    // reverse normals
    for (int i = highI; i > 0; i--) {
      normals.set(i, new PointD(-normals.get(i - 1).x, -normals.get(i - 1).y));
    }
    normals.set(0, normals.get(highI));

    if (deltaCallback != null) {
      groupDelta = deltaCallback.invoke(path, normals, highI, highI);
    }

    // end cap
    if (Math.abs(groupDelta) < TOLERANCE) {
      pathOut.add(path.get(highI));
    } else {
      switch (endType) {
        case Butt -> doBevel(path, highI, highI);
        case Round -> doRound(path, highI, highI, Math.PI);
        default -> doSquare(path, highI, highI);
      }
    }

    // left side going back
    k[0] = highI;
    for (int i = highI - 1; i > 0; i--) {
      offsetPoint(group, path, i, k);
    }

    solution.add(pathOut);
  }

  private void doGroupOffset(Group group) {
    if (group.endType == EndType.Polygon) {
      if (group.lowestPathIdx < 0) delta = Math.abs(delta);
      groupDelta = group.pathsReversed ? -delta : delta;
    } else {
      groupDelta = Math.abs(delta);
    }

    double absDelta = Math.abs(groupDelta);

    joinType = group.joinType;
    endType = group.endType;

    if (group.joinType == JoinType.Round || group.endType == EndType.Round) {
      double arcTol = arcTolerance > 0.01 ? arcTolerance : absDelta * ARC_CONST;
      double stepsPer360 = Math.PI / Math.acos(1 - arcTol / absDelta);
      stepSin = Math.sin((2 * Math.PI) / stepsPer360);
      stepCos = Math.cos((2 * Math.PI) / stepsPer360);
      if (groupDelta < 0.0) stepSin = -stepSin;
      stepsPerRad = stepsPer360 / (2 * Math.PI);
    }

    for (Path64 p : group.inPaths) {
      pathOut = new Path64();
      int cnt = p.size();

      if (cnt == 1) {
        Point64 pt = p.get(0);
        if (deltaCallback != null) {
          groupDelta = deltaCallback.invoke(p, normals, 0, 0);
          if (group.pathsReversed) groupDelta = -groupDelta;
          absDelta = Math.abs(groupDelta);
        }
        if (group.endType == EndType.Round) {
          int steps = (int) Math.ceil(stepsPerRad * 2 * Math.PI);
          pathOut = Clipper.ellipse(pt, absDelta, absDelta, steps);
        } else {
          int d = (int) Math.ceil(groupDelta);
          Rect64 r = new Rect64(pt.x - d, pt.y - d, pt.x + d, pt.y + d);
          pathOut = r.asPath();
        }
        solution.add(pathOut);
        continue;
      }

      EndType effectiveEndType = endType;
      if (cnt == 2 && group.endType == EndType.Joined) {
        effectiveEndType = (group.joinType == JoinType.Round) ? EndType.Round : EndType.Square;
      }

      buildNormals(p);
      switch (effectiveEndType) {
        case Polygon -> offsetPolygon(group, p);
        case Joined -> offsetOpenJoined(group, p);
        default -> offsetOpenPath(group, p);
      }
    }
  }

}
