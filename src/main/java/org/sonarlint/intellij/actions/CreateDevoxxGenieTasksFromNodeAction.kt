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
package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.util.DataKeys
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity

class CreateDevoxxGenieTasksFromNodeAction : AnAction(
    "Create DevoxxGenie Tasks",
    "Create backlog tasks for issues under the selected node",
    AllIcons.Actions.AddList
) {

    companion object {
        private const val DEVOXX_GENIE_PLUGIN_ID = "com.devoxx.genie"
        private const val EXT_SERVICE_CLASS = "com.devoxx.genie.service.ExternalTaskService"

        private fun isDevoxxGenieInstalled(): Boolean =
            PluginManagerCore.getPlugin(PluginId.getId(DEVOXX_GENIE_PLUGIN_ID))?.isEnabled == true
    }

    override fun update(e: AnActionEvent) {
        val issues = e.getData(DataKeys.ISSUE_LIST_DATA_KEY)
        e.presentation.isVisible = isDevoxxGenieInstalled()
        e.presentation.isEnabled = !issues.isNullOrEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issues = e.getData(DataKeys.ISSUE_LIST_DATA_KEY) ?: return
        if (issues.isEmpty()) return
        createTasks(project, issues)
    }

    private fun createTasks(project: Project, issues: List<LiveIssue>) {
        val count = tryViaExternalTaskService(project, issues)
            ?: writeFilesDirectly(project, issues)

        NotificationGroupManager.getInstance()
            .getNotificationGroup("SonarQube for IDE")
            .createNotification(
                "DevoxxGenie Tasks Created",
                "Created $count task(s) in <code>backlog/tasks/</code>",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    /**
     * Attempts to create tasks via DevoxxGenie's ExternalTaskService (loaded through
     * DevoxxGenie's own classloader to cross the plugin isolation boundary).
     * Returns the count created, or null if the service is unavailable.
     */
    private fun tryViaExternalTaskService(project: Project, issues: List<LiveIssue>): Int? {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(DEVOXX_GENIE_PLUGIN_ID)) ?: return null
        val classLoader = plugin.pluginClassLoader ?: return null
        return try {
            val cls = classLoader.loadClass(EXT_SERVICE_CLASS)
            val service = cls.getMethod("getInstance", Project::class.java).invoke(null, project)
            val method = cls.getMethod(
                "createBacklogTask",
                String::class.java, String::class.java, String::class.java, List::class.java
            )
            var count = 0
            for (issue in issues) {
                try {
                    method.invoke(service, buildTitle(issue), buildDescription(project, issue), priority(issue), labels(issue))
                    count++
                } catch (_: Exception) { }
            }
            count
        } catch (_: Exception) {
            null
        }
    }

    /** Fallback: write task markdown files directly to backlog/tasks/. */
    private fun writeFilesDirectly(project: Project, issues: List<LiveIssue>): Int {
        val projectRoot = project.basePath ?: return 0
        val backlogDir = File(projectRoot, "backlog/tasks")
        backlogDir.mkdirs()

        val nextTaskNumber = nextTaskNumber(backlogDir)
        val createdDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        var createdCount = 0

        for ((index, issue) in issues.withIndex()) {
            val file = issue.file()
            val relativePath = computeRelativePath(file.path, projectRoot)
            val lineNumber = getLineNumber(issue)
            val ruleKey = issue.getRuleKey()
            val taskNumber = nextTaskNumber + index
            val taskFile = File(backlogDir, buildTaskFileName(taskNumber, ruleKey, file.name, lineNumber))
            taskFile.writeText(buildTaskContent(project, issue, relativePath, lineNumber, ruleKey, file.name, taskNumber, createdDate))
            createdCount++
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(backlogDir.path)
        return createdCount
    }

    private fun buildTitle(issue: LiveIssue): String {
        val lineNumber = getLineNumber(issue)
        return "Fix ${issue.getRuleKey()} in ${issue.file().name} at line $lineNumber"
    }

    private fun buildDescription(project: Project, issue: LiveIssue): String {
        val template = Settings.getSettingsFor(project).devoxxGenieTaskTemplate
        val ruleKey = issue.getRuleKey()
        val message = issue.getMessage() ?: ""
        val severity = severityLabel(issue)
        val lineNumber = getLineNumber(issue)
        val fileName = issue.file().name
        val relativePath = computeRelativePath(issue.file().path, project.basePath ?: fileName)
        return template
            .replace("{ruleKey}", ruleKey)
            .replace("{fileName}", fileName)
            .replace("{relativePath}", relativePath)
            .replace("{line}", lineNumber.toString())
            .replace("{severity}", severity)
            .replace("{message}", message)
    }

    private fun priority(issue: LiveIssue): String {
        val highestImpact = issue.getHighestImpact()
        if (highestImpact != null) {
            return when (highestImpact) {
                ImpactSeverity.HIGH -> "high"
                ImpactSeverity.MEDIUM -> "medium"
                else -> "low"
            }
        }
        return when (issue.getUserSeverity()) {
            IssueSeverity.BLOCKER, IssueSeverity.CRITICAL -> "high"
            IssueSeverity.MAJOR -> "medium"
            else -> "low"
        }
    }

    private fun labels(issue: LiveIssue): List<String> {
        val rulePrefix = issue.getRuleKey().substringBefore(':').lowercase()
        return listOf("sonarqube", rulePrefix)
    }

    private fun severityLabel(issue: LiveIssue): String {
        val highestImpact = issue.getHighestImpact()
        val highestQuality = issue.getHighestQuality()
        if (highestImpact != null && highestQuality != null) {
            return "${highestImpact.name.lowercase().replaceFirstChar { it.uppercase() }} impact on ${highestQuality.name.lowercase().replaceFirstChar { it.uppercase() }}"
        }
        return issue.getUserSeverity()?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }

    private fun getLineNumber(issue: LiveIssue): Int {
        val range = issue.getRange() ?: return 0
        if (!range.isValid) return 0
        val document = FileDocumentManager.getInstance().getCachedDocument(issue.file()) ?: return 0
        return document.getLineNumber(range.startOffset) + 1
    }

    private fun computeRelativePath(absPath: String, projectRoot: String): String =
        if (absPath.startsWith(projectRoot)) absPath.removePrefix(projectRoot).trimStart('/', '\\')
        else absPath.substringAfterLast('/')

    private fun sanitize(input: String): String =
        input.replace(Regex("[^a-zA-Z0-9]"), "-").lowercase().take(60).trimEnd('-')

    private fun buildTaskFileName(taskNumber: Int, ruleKey: String, fileName: String, lineNumber: Int): String {
        val baseName = "TASK-$taskNumber-sonar-${sanitize(ruleKey)}-${sanitize(fileName)}-l$lineNumber"
        return baseName.take(80) + ".md"
    }

    private fun nextTaskNumber(backlogDir: File): Int {
        val backlogRoot = backlogDir.parentFile
        val dirsToScan = listOf(
            File(backlogRoot, "tasks"),
            File(backlogRoot, "completed"),
            File(backlogRoot, "archive/tasks")
        )
        val maxId = dirsToScan
            .flatMap { it.listFiles { f -> f.extension == "md" }?.toList() ?: emptyList() }
            .mapNotNull { f ->
                try {
                    f.useLines { lines ->
                        lines.take(20).firstOrNull { it.trimStart().startsWith("id:") }
                            ?.let { Regex("\\d+$").find(it)?.value?.toIntOrNull() }
                    }
                } catch (_: Exception) { null }
            }
            .maxOrNull() ?: 0
        return maxId + 1
    }

    private fun buildTaskContent(
        project: Project, issue: LiveIssue, relativePath: String, lineNumber: Int,
        ruleKey: String, fileName: String, taskNumber: Int, createdDate: String
    ): String {
        val priority = priority(issue)
        val severity = severityLabel(issue)
        val message = issue.getMessage()
        val rulePrefix = ruleKey.substringBefore(':').lowercase()
        val ordinal = taskNumber * 1000

        val body = Settings.getSettingsFor(project).devoxxGenieTaskTemplate
            .replace("{ruleKey}", ruleKey)
            .replace("{fileName}", fileName)
            .replace("{relativePath}", relativePath)
            .replace("{line}", lineNumber.toString())
            .replace("{severity}", severity)
            .replace("{message}", message ?: "")

        return """---
id: TASK-$taskNumber
title: Fix $ruleKey in $fileName at line $lineNumber
status: To Do
priority: $priority
assignee: []
created_date: '$createdDate'
labels:
  - sonarqube
  - $rulePrefix
dependencies: []
references: []
documentation: []
ordinal: $ordinal
---

$body"""
    }
}
