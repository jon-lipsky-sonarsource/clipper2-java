/*******************************************************************************
 * Java port of C# Clipper2 tests                                               *
 * Original Author :  Angus Johnson                                             *
 * License         :  https://www.boost.org/LICENSE_1_0.txt                    *
 *******************************************************************************/

package clipper2;

import clipper2.core.*;
import clipper2.engine.*;
import clipper2.utils.ClipperFileIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestPolytree {

  private static String getTestDataPath(String filename) {
    java.net.URL resource = TestPolytree.class.getResource("/");
    if (resource != null) {
      try {
        java.io.File dir = new java.io.File(resource.toURI());
        for (int i = 0; i < 10; i++) {
          java.io.File candidate = new java.io.File(dir, "Tests/" + filename);
          if (candidate.exists()) return candidate.getAbsolutePath();
          dir = dir.getParentFile();
          if (dir == null) break;
        }
      } catch (Exception ignored) {}
    }
    return "../Tests/" + filename;
  }

  private static void polyPathContainsPoint(PolyPath64 pp, Point64 pt, int[] counter) {
    if (Clipper.pointInPolygon(pt, pp.getPolygon()) != PointInPolygonResult.IsOutside) {
      if (pp.isHole()) counter[0]--;
      else counter[0]++;
    }
    for (int i = 0; i < pp.size(); i++) {
      polyPathContainsPoint((PolyPath64) pp.get(i), pt, counter);
    }
  }

  private static boolean polytreeContainsPoint(PolyTree64 tree, Point64 pt) {
    int[] counter = {0};
    for (int i = 0; i < tree.size(); i++) {
      polyPathContainsPoint((PolyPath64) tree.get(i), pt, counter);
    }
    assertTrue(counter[0] >= 0, "Polytree has too many holes");
    return counter[0] != 0;
  }

  private static boolean polyPathFullyContainsChildren(PolyPath64 pp) {
    for (int i = 0; i < pp.size(); i++) {
      PolyPath64 child = (PolyPath64) pp.get(i);
      for (Point64 pt : child.getPolygon()) {
        if (Clipper.pointInPolygon(pt, pp.getPolygon()) == PointInPolygonResult.IsOutside) {
          return false;
        }
      }
      if (child.size() > 0 && !polyPathFullyContainsChildren(child)) return false;
    }
    return true;
  }

  private static boolean checkPolytreeFullyContainsChildren(PolyTree64 polytree) {
    for (int i = 0; i < polytree.size(); i++) {
      PolyPath64 child = (PolyPath64) polytree.get(i);
      if (child.size() > 0 && !polyPathFullyContainsChildren(child)) return false;
    }
    return true;
  }

  @Test
  public void testPolytree2() {
    Paths64 subject = new Paths64();
    Paths64 subjectOpen = new Paths64();
    Paths64 clip = new Paths64();

    String dataFile = getTestDataPath("PolytreeHoleOwner2.txt");
    ClipperFileIO.TestCase tc = ClipperFileIO.loadTestNum(dataFile, 1, subject, subjectOpen, clip);
    assertTrue(tc.found, "Unable to read PolytreeHoleOwner2.txt");

    Path64 pointsOfInterestOutside = new Path64();
    pointsOfInterestOutside.add(new Point64(21887, 10420));
    pointsOfInterestOutside.add(new Point64(21726, 10825));
    pointsOfInterestOutside.add(new Point64(21662, 10845));
    pointsOfInterestOutside.add(new Point64(21617, 10890));

    for (Point64 pt : pointsOfInterestOutside) {
      for (Path64 path : subject) {
        assertEquals(PointInPolygonResult.IsOutside,
            Clipper.pointInPolygon(pt, path),
            "Outside point of interest found inside subject");
      }
    }

    Path64 pointsOfInterestInside = new Path64();
    pointsOfInterestInside.add(new Point64(21887, 10430));
    pointsOfInterestInside.add(new Point64(21843, 10520));
    pointsOfInterestInside.add(new Point64(21810, 10686));
    pointsOfInterestInside.add(new Point64(21900, 10461));

    for (Point64 pt : pointsOfInterestInside) {
      int poiInsideCounter = 0;
      for (Path64 path : subject) {
        if (Clipper.pointInPolygon(pt, path) == PointInPolygonResult.IsInside) {
          poiInsideCounter++;
        }
      }
      assertEquals(1, poiInsideCounter,
          String.format("poi_inside_counter - expected 1 but got %d", poiInsideCounter));
    }

    PolyTree64 solutionTree = new PolyTree64();
    Paths64 solutionOpen = new Paths64();
    Clipper64 clipper = new Clipper64();

    clipper.addSubject(subject);
    clipper.addOpenSubject(subjectOpen);
    clipper.addClip(clip);
    clipper.execute(tc.clipType, tc.fillRule, solutionTree, solutionOpen);

    Paths64 solutionPaths = Clipper.polyTreeToPaths64(solutionTree);
    double a1 = Clipper.area(solutionPaths);
    double a2 = solutionTree.area();

    assertTrue(a1 > 330000,
        String.format("Solution has wrong area - expected >330000, got %.1f", a1));

    assertTrue(Math.abs(a1 - a2) < 0.0001,
        String.format("Solution tree has wrong area - paths=%.4f tree=%.4f", a1, a2));

    assertTrue(checkPolytreeFullyContainsChildren(solutionTree),
        "The polytree doesn't properly contain its children");

    for (Point64 pt : pointsOfInterestOutside) {
      assertFalse(polytreeContainsPoint(solutionTree, pt),
          "The polytree indicates it contains a point that it should not contain");
    }

    for (Point64 pt : pointsOfInterestInside) {
      assertTrue(polytreeContainsPoint(solutionTree, pt),
          "The polytree indicates it does not contain a point that it should contain");
    }
  }

  @Test
  public void testPolytree3() {
    Paths64 subject = new Paths64();
    subject.add(Clipper.makePath(new long[]{1588700, -8717600, 1616200, -8474800, 1588700, -8474800}));
    subject.add(Clipper.makePath(new long[]{13583800, -15601600, 13582800, -15508500,
        13555300, -15508500, 13555500, -15182200, 13010900, -15185400}));
    subject.add(Clipper.makePath(new long[]{956700, -3092300, 1152600, 3147400, 25600, 3151700}));
    subject.add(Clipper.makePath(new long[]{
        22575900, -16604000, 31286800, -12171900,
        31110200, 4882800, 30996200, 4826300, 30414400, 5447400, 30260000, 5391500,
        29662200, 5805400, 28844500, 5337900, 28435000, 5789300, 27721400, 5026400,
        22876300, 5034300, 21977700, 4414900, 21148000, 4654700, 20917600, 4653400,
        19334300, 12411000, -2591700, 12177200, 53200, 3151100, -2564300, 12149800,
        7819400, 4692400, 10116000, 5228600, 6975500, 3120100, 7379700, 3124700,
        11037900, 596200, 12257000, 2587800, 12257000, 596200, 15227300, 2352700,
        18444400, 1112100, 19961100, 5549400, 20173200, 5078600, 20330000, 5079300,
        20970200, 4544300, 20989600, 4563700, 19465500, 1112100, 21611600, 4182100,
        22925100, 1112200, 22952700, 1637200, 23059000, 1112200, 24908100, 4181200,
        27070100, 3800600, 27238000, 3800700, 28582200, 520300, 29367800, 1050100,
        29291400, 179400, 29133700, 360700, 29056700, 312600, 29121900, 332500,
        29269900, 162300, 28941400, 213100, 27491300, -3041500, 27588700, -2997800,
        22104900, -16142800, 13010900, -15603000, 13555500, -15182200,
        13555300, -15508500, 13582800, -15508500, 13583100, -15154700,
        1588700, -8822800, 1588700, -8379900, 1588700, -8474800, 1616200, -8474800,
        1003900, -630100, 1253300, -12284500, 12983400, -16239900}));
    subject.add(Clipper.makePath(new long[]{198200, 12149800, 1010600, 12149800, 1011500, 11859600}));
    subject.add(Clipper.makePath(new long[]{21996700, -7432000, 22096700, -7432000, 22096700, -7332000}));

    PolyTree64 solutionTree = new PolyTree64();
    Clipper64 clipper = new Clipper64();
    clipper.addSubject(subject);
    clipper.execute(ClipType.Union, FillRule.NonZero, solutionTree);

    assertTrue(
        solutionTree.size() == 1
            && solutionTree.get(0).size() == 2
            && solutionTree.get(0).get(1).size() == 1,
        "Incorrect PolyTree nesting.");
  }

}
