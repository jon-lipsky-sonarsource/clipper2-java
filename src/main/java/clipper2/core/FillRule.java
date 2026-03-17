/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  12 December 2025                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2025                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.core;

// By far the most widely used filling rules for polygons are EvenOdd
// and NonZero, sometimes called Alternate and Winding respectively.
// https://en.wikipedia.org/wiki/Nonzero-rule
public enum FillRule {
  EvenOdd,
  NonZero,
  Positive,
  Negative
}
