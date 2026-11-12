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

import java.nio.charset.StandardCharsets.ISO_8859_1
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SwingCompatibilityTests {
    @ParameterizedTest
    @ValueSource(strings = [
        "org/sonarlint/intellij/config/global/rules/RulesTreeTableRenderer",
        "org/sonarlint/intellij/dogfood/DogfoodPanel",
        "org/sonarlint/intellij/ui/inlay/FixSuggestionInlayPanel"
    ])
    fun `UI classes do not link to SwingX removed from current IDEs`(className: String) {
        val bytecode = javaClass.classLoader.getResourceAsStream("$className.class").use {
            requireNotNull(it) { "Missing compiled class: $className" }.readBytes()
        }

        // Check the constant pool, including method-body references that class loading alone misses.
        assertThat(String(bytecode, ISO_8859_1)).doesNotContain("org/jdesktop/swingx/")
    }
}
