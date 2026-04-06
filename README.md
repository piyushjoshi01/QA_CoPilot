# QA_CoPilot

Monorepo foundation for QA_CoPilot, focused first on IntelliJ plugin + Spring Boot analysis.

## Current Phase

This repository is set up for **Phase 0 (project foundation)**.

## Monorepo Modules

- `analysis-core`: Static analysis engine foundation (Spring Boot parsing and IR extraction).
- `generation-engine`: Test case generation layer (Gherkin + step mapping).
- `backend-api`: SaaS-facing backend orchestration foundation.
- `intellij-plugin`: IntelliJ plugin app module.

## Local Requirements

- JDK 17
- Gradle 8.x (or Gradle Wrapper once added)
- IntelliJ IDEA

## Suggested Next Milestones

1. Add Gradle Wrapper and verify `./gradlew build`.
2. Turn `intellij-plugin` into a runnable IntelliJ Platform plugin.
3. Implement first endpoint extractor in `analysis-core`:
   - detect `@RestController`
   - parse `@GetMapping`, `@PostMapping`
   - emit minimal `EndpointIR` JSON.
4. Wire plugin action: "Analyze Spring Controllers".

## Repository Layout

```text
QA_CoPilot/
  analysis-core/
  generation-engine/
  backend-api/
  intellij-plugin/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
```