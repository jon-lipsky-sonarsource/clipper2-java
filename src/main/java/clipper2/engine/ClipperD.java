/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.Clipper;
import clipper2.core.ClipType;
import clipper2.core.FillRule;
import clipper2.core.PathD;
import clipper2.core.PathType;
import clipper2.core.Paths64;
import clipper2.core.PathsD;

/**
 * ClipperD — floating-point coordinate polygon clipping engine.
 * Paths are scaled to integers internally (by the given decimal precision),
 * clipped, and then scaled back.
 */
public class ClipperD extends ClipperBase {

  private static final String PRECISION_RANGE_ERROR = "Error: Precision is out of range.";

  private final double _scale;
  private final double _invScale;

  public ClipperD() {
    this(2);
  }

  public ClipperD(int roundingDecimalPrecision) {
    if (roundingDecimalPrecision < -8 || roundingDecimalPrecision > 8)
      throw new RuntimeException(PRECISION_RANGE_ERROR);
    _scale = Math.pow(10, roundingDecimalPrecision);
    _invScale = 1.0 / _scale;
  }

  public void addPath(PathD path, PathType polytype) {
    addPath(path, polytype, false);
  }

  public void addPath(PathD path, PathType polytype, boolean isOpen) {
    super.addPath(Clipper.scalePath64(path, _scale), polytype, isOpen);
  }

  public void addPaths(PathsD paths, PathType polytype) {
    addPaths(paths, polytype, false);
  }

  public void addPaths(PathsD paths, PathType polytype, boolean isOpen) {
    super.addPaths(Clipper.scalePaths64(paths, _scale), polytype, isOpen);
  }

  public void addSubject(PathD path) {
    addPath(path, PathType.Subject);
  }

  public void addOpenSubject(PathD path) {
    addPath(path, PathType.Subject, true);
  }

  public void addClip(PathD path) {
    addPath(path, PathType.Clip);
  }

  public void addSubject(PathsD paths) {
    addPaths(paths, PathType.Subject);
  }

  public void addOpenSubject(PathsD paths) {
    addPaths(paths, PathType.Subject, true);
  }

  public void addClip(PathsD paths) {
    addPaths(paths, PathType.Clip);
  }

  public boolean execute(ClipType clipType, FillRule fillRule,
      PathsD solutionClosed, PathsD solutionOpen) {
    Paths64 solClosed64 = new Paths64(), solOpen64 = new Paths64();
    boolean success = true;
    solutionClosed.clear();
    solutionOpen.clear();
    try {
      executeInternal(clipType, fillRule);
      buildPaths(solClosed64, solOpen64);
    } catch (Exception e) {
      success = false;
    }
    clearSolutionOnly();
    if (!success) return false;

    for (clipper2.core.Path64 path : solClosed64)
      solutionClosed.add(Clipper.scalePathD(path, _invScale));
    for (clipper2.core.Path64 path : solOpen64)
      solutionOpen.add(Clipper.scalePathD(path, _invScale));
    return true;
  }

  public boolean execute(ClipType clipType, FillRule fillRule, PathsD solutionClosed) {
    return execute(clipType, fillRule, solutionClosed, new PathsD());
  }

  public boolean execute(ClipType clipType, FillRule fillRule,
      PolyTreeD polytree, PathsD openPaths) {
    polytree.clear();
    openPaths.clear();
    _using_polytree = true;
    ((PolyPathD) polytree).setScale(_scale);
    Paths64 oPaths = new Paths64();
    boolean success = true;
    try {
      executeInternal(clipType, fillRule);
      buildTree(polytree, oPaths);
    } catch (Exception e) {
      success = false;
    }
    clearSolutionOnly();
    if (!success) return false;
    if (oPaths.isEmpty()) return true;
    for (clipper2.core.Path64 path : oPaths)
      openPaths.add(Clipper.scalePathD(path, _invScale));
    return true;
  }

  public boolean execute(ClipType clipType, FillRule fillRule, PolyTreeD polytree) {
    return execute(clipType, fillRule, polytree, new PathsD());
  }
}
