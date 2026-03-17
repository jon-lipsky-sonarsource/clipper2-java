/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * Purpose   :  Core structures and functions for the Clipper Library           *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

import java.util.ArrayList;
import java.util.Collection;

public class Path64 extends ArrayList<Point64> {

  public Path64() {
    super();
  }

  public Path64(int capacity) {
    super(capacity);
  }

  public Path64(Collection<Point64> path) {
    super(path);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(get(i));
    }
    return sb.toString();
  }

}
