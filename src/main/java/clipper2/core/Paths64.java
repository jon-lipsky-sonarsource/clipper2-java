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

public class Paths64 extends ArrayList<Path64> {

  public Paths64() {
    super();
  }

  public Paths64(int capacity) {
    super(capacity);
  }

  public Paths64(Collection<Path64> paths) {
    super(paths);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < size(); i++) {
      if (i > 0) sb.append(System.lineSeparator());
      sb.append(get(i));
    }
    return sb.toString();
  }

}
