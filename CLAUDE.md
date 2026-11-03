# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this project is

**QA CoPilot** scans a **Spring Boot / Spring MVC** codebase, builds a structured **API contract** (an intermediate representation, "IR"), and generates a standalone Gradle **test project** containing **Cucumber** features with **Rest Assured**-backed step definitions. Scenarios come from either a deterministic `template` generator or an `ai` generator (multi-provider LLM). The generated project runs in **dry-run** mode (no server) or against a live API, and produces an HTML API report.

It ships two ways: a **CLI fat JAR** (`generation-engine`) and an **IntelliJ plugin** (`intellij-plugin`) that wraps the same engine.

Pipeline, end to end:

1. **analysis-core** parses `*.java` with JavaParser, finds `@RestController` endpoints, enriches DTO/validation fields, reads Springdoc annotations, scans security filter chains, and extracts service-layer call info → `EndpointContract` / `ServiceContract` / `ApplicationGraph`.
2. **generation-engine** fingerprints endpoints, merges optional OpenAPI 3 specs, uses caches for incremental runs, plans multi-step **flows** (create → use → delete, negative paths), optionally ingests a **business requirements document**, synthesizes Gherkin, and tracks **contract evolution** between runs.
3. **TestFrameworkScaffolder** writes/patches the output Gradle test project (Cucumber + JUnit Platform suite, Rest Assured steps, HTML report plugin).

## Repository layout

Gradle multi-module monorepo (Kotlin DSL, root project `QA_CoPilot`, group `com.qacopilot`, version in root `build.gradle.kts`). **JDK 17** toolchain everywhere. Always use `./gradlew` from the repo root.

| Module | Language | Role |
|---|---|---|
| `analysis-core` | Java | JavaParser (+ symbol solver) scanner. Packages: `extractor` (`SpringEndpointExtractor`, `DtoFieldEnricher`, `SecurityFilterChainScanner`, `ServiceLayerExtractor`, `SpringdocAnnotationParser`), `ir` (`EndpointContract`, `ServiceContract`, `MethodContract`, `AuthContract`, `ParamBinding`, `RequestBodyContract`, `FieldContract`, …), `graph` (`ApplicationGraph`, `GraphEdge`, `EdgeKind`). |
| `generation-engine` | Java | Everything downstream of the IR; also the CLI entry point (`cli/LocalPipelineCli`, `Main-Class` of the shadow JAR). See package map below. |
| `backend-api` | Java | Placeholder library depending on the two above (`BackendApiMarker` only). Don't expect real logic here. |
| `intellij-plugin` | Kotlin | IntelliJ plugin (IC 2024.1.4, sinceBuild 241). Depends on `generation-engine` and `analysis-core`. |

### `generation-engine` packages (`com.qacopilot.generation.*`)

- `cli` – `LocalPipelineCli`: commands `sync` and `repair-gherkin`.
- `pipeline` – `QaCopilotPipeline` (orchestrates a whole sync), `PipelineSyncRequest` / `PipelineSyncResult`.
- `ai` – provider abstraction. `AIClient`, `AIClientConfig`, `AIClientFactory`, `AIProvider` (openai, anthropic, azure-openai, local-llm, hugging-face), decorators `RetryingAIClient`, `RateLimitedAIClient`, `FallbackAIClient`, `ai/cache/AIResponseCache`, and `ai/impl/*` (one client per provider). Config is resolved in `AIClientConfig` from CLI flags → env vars → `.env`.
- `openai` – older single-provider OpenAI path (`OpenAiConfig`, `OpenAiScenarioClient`) and `DotEnvLoader` (searches upward from the CWD and from the JAR location for `.env`; `QA_COPILOT_DOTENV` overrides).
- `scenario` – Gherkin generation: `ScenarioGenerator(Factory)`, `TemplateScenarioGenerator`, block sources (`TemplateEndpointBlockSource`, `OpenAiEndpointBlockSource`, `MultiModelEndpointBlockSource`, `FlowAwareBlockSource`), `GherkinBlocksProtocol` (the contract with the LLM output), `AiGherkinSanitizer`, `DomainClassifier`, `FeatureSynthesis`/`FeatureArtifact`, scenario cache types.
- `flow` – multi-step flow planning (`FlowPlanner`, `FlowPlan`, `FlowStep`, `Capture`, `DataBinding`, `Precondition`, negative-path and edge-value synthesizers, `GherkinFlowRenderer`).
- `requirements` – business requirements ingestion: parsers for txt/md/PDF/DOCX (`requirements/parser/*`, chosen by `RequirementParserFactory`), `RequirementExtractor`, `RequirementCoverageMapper`, `RequirementAwareBlockSource`, `RequirementStore`.
- `evolution` – contract change tracking (`ContractEvolutionTracker`, `ContractSnapshot`, `ContractDelta`, `EndpointChange`, `ChangeType`, `SnapshotStore`).
- `state` – everything persisted under `<output>/.qacopilot/` plus extraction glue (`ProjectEndpointExtractor`, `OpenApiSpecMerger`, `ParseCacheStore`, `ScenarioCache`, `EndpointFingerprints`, `QacopilotPaths`, writers for the JSON artifacts).
- `graph` – `ApplicationGraphBuilder`.
- `scaffold` – `TestFrameworkScaffolder`, `DomainStepDefinitionsWriter`, `CodegenVersions`.

Scaffolded support code lives as **resource templates** in `generation-engine/src/main/resources/scaffold-support/` (`ApiTestContext`, `CommonSteps`, `RestAssuredClient`, `ScenarioContextStore`, `QaCopilotHtmlReport`, `QaCopilotHtmlReportPlugin`, `cucumber.properties`). These `.java` files are **copied into generated projects**, not compiled as part of this repo's build — they reference classes (e.g. `GeneratedPayloadFactory`) that only exist in the generated project.

### IntelliJ plugin (`com.qacopilot.intellij.*`, id `io.github.piyushjoshi01.qacopilot`)

- `actions/GenerateQaTestsAction` – Tools/Project View/Editor menu entry; `QaCopilotGenerationRunner` invokes the engine.
- `ui/` – tool window (View → Tool Windows → QA CoPilot), panel, notifier.
- `run/` – "QA CoPilot tests" run configuration type, producer, settings editor, and Gradle+Cucumber (tag-based) command-line/execution.
- `settings/` – project settings, `QaCopilotConfigurable` (General, AI Provider, Business Requirements, Test Execution), `QaCopilotCredentialStore` (API keys go through the credential store, not plain settings).
- `util/` – path helpers, `SpringBootProjectHeuristic`, `IntellijAnalysisOptionsFactory`.
- `plugin.xml` in `src/main/resources/META-INF/` also holds the user-facing description and change notes — update them when shipping user-visible changes.

## Commands

```bash
./gradlew build                                   # compile + test everything (what CI runs)
./gradlew test                                    # all unit tests (JUnit 5)
./gradlew :analysis-core:test
./gradlew :generation-engine:test
./gradlew :generation-engine:test --tests '*TestFrameworkScaffolderTest'
./gradlew :generation-engine:shadowJar            # fat JAR → generation-engine/build/libs/generation-engine-<version>-all.jar
./gradlew :intellij-plugin:runIde                 # sandbox IDE with the plugin
./gradlew :intellij-plugin:buildPlugin            # distributable plugin zip
```

Run the CLI:

```bash
java -jar generation-engine/build/libs/generation-engine-*-all.jar sync \
  --spring-project /abs/path/to/spring-app \
  --output /abs/path/to/test_framework \
  --generator template            # or: ai
```

Key `sync` flags: `--generator template|ai`, `--scenario-mode incremental|regenerate-all`, `--force-scaffold`, `--ai-provider`, `--ai-model`, `--ai-api-key`, `--ai-base-url`, `--ai-api-version`, `--ai-fallback-template`, `--ai-response-cache`, `--openai-api-key`/`--openai-model` (legacy), `--requirements-doc <file>`, `--track-evolution`, `--write-ir`, `--include`/`--exclude` (Ant globs), `--no-incremental-parse`, `--no-symbol-solver`, `--analysis-jar`, `--analysis-src-root`, `--openapi-spec <file>` (repeatable), `--no-openapi-discover`. Run with `--help` for the authoritative list. `repair-gherkin --output <dir>` fixes malformed AI Gherkin.

Run a generated project:

```bash
cd /abs/path/to/test_framework
./gradlew test -Dqacopilot.dryRun=true                                       # no server
./gradlew test -Dqacopilot.dryRun=false -Dapi.base.url=http://localhost:8080 # live
```

Reports: `build/reports/tests/test/index.html` (Gradle) and `build/reports/qacopilot-api/report.html` (QA CoPilot API report).

## Configuration & secrets

- `.env` (gitignored) is loaded automatically; copy `.env.example`. Env vars: `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL`, `AI_BASE_URL`, `AI_API_VERSION`, `AI_TEMPERATURE` (0.38), `AI_MAX_TOKENS` (16384), `AI_TIMEOUT_SECONDS` (120), `AI_MAX_RETRIES` (3), `AI_RATE_LIMIT_DELAY_MS` (350); provider-specific `OPENAI_API_KEY`, `OPENAI_MODEL` (default `gpt-4o-mini`), `ANTHROPIC_API_KEY`, `AZURE_OPENAI_API_KEY`.
- **Never commit real keys, never print them, never read `.env` contents into output.** Treat generated tests as local artifacts.
- In `template` mode nothing is sent to any external service; in `ai` mode endpoint names and (optionally) the requirements doc are sent to the configured provider.

## State written to the output project

Under `<output>/.qacopilot/`: `meta.json`, `last-endpoints.json`, `api-contract.json`, `parse-cache.json`, `scenario-cache.json`, `application-graph.json`, `flow-plans.json`, plus requirement/evolution snapshots. Paths are centralized in `state/QacopilotPaths`. Features are written as one file per domain: `src/test/resources/features/<domain>/<domain>.feature`; shared steps in `stepdefs/common/CommonSteps.java`.

## Conventions & gotchas

- **Bump `CodegenVersions.STEPS_VERSION`** (`scaffold/CodegenVersions.java`) whenever anything in `resources/scaffold-support/` or generated step/support code changes. Existing test projects only get patched when the version increases (or with `--force-scaffold`). Currently 9. Update `TestFrameworkScaffolderTest` expectations if they assert on generated output.
- Changing the IR (`EndpointContract` etc.) usually requires matching changes in `state/EndpointContractSerde`, fingerprints (`EndpointFingerprints`) so caches invalidate correctly, and the contract writers.
- Changing the LLM output format means editing `GherkinBlocksProtocol` **and** `AiGherkinSanitizer` together.
- New AI provider: add to `AIProvider`, implement `AIClient` in `ai/impl/`, wire it in `AIClientFactory` and key resolution in `AIClientConfig`, then expose it in the plugin's AI settings UI.
- PDF/DOCX requirement parsing uses PDFBox / POI which are `compileOnly` and loaded reflectively — they are *not* in the shadow JAR by default. Don't add hard references outside `requirements/parser/`.
- Code style (`.editorconfig`): UTF-8, LF, 4-space indent, final newline; Kotlin uses the official style.
- **Compiled output is tracked in git**: `*/bin/` directories (IDE-produced `.class` files and copied sources) are committed and show up as modified in `git status`. Never edit files under `bin/` or `build/`; change `src/` only, and don't stage `bin/` churn unless asked.
- CI (`.github/workflows/ci.yml`) runs `gradle build` on JDK 17 for pushes/PRs to `main`.
- `README.md` references `fixtures/`, `scripts/local-sync-demo.sh`, and `docs/`, which do not currently exist in the repo. For a manual check, point `--spring-project` at any Spring Boot app.
- Tests: JUnit 5 in `analysis-core/src/test` (extractor, security scanner) and `generation-engine/src/test` (flow, graph, openai, pipeline, scaffold, scenario, state). Add a test next to the code you change in the matching package.
- The working tree is typically mid-feature (multi-model AI, service-layer/application-graph/flow planning). Check `git status` before assuming a file is committed.
