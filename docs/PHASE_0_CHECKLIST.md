# Phase 0 Checklist

Use this checklist to confirm project bootstrap readiness before feature development.

## 1. Monorepo Foundation

- [x] Multi-module Gradle setup
- [x] Base module boundaries created
- [x] JDK 17 toolchain configured
- [x] Shared repository configuration

## 2. Development Standards

- [x] `.editorconfig` added
- [x] `.gitignore` added
- [ ] Code style and static analysis tooling (optional for next step)

## 3. Build and CI

- [ ] Add Gradle Wrapper files
- [ ] Add CI workflow to run `./gradlew test`
- [ ] Validate on clean environment

## 4. Security and Config Baseline

- [ ] Add `.env.example` (if backend secrets needed)
- [ ] Define secret handling policy
- [ ] Define branch protection strategy

## 5. Product Readiness for Phase 1

- [ ] Finalize IntelliJ plugin baseline module
- [ ] Define `EndpointIR` schema (v0)
- [ ] Create sample Spring Boot fixture project for analysis tests
