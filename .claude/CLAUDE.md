# Claude Code – Working Agreement

## Workflow: Plan → Write Tests → Implement → Verify

### 1. Plan First
Before writing any code, produce an explicit plan:
- Restate the requirement in your own words to confirm understanding.
- List every file you intend to create or modify.
- Identify risks or unknowns upfront.
- Wait for confirmation **only** when the plan reveals an ambiguity that cannot be resolved from context. Otherwise proceed immediately.

### 2. Write Tests
Before implementing, write tests that should cover the new behaviour.
Only write tests for the new behaviour.

### 3. Implement
Follow the plan. Do not add scope beyond what was agreed.

### 4. Verify Every Requirement
After implementation, go through each requirement one-by-one and confirm it is satisfied:
- Run the relevant tests (unit, integration, or build check) for every changed module.
- If a requirement **cannot** be fulfilled, do **not** silently skip it — document it immediately (see *Unresolved Requirements* below).

---

## No Code Without Verification (Testing)

- Every new behaviour must be covered by at least one automated test before the task is considered done.
- Every bug fix must be accompanied by a regression test that fails before the fix and passes after.
- Run `./gradlew :modules:<module>:test` (or the appropriate Gradle task) and confirm a green build before marking work complete.
- If a test cannot be written for a legitimate reason, document it in `docs/unresolved.md` with an explanation.

---

## Automatic Bug Fixing

- When a test or build step fails, attempt to fix the root cause immediately — do **not** ask for permission.
- Apply the fix, re-run the verification, and continue until green.
- If the same failure persists after **three** fix attempts, stop, log the issue in `docs/unresolved.md`, and surface a concise summary.

---

## Unresolved Requirements → `docs/unresolved.md`

When a requirement or bug cannot be resolved, append an entry to `docs/unresolved.md`:

```markdown
## [YYYY-MM-DD] <Short title>

**Requirement / Bug:**
<What was requested or what failed>

**Root Cause (if known):**
<Why it cannot be resolved right now>

**Attempted Fixes:**
1. <What was tried>
2. …

**Suggested Next Step:**
<What a human engineer should investigate>
```

Create the file if it does not exist. Never delete existing entries.

---

## Project Structure

```
.                        ← Repository root (multi-project Gradle setup)
├── build.gradle.kts     ← Root build file (shared plugins, dependency versions)
├── settings.gradle.kts  ← Gradle settings (declares all subprojects)
├── modules/             ← One subdirectory per microservice
│   └── <service>/
│       ├── build.gradle.kts
│       └── src/
└── docs/                ← Architecture Decision Records, API docs, unresolved issues
    └── unresolved.md
```

### Conventions
- All microservices live under `modules/{service-name}`. Never place service code in the root.
- Shared configuration (dependency versions, plugin setup) belongs in the **root** `build.gradle.kts` or in `buildSrc` / a version catalog.
- `settings.gradle.kts` must include every module via `include(":modules:<service>")`.
- Architecture decisions go in `docs/adr/` as numbered Markdown files (`ADR-001-<title>.md`).
- API contracts live in `/docs/api/`.
- Unit tests extend `AnyFlatSpec with Matchers` — no `@Test` annotations, no `: Unit` requirement
- Integration tests use `@QuarkusTest` with JUnit 5 — `@Test` methods must be explicitly typed `: Unit`
- Always exclude scala-library from Quarkus deps to avoid Scala 2 conflicts

## Coverage Conventions
- Branch coverage must be at least 90% - unless there is a good reason not to.
- Line coverage must be at least 95% - unless there is a good reason not to.
- Method coverage must be at least 90% - unless there is a good reason not to.
- To check coverage use jacoco-reporter/scoverage_coverage_gaps.py modules/{service}/build/reports/scoverageTest/scoverage.xml
- IMPORTANT: modules/{service}/build/reports/scoverage/scoverage.xml is not used for coverage TEST calculation. Do not use it.

## Agent Routing Rules

### Use agents in PARALLEL when:
- Tasks touch different, independent microservices
- No shared files or state between tasks
- Example: "implement service-user AND service-orders simultaneously"

### Use agents SEQUENTIALLY when:
- Tasks have dependencies (architect → implementer → test-writer)
- Shared API contracts are involved
- Example: design API first, then implement, then test

## Quick-Reference Checklist

Before considering any task done, confirm:

- [ ] Plan was written and requirements restated
- [ ] All planned files were created / modified
- [ ] Automated tests cover the new behaviour
- [ ] `./gradlew build` (or scoped task) is green
- [ ] Each requirement has been explicitly verified
- [ ] Any unresolved items are logged in `docs/unresolved.md`