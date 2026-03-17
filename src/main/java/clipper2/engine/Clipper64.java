/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.ClipType;
import clipper2.core.FillRule;
import clipper2.core.Path64;
import clipper2.core.PathType;
import clipper2.core.Paths64;

/**
 * Clipper64 — integer-coordinate polygon clipping engine.
 */
public class Clipper64 extends ClipperBase {

  public void addPath(Path64 path, PathType polytype, boolean isOpen) {
    super.addPath(path, polytype, isOpen);
  }

  public void addPath(Path64 path, PathType polytype) {
    super.addPath(path, polytype, false);
  }

  @Override
  public void addReuseableData(ReuseableDataContainer64 reuseableData) {
    super.addReuseableData(reuseableData);
  }

  public void addPaths(Paths64 paths, PathType polytype, boolean isOpen) {
    super.addPaths(paths, polytype, isOpen);
  }

  public void addPaths(Paths64 paths, PathType polytype) {
    super.addPaths(paths, polytype, false);
  }

  public void addSubject(Paths64 paths) {
    addPaths(paths, PathType.Subject);
  }

  public void addOpenSubject(Paths64 paths) {
    addPaths(paths, PathType.Subject, true);
  }

  public void addClip(Paths64 paths) {
    addPaths(paths, PathType.Clip);
  }

  public boolean execute(ClipType clipType, FillRule fillRule,
      Paths64 solutionClosed, Paths64 solutionOpen) {
    solutionClosed.clear();
    solutionOpen.clear();
    try {
      executeInternal(clipType, fillRule);
      buildPaths(solutionClosed, solutionOpen);
    } catch (Exception e) {
      _succeeded = false;
    }
    clearSolutionOnly();
    return _succeeded;
  }

  public boolean execute(ClipType clipType, FillRule fillRule, Paths64 solutionClosed) {
    return execute(clipType, fillRule, solutionClosed, new Paths64());
  }

  public boolean execute(ClipType clipType, FillRule fillRule,
      PolyTree64 polytree, Paths64 openPaths) {
    polytree.clear();
    openPaths.clear();
    _using_polytree = true;
    try {
      executeInternal(clipType, fillRule);
      buildTree(polytree, openPaths);
    } catch (Exception e) {
      _succeeded = false;
    }
    clearSolutionOnly();
    return _succeeded;
  }

  public boolean execute(ClipType clipType, FillRule fillRule, PolyTree64 polytree) {
    return execute(clipType, fillRule, polytree, new Paths64());
  }
}
