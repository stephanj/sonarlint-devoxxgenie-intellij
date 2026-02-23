# Research: Opus-Powered Code Reviews via SonarLint + DevoxxGenie

## Executive Summary

This document analyzes how Claude Opus can be leveraged for deep, AI-powered code reviews within the SonarLint-DevoxxGenie IntelliJ plugin ecosystem. The existing integration already connects SonarLint findings to DevoxxGenie's LLM chat for individual fix suggestions. This research explores architectures for a more comprehensive **Opus-powered code review** feature: batch analysis of multiple findings, whole-file or whole-project reviews, and agentic remediation workflows.

---

## 1. Current State of Integration

### 1.1 SonarLint → DevoxxGenie Bridge

The SonarLint fork (`org.sonarlint.idea.devoxxgenie`) integrates with DevoxxGenie through three mechanisms:

**A. Single-Issue Fix via Chat (`FixWithLlmAction` + `DevoxxGenieBridge`)**

- User clicks lightbulb intention or "Fix with DevoxxGenie" button on a single finding
- `FixWithLlmAction.buildPrompt()` extracts: rule name, rule key, message, file path, line number, and a ±10-line code snippet
- Prompt is built from a customizable template (`SonarLintProjectSettings.devoxxGeniePromptTemplate`)
- `DevoxxGenieBridge.sendPrompt()` sends the prompt to DevoxxGenie via reflection (`ExternalPromptService.setPromptText()`)
- DevoxxGenie opens its tool window and populates the chat input; the user reviews and submits

**B. Backlog Task Creation (`CreateDevoxxGenieTasksAction` / `CreateDevoxxGenieTasksFromNodeAction`)**

- User selects one or more findings in the report panel
- Tasks are written as markdown files with YAML frontmatter to `backlog/tasks/`
- If DevoxxGenie's `ExternalTaskService` is available, tasks are created through it instead
- Task content uses a customizable template (`SonarLintProjectSettings.devoxxGenieTaskTemplate`)

**C. Editor Intention Action (`FixWithDevoxxGenieIntentionAction`)**

- Registered in `SonarExternalAnnotator` alongside SonarQube's own AI CodeFix
- Available when cursor is on a `LiveIssue` and DevoxxGenie plugin is installed
- Delegates to `FixWithLlmAction.fixWithLlm()`

### 1.2 DevoxxGenie's Capabilities

DevoxxGenie (v0.9.17) provides:

- **20+ LLM providers** via LangChain4J (including Anthropic/Claude, OpenAI, Ollama, etc.)
- **Agent mode** with IDE-aware tools:
  - `read_file`, `write_file`, `edit_file`, `search_files` — file operations
  - `find_symbols`, `document_symbols`, `find_references`, `find_definition`, `find_implementations` — PSI-based code intelligence
  - `run_command`, `run_tests` — build/test execution
  - `parallel_explore` — parallel sub-agent exploration
  - `security_scan` — Gitleaks/OpenGrep/Trivy scanning
  - `create_task`, `update_task`, `list_tasks` — backlog management
- **Custom commands** (`/review`, `/test`, `/explain`, `/find`) with user-defined templates
- **Spec Driven Development** with Backlog.md, Kanban board, and agent loop automation
- **RAG** for semantic code search via ChromaDB
- **Streaming** and **non-streaming** execution modes

### 1.3 SonarLint's Analysis Model

SonarLint findings carry rich metadata:

| Field | Description |
|-------|-------------|
| `ruleKey` | Rule identifier (e.g., `java:S1234`) |
| `message` | Human-readable issue description |
| `range` (RangeMarker) | Precise location in editor |
| `cleanCodeAttribute` | CONSISTENT, INTENTIONAL, ADAPTABLE, RESPONSIBLE |
| `impacts` | List of (SoftwareQuality, ImpactSeverity) |
| `highestImpact` | HIGH, MEDIUM, LOW |
| `type` | BUG, VULNERABILITY, CODE_SMELL, SECURITY_HOTSPOT |
| `isOnNewCode` | Whether the issue is in new (changed) code |
| `isAiCodeFixable` | SonarQube's own AI CodeFix eligibility |
| `quickFixes` | Available automatic fixes |
| `introductionDate` | When the issue first appeared |

---

## 2. Opportunities for Opus Code Reviews

### 2.1 Why Opus?

Claude Opus excels at:
- **Deep reasoning** about complex code patterns, architectural issues, and subtle bugs
- **Large context windows** (200K tokens) enabling whole-file or multi-file analysis
- **Structured output** for producing actionable review reports
- **Nuanced understanding** of software engineering principles (SOLID, Clean Code, security best practices)

SonarLint's rule-based analysis catches well-known patterns. Opus can complement this with:
- Cross-file analysis (data flow, API contracts, architectural concerns)
- Business logic review (does the code actually do what it should?)
- Idiomatic code suggestions (language-specific best practices beyond rules)
- Explanation depth (why something is a problem, not just that it is)
- Holistic review (considering the interaction between multiple findings)

### 2.2 Review Modes

#### Mode 1: Enriched Single-Finding Fix (Enhancement of Current)

**Current:** A templated prompt with rule key + code snippet is sent to DevoxxGenie chat.

**Enhanced with Opus:**
- Include the full rule description HTML (available via `BackendService.getActiveRuleDetails()`)
- Include related findings in the same file (cluster analysis)
- Include the full file content (not just ±10 lines) for better context
- Request structured output: root cause, fix suggestion, alternative approaches, test impact
- Use Opus's reasoning to determine if the fix might introduce new issues

**Implementation approach:** Extend `FixWithLlmAction.buildPrompt()` to include richer context, or create a new `DeepReviewAction` alongside it.

#### Mode 2: Batch Finding Review

**Concept:** Review all findings for a file (or a set of files) in a single Opus conversation, producing a prioritized remediation plan.

**Data flow:**
```
ReportPanel (selected findings)
  → BatchReviewAction
  → Collect all findings + full file contents
  → Build structured prompt with all findings
  → Send to DevoxxGenie via ExternalPromptService
  → Opus produces prioritized review with fix order
```

**Why this matters:** Many SonarLint findings interact. Fixing one issue (e.g., extracting a method) may resolve several others (complexity, duplication, naming). Opus can reason about these interactions and suggest an optimal fix order.

**Prompt structure:**
```markdown
Review the following SonarQube findings for `{filePath}`.
Analyze interactions between findings and suggest a prioritized fix plan.

## Findings

1. **{ruleKey1}** (line {line1}, {severity1}): {message1}
2. **{ruleKey2}** (line {line2}, {severity2}): {message2}
...

## Full File Content
```{language}
{fileContent}
```

## Instructions
- Group related findings
- Suggest fix order (which fixes unlock or resolve others?)
- For each fix, provide the corrected code
- Flag any findings that might be false positives
- Estimate risk level of each fix (safe refactor vs behavioral change)
```

#### Mode 3: Whole-File Code Review (Beyond SonarLint Rules)

**Concept:** Use Opus as a senior code reviewer, analyzing a file for issues that SonarLint rules don't cover.

**Complementary to SonarLint:**
- **Architecture:** Does this class have too many responsibilities? Is the abstraction level appropriate?
- **Error handling:** Are edge cases covered? Are exceptions handled correctly?
- **Concurrency:** Thread safety issues, race conditions, deadlock potential
- **Performance:** Unnecessary allocations, O(n²) algorithms, cache misses
- **Security:** Business logic vulnerabilities, authorization checks, input validation beyond OWASP patterns
- **API design:** Is the public API intuitive? Are contracts clear?
- **Testability:** Is the code testable? What tests are missing?

**Implementation:** New action "Deep Code Review" in editor context menu that sends full file content + SonarLint findings to Opus via DevoxxGenie.

#### Mode 4: VCS-Changed Files Review (Pre-Commit / PR Review)

**Concept:** Review all files changed in the current VCS changeset, combining SonarLint findings with Opus's holistic review.

**Data flow:**
```
VCS Changeset (git diff)
  → Collect changed files + their SonarLint findings
  → Include diff context (what changed and why)
  → Opus reviews changes holistically
  → Output: structured review with file-by-file feedback
```

**This is the highest-value integration** because it mirrors the human code review workflow:
1. Developer makes changes
2. SonarLint catches rule violations
3. Opus reviews the changes for deeper issues
4. Developer gets a unified review before committing

#### Mode 5: Agentic Remediation (DevoxxGenie Agent Mode)

**Concept:** Use DevoxxGenie's agent mode to automatically fix SonarLint findings, with Opus reasoning about each fix.

**Data flow:**
```
SonarLint findings
  → Create backlog tasks (existing feature)
  → DevoxxGenie agent loop picks up tasks
  → Agent uses tools (read_file, edit_file, run_tests) to:
    1. Read the problematic code
    2. Understand the rule violation
    3. Apply a fix
    4. Run tests to verify
    5. Mark task as done
```

**This already partially exists** through the backlog task creation feature. The missing piece is:
- A "Fix All with Agent" action that creates tasks AND triggers the agent loop
- Agent awareness of SonarLint re-analysis (verify the fix resolved the finding)
- Feedback loop: re-run SonarLint after fixes, report remaining issues

---

## 3. Architecture Options

### Option A: Enhanced Bridge (Minimal Changes)

**Approach:** Keep the current architecture but enrich the prompts sent to DevoxxGenie.

**Changes to SonarLint fork:**
1. New `DeepReviewAction` class that builds richer prompts (full file, all findings, rule descriptions)
2. New `BatchReviewAction` that collects multiple findings
3. New `VcsReviewAction` that combines VCS changes with findings
4. Extended prompt templates with more placeholders

**Changes to DevoxxGenie:** None (uses existing `ExternalPromptService.setPromptText()`).

**Pros:** Minimal code changes, no new APIs needed, user stays in control (reviews prompt before sending).
**Cons:** Limited by `setPromptText()` API (just populates chat input), no structured response handling, no feedback loop.

### Option B: New API Surface on DevoxxGenie

**Approach:** Add new methods to `ExternalPromptService` or create a new `ExternalReviewService`.

**New DevoxxGenie API:**
```kotlin
class ExternalReviewService {
    // Execute a review prompt and get structured results
    fun submitReview(project: Project, request: ReviewRequest): CompletableFuture<ReviewResult>

    // Execute review in agent mode (reads files, applies fixes)
    fun submitAgentReview(project: Project, request: AgentReviewRequest): CompletableFuture<AgentReviewResult>
}

data class ReviewRequest(
    val findings: List<FindingData>,  // Serialized finding info
    val files: Map<String, String>,   // filePath → content
    val reviewType: ReviewType,       // SINGLE_FIX, BATCH, WHOLE_FILE, VCS_CHANGES
    val modelPreference: String?,     // e.g., "claude-opus-4-6"
    val customInstructions: String?
)

data class ReviewResult(
    val summary: String,
    val fileReviews: List<FileReview>,
    val suggestedFixes: List<SuggestedFix>,
    val overallRisk: RiskLevel
)
```

**Changes to SonarLint fork:** New actions that call the new API.
**Changes to DevoxxGenie:** New `ExternalReviewService`, new prompt strategies.

**Pros:** Structured input/output, programmatic control, model selection, background execution.
**Cons:** More code, API versioning concerns, tighter coupling.

### Option C: SonarLint-Side LLM Integration (Independent of DevoxxGenie)

**Approach:** Add LLM capabilities directly to the SonarLint fork, bypassing DevoxxGenie.

**Implementation:** Add LangChain4J dependency to SonarLint, create review service with Anthropic SDK.

**Pros:** Full control, no inter-plugin communication overhead, simpler deployment.
**Cons:** Duplicates DevoxxGenie functionality, loses DevoxxGenie's multi-provider support and agent tools, settings management burden.

### Recommendation: Option A (Short Term) + Option B (Medium Term)

**Phase 1 (Option A):** Quickly deliver value by enriching prompts within the existing bridge.
**Phase 2 (Option B):** Build a proper API surface for structured reviews and agent-mode remediation.

---

## 4. Detailed Design: Phase 1 (Enhanced Bridge)

### 4.1 New Actions

#### `DeepReviewWithOpusAction` (single file, all findings)

```kotlin
class DeepReviewWithOpusAction : AnAction("Deep Code Review with AI") {
    fun actionPerformed(e: AnActionEvent) {
        val file = currentEditorFile
        val findings = onTheFlyFindingsHolder.getIssuesFor(file)
        val fileContent = readFileContent(file)
        val prompt = buildDeepReviewPrompt(findings, fileContent, file.path)
        DevoxxGenieBridge.sendPrompt(project, prompt)
    }
}
```

#### `ReviewVcsChangesAction` (VCS changeset review)

```kotlin
class ReviewVcsChangesAction : AnAction("Review Changes with AI") {
    fun actionPerformed(e: AnActionEvent) {
        val changedFiles = getVcsChangedFiles(project)
        val findingsPerFile = changedFiles.associate { it to getFindings(it) }
        val prompt = buildVcsReviewPrompt(findingsPerFile)
        DevoxxGenieBridge.sendPrompt(project, prompt)
    }
}
```

#### `BatchFixWithAgentAction` (creates tasks + triggers agent)

```kotlin
class BatchFixWithAgentAction : AnAction("Fix All with AI Agent") {
    fun actionPerformed(e: AnActionEvent) {
        val findings = getSelectedFindings()
        // Create backlog tasks (reuse existing logic)
        createBacklogTasks(findings)
        // Send agent trigger prompt to DevoxxGenie
        val agentPrompt = buildAgentPrompt(findings)
        DevoxxGenieBridge.sendPrompt(project, agentPrompt)
    }
}
```

### 4.2 Enhanced Prompt Templates

**Deep Review Template:**
```
Perform a thorough code review of the following file.

## SonarQube Findings

{findingsList}

## Full File Content

**File:** `{filePath}`
**Language:** {language}

```{language}
{fileContent}
```

## Review Instructions

1. **Analyze each SonarQube finding** — explain why it matters and suggest a fix
2. **Identify additional issues** not caught by SonarQube rules:
   - Architecture and design concerns
   - Error handling gaps
   - Performance issues
   - Security vulnerabilities
   - Concurrency problems
   - Missing edge case handling
3. **Prioritize** all findings (SonarQube + your own) by impact
4. **Suggest a fix order** — which changes should be made first?
5. **Provide corrected code** for the top 3 most impactful issues

Format your response with clear sections and code blocks.
```

**VCS Review Template:**
```
Review the following code changes before commit.

## Changed Files and Their SonarQube Findings

{perFileSection}

## Review Instructions

1. For each changed file, review the diff and any SonarQube findings
2. Flag issues introduced by the changes (not pre-existing issues)
3. Check for:
   - Regressions or behavioral changes
   - Missing error handling for new code paths
   - Security implications of the changes
   - Test coverage gaps
4. Provide an overall assessment: APPROVE, NEEDS_CHANGES, or BLOCK
5. List specific action items for the developer
```

### 4.3 Settings Extensions

Add to `SonarLintProjectSettings`:
```java
private String deepReviewPromptTemplate = DEFAULT_DEEP_REVIEW_PROMPT_TEMPLATE;
private String vcsReviewPromptTemplate = DEFAULT_VCS_REVIEW_PROMPT_TEMPLATE;
private boolean includeFullFileInPrompt = true;
private boolean includeRuleDescriptions = false;
private int maxFilesInBatchReview = 10;
```

Add corresponding UI panel in the DevoxxGenie settings tab.

---

## 5. Detailed Design: Phase 2 (New API Surface)

### 5.1 DevoxxGenie: `ExternalReviewService`

A new project-level service in DevoxxGenie:

```java
@Service(Service.Level.PROJECT)
public final class ExternalReviewService {

    public static ExternalReviewService getInstance(@NotNull Project project) {
        return project.getService(ExternalReviewService.class);
    }

    /**
     * Submit a code review request. Executes asynchronously.
     * The review opens in a new DevoxxGenie conversation tab.
     */
    public CompletableFuture<String> submitReview(
        @NotNull String prompt,
        @Nullable String modelPreference,
        @Nullable String systemInstructions
    ) { ... }

    /**
     * Submit findings for agent-mode remediation.
     * Creates backlog tasks and optionally triggers the agent loop.
     */
    public CompletableFuture<List<String>> submitForRemediation(
        @NotNull List<Map<String, String>> findings,
        boolean triggerAgent
    ) { ... }
}
```

### 5.2 SonarLint: `OpusReviewService`

A new service coordinating review workflows:

```kotlin
@Service(Service.Level.PROJECT)
class OpusReviewService(private val project: Project) {

    fun reviewCurrentFile() { ... }
    fun reviewFindings(findings: List<LiveIssue>) { ... }
    fun reviewVcsChanges() { ... }
    fun remediateFindings(findings: List<LiveIssue>) { ... }

    private fun buildReviewContext(findings: List<LiveIssue>): ReviewContext { ... }
    private fun sendToDevoxxGenie(context: ReviewContext) { ... }
}
```

### 5.3 Response Handling

For Phase 2, DevoxxGenie could return structured review results:

```json
{
  "summary": "3 critical issues found, 2 in new code",
  "verdict": "NEEDS_CHANGES",
  "findings": [
    {
      "file": "src/main/java/Foo.java",
      "line": 42,
      "severity": "HIGH",
      "category": "SECURITY",
      "message": "SQL injection via string concatenation",
      "suggestedFix": "Use parameterized query...",
      "codeBlock": "..."
    }
  ],
  "fixOrder": ["finding-1", "finding-3", "finding-2"],
  "estimatedEffort": "30 minutes"
}
```

SonarLint could then display these in a custom review panel (similar to the existing CodeFix diff view).

---

## 6. Key Integration Points (File Reference)

### SonarLint Fork Files

| File | Purpose |
|------|---------|
| `src/.../integration/DevoxxGenieBridge.kt` | Reflection-based bridge to DevoxxGenie |
| `src/.../actions/FixWithLlmAction.kt` | Single-issue prompt builder |
| `src/.../actions/FixWithDevoxxGenieIntentionAction.kt` | Editor intention action |
| `src/.../actions/CreateDevoxxGenieTasksAction.kt` | Batch task creation (selection-based) |
| `src/.../actions/CreateDevoxxGenieTasksFromNodeAction.kt` | Task creation (node-based, with fallback) |
| `src/.../config/project/SonarLintProjectSettings.java` | Settings with prompt/task templates |
| `src/.../config/project/DevoxxGeniePanel.kt` | Settings UI panel |
| `src/.../analysis/AnalysisSubmitter.kt` | Analysis trigger and orchestration |
| `src/.../analysis/OnTheFlyFindingsHolder.kt` | Current file findings cache |
| `src/.../finding/Finding.kt` | Finding interface |
| `src/.../finding/LiveFinding.java` | Finding base class with full metadata |
| `src/.../finding/issue/LiveIssue.java` | Issue finding with type and AI fixability |
| `src/.../editor/SonarExternalAnnotator.java` | Editor annotations and intention registration |
| `src/.../ui/report/ReportPanel.kt` | Findings report with toolbar actions |
| `src/.../ui/ruledescription/RuleHeaderPanel.kt` | Rule header with Fix button |
| `src/.../core/BackendService.kt` | RPC backend for analysis and rule details |

### DevoxxGenie Files

| File | Purpose |
|------|---------|
| `src/.../service/ExternalPromptService.java` | Public API: set prompt text |
| `src/.../service/ExternalTaskService.java` | Public API: create backlog tasks |
| `src/.../service/prompt/PromptExecutionService.java` | Prompt execution orchestrator |
| `src/.../service/prompt/command/PromptCommandProcessor.java` | Command processing (/review, /test) |
| `src/.../service/prompt/command/CustomPromptCommand.java` | Custom command expansion |
| `src/.../service/agent/tool/` | Agent tools (read_file, edit_file, etc.) |
| `src/.../service/security/SecurityScannerService.java` | Security scanning integration |
| `src/.../chatmodel/ChatModelFactory.java` | LLM provider factory |
| `src/.../model/Constant.java` | Default prompts (/review, /test, /explain) |
| `src/.../model/request/ChatMessageContext.java` | Chat message context model |
| `src/.../ui/settings/DevoxxGenieStateService.java` | Plugin settings |

---

## 7. Implementation Roadmap

### Phase 1: Enhanced Single & Batch Review (2-3 weeks)

1. **`DeepReviewAction`** — Full-file review prompt with all findings
2. **Enhanced `FixWithLlmAction`** — Include rule description and broader context
3. **`BatchReviewAction`** — Multi-finding review from report panel
4. **New prompt templates** — Deep review and batch review templates in settings
5. **UI integration** — New buttons in report panel toolbar and editor context menu

### Phase 2: VCS Integration (1-2 weeks)

6. **`ReviewVcsChangesAction`** — Review changed files with findings
7. **Pre-commit review** — Optional AI review during commit (alongside existing SonarLint pre-commit check)
8. **Diff-aware prompts** — Include git diff context in review prompts

### Phase 3: Agent Remediation (2-3 weeks)

9. **DevoxxGenie `ExternalReviewService`** — New API for structured reviews
10. **`FixAllWithAgentAction`** — Create tasks + trigger agent loop
11. **Re-analysis verification** — After agent fixes, re-run SonarLint to verify
12. **Review result panel** — Display structured Opus review results in SonarLint UI

### Phase 4: Advanced Features (ongoing)

13. **Model selection** — Let users choose Opus for deep reviews, Haiku for quick fixes
14. **Review history** — Store and compare review results over time
15. **Team review standards** — Configurable review checklists per project
16. **RAG-enhanced reviews** — Use DevoxxGenie's RAG to include related code in review context
17. **MCP integration** — Use MCP tools for additional context (documentation, API specs)

---

## 8. Considerations

### Token Usage & Cost

- **Single finding fix:** ~500-1500 tokens input, ~500-1000 tokens output
- **Full file review:** ~2000-10000 tokens input, ~1000-3000 tokens output
- **Batch review (10 findings):** ~5000-20000 tokens input, ~2000-5000 tokens output
- **VCS changeset review:** ~10000-50000 tokens input, ~3000-8000 tokens output

Opus pricing makes batch/full-file reviews non-trivial for large files. Consider:
- Token budget settings in project configuration
- Model tiering: Haiku for quick fixes, Sonnet for reviews, Opus for deep analysis
- Truncation strategies for very large files

### Privacy & Security

- Code is sent to external LLM APIs (unless using local models via Ollama)
- Sensitive code should be handled per organization policy
- DevoxxGenie supports local-only models for air-gapped environments

### Latency

- Single fix prompt: 2-5 seconds (Sonnet), 5-15 seconds (Opus)
- Full file review: 10-30 seconds (Opus)
- Batch review: 15-60 seconds (Opus)
- Agent remediation: 1-5 minutes (multiple tool calls)

Background execution and streaming are essential for longer operations.

### Quality Control

- Opus suggestions should be treated as recommendations, not automatic fixes
- User review step is critical before applying any changes
- Consider a confidence score in structured output
- Track fix acceptance rate for continuous improvement

---

## 9. Conclusion

The SonarLint + DevoxxGenie ecosystem is well-positioned for Opus-powered code reviews. The existing bridge architecture (`DevoxxGenieBridge` → `ExternalPromptService`) provides a working foundation, while DevoxxGenie's agent mode and tool system offer the infrastructure for automated remediation.

The recommended approach is incremental:
1. **Start with richer prompts** (Phase 1) — immediate value, minimal code changes
2. **Add VCS integration** (Phase 2) — highest developer workflow impact
3. **Build structured API** (Phase 3) — enables agent-mode remediation and review panels
4. **Iterate on advanced features** (Phase 4) — model tiering, RAG, MCP

The key insight is that **SonarLint provides the "what" (findings with rich metadata) while Opus provides the "why" and "how" (deep reasoning about root causes and fixes)**. Together, they create a code review experience that combines the reliability of rule-based analysis with the intelligence of frontier AI models.
