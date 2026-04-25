# QA CoPilot

QA CoPilot scans a **Spring Boot / Spring MVC** codebase, builds a structured **API contract**, and generates **Cucumber** features with **Rest Assured**-backed steps. You get a standalone Gradle **test project** that can run in **dry-run** mode (no server) or against a live API.

---

## What you get

| Output | Purpose |
|--------|---------|
| **Gherkin scenarios** | One feature file **per domain** under `src/test/resources/features/<domain>/<domain>.feature` (domain from controller or URL); shared steps in `stepdefs/common/CommonSteps.java` |
| **`api-contract.json`** | Machine-readable contract (endpoints, security hints, OpenAPI merge metadata) under `.qacopilot/` |
| **Gradle test report** | Standard report at `build/reports/tests/test/index.html` after `./gradlew test` |
| **API HTML report** | Executive summary (counts, timestamp, dry-run vs live), table of contents, per-scenario cards: base URL, full URL, path template, **path & query params**, request body, side-by-side **expected vs actual** HTTP status and body, match/mismatch pills — `build/reports/qacopilot-api/report.html` |

---

## Repository layout

```text
QA_CoPilot/
  analysis-core/       # JavaParser-based Spring scanner → EndpointContract IR
  generation-engine/   # Feature synthesis, OpenAPI merge, CLI, test-framework scaffolder
  backend-api/         # Small library stub (orchestration placeholder)
  intellij-plugin/     # IntelliJ plugin (generate, tool window, Gradle+Cucumber run configs with tags)
  fixtures/            # minimal-spring-api (demo sources for local scans)
  scripts/             # local-sync-demo.sh
  docs/                # Optional checklists / notes
```

The **generated test framework** is usually **outside** this repo (any directory you pass as `--output`). It can also live next to this repo on your machine.

---

## Requirements

- **JDK 17**
- **Gradle Wrapper** (included) — always prefer `./gradlew` from the `QA_CoPilot/` directory

---

## Quick start (inside this repo)

### 1. Build the fat JAR

```bash
cd QA_CoPilot   # this monorepo root (contains gradlew)
./gradlew :generation-engine:shadowJar
```

Artifact: `generation-engine/build/libs/generation-engine-*-all.jar`

### 2. Sync a Spring project into a test folder

```bash
java -jar generation-engine/build/libs/generation-engine-0.1.0-SNAPSHOT-all.jar sync \
  --spring-project /absolute/path/to/your-spring-app \
  --output /absolute/path/to/test_framework \
  --generator template
```

Use **`--generator ai`** for OpenAI-written scenarios (needs an API key — see below).

### 3. Run tests

```bash
cd /absolute/path/to/test_framework
./gradlew test -Dqacopilot.dryRun=true
```

Then open:

- `build/reports/tests/test/index.html` — Gradle / Cucumber listing  
- `build/reports/qacopilot-api/report.html` — professional API report (summary, TOC, URL/params/body, expected vs actual with match indicators)

Against a real server:

```bash
./gradlew test -Dqacopilot.dryRun=false -Dapi.base.url=http://localhost:8080
```

---

## `sync` command (cheat sheet)

| Flag | Meaning |
|------|---------|
| `--spring-project <dir>` | Root of the Java/Spring app to scan |
| `--output <dir>` | Generated Gradle test project |
| `--generator template` \| `ai` | Deterministic scenarios vs OpenAI |
| `--scenario-mode incremental` \| `regenerate-all` | Reuse or rebuild cached Gherkin |
| `--force-scaffold` | Rewrite step/support sources (use after upgrading CoPilot when steps version bumps) |
| `--openai-api-key` / env `OPENAI_API_KEY` | For `--generator ai` |
| `--openai-model` / env `OPENAI_MODEL` | Default model (e.g. `gpt-4o-mini`) |
| `--ai-fallback-template` | If a chunk fails against OpenAI, use template for that chunk |
| `--include` / `--exclude` | Ant-style globs for which `*.java` files to scan |
| `--no-incremental-parse` | Full re-parse (ignore `.qacopilot/parse-cache.json`) |
| `--no-symbol-solver` | Lighter analysis (no JavaParser type solver) |
| `--analysis-jar` / `--analysis-src-root` | Extra classpath / sources for symbol resolution |
| `--openapi-spec <file>` | Extra OpenAPI 3 JSON/YAML to merge (repeatable) |
| `--no-openapi-discover` | Do not auto-pick `openapi.*` under resources/build |

State written under **`--output/.qacopilot/`**: `scenario-cache.json`, `parse-cache.json`, `api-contract.json`, `last-endpoints.json`, `meta.json`.

---

## OpenAI and `.env`

For **`--generator ai`**, set a key in the environment or in a `.env` file (loaded automatically when present):

```bash
export OPENAI_API_KEY='your-key'
export OPENAI_MODEL='gpt-4o-mini'
```

Copy `QA_CoPilot/.env.example` to `.env` and fill values. **Never commit real keys.** `.env` is listed in `.gitignore`.

You can also pass the key once on the CLI:

```bash
java -jar …/generation-engine-*-all.jar sync \
  --spring-project /path/to/app \
  --output /path/to/test_framework \
  --generator ai \
  --openai-api-key "$OPENAI_API_KEY"
```

---

## One-command demo (fixture in this repo)

From `QA_CoPilot/`:

```bash
chmod +x scripts/local-sync-demo.sh
./scripts/local-sync-demo.sh
```

This uses `fixtures/minimal-spring-api`, writes **`local-demo-output/`** (gitignored), and runs tests in dry-run mode.

- Override output: `QACOPILOT_DEMO_OUTPUT=/path ./scripts/local-sync-demo.sh`
- Use AI: `QACOPILOT_DEMO_GENERATOR=ai` plus `OPENAI_API_KEY`

---

## How it works (short)

1. **analysis-core** walks `*.java`, finds `@RestController` endpoints, optional DTO/validation hints, Springdoc/OpenAPI annotations, and security filter-chain heuristics.
2. **generation-engine** fingerprints endpoints, optionally merges **OpenAPI 3** files, caches parse results, and synthesizes Gherkin (template or OpenAI).
3. **TestFrameworkScaffolder** materializes a minimal Gradle project: Cucumber + JUnit Platform suite, Rest Assured steps, and the **HTML API report** plugin.

After upgrading this repo, if step definitions change, run **`sync --force-scaffold`** once so your test project picks up new support code (`CodegenVersions.STEPS_VERSION` in `generation-engine`).

---

## Development

```bash
./gradlew test
```

Repair invalid AI-generated Gherkin structure (optional):

```bash
java -jar generation-engine/build/libs/generation-engine-*-all.jar repair-gherkin \
  --output /path/to/test_framework
```

---

## Modules (details)

| Module | Role |
|--------|------|
| **analysis-core** | `SpringEndpointExtractor`, security scanner, DTO enricher, `EndpointContract` IR |
| **generation-engine** | `ProjectEndpointExtractor`, OpenAPI merge, `LocalPipelineCli`, scaffolder, AI client |
| **backend-api** | Depends on analysis + generation; placeholder for future services |
| **intellij-plugin** | IDE: **View → Tool Windows → QA CoPilot** (generate, last run, tag-based `./gradlew test`), **Run/Debug** configuration type *QA CoPilot tests*, settings for default API URL and Cucumber tags |

---

## License / status

This is a **foundation / tooling** codebase. Treat generated tests and API keys as **local secrets**; review Gherkin before relying on it in CI.
