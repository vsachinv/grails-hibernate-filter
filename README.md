# grails-hibernate-filter

Declarative [Hibernate filters](https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#pc-filtering) for Grails / GORM domain classes.

Define a filter once in a `static hibernateFilters` block on a domain class and Hibernate appends its
condition to **every** query for that entity or collection while the filter is enabled: `list()`,
dynamic finders, criteria, HQL, and lazy-loaded associations alike. Mark a filter as `default` and the
plugin enables it automatically for every web request.

This is a fork of the original Grails Hibernate Filter plugin, continued from
[alexkramer/grails-hibernate-filter](https://github.com/alexkramer/grails-hibernate-filter), kept
current with Grails 4 through Apache Grails 7.0.x, GORM 7 through 9, and Hibernate 5.

## When to use it

Hibernate filters solve the "every query must remember to add this WHERE clause" problem. Typical
cases:

| Use case | Filter shape |
|----------|--------------|
| **Soft delete / active flag.** Hide rows where `active = false` or `deleted = true` from all reads without touching every finder | `activeFilter(condition: 'active = true', default: true)` |
| **Multi-tenancy by discriminator column.** Restrict every query to the current tenant | `tenantFilter(condition: 'tenant_id = :tenantId', types: 'long')`, parameter set per request |
| **Row-level status scoping.** Only "published", "approved", or `status = 1` rows are visible by default; admin code opts out with `withoutHibernateFilter` | `collegeFilter(condition: 'status = 1')` |
| **Filtering collections, not just roots.** Hide disabled children when loading `parent.children`, including through join tables | `barFilter(collection: 'bars', condition: 'enabled = true')` |
| **Two views of one entity.** Expose `Foo` unfiltered and `EnabledFoo` as a pre-filtered alias to the same domain class | `fooEnabledFilter(condition: 'enabled = true', aliasDomain: 'EnabledFoo')` |

Compared with the alternatives: a `where` query or named query only applies when you call it, and
`@Where`-style hard-coded mappings cannot be switched off. Hibernate filters apply everywhere and can be
toggled per session.

## Requirements

| Plugin version | Grails | Java | Hibernate / GORM |
|----------------|--------|------|------------------|
| 7.0.0-M1 | Apache Grails 7.0.x | 17+ | Hibernate 5.6 (jakarta), GORM 9 |
| 6.0-M1 | Grails 6.x | 11+ | Hibernate 5.6, GORM 8 |
| 5.0-M1 | Grails 5.x | 11+ | Hibernate 5, GORM 7 |
| 4.0-M2 | Grails 4.x | 8+ | Hibernate 5, GORM 7 |
| 0.5.5 | Grails 3.x | 8+ | Hibernate 4/5 |

Grails 7.1 and later ship Hibernate 6, which this plugin does not yet support. See
`UPGRADE_PLAN.md`, section 9, for the scoped follow-on work.

## Installation

```groovy
repositories {
    maven { url = "https://maven.pkg.github.com/vsachinv/grails-hibernate-filter" }
}

dependencies {
    // Apache Grails 7.0.x
    implementation "org.grails.plugins:hibernate-filter-plugin:7.0.0-M1"

    // Grails 6.x
    // implementation "org.grails.plugins:hibernate-filter-plugin:6.0-M1"

    // Grails 5.x
    // implementation "org.grails.plugins:hibernate-filter-plugin:5.0-M1"

    // Grails 4.x
    // implementation "org.grails.plugins:hibernate-filter-plugin:4.0-M2"
}
```

GitHub Packages requires an authenticated Maven repository even for public packages.

## Defining filters

Add a `static hibernateFilters` closure to a domain class. Each method call inside it defines one
filter; the method name is the filter name.

```groovy
class Foo {
    String name
    Boolean enabled
    static hasMany = [bars: Bar]

    static hibernateFilters = {
        // Enabled on every web request. Alias 'EnabledFoo' gives filtered access from any artefact.
        fooEnabledFilter(condition: 'enabled = true', default: true, aliasDomain: 'EnabledFoo')

        // Applied when the 'bars' collection is loaded through a Foo.
        barEnabledFilter(collection: 'bars', condition: 'enabled = true', default: true)

        // Parameterised. Types are positional and follow the order of first appearance.
        fooNameFilter(condition: ':name = name', types: 'string')
        inListFilter(condition: 'organisation_id = :orgId or organisation_id in (:orgIds)',
                     paramTypes: 'long, long')

        // A parameter may be reused; it is typed once.
        multipleUseParamFilter(condition: 'id > :avoid or id < :avoid', types: 'long')

        // Decide at runtime whether this counts as a default filter.
        closureDefaultFilter(condition: 'enabled = true', default: { -> false })
    }
}
```

### Options

| Option | Type | Meaning |
|--------|------|---------|
| `condition` | String | SQL fragment appended to the WHERE clause. Use **column** names, not property names. `:param` placeholders become filter parameters. Optional if a filter of the same name was already defined on another class; the stored condition is reused. |
| `types` or `paramTypes` | String | Comma-separated Hibernate basic type names (`string`, `long`, `integer`, `boolean`, `date`, ...) for the `:param` placeholders, in order of first appearance in `condition`. Required when the condition has parameters. |
| `default` | boolean or Closure | `true` registers the filter as a default filter, enabled on every web request by the plugin's interceptor. A closure is stored as a callback for application code to evaluate; the plugin does not call it. |
| `collection` | String | Attach the filter to a `hasMany` collection of this class instead of the class itself. Looked up on the class, then on each parent entity for subclasses. |
| `joinTable` | boolean | With `collection`, attach the filter to the many-to-many join table rather than the target entity. Needed for `hasMany` mapped through `joinTable`. |
| `aliasDomain` | String | Register a proxy under this name on every Grails artefact. Static calls through the alias run with the filter enabled. Root entities only. |

The same filter name may be used on several domain classes and collections. The definition is created
once; each additional use attaches it to another entity or collection.

```groovy
class Student {
    static hasMany = [courses: Course, loans: Loan, pens: Pen]
    static mapping = { courses(joinTable: 'courses_students', key: 'student_id') }

    static hibernateFilters = {
        collegeFilter(condition: 'status = 1')                          // Student rows
        collegeFilter(condition: 'status = 1', collection: 'loans')     // Student.loans
        collegeFilter(condition: 'status = 1', collection: 'pens', joinTable: true)
    }
}
```

## Using filters at runtime

The plugin adds these static methods to every domain class:

| Method | Effect |
|--------|--------|
| `Foo.withHibernateFilter(name) { ... }` | Enable one filter for the closure, then restore its previous state |
| `Foo.withHibernateFilters { ... }` | Enable all default filters for the closure, then restore |
| `Foo.withoutHibernateFilter(name) { ... }` | Disable one filter for the closure, then restore |
| `Foo.withoutHibernateFilters { ... }` | Disable all default filters for the closure, then restore |
| `Foo.enableHibernateFilter(name)` | Enable on the current session and return the `org.hibernate.Filter` so you can set parameters |
| `Foo.disableHibernateFilter(name)` | Disable on the current session |

Filters live on the Hibernate **session**, so the entity you call these methods on is only a
convenient handle. Enabling `collegeFilter` through `Student` also affects `Course` queries that join
students.

```groovy
// Parameterised filter
Filter filter = Foo.enableHibernateFilter('fooNameFilter')
filter.setParameter('name', 'enabledFoo')
List matches = Foo.list()

// Temporarily see everything, for example in an admin action
List all = Foo.withoutHibernateFilters { Foo.list() }

// Alias domain: the same as Foo.withHibernateFilter('fooEnabledFilter') { Foo.list() }
List enabledOnly = EnabledFoo.list()
```

### Default filters and web requests

Filters declared with `default: true` are enabled by `HibernateFilterInterceptor` before every
controller action. They are **not** enabled automatically in services called from outside a request,
scheduled jobs, `BootStrap`, or integration tests. In those places wrap the work in
`withHibernateFilters { }` or call `enableHibernateFilter` yourself.

Hibernate filters do not apply to `get(id)` and `load(id)`. They apply to queries and to collection
loading.

## Repository layout

| Directory | Purpose |
|-----------|---------|
| `hibernate-filter-plugin/` | The published plugin. Unit specs under `src/test/groovy` mock Hibernate metadata and sessions. |
| `hibernate-filter-example/` | A Grails application that consumes the plugin via `project(':hibernate-filter-plugin')`. Its domain classes demonstrate every option above, and its integration specs prove the filters reach the database. |

## Building

```bash
./gradlew hibernate-filter-plugin:jar                  # build the plugin jar
./gradlew hibernate-filter-plugin:publishToMavenLocal  # publish to ~/.m2
./gradlew hibernate-filter-plugin:test                 # plugin unit specs
./gradlew hibernate-filter-example:integrationTest     # end-to-end specs against H2
./gradlew hibernate-filter-example:bootRun             # example app on http://localhost:8080
```

A JDK 17 or newer must be on `JAVA_HOME`. Publishing to GitHub Packages or a Nexus repository reads
credentials from Gradle properties or environment variables; see `hibernate-filter-plugin/build.gradle`.

## Further reading

- `CHANGELOG` for release history.
- `UPGRADE_PLAN.md` for the Grails 6 to 7 migration record and the Hibernate 6 roadmap.
- The original project's [wiki](https://github.com/alexkramer/grails-hibernate-filter/wiki) covers
  the same DSL for Grails 2 and 3.
