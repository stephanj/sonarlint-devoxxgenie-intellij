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
package org.sonarlint.intellij.ui.report

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.time.Instant
import java.util.UUID
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.finding.LiveFindings
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot
import org.sonarlint.intellij.finding.issue.LiveIssue
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarsource.sonarlint.core.client.utils.CleanCodeAttribute
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.issue.ResolutionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.VulnerabilityProbability
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType

// Persistence data classes
data class PersistedImpact(
    @SerializedName("quality") val quality: String,
    @SerializedName("severity") val severity: String,
)

data class PersistedFinding(
    @SerializedName("backendId") val backendId: String,
    @SerializedName("ruleKey") val ruleKey: String,
    @SerializedName("message") val message: String,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("startOffset") val startOffset: Int,
    @SerializedName("endOffset") val endOffset: Int,
    @SerializedName("isOnNewCode") val isOnNewCode: Boolean,
    @SerializedName("isResolved") val isResolved: Boolean,
    @SerializedName("isMqrMode") val isMqrMode: Boolean,
    @SerializedName("severity") val severity: String?,
    @SerializedName("cleanCodeAttribute") val cleanCodeAttribute: String?,
    @SerializedName("impacts") val impacts: List<PersistedImpact>,
    @SerializedName("introductionDate") val introductionDate: Long?,
    @SerializedName("kind") val kind: String,
    // Issue-specific
    @SerializedName("type") val type: String?,
    @SerializedName("isAiCodeFixable") val isAiCodeFixable: Boolean = false,
    @SerializedName("issueStatus") val issueStatus: String?,
    // Hotspot-specific
    @SerializedName("vulnerabilityProbability") val vulnerabilityProbability: String?,
    @SerializedName("hotspotStatus") val hotspotStatus: String?,
)

data class PersistedReport(
    @SerializedName("tabTitle") val tabTitle: String,
    @SerializedName("analysisDate") val analysisDate: Long,
    @SerializedName("findings") val findings: List<PersistedFinding>,
    @SerializedName("analyzedFilePaths") val analyzedFilePaths: List<String>,
)

@Service(Service.Level.PROJECT)
class ReportPersistenceService(private val project: Project) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private fun getCacheFile(): File {
        val basePath = project.basePath ?: return File(System.getProperty("java.io.tmpdir"), "sonarlint-report-cache.json")
        val sonarlintDir = File(basePath, ".idea/sonarlint")
        sonarlintDir.mkdirs()
        return File(sonarlintDir, "report-cache.json")
    }

    fun saveReport(tabTitle: String, analysisResult: AnalysisResult) {
        try {
            val persistedFindings = mutableListOf<PersistedFinding>()

            for ((file, issues) in analysisResult.findings.issuesPerFile) {
                for (issue in issues) {
                    persistedFindings.add(convertIssue(issue, file))
                }
            }

            for ((file, hotspots) in analysisResult.findings.securityHotspotsPerFile) {
                for (hotspot in hotspots) {
                    persistedFindings.add(convertHotspot(hotspot, file))
                }
            }

            val persistedReport = PersistedReport(
                tabTitle = tabTitle,
                analysisDate = analysisResult.analysisDate.toEpochMilli(),
                findings = persistedFindings,
                analyzedFilePaths = analysisResult.analyzedFiles.map { it.path },
            )

            getCacheFile().writeText(gson.toJson(persistedReport))
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Failed to persist report findings", e)
        }
    }

    fun restoreReport(): Pair<String, AnalysisResult>? {
        try {
            val cacheFile = getCacheFile()
            if (!cacheFile.exists()) return null

            val json = cacheFile.readText()
            val persisted = gson.fromJson(json, PersistedReport::class.java) ?: return null

            val issuesPerFile = mutableMapOf<VirtualFile, MutableCollection<LiveIssue>>()
            val hotspotsPerFile = mutableMapOf<VirtualFile, MutableCollection<LiveSecurityHotspot>>()
            val analyzedFiles = mutableListOf<VirtualFile>()

            // Resolve analyzed files
            for (path in persisted.analyzedFilePaths) {
                LocalFileSystem.getInstance().findFileByPath(path)?.let { analyzedFiles.add(it) }
            }

            // Restore findings
            for (finding in persisted.findings) {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(finding.filePath) ?: continue
                val module = SonarLintAppUtils.findModuleForFile(virtualFile, project) ?: continue

                when (finding.kind) {
                    "ISSUE" -> {
                        val issue = restoreIssue(finding, module, virtualFile)
                        issuesPerFile.getOrPut(virtualFile) { mutableListOf() }.add(issue)
                    }
                    "HOTSPOT" -> {
                        val hotspot = restoreHotspot(finding, module, virtualFile)
                        hotspotsPerFile.getOrPut(virtualFile) { mutableListOf() }.add(hotspot)
                    }
                }
            }

            val liveFindings = LiveFindings(issuesPerFile, hotspotsPerFile)
            val analysisResult = AnalysisResult(
                null,
                liveFindings,
                analyzedFiles,
                Instant.ofEpochMilli(persisted.analysisDate),
            )

            return Pair(persisted.tabTitle, analysisResult)
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Failed to restore persisted report findings", e)
            return null
        }
    }

    fun clearReport() {
        try {
            val cacheFile = getCacheFile()
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            SonarLintConsole.get(project).error("Failed to clear persisted report", e)
        }
    }

    private fun convertIssue(issue: LiveIssue, file: VirtualFile): PersistedFinding {
        val range = issue.range
        return PersistedFinding(
            backendId = issue.backendId.toString(),
            ruleKey = issue.getRuleKey(),
            message = issue.message ?: "",
            filePath = file.path,
            startOffset = range?.startOffset ?: 0,
            endOffset = range?.endOffset ?: 0,
            isOnNewCode = issue.isOnNewCode(),
            isResolved = issue.isResolved(),
            isMqrMode = issue.isMqrMode(),
            severity = issue.userSeverity?.name,
            cleanCodeAttribute = issue.getCleanCodeAttribute()?.name,
            impacts = issue.getImpacts().map { PersistedImpact(it.softwareQuality.name, it.impactSeverity.name) },
            introductionDate = issue.introductionDate?.toEpochMilli(),
            kind = "ISSUE",
            type = issue.getType()?.name,
            isAiCodeFixable = issue.isAiCodeFixable(),
            issueStatus = issue.status?.name,
            vulnerabilityProbability = null,
            hotspotStatus = null,
        )
    }

    private fun convertHotspot(hotspot: LiveSecurityHotspot, file: VirtualFile): PersistedFinding {
        val range = hotspot.range
        return PersistedFinding(
            backendId = hotspot.backendId.toString(),
            ruleKey = hotspot.getRuleKey(),
            message = hotspot.message ?: "",
            filePath = file.path,
            startOffset = range?.startOffset ?: 0,
            endOffset = range?.endOffset ?: 0,
            isOnNewCode = hotspot.isOnNewCode(),
            isResolved = hotspot.isResolved(),
            isMqrMode = hotspot.isMqrMode(),
            severity = hotspot.userSeverity?.name,
            cleanCodeAttribute = null,
            impacts = emptyList(),
            introductionDate = hotspot.introductionDate?.toEpochMilli(),
            kind = "HOTSPOT",
            type = null,
            isAiCodeFixable = false,
            issueStatus = null,
            vulnerabilityProbability = hotspot.vulnerabilityProbability?.name,
            hotspotStatus = hotspot.status?.name,
        )
    }

    private fun restoreIssue(finding: PersistedFinding, module: Module, virtualFile: VirtualFile): LiveIssue {
        val impacts = finding.impacts.map {
            ImpactDto(
                org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality.valueOf(it.quality),
                org.sonarsource.sonarlint.core.rpc.protocol.common.ImpactSeverity.valueOf(it.severity)
            )
        }

        return LiveIssue(
            module,
            UUID.fromString(finding.backendId),
            virtualFile,
            null, // RangeMarker cannot be restored without loading the document
            finding.message,
            finding.ruleKey,
            finding.isOnNewCode,
            finding.isResolved,
            finding.isMqrMode,
            finding.severity?.let { IssueSeverity.valueOf(it) },
            finding.cleanCodeAttribute?.let { CleanCodeAttribute.valueOf(it) },
            impacts,
            finding.introductionDate?.let { Instant.ofEpochMilli(it) },
            finding.type?.let { RuleType.valueOf(it) },
            finding.isAiCodeFixable,
            finding.issueStatus?.let { ResolutionStatus.valueOf(it) },
        )
    }

    private fun restoreHotspot(finding: PersistedFinding, module: Module, virtualFile: VirtualFile): LiveSecurityHotspot {
        return LiveSecurityHotspot(
            module,
            UUID.fromString(finding.backendId),
            virtualFile,
            null, // RangeMarker cannot be restored without loading the document
            finding.message,
            finding.ruleKey,
            finding.isOnNewCode,
            finding.isResolved,
            finding.isMqrMode,
            finding.severity?.let { IssueSeverity.valueOf(it) },
            null, // Hotspots don't have cleanCodeAttribute
            emptyList(), // Hotspots don't have impacts
            finding.introductionDate?.let { Instant.ofEpochMilli(it) },
            finding.vulnerabilityProbability?.let { VulnerabilityProbability.valueOf(it) } ?: VulnerabilityProbability.LOW,
            finding.hotspotStatus?.let { HotspotStatus.valueOf(it) } ?: HotspotStatus.TO_REVIEW,
        )
    }
}
