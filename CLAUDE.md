# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A fork of the Grails Hibernate Filter plugin, maintained to track current Grails/GORM/Hibernate versions. It lets domain classes declare Hibernate `@FilterDef`-style filters via a `static hibernateFilters = { ... }` closure and toggle them at runtime. Gradle multi-project with two subprojects:

- `hibernate-filter-plugin/` — the published plugin (`org.grails.plugins:hibernate-filter-plugin`). Version lives in its `gradle.properties`.
- `hibernate-filter-example/` — a Grails app that depends on the plugin via `project(':hibernate-filter-plugin')` and holds **all the meaningful tests**. The plugin itself has no test sources.

Current stack (branch `7.x-upgrade` is for moving beyond this): Grails 6.2.0, Grails Gradle plugin 6.1.2, Hibernate 5.6.x via `org.grails.plugins:hibernate5`, Java 11, Gradle 7.6.4 wrapper. Gradle plugin versions are pinned in `settings.gradle` (pluginManagement) and duplicated in `buildSrc/build.gradle`; change both together.

## Commands

Run from the repo root.

```bash
./gradlew hibernate-filter-plugin:jar                 # build plugin jar
./gradlew hibernate-filter-plugin:publishToMavenLocal  # publish to ~/.m2 for consumption by other apps
./gradlew hibernate-filter-example:bootRun             # run example app (H2 in-memory)
./gradlew hibernate-filter-example:test                # unit tests (src/test)
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
