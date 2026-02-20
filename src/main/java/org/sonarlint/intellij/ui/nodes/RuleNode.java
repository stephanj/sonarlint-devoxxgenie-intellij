/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij.ui.nodes;

import com.intellij.ui.SimpleTextAttributes;
import java.util.Objects;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.sonarlint.intellij.common.util.SonarLintUtils.pluralize;

public class RuleNode extends AbstractNode {
  private final String ruleKey;
  private final String ruleMessage;

  public RuleNode(String ruleKey, String ruleMessage) {
    this.ruleKey = ruleKey;
    this.ruleMessage = ruleMessage;
  }

  public String ruleKey() {
    return ruleKey;
  }

  public int getFileCount() {
    return super.getChildCount();
  }

  @Override
  public void render(TreeCellRenderer renderer) {
    renderer.append(ruleKey);
    var truncatedMessage = ruleMessage.length() > 80 ? ruleMessage.substring(0, 77) + "..." : ruleMessage;
    renderer.append(spaceAndThinSpace() + truncatedMessage, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    renderer.append(spaceAndThinSpace() + "(" + getFileCount() + pluralize(" file", getFileCount()) + ")",
      SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    renderer.setToolTipText(ruleKey + " - " + ruleMessage);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var ruleNode = (RuleNode) o;
    return Objects.equals(ruleKey, ruleNode.ruleKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleKey);
  }

  @Override
  public String toString() {
    return ruleKey;
  }
}
