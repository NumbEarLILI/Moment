# Moment MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable Android MVP for Moment: capture daily fragments, generate a rule-based diary draft, edit/save it, and browse saved diaries.

**Architecture:** Create a single-module Kotlin Android app using package-level layering. Domain logic stays independent and unit-tested; persistence uses Room repositories; Compose screens interact with ViewModels and use cases.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Room, Hilt, Coroutines/Flow, Kotlin serialization, JUnit, Gradle Wrapper.

---

## File Structure

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/wrapper/*`: Gradle Android project.
- `app/build.gradle.kts`: Android app module configuration and dependencies.
- `app/src/main/AndroidManifest.xml`: Application, activity, and theme metadata.
- `app/src/main/java/com/example/moment/MomentApplication.kt`: Hilt application entry point.
- `app/src/main/java/com/example/moment/MainActivity.kt`: Compose host activity.
- `app/src/main/java/com/example/moment/domain/model/*`: Pure Kotlin models.
- `app/src/main/java/com/example/moment/domain/generator/*`: Diary generator interface and rule implementation.
- `app/src/main/java/com/example/moment/domain/repository/*`: Repository interfaces.
- `app/src/main/java/com/example/moment/domain/usecase/*`: Fragment and diary use cases.
- `app/src/main/java/com/example/moment/data/local/*`: Room database, DAOs, converters, entities.
- `app/src/main/java/com/example/moment/data/repository/*`: Repository implementations and mappers.
- `app/src/main/java/com/example/moment/di/AppModule.kt`: Hilt bindings.
- `app/src/main/java/com/example/moment/ui/*`: Navigation, screens, ViewModels, theme.
- `app/src/test/java/com/example/moment/domain/*`: Unit tests for generator and use cases.

## Task 1: Project Skeleton

- [ ] Create Gradle wrapper and Android module files.
- [ ] Add manifest, application class, main activity, and Compose theme.
- [ ] Verification: run `./gradlew --version` and `./gradlew :app:compileDebugKotlin`.

## Task 2: Domain Models and Diary Generation

- [ ] Write failing unit tests for `RuleBasedDiaryGenerator`.
- [ ] Implement `Mood`, `LifeFragment`, `DiaryDraft`, `DiaryEntry`, `DiaryGenerator`, and `RuleBasedDiaryGenerator`.
- [ ] Verification: run `./gradlew :app:testDebugUnitTest --tests '*RuleBasedDiaryGeneratorTest'`.

## Task 3: Use Cases and In-Memory Test Fakes

- [ ] Write failing unit tests for adding fragments and generating diary drafts from repository data.
- [ ] Implement repository interfaces and use cases: `AddFragmentUseCase`, `DeleteFragmentUseCase`, `ObserveTodayFragmentsUseCase`, `GenerateDiaryDraftUseCase`, `SaveDiaryUseCase`, `ObserveDiaryEntriesUseCase`.
- [ ] Verification: run `./gradlew :app:testDebugUnitTest`.

## Task 4: Room Persistence

- [ ] Implement Room entities, DAOs, type converters, database, mappers, and repository implementations.
- [ ] Bind dependencies in Hilt.
- [ ] Verification: run `./gradlew :app:compileDebugKotlin`.

## Task 5: Compose MVP Screens

- [ ] Implement navigation routes for home, capture, preview, history, and detail.
- [ ] Implement ViewModels and Compose screens for fragment capture, timeline, diary generation/edit/save, and history/detail.
- [ ] Verification: run `./gradlew :app:assembleDebug`.

## Task 6: Documentation and Final Verification

- [ ] Update README with build/test commands and MVP feature list.
- [ ] Run `./gradlew testDebugUnitTest assembleDebug`.
- [ ] Run `git diff --check`.
- [ ] Commit and push the finished implementation.
