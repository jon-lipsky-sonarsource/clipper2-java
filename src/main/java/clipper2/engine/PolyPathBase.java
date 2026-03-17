/*******************************************************************************
 * Author    :  Angus Johnson                                                   *
 * Date      :  21 February 2026                                                *
 * Website   :  https://www.angusj.com                                          *
 * Copyright :  Angus Johnson 2010-2026                                         *
 * License   :  https://www.boost.org/LICENSE_1_0.txt                           *
 *******************************************************************************/

package clipper2.engine;

import clipper2.core.Path64;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Abstract base class for PolyPath64 and PolyPathD.
 * Represents one node in a polygon tree.
 */
public abstract class PolyPathBase implements Iterable<PolyPathBase> {

  PolyPathBase _parent;
  public List<PolyPathBase> _childs = new ArrayList<>();

  protected PolyPathBase() {
    _parent = null;
  }

  protected PolyPathBase(PolyPathBase parent) {
    _parent = parent;
  }

  @Override
  public Iterator<PolyPathBase> iterator() {
    // Snapshot the list so modifications during iteration are safe
    List<PolyPathBase> snapshot = new ArrayList<>(_childs);
    return snapshot.iterator();
  }

  private int getLevel() {
    int result = 0;
    PolyPathBase pp = _parent;
    while (pp != null) { ++result; pp = pp._parent; }
    return result;
  }

  public int getLevel_() { return getLevel(); }

  public boolean isHole() {
    int lvl = getLevel();
    return lvl != 0 && (lvl & 1) == 0;
  }

  public int count() { return _childs.size(); }

  public int size() { return _childs.size(); }

  public PolyPathBase get(int index) { return _childs.get(index); }

  /** Add a child from a Path64. Implemented by concrete subclasses. */
  public abstract PolyPathBase addChild(Path64 p);

  public void clear() {
    _childs.clear();
  }

  String toStringInternal(int idx, int level) {
    StringBuilder sb = new StringBuilder();
    String padding = "  ".repeat(level);
    String plural = _childs.size() == 1 ? "" : "s";
    if ((level & 1) == 0)
      sb.append(padding).append("+- hole (").append(idx).append(") contains ")
          .append(_childs.size()).append(" nested polygon").append(plural).append(".\n");
    else
      sb.append(padding).append("+- polygon (").append(idx).append(") contains ")
          .append(_childs.size()).append(" hole").append(plural).append(".\n");
    for (int i = 0; i < count(); i++)
      if (_childs.get(i).count() > 0)
        sb.append(_childs.get(i).toStringInternal(i, level + 1));
    return sb.toString();
  }

  @Override
  public String toString() {
    if (getLevel() > 0) return "";
    String plural = _childs.size() == 1 ? "" : "s";
    StringBuilder sb = new StringBuilder("Polytree with ")
        .append(_childs.size()).append(" polygon").append(plural).append(".\n");
    for (int i = 0; i < count(); i++)
      if (_childs.get(i).count() > 0)
        sb.append(_childs.get(i).toStringInternal(i, 1));
    sb.append('\n');
    return sb.toString();
  }
}
