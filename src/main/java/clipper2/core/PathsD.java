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

public class PathsD extends ArrayList<PathD> {

  public PathsD() {
    super();
  }

  public PathsD(int capacity) {
    super(capacity);
  }

  public PathsD(Collection<PathD> paths) {
    super(paths);
  }

  public String toString(int precision) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size(); i++) {
      if (i > 0) sb.append(System.lineSeparator());
      sb.append(get(i).toString(precision));
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return toString(2);
  }

}
