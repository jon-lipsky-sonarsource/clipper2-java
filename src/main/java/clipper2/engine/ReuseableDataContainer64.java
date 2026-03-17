/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * Purpose   :  This is the main polygon clipping module                        *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.PathType;
import clipper2.core.Paths64;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable data container — holds pre-processed vertex lists and minima lists
 * so that the same input geometry can be reused across multiple Execute() calls
 * without re-processing.
 *
 * <p>Use {@link #addPaths} to pre-process geometry, then pass this container
 * to {@link Clipper64#addReuseableData} for efficient repeated clipping.
 */
public class ReuseableDataContainer64 {
  final List<LocalMinima> _minimaList = new ArrayList<>();
  final List<Vertex> _vertexList = new ArrayList<>();

  public void clear() {
    _minimaList.clear();
    _vertexList.clear();
  }

  public void addPaths(Paths64 paths, PathType pt, boolean isOpen) {
    ClipperEngine.addPathsToVertexList(paths, pt, isOpen, _minimaList, _vertexList);
  }
}
