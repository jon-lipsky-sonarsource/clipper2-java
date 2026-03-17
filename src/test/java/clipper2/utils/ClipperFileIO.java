/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  16 September 2022                                               *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2022                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.utils;

import clipper2.core.*;

import java.io.*;
import java.nio.file.*;

public final class ClipperFileIO {

  private ClipperFileIO() {}

  public static Paths64 pathFromStr(String s) {
    Path64 p = new Path64();
    Paths64 pp = new Paths64();
    if (s == null) return pp;

    int len = s.length(), i = 0;
    while (i < len) {
      // skip whitespace
      while (i < len && s.charAt(i) < 33) i++;
      if (i >= len) break;

      // get X
      boolean isNeg = s.charAt(i) == '-';
      if (isNeg) i++;
      if (i >= len || s.charAt(i) < '0' || s.charAt(i) > '9') break;
      int j = i + 1;
      while (j < len && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
      long x;
      try {
        x = Long.parseLong(s.substring(i, j));
      } catch (NumberFormatException e) {
        break;
      }
      if (isNeg) x = -x;

      // skip space or comma between X and Y
      i = j;
      while (i < len && (s.charAt(i) == ' ' || s.charAt(i) == ',')) i++;

      // get Y
      if (i >= len) break;
      isNeg = s.charAt(i) == '-';
      if (isNeg) i++;
      if (i >= len || s.charAt(i) < '0' || s.charAt(i) > '9') break;
      j = i + 1;
      while (j < len && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
      long y;
      try {
        y = Long.parseLong(s.substring(i, j));
      } catch (NumberFormatException e) {
        break;
      }
      if (isNeg) y = -y;

      p.add(new Point64(x, y));

      // skip trailing space/comma; count newlines to detect blank line (new path)
      i = j;
      int nlCnt = 0;
      while (i < len && (s.charAt(i) < 33 || s.charAt(i) == ',')) {
        if (s.charAt(i) == '\n') {
          nlCnt++;
          if (nlCnt == 2) {
            if (!p.isEmpty()) pp.add(p);
            p = new Path64();
          }
        }
        i++;
      }
    }
    if (!p.isEmpty()) pp.add(p);
    return pp;
  }

  public static class TestCase {
    public final boolean found;
    public final ClipType clipType;
    public final FillRule fillRule;
    public final long area;
    public final int count;
    public final String caption;

    public TestCase(boolean found, ClipType clipType, FillRule fillRule,
        long area, int count, String caption) {
      this.found = found;
      this.clipType = clipType;
      this.fillRule = fillRule;
      this.area = area;
      this.count = count;
      this.caption = caption;
    }
  }

  /**
   * Loads test case number {@code num} from the given file.
   * Populates subj, subjOpen, clip with the parsed paths.
   * Returns a TestCase with metadata (clipType, fillRule, area, count, caption).
   */
  public static TestCase loadTestNum(String filename, int num,
      Paths64 subj, Paths64 subjOpen, Paths64 clip) {

    if (subj == null) subj = new Paths64(); else subj.clear();
    if (subjOpen == null) subjOpen = new Paths64(); else subjOpen.clear();
    if (clip == null) clip = new Paths64(); else clip.clear();

    ClipType ct = ClipType.Intersection;
    FillRule fillRule = FillRule.EvenOdd;
    boolean result = false;
    if (num < 1) num = 1;
    String caption = "";
    long area = 0;
    int count = 0;

    BufferedReader reader;
    try {
      reader = new BufferedReader(new FileReader(filename));
    } catch (IOException e) {
      return new TestCase(false, ct, fillRule, area, count, caption);
    }

    try {
      String s;
      while ((s = reader.readLine()) != null) {

        if (s.startsWith("CAPTION: ")) {
          num--;
          if (num != 0) continue;
          caption = s.substring(9);
          result = true;
          continue;
        }

        if (num > 0) continue;

        if (s.startsWith("CLIPTYPE: ")) {
          String upper = s.toUpperCase();
          if (upper.contains("INTERSECTION")) ct = ClipType.Intersection;
          else if (upper.contains("UNION")) ct = ClipType.Union;
          else if (upper.contains("DIFFERENCE")) ct = ClipType.Difference;
          else ct = ClipType.Xor;
          continue;
        }

        if (s.startsWith("FILLTYPE: ") || s.startsWith("FILLRULE: ")) {
          String upper = s.toUpperCase();
          if (upper.contains("EVENODD")) fillRule = FillRule.EvenOdd;
          else if (upper.contains("POSITIVE")) fillRule = FillRule.Positive;
          else if (upper.contains("NEGATIVE")) fillRule = FillRule.Negative;
          else fillRule = FillRule.NonZero;
          continue;
        }

        if (s.startsWith("SOL_AREA: ")) {
          try { area = Long.parseLong(s.substring(10).trim()); } catch (NumberFormatException ignored) {}
          continue;
        }

        if (s.startsWith("SOL_COUNT: ")) {
          try { count = Integer.parseInt(s.substring(11).trim()); } catch (NumberFormatException ignored) {}
          continue;
        }

        int getIdx;
        if (s.startsWith("SUBJECTS_OPEN")) getIdx = 2;
        else if (s.startsWith("SUBJECTS")) getIdx = 1;
        else if (s.startsWith("CLIPS")) getIdx = 3;
        else continue;

        // read path lines until blank / next section
        while ((s = reader.readLine()) != null) {
          Paths64 paths = pathFromStr(s);
          if (paths == null || paths.isEmpty()) {
            if (getIdx == 3) {
              reader.close();
              return new TestCase(result, ct, fillRule, area, count, caption);
            }
            String upper = s.toUpperCase();
            if (upper.startsWith("SUBJECTS_OPEN")) getIdx = 2;
            else if (upper.startsWith("CLIPS")) getIdx = 3;
            else {
              reader.close();
              return new TestCase(result, ct, fillRule, area, count, caption);
            }
            continue;
          }
          switch (getIdx) {
            case 1 -> subj.add(paths.get(0));
            case 2 -> subjOpen.add(paths.get(0));
            default -> clip.add(paths.get(0));
          }
        }
      }
    } catch (IOException e) {
      // fall through
    } finally {
      try { reader.close(); } catch (IOException ignored) {}
    }

    return new TestCase(result, ct, fillRule, area, count, caption);
  }

  public static Paths64 affineTranslatePaths(Paths64 paths, long dx, long dy) {
    Paths64 result = new Paths64(paths.size());
    for (Path64 path : paths) {
      Path64 p = new Path64(path.size());
      for (Point64 pt : path) {
        p.add(new Point64(pt.x + dx, pt.y + dy));
      }
      result.add(p);
    }
    return result;
  }

}
