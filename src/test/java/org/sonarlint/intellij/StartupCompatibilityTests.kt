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
package org.sonarlint.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.serviceContainer.NonInjectable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.sonarlint.intellij.vcs.DefaultVcsService

class StartupCompatibilityTests {
    @ParameterizedTest
    @ValueSource(strings = [
        "org.sonarlint.intellij.StartServicesOnProjectOpened",
        "org.sonarlint.intellij.OpenWelcomePageOnceOneProjectOpened",
        "org.sonarlint.intellij.core.cfamily.CFamilyAnalyzerStartupActivity"
    ])
    fun `startup activities implement the coroutine API required by current IDEs`(className: String) {
        assertThat(ProjectActivity::class.java.isAssignableFrom(Class.forName(className))).isTrue()
    }

    @Test
    fun `VCS service project constructor is available for platform injection`() {
        val constructor = DefaultVcsService::class.java.getConstructor(Project::class.java)

        assertThat(constructor.isAnnotationPresent(NonInjectable::class.java)).isFalse()
    }
}
