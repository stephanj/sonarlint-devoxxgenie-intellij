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

class DevoxxGeniePanel : ConfigurationPanel<SonarLintProjectSettings> {

    private val textArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        rows = 20
    }

    private val panel = JPanel(BorderLayout(0, 8)).apply {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val description = JBLabel(
            "<html>Customize the prompt template sent to DevoxxGenie when using &quot;Fix with DevoxxGenie&quot;.<br><br>" +
                "Available placeholders: <b>{ruleName}</b>, <b>{ruleKey}</b>, <b>{message}</b>, " +
                "<b>{filePath}</b>, <b>{line}</b>, <b>{codeSnippet}</b></html>"
        )
        add(description, BorderLayout.NORTH)

        val scrollPane = JBScrollPane(textArea)
        add(scrollPane, BorderLayout.CENTER)

        val resetButton = JButton("Reset to Default").apply {
            addActionListener {
                textArea.text = SonarLintProjectSettings.DEFAULT_DEVOXX_GENIE_PROMPT_TEMPLATE
            }
        }
        val buttonPanel = JPanel(BorderLayout()).apply {
            add(resetButton, BorderLayout.WEST)
        }
        add(buttonPanel, BorderLayout.SOUTH)
    }

    override fun getComponent(): JComponent = panel

    override fun isModified(settings: SonarLintProjectSettings): Boolean {
        return textArea.text != settings.devoxxGeniePromptTemplate
    }

    override fun load(settings: SonarLintProjectSettings) {
        textArea.text = settings.devoxxGeniePromptTemplate
    }

    override fun save(settings: SonarLintProjectSettings) {
        settings.devoxxGeniePromptTemplate = textArea.text
    }
}
