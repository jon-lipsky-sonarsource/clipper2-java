/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.PathType;

/**
 * Translated from C# internal readonly struct LocalMinima.
 * Made a mutable class (struct semantics not needed here — identity by vertex reference).
 */
class LocalMinima {
  final Vertex vertex;
  final PathType polytype;
  final boolean isOpen;

  LocalMinima(Vertex vertex, PathType polytype, boolean isOpen) {
    this.vertex = vertex;
    this.polytype = polytype;
    this.isOpen = isOpen;
  }

  LocalMinima(Vertex vertex, PathType polytype) {
    this(vertex, polytype, false);
  }

  /** Default/empty constructor — creates a null-vertex sentinel. */
  LocalMinima() {
    this.vertex = null;
    this.polytype = PathType.Subject;
    this.isOpen = false;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof LocalMinima other) {
      return this.vertex == other.vertex; // reference equality
    }
    return false;
  }

  @Override
  public int hashCode() {
    return vertex == null ? 0 : System.identityHashCode(vertex);
  }
}
