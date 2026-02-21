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
package org.sonarlint.intellij.config.project

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.sonarlint.intellij.config.ConfigurationPanel
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane

class DevoxxGeniePanel : ConfigurationPanel<SonarLintProjectSettings> {

    private val promptTextArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
    }

    private val taskTextArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 10
    }

    private fun buildSection(
        title: String,
        descriptionHtml: String,
        textArea: JBTextArea,
        resetAction: () -> Unit
    ): JPanel = JPanel(BorderLayout(0, 8)).apply {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val header = JBLabel("<html><b>$title</b></html>")
        val description = JBLabel("<html>$descriptionHtml</html>")
        val headerPanel = JPanel(BorderLayout(0, 4)).apply {
            add(header, BorderLayout.NORTH)
            add(description, BorderLayout.CENTER)
        }
        add(headerPanel, BorderLayout.NORTH)
        add(JBScrollPane(textArea), BorderLayout.CENTER)

        val resetButton = JButton("Reset to Default").apply {
            addActionListener { resetAction() }
        }
        val buttonPanel = JPanel(BorderLayout()).apply {
            add(resetButton, BorderLayout.WEST)
        }
        add(buttonPanel, BorderLayout.SOUTH)
    }

    private val panel = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        resizeWeight = 0.5

        topComponent = buildSection(
            title = "Chat Prompt",
            descriptionHtml = "Customize the prompt template sent to DevoxxGenie when using &quot;Fix with DevoxxGenie&quot;.<br><br>" +
                "Available placeholders: <b>{ruleName}</b>, <b>{ruleKey}</b>, <b>{message}</b>, " +
                "<b>{filePath}</b>, <b>{line}</b>, <b>{codeSnippet}</b>",
            textArea = promptTextArea
        ) {
            promptTextArea.text = SonarLintProjectSettings.DEFAULT_DEVOXX_GENIE_PROMPT_TEMPLATE
        }

        bottomComponent = buildSection(
            title = "Task Template",
            descriptionHtml = "Customize the task body written to <code>backlog/tasks/*.md</code> when using &quot;Create DevoxxGenie Tasks&quot;.<br><br>" +
                "Available placeholders: <b>{ruleKey}</b>, <b>{fileName}</b>, <b>{relativePath}</b>, " +
                "<b>{line}</b>, <b>{severity}</b>, <b>{message}</b>",
            textArea = taskTextArea
        ) {
            taskTextArea.text = SonarLintProjectSettings.DEFAULT_DEVOXX_GENIE_TASK_TEMPLATE
        }
    }

    override fun getComponent(): JComponent = panel

    override fun isModified(settings: SonarLintProjectSettings): Boolean {
        return promptTextArea.text != settings.devoxxGeniePromptTemplate
            || taskTextArea.text != settings.devoxxGenieTaskTemplate
    }

    override fun load(settings: SonarLintProjectSettings) {
        promptTextArea.text = settings.devoxxGeniePromptTemplate
        taskTextArea.text = settings.devoxxGenieTaskTemplate
    }

    override fun save(settings: SonarLintProjectSettings) {
        settings.devoxxGeniePromptTemplate = promptTextArea.text
        settings.devoxxGenieTaskTemplate = taskTextArea.text
    }
}
