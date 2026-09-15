# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A fork of the Grails Hibernate Filter plugin, maintained to track current Grails/GORM/Hibernate versions. It lets domain classes declare Hibernate `@FilterDef`-style filters via a `static hibernateFilters = { ... }` closure and toggle them at runtime. Gradle multi-project with two subprojects:

- `hibernate-filter-plugin/` — the published plugin (`org.grails.plugins:hibernate-filter-plugin`). Version lives in its `gradle.properties`.
- `hibernate-filter-example/` — a Grails app that depends on the plugin via `project(':hibernate-filter-plugin')`. Its integration specs are the end-to-end proof that filters reach the database.

Tests live in two places. Plugin unit specs in `hibernate-filter-plugin/src/test/groovy/` mock Hibernate's `InFlightMetadataCollector`, `PersistentClass`, and `Session` and cover the DSL builder, second pass, binder, interceptor, utils, and proxy in isolation. `TestFixtures.groovy` there holds `FilteredDomain`, whose static `hibernateFilters` closure is reassigned per feature. Because `DefaultHibernateFiltersHolder` is global static state, every spec clears it in `setup` and `cleanup`.

Current stack: Apache Grails 7.0.16 (`grailsVersion` in each subproject's `gradle.properties`), Spring Boot 3.5, GORM 9, Hibernate 5.6.15 via `org.apache.grails:grails-data-hibernate5` and `org.hibernate:hibernate-core-jakarta`, Groovy 4, Java 17, Gradle 8.14.4 wrapper. Each subproject has its own `buildscript {}` block that imports `org.apache.grails:grails-bom`; there is no `buildSrc` and no `pluginManagement`. All Grails, Spring, and Hibernate versions come from the BOM, so do not pin them.

Hibernate 5 is deliberate. `HibernateFilterBuilder` uses `TypeResolver.basic()` and the Hibernate 5 `FilterDefinition` constructor, both gone in Hibernate 6, so Grails 7.1+ needs a port of the metadata code first. See `UPGRADE_PLAN.md` §9. Never add `org.hibernate:hibernate-ehcache`: it depends on the non-jakarta `hibernate-core` and puts two copies of Hibernate on the classpath.

The build needs a Java 17 daemon. If Gradle picks up a Java 11 JVM (SDKMAN `current` pointing elsewhere), it fails at configuration with "Run this build using a Java 17 or newer JVM"; set `JAVA_HOME` or `org.gradle.java.home`.

## Commands

Run from the repo root.

```bash
./gradlew hibernate-filter-plugin:jar                 # build plugin jar
./gradlew hibernate-filter-plugin:publishToMavenLocal  # publish to ~/.m2 for consumption by other apps
./gradlew hibernate-filter-example:bootRun             # run example app (H2 in-memory)
./gradlew hibernate-filter-plugin:test                 # plugin unit specs (mocked Hibernate metadata/session)
./gradlew hibernate-filter-plugin:test --tests 'org.grails.plugin.hibernate.filter.HibernateFilterBuilderSpec'
./gradlew hibernate-filter-example:test                # example unit tests (src/test)
./gradlew hibernate-filter-example:integrationTest     # integration tests (src/integration-test) — these exercise the plugin
./gradlew hibernate-filter-example:integrationTest --tests 'hibernate.filter.example.FilterSpec'
./gradlew hibernate-filter-example:integrationTest --tests 'hibernate.filter.example.FilterSpec.testDefaultFilters'
```

Publishing to GitHub Packages / Nexus reads credentials from Gradle properties (`gpr.user`/`gpr.key`, `nexusUsername`/`nexusPassword`/`nexusUrl`, `isNexusUrlInsecure`) or the equivalent env vars; see `hibernate-filter-plugin/build.gradle`.

## Architecture: how a `hibernateFilters` closure becomes a Hibernate filter

All plugin code is in `hibernate-filter-plugin/src/main/groovy/org.grails.plugin.hibernate.filter/` (note: the directory is literally named with dots, not nested folders). The flow spans several classes and two phases:

**Phase 1 — Hibernate metadata build (startup, before SessionFactory exists)**

1. `HibernateFilterGrailsPlugin.doWithSpring()` registers a `metadataContributor` bean (`HibernateFilterBinder`). GORM's `hibernate5` plugin picks up any `MetadataContributor` bean and calls it during metadata building.
2. `HibernateFilterBinder.contribute()` registers a `HibernateFilterSecondPass`. A Hibernate `SecondPass` is required because entity/collection bindings for all classes must exist before filters can be attached to them.
3. `HibernateFilterSecondPass.doSecondPass()` walks every `PersistentEntity` in the GORM `HibernateMappingContext`, and for each with a static `hibernateFilters` field instantiates a `HibernateFilterBuilder`.
4. `HibernateFilterBuilder` runs the closure with itself as `DELEGATE_ONLY` delegate. Every call inside the closure (e.g. `activeFilter(condition: 'active=true', default: true)`) hits `methodMissing`, which routes to `addFilter(name, options)`. `addFilter`:
   - registers a `FilterDefinition` on the `InFlightMetadataCollector` if the name is new (parsing `:param` placeholders and mapping them to Hibernate basic types from `types`/`paramTypes`);
   - attaches the filter to the `PersistentClass`, or to a collection binding when `collection:` is given (walking up parent entities for subclasses), using `addManyToManyFilter` when `joinTable: true`;
   - records side-effects in the static `DefaultHibernateFiltersHolder`: `default: true` filters, `default: { -> ... }` callbacks, and `aliasDomain:` proxies.

Supported option keys: `condition`, `default` (boolean or Closure), `collection`, `joinTable`, `types`/`paramTypes` (comma-separated), `aliasDomain`.

**Phase 2 — runtime**

- `HibernateFilterGrailsPlugin.doWithDynamicMethods()` calls `HibernateFilterUtils.addDomainClassMethods` on every domain class, adding static metaclass methods: `withHibernateFilter(name){}`, `withHibernateFilters{}` (all defaults), `withoutHibernateFilter(name){}`, `withoutHibernateFilters{}`, `enableHibernateFilter(name)`, `disableHibernateFilter(name)`. The `with*` variants restore prior enabled/disabled state in `finally`.
- It also injects each `aliasDomain` proxy (`HibernateFilterDomainProxy`) as a metaclass property on every Grails artefact, so `EnabledFoo.list()` transparently runs `Foo.list()` inside `withHibernateFilter('fooEnabledFilter')`.
- `HibernateFilterInterceptor` (in `grails-app/controllers/`, registered as a Spring bean with `matchAll()`) enables all `DefaultHibernateFiltersHolder.defaultFilters` on the current session before each controller action. Default filters therefore apply to web requests, not to arbitrary service/background code — use `withHibernateFilters {}` there.

`DefaultHibernateFiltersHolder` is global static state populated at metadata time and read at runtime; it is never cleared, which matters for tests that reload the context.

## Example app conventions

Domain classes in `hibernate-filter-example/grails-app/domain/hibernate/filter/example/` each demonstrate a feature (`Foo` covers most options, `Student`/`Course`/`Loan`/`Pen` cover collection and join-table filters, `SubFoo` covers inheritance). Integration specs are `@Integration @Rollback` Spock specs that create data inside `when:` (not `setup()`) because the Hibernate session only exists inside the transactional test body. Add new plugin behaviour there, with a domain class that exercises it.
