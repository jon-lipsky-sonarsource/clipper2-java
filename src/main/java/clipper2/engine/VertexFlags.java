/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

/**
 * Flags for Vertex. Translated from C# [Flags] enum VertexFlags.
 * Use bitwise integer constants rather than a Java enum so that | and & work naturally.
 */
final class VertexFlags {
  static final int None      = 0;
  static final int OpenStart = 1;
  static final int OpenEnd   = 2;
  static final int LocalMax  = 4;
  static final int LocalMin  = 8;

  private VertexFlags() {}
}
