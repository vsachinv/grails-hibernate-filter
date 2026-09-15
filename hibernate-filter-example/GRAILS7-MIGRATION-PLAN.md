# Grails 7.x Migration Plan — hibernate-filter-example

**Generated:** 2026-09-15
**Source:** Grails 6.2.0 / Java 11 (build files) on a Java 17 machine
**Target:** Apache Grails 7.0.16 / Java 17 / Spring Boot 3.5.16 / Hibernate 5.6.15-jakarta
**Status:** ✅ **Executed 2026-09-15.** All four mechanical changes applied. Verified: `grails-layout`, `grails-i18n`, and `asset-pipeline-grails` on the runtime classpath; 19 specs pass; `GET /user` renders through `main.gsp` with no leaked `grailsLayout:` or `asset:` tags; `/assets/application.css`, `/assets/application.js`, `/assets/grails.svg`, and `/assets/favicon.ico` all return 200 with content; `/user.json` still hides the inactive seeded user.

## Context

This application is the test harness for `hibernate-filter-plugin` and was moved to Grails 7.0.16 alongside the plugin (commit `bf82a83`). It compiles, its 7 unit and 12 integration specs pass, and `/user.json` serves filtered data. This plan is the application-level audit the `grails7-app-upgrade` skill prescribes, run after the fact. It found two real gaps that the earlier pass missed.

## Executive Summary

- Total dependencies analyzed: 21 (18 Grails/Spring modules, 1 custom plugin, 2 test libs)
- Compatible, version bump only: 21 (all already applied)
- Compatible with minor changes: 0
- Breaking changes / code work: 0
- Custom plugins to upgrade separately: 1 (`hibernate-filter-plugin`, already upgraded via `grails7-plugin-upgrade`)
- **Gaps found by this audit: 2** (missing core modules, stale nested Gradle wrapper)
- Estimated effort: Low

## Dependency Inventory (current state, post plugin upgrade)

### Source environment (as of commit `8cbbf7c`, before any upgrade)
| Setting | Value |
|---------|-------|
| Grails | 6.2.0 |
| Java toolchain | sourceCompatibility 11 |
| Groovy | 3.0.x |
| Spring Boot | 2.7.x |
| Gradle wrapper | 7.6.4 (root) and 7.6.4 (stale copy inside this subproject) |

### Grails plugins
| Plugin | Current | Type | Grails 7 status |
|--------|---------|------|-----------------|
| hibernate-filter-plugin | project(':hibernate-filter-plugin') 7.0.0-M1 | 🏢 Custom | ✅ Upgraded (see `../UPGRADE_PLAN.md`) |
| gsp | org.apache.grails:grails-gsp | Official | ✅ applied |
| hibernate5 | org.apache.grails:grails-data-hibernate5 | Official | ✅ applied |
| scaffolding | org.apache.grails:grails-scaffolding | Official | ✅ applied |
| geb | removed | Official | 🚫 Removed: no Geb specs exist; webdriver download blocked tests |

### Third-party libraries
| Library | Current | Status |
|---------|---------|--------|
| org.hibernate:hibernate-core-jakarta | BOM 5.6.15.Final | ✅ |
| com.h2database:h2 | BOM | ✅ |
| org.apache.tomcat:tomcat-jdbc | BOM | ✅ |
| org.fusesource.jansi:jansi | BOM | ✅ |
| net.bytebuddy:byte-buddy (+agent) | BOM, test | ✅ |
| Selenium (6 artifacts) | removed | 🚫 Removed with Geb |

### Build plugins
| Plugin | Current | Status |
|--------|---------|--------|
| org.apache.grails.gradle.grails-web | BOM | ✅ |
| org.apache.grails.gradle.grails-gsp | BOM | ✅ |
| cloud.wondrify.asset-pipeline | BOM 5.0.34 | ✅ (views use `<asset:*>`) |
| com.github.erdi.webdriver-binaries | removed | 🚫 Removed with Geb |
| application (Gradle core) | added | ✅ required explicitly in Grails 7 |

## Compatibility Assessment

### Starter POM diff (§0a of the skill)

`org.apache.grails:grails-dependencies-starter-web:7.0.16` versus the hand-listed set in `build.gradle`:

| Module | In starter | Declared here | On runtime classpath now | Used by this app |
|--------|-----------|---------------|--------------------------|------------------|
| grails-async | yes | no | **no** | no |
| grails-cache | yes | no | **no** | no |
| grails-codecs | yes | no | yes (transitive) | yes (GSP encoding) |
| grails-controllers | yes | no | yes (transitive) | yes |
| grails-converters | yes | no | yes (transitive) | yes (`respond`) |
| grails-domain-class | yes | no | yes (transitive) | yes |
| grails-encoder | yes | no | yes (transitive) | yes |
| grails-events | yes | no | **no** | no |
| **grails-layout** | yes | no | **no** | **yes**, `main.gsp` uses `g:layoutTitle/Head/Body` |
| grails-i18n | **no** (never in starter) | no | **no** | **yes**, `g:message` in views and `message()` in `UserController` |
| everything else | yes | yes | yes | yes |

This is exactly the failure mode the skill warns about: the build compiles, tests pass, and the JSON endpoint works, yet the HTML pages are broken at runtime.

**Observed on 2026-09-15 with `bootRun` against the current build:**

- `GET /user` returns HTTP 200, but the response is 1.6 KB of undecorated content. The raw `<grailsLayout:captureHead>` and `<grailsLayout:captureBody>` tags leak into the HTML, `main.gsp` is never applied, and no stylesheet, navbar, or `<head>` is emitted. Cause: `grails-layout` absent.
- `g:message` resolved to the right text ("User List", "First Name"). That works only because Spring Boot's own `MessageSourceAutoConfiguration` happens to find `messages.properties` on the classpath root. Grails locale resolution and the `LocaleResolver` bean come from `grails-i18n`, which is absent, so this is accidental rather than configured.

### Version pin sweep (§2a)

`gradle.properties` carries no library pins. `build.gradle` carries no `dependencyManagement` block and no hard-coded versions apart from the BOM platform. Nothing to remove.

### Configuration audit (application.yml / logback.xml)

| Key | Owner | Action |
|-----|-------|--------|
| `dataSource.jmxExport`, `dbCreate`, `formatSql`, `pooled` | Grails | keep |
| `hibernate.cache.*` | Grails/Hibernate | keep |
| `grails.mime.*`, `grails.views.*`, `grails.controllers.defaultScope` | Grails | keep |
| `server.*`, `spring.*`, `management.*` | none present | nothing to rename |
| `logback.xml` `ColorConverter`, `WhitespaceThrowableProxyConverter` | Spring Boot 3.5 | both classes still exist; keep |

### javax → jakarta

Zero `javax.*` imports in Groovy source. One servlet request attribute string in `error.gsp` was already renamed to `jakarta.servlet.error.exception`.

### Groovy 3 → 4

No `transient` methods, no `javax.xml` usage, no XML class references. All sources compile under Groovy 4 today.

### Found during execution: asset-pipeline runtime taglib missing

After adding `grails-layout`, the decorated page exposed a third gap: `<asset:link>` and `<asset:stylesheet>` leaked raw into the HTML. The Grails 6 build had `runtimeOnly "com.bertramlabs.plugins:asset-pipeline-grails"`, which is the Grails plugin that supplies the `asset:` taglib and serves `/assets/**`. The plugin upgrade replaced it with only the `cloud.wondrify.asset-pipeline` Gradle plugin (build-time processing) and `assets "org.apache.grails:grails-dependencies-assets"` (webjars for the Gradle `assets` configuration). Neither puts the taglib on the runtime classpath. Fix: `runtimeOnly "cloud.wondrify:asset-pipeline-grails"`, version managed by the asset-pipeline BOM that `grails-bom` imports.

## Source Code Changes Required

### Mechanical (auto-apply during execution)

| # | File | Change | Category |
|---|------|--------|----------|
| 1 | `build.gradle` | Replace the hand-listed Grails/Spring modules with `org.apache.grails:grails-dependencies-starter-web` and `testImplementation "org.apache.grails:grails-dependencies-test"`. Add `org.apache.grails.i18n:grails-i18n` explicitly (not in the starter). Keep `grails-scaffolding`, `grails-data-hibernate5`, `hibernate-core-jakarta`, the plugin project dependency, `grails-testing-support-core`, byte-buddy | Build |
| 2 | `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Delete the stale Gradle 7.6.4 wrapper committed inside this subproject. It is a Gradle multi-project build; the root wrapper (8.14.4) is the only valid entry point. Running `./gradlew` from this directory today fails with the Java 17 / Gradle 7 incompatibility | Build |
| 3 | `grails-cli.yml` | Update `features` list to drop `geb`, `grails-console` unchanged; purely informational for the Grails CLI. Optional | Metadata |
| 4 | `build.gradle` | Add `runtimeOnly "cloud.wondrify:asset-pipeline-grails"` (found during execution, see above) | Build |

### Logic (require approval during execution)

None. No method bodies, queries, security config, or bean wiring change.

## Custom Plugins Requiring Separate Upgrade

- `hibernate-filter-plugin`: already upgraded to 7.0.0-M1 via `grails7-plugin-upgrade`. Nothing further.

## Verification Steps

1. `./gradlew hibernate-filter-example:dependencies --configuration runtimeClasspath | grep -E "grails-(layout|i18n)"` must show both.
2. `./gradlew hibernate-filter-example:test hibernate-filter-example:integrationTest` — 19 specs.
3. `./gradlew hibernate-filter-example:bootRun`, then:
   - `GET /user.json` returns only active users (filter path).
   - `GET /user` returns HTTP 200 HTML containing the layout's `<nav` markup and a resolved message, not a `g:message` tag error (layout + i18n path).
4. `cd hibernate-filter-example && ls gradlew` must fail: the nested wrapper is gone.

## Known Risks

- None security-, crypto-, or PII-related. The example holds synthetic seed data only.
- `grails-dependencies-starter-web` adds `grails-async`, `grails-cache`, and `grails-events`, which the app does not use. This matches the canonical Grails 7 application template; the cost is jar size only.
