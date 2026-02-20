# Changelog

## 11.15.1 (2026-02-20)

### Report Panel

- **Persist report across restarts**: The last analysis report is now saved to `.idea/sonarlint/report-cache.json` and automatically restored when the IDE reopens, so findings are no longer lost on restart.

### DevoxxGenie Integration

- **Customizable prompt template**: A new "DevoxxGenie" tab in Project Settings lets users edit the prompt template sent to DevoxxGenie when using "Fix with DevoxxGenie". Available placeholders: `{ruleName}`, `{ruleKey}`, `{message}`, `{filePath}`, `{line}`, `{codeSnippet}`. Includes a "Reset to Default" button.

## 11.15.0 (2026-02-20)

### Report Panel

- **"Group by Rule" view**: A new "Group by:" combo in the filter panel lets you switch between grouping issues by file (default) or by rule key. When grouped by rule, the tree shows each rule with its issue count sorted by prevalence, with file nodes nested underneath. The summary text adapts to show "Found X issues across Y rules". The preference persists across IDE restarts.
- **"Analyse file" context menu action**: Right-clicking a file node in the Issues, Security Hotspots, or Taint Vulnerabilities tree now shows an "Analyse file" option that triggers SonarQube for IDE analysis on the selected file. The action reuses the existing `SonarAnalyzeFilesAction` and is automatically hidden when a finding node is selected.

## 11.14.1 (2026-02-18)

### DevoxxGenie Integration

- **Backlog task creation**: A "Create DevoxxGenie Task(s)" toolbar button in the report panel lets users select one or more issues via checkboxes and generate structured backlog task files in `backlog/tasks/`. Each file uses YAML frontmatter compatible with DevoxxGenie's CLI Runner, including rule key, severity, file path, and line number.
- **Task ID synchronisation**: Task numbering scans all three backlog directories (`tasks/`, `completed/`, `archive/tasks/`) and reads the `id:` YAML frontmatter field to find the highest existing task number, avoiding collisions with tasks already managed by DevoxxGenie.

## 11.14.0 (2026-02-16)

### DevoxxGenie Integration

- **Editor intention action**: When the DevoxxGenie plugin is installed, a "DevoxxGenie: Fix '...'" action appears in the lightbulb menu for SonarLint issues, sending issue details and code context to DevoxxGenie for AI-assisted fix suggestions.
- **"Fix with DevoxxGenie" button**: A styled button in the rule description header panel sends a structured prompt containing the rule name, issue message, file location, and a ~20-line code snippet to DevoxxGenie.
- **Reflective bridge**: Uses runtime reflection to communicate with DevoxxGenie's `ExternalPromptService`, so there is no compile-time dependency. The integration activates automatically when DevoxxGenie is installed and works with any LLM provider configured in DevoxxGenie.

### Build

- Upgraded minimum IntelliJ platform from 2023.1 to 2024.2 (first version bundling JBR 21).
- Upgraded Java/Kotlin toolchain and JVM target from 17 to 21.
- Updated IDE build versions: IntelliJ/CLion 2024.2.5, Rider 2024.2.7.

### Fork Changes

- Changed plugin ID from `org.sonarlint.idea` to `org.sonarlint.idea.devoxxgenie` to prevent the official SonarLint marketplace release from overwriting the fork.
- Updated plugin name to "SonarQube with DevoxxGenie".
- Updated plugin overview description to highlight the DevoxxGenie integration.
- Fixed safe property access for Artifactory Gradle extras (`extra.has()` check) to avoid build failures without `gradle.properties`.

### Base Version

Based on SonarLint for IntelliJ 11.13 (commit `d8f85ccc`).
