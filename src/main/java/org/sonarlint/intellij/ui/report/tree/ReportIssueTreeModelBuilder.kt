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
package org.sonarlint.intellij.ui.report.tree

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.tree.DefaultTreeModel
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.ui.filter.FilterSettingsService
import org.sonarlint.intellij.ui.filter.GroupMode
import org.sonarlint.intellij.ui.filter.SortMode
import org.sonarlint.intellij.ui.nodes.FileNode
import org.sonarlint.intellij.ui.nodes.IssueNode
import org.sonarlint.intellij.ui.nodes.RuleNode
import org.sonarlint.intellij.ui.nodes.SummaryNode
import org.sonarlint.intellij.ui.tree.FindingTreeSummary
import org.sonarlint.intellij.ui.tree.TreeContentKind

class ReportIssueTreeModelBuilder(project: Project, isOld: Boolean) {

    val model: DefaultTreeModel
    var sortMode: SortMode = getService(FilterSettingsService::class.java).getDefaultSortMode()
    var groupMode: GroupMode = getService(FilterSettingsService::class.java).getDefaultGroupMode()
    private val summaryNode: SummaryNode
    private val treeSummary = FindingTreeSummary(project, TreeContentKind.ISSUES, isOld)
    private var latestIssues = mutableListOf<LiveIssue>()

    init {
        summaryNode = SummaryNode(treeSummary)
        model = DefaultTreeModel(summaryNode)
        model.setRoot(summaryNode)
    }

    fun updateModel(findings: Map<VirtualFile, Collection<LiveIssue>>) {
        val allIssues = findings.values.flatten()
        latestIssues = allIssues.toMutableList()

        clear()

        when (groupMode) {
            GroupMode.FILE -> buildGroupByFileTree(allIssues)
            GroupMode.RULE -> buildGroupByRuleTree(allIssues)
        }

        model.nodeStructureChanged(summaryNode)
    }

    private fun buildGroupByFileTree(allIssues: List<LiveIssue>) {
        val issuesByFile = allIssues.groupBy { it.file() }

        for ((file, fileIssues) in issuesByFile) {
            val fileNode = FileNode(file, false)
            sortIssues(fileIssues).forEach { issue ->
                fileNode.add(IssueNode(issue))
            }
            summaryNode.add(fileNode)
        }

        if (summaryNode.childCount > 0) {
            val children = (0 until summaryNode.childCount).map { summaryNode.getChildAt(it) as FileNode }
            val sortedChildren = children.sortedWith(compareBy { it.file().name })
            summaryNode.removeAllChildren()
            sortedChildren.forEach { summaryNode.add(it) }
        }

        treeSummary.refresh(issuesByFile.size, allIssues.size)
    }

    private fun buildGroupByRuleTree(allIssues: List<LiveIssue>) {
        val issuesByRule = allIssues.groupBy { it.getRuleKey() }

        for ((ruleKey, ruleIssues) in issuesByRule.entries.sortedByDescending { it.value.size }) {
            val ruleMessage = ruleIssues.first().getMessage() ?: ruleKey
            val ruleNode = RuleNode(ruleKey, ruleMessage)

            val issuesByFile = ruleIssues.groupBy { it.file() }
            for ((file, fileIssues) in issuesByFile.entries.sortedBy { it.key.name }) {
                val fileNode = FileNode(file, false)
                sortIssues(fileIssues).forEach { issue ->
                    fileNode.add(IssueNode(issue))
                }
                ruleNode.add(fileNode)
            }

            summaryNode.add(ruleNode)
        }

        treeSummary.refreshWithRuleCount(issuesByRule.size, allIssues.size)
    }

    private fun sortIssues(issues: List<LiveIssue>): List<LiveIssue> {
        return when (sortMode) {
            SortMode.IMPACT -> issues.sortedWith(compareByDescending { it.getHighestImpact() })
            SortMode.DATE -> issues.sortedByDescending { it.introductionDate }
            SortMode.RULE_KEY -> issues.sortedBy { it.getRuleKey() }
            SortMode.LINE_NUMBER -> issues.sortedBy { it.validTextRange?.startOffset ?: Int.MAX_VALUE }
        }
    }

    fun isEmpty(): Boolean = latestIssues.isEmpty()

    fun numberOfDisplayedFindings(): Int = latestIssues.size

    fun clear() {
        latestIssues.clear()
        summaryNode.removeAllChildren()
        treeSummary.refresh(0, 0)
        model.nodeStructureChanged(summaryNode)
    }

}
