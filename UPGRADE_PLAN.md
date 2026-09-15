# Upgrade Plan: grails-hibernate-filter — Grails 6.2.0 → Apache Grails 7.0.16

Status: **plan only, nothing applied.** Prepared 2026-09-15 on branch `7.x-upgrade`, HEAD `8cbbf7c` (tagged `6.0-M1`).

## 1. Baseline and target

| Item | Current | Target |
|------|---------|--------|
| Grails | 6.2.0 | 7.0.16 (latest 7.0.x at time of writing) |
| Grails Gradle plugin | 6.1.2 via `buildSrc` + `pluginManagement` | `org.apache.grails:grails-gradle-plugins` from BOM via `buildscript {}` |
| Spring Boot | 2.7.x | 3.5.16 (managed by grails-bom) |
| Hibernate | 5.6.15.Final (`hibernate-core`, hard-pinned) | 5.6.15.Final (`hibernate-core-jakarta`, BOM-managed) |
| GORM | 8.x | 9.0.x |
| Groovy | 3.x | 4.0.x |
| Java | sourceCompatibility 11 | release 17 (machine has Zulu 17.0.6 and 21.0.11 installed) |
| Gradle wrapper | 7.6.4 | 8.14.4 |
| Servlet API | javax | jakarta |
| Plugin version | 6.0-M1 | 7.0.0-M1 |

### Why 7.0.x and not 7.1.x / 7.2.x

Grails 7.1+ ships Hibernate 6. The plugin's metadata code depends on Hibernate 5 internals that Hibernate 6 removed or reshaped:

- `HibernateFilterBuilder.addFilter()` calls `mappings.getTypeResolver().basic(typeName)` to resolve filter parameter types. `TypeResolver` does not exist in Hibernate 6.
- `new FilterDefinition(name, condition, paramsMap)` takes a `Map<String, Type>` in Hibernate 5; Hibernate 6 changed the parameter model (`JdbcMapping`, `resolveParameter`).
- `PersistentClass.addFilter(name, condition, autoAlias, aliasTableMap, aliasEntityMap)` and `Collection.addManyToManyFilter(...)` signatures are the same in 6, but the `SecondPass` / `InFlightMetadataCollector` interaction around filter definitions changed.

Moving to 7.0.x first isolates the Grails/Spring Boot 3/jakarta/Groovy 4 work from a Hibernate 6 port. Hibernate 6 becomes a separate, well-scoped follow-on (see §9).

## 2. Triage

| Question | Answer |
|----------|--------|
| Risk level | **Medium**. GORM metadata integration, one interceptor, no servlet filters, no security, no JPA annotations. |
| `javax.*` imports in Groovy/Java source | None. Only a string attribute name in `error.gsp`. |
| GORM domain mappings / HQL | Plugin has none. Example app has domains and HQL only in an integration spec. |
| Spring Security | Not used. |
| Deprecated Grails 6 APIs (`GrailsWebMockUtil`, `ServletContextHolder`, `ClassRelativeResourcePatternResolver`) | None used. |
| Assets | Plugin: none. Example app: `grails-app/assets/` exists and `layouts/main.gsp` uses `<asset:stylesheet>`, `<asset:javascript>`, `<asset:image>`, `<asset:link>`, so the example keeps the asset pipeline. The empty-jar packaging fix does not apply because the example is an application, not a published plugin. |
| Tests | Plugin has no tests. Example: 1 unit spec (`UserControllerSpec`), 3 integration specs (`FilterSpec`, `CollegeFilterSpec`, `ParamsMultipleUseSpec`) that exercise the plugin. |

## 3. Coordinate mapping (verified against grails-bom 7.0.16 and grails-base-bom 7.0.16)

| Grails 6 | Grails 7.0.16 | Note |
|----------|---------------|------|
| plugin id `org.grails.grails-plugin` | `org.apache.grails.gradle.grails-plugin` | apply via `apply plugin:` after `buildscript {}` |
| plugin id `org.grails.grails-web` | `org.apache.grails.gradle.grails-web` | example app |
| plugin id `org.grails.grails-gsp` | `org.apache.grails.gradle.grails-gsp` | example app |
| plugin id `com.bertramlabs.asset-pipeline` 4.3.0 | `cloud.wondrify.asset-pipeline` 5.0.34 | version is BOM-managed; classpath `cloud.wondrify:asset-pipeline-gradle` |
| `org.grails:grails-core` | `org.apache.grails:grails-core` | |
| `org.grails:grails-logging` | `org.apache.grails:grails-logging` | |
| `org.grails:grails-plugin-databinding` | `org.apache.grails:grails-databinding` | |
| `org.grails:grails-plugin-i18n` | drop | bundled |
| `org.grails:grails-plugin-interceptors` | `org.apache.grails:grails-interceptors` | **required** in the plugin: `HibernateFilterInterceptor` is `@CompileStatic` and calls `matchAll()` from the injected trait |
| `org.grails:grails-plugin-rest` | `org.apache.grails:grails-rest-transforms` | example only |
| `org.grails:grails-plugin-services` | `org.apache.grails:grails-services` | |
| `org.grails:grails-plugin-url-mappings` | `org.apache.grails:grails-url-mappings` | |
| `org.grails:grails-web-boot` | `org.apache.grails:grails-web-boot` | |
| `org.grails.plugins:hibernate5` | `org.apache.grails:grails-data-hibernate5` | |
| `org.grails.plugins:gsp` | `org.apache.grails:grails-gsp` | example only |
| `org.grails.plugins:scaffolding` | `org.apache.grails:grails-scaffolding` | example only |
| `org.grails.plugins:geb` | `org.apache.grails:grails-geb` | example only |
| `org.grails:grails-console` | `org.apache.grails:grails-console` | `console` configuration |
| `org.hibernate:hibernate-core:5.6.15.Final` | `org.hibernate:hibernate-core-jakarta` | BOM-managed. The non-jakarta artifact must go; both on the classpath = duplicate classes |
| `org.hibernate:hibernate-ehcache:5.6.15.Final` | `org.hibernate:hibernate-ehcache` | BOM-managed |
| `org.grails:grails-gorm-testing-support` | `org.apache.grails:grails-testing-support-datamapping` | |
| `org.grails:grails-web-testing-support` | `org.apache.grails:grails-testing-support-web` | |
| n/a | `org.apache.grails.testing:grails-testing-support-core` | note the `.testing` group |
| n/a | `net.bytebuddy:byte-buddy` (test) | Spock concrete-class mocking, no longer transitive |
| `io.micronaut:micronaut-inject-groovy`, `micronaut-http-client` | drop | not used by any source |
| `com.bertramlabs.plugins:asset-pipeline-grails:4.3.0` | `org.apache.grails:grails-dependencies-assets` (`assets` config) | example only |
| `org.seleniumhq.selenium:*:4.19.1` | same artifacts, versions from BOM (selenium-bom imported) | example only |
| `com.gorylenko.gradle-git-properties` 2.4.2 | 4.0.1 | Gradle 8 compatible |
| `com.github.erdi.webdriver-binaries` 3.2 | 3.2 (unchanged, latest) | example only |

## 4. Mechanical changes

### 4.1 Gradle wrapper

```bash
./gradlew wrapper --gradle-version 8.14.4 --distribution-type bin
./gradlew wrapper --gradle-version 8.14.4 --distribution-type bin   # second pass regenerates the jar
./gradlew --version
```

Expect four changed files: `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat`. If the first invocation fails because the old build no longer resolves, hand-edit `distributionUrl` to `gradle-8.14.4-bin.zip` first, then rerun the wrapper task.

### 4.2 `settings.gradle`

```groovy
include 'hibernate-filter-plugin', 'hibernate-filter-example'
```

The `pluginManagement` block goes away. Each subproject resolves the Grails Gradle plugin from its own `buildscript {}`.

### 4.3 Delete `buildSrc/`

`buildSrc/build.gradle` only existed to put the Grails 6 Gradle plugin, hibernate5 gradle support, asset-pipeline, and git-properties on the build classpath. All four move to per-project `buildscript {}` blocks. Delete the directory including its `.gradle/` and `build/` caches.

### 4.4 `hibernate-filter-plugin/gradle.properties`

```properties
version=7.0.0-M1
grailsVersion=7.0.16
springBootVersion=3.5.16
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1024M
# Uncomment if the default JVM is not 17:
# org.gradle.java.home=/Users/sachinverma/.sdkman/candidates/java/17.0.6-zulu
```

`grailsGradlePluginVersion` is removed; the Gradle plugin version comes from the BOM.

### 4.5 `hibernate-filter-plugin/build.gradle`

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = 'https://repo.grails.org/grails/restricted' }
    }
    dependencies {
        classpath platform("org.apache.grails:grails-bom:$grailsVersion")
        classpath "org.apache.grails:grails-gradle-plugins"
        classpath "com.gorylenko.gradle-git-properties:gradle-git-properties:4.0.1"
    }
}

apply plugin: "groovy"
apply plugin: "idea"
apply plugin: "eclipse"
apply plugin: "org.apache.grails.gradle.grails-plugin"
apply plugin: "maven-publish"
apply plugin: "com.gorylenko.gradle-git-properties"

group = "org.grails.plugins"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = 'https://repo.grails.org/grails/restricted' }
}

dependencies {
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")

    implementation "org.apache.grails:grails-core"
    implementation "org.apache.grails:grails-logging"
    implementation "org.apache.grails:grails-databinding"
    implementation "org.apache.grails:grails-interceptors"      // HibernateFilterInterceptor trait injection
    implementation "org.apache.grails:grails-services"
    implementation "org.apache.grails:grails-url-mappings"
    implementation "org.apache.grails:grails-web-boot"
    implementation "org.apache.grails:grails-data-hibernate5"
    implementation "org.hibernate:hibernate-core-jakarta"
    implementation "org.hibernate:hibernate-ehcache"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
    implementation "org.springframework.boot:spring-boot-starter-logging"
    implementation "org.springframework.boot:spring-boot-starter-validation"

    console "org.apache.grails:grails-console"
    profile "org.apache.grails.profiles:plugin"

    runtimeOnly "com.h2database:h2"
    runtimeOnly "org.apache.tomcat:tomcat-jdbc"

    testImplementation "org.apache.grails.testing:grails-testing-support-core"
    testImplementation "org.apache.grails:grails-testing-support-datamapping"
    testImplementation "org.spockframework:spock-core"
    testImplementation "net.bytebuddy:byte-buddy"
    testRuntimeOnly    "net.bytebuddy:byte-buddy-agent"
}

compileJava.options.release = 17

// https://github.com/apache/grails-core/issues/15321
tasks.withType(GroovyCompile).configureEach {
    groovyOptions.optimizationOptions.indy = false
    groovyOptions.forkOptions.jvmArgs = ['-Xmx1024m']
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}

gitProperties {
    keys = ['git.branch', 'git.commit.id', 'git.commit.time', 'git.commit.id.abbrev']
    failOnNoGitDirectory = true
    extProperty = 'gitProps'
}

generateGitProperties.outputs.upToDateWhen { false }

jar {
    dependsOn generateGitProperties
    manifest {
        attributes("Built-By": System.getProperty("user.name"))
        attributes(["Plugin-Version"        : version,
                    "Plugin-Title"          : project.name,
                    "Plugin-Build-Timestamp": new Date().format("yyyy-MM-dd'T'HH:mm:ssZ"),
                    "Git-Commit"            : "${-> project.ext.gitProps['git.commit.id.abbrev']}",
                    "Git-Branch"            : "${-> project.ext.gitProps['git.branch']}"])
    }
    from sourceSets.main.output
    exclude 'git.properties'
}

tasks.register('sourceJar', Jar) {
    archiveClassifier = 'sources'
    from sourceSets.main.allSource
}

tasks.register('packageJavadoc', Jar) {
    from javadoc
    archiveClassifier = 'javadoc'
}

tasks.register('packageGroovydoc', Jar) {
    from groovydoc
    archiveClassifier = 'groovydoc'
}

bootJar.enabled = false

publishing {
    publications {
        mavenJar(MavenPublication) {
            from components.java
            artifact sourceJar
            artifact packageJavadoc
            artifact packageGroovydoc
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/vsachinv/grails-hibernate-filter")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "NexusRepo"
            credentials {
                username = project.findProperty("nexusUsername") ?: System.getenv("NEXUS_USERNAME")
                password = project.findProperty("nexusPassword") ?: System.getenv("NEXUS_PASSWORD")
            }
            url = project.findProperty("nexusUrl") ?: System.getenv("NEXUS_URL")
            allowInsecureProtocol = project.findProperty("isNexusUrlInsecure") ? true : false
        }
    }
}
```

Notes on what changed versus the current file:

- `plugins {}` block replaced by `buildscript {}` + `apply plugin:` (Grails plugin is not on the Gradle Plugin Portal).
- `application` plugin removed; a plugin project does not need it and `bootJar.enabled = false` already prevents packaging as an app.
- The `developmentOnly` configuration block is dropped; the Grails 7 Gradle plugin declares it.
- `java { sourceCompatibility = 11 }` → `compileJava.options.release = 17`.
- `classifier =` → `archiveClassifier =` (removed in Gradle 8).
- `task x(type: Jar)` → `tasks.register('x', Jar)` (Gradle 8 idiom; old syntax still works but is deprecated).
- Hibernate pins removed; both hibernate artifacts are now BOM-managed, and `hibernate-core` becomes `hibernate-core-jakarta`.

### 4.6 `hibernate-filter-example/gradle.properties`

```properties
version=0.1
grailsVersion=7.0.16
springBootVersion=3.5.16
org.gradle.caching=true
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.jvmargs=-Dfile.encoding=UTF-8 -Xmx1024M
```

### 4.7 `hibernate-filter-example/build.gradle`

```groovy
buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = 'https://repo.grails.org/grails/restricted' }
    }
    dependencies {
        classpath platform("org.apache.grails:grails-bom:$grailsVersion")
        classpath "org.apache.grails:grails-gradle-plugins"
        classpath "cloud.wondrify:asset-pipeline-gradle"
        classpath "com.github.erdi:webdriver-binaries-gradle-plugin:3.2"
    }
}

apply plugin: "groovy"
apply plugin: "idea"
apply plugin: "eclipse"
apply plugin: "war"
apply plugin: "org.apache.grails.gradle.grails-web"
apply plugin: "org.apache.grails.gradle.grails-gsp"
apply plugin: "cloud.wondrify.asset-pipeline"
apply plugin: "com.github.erdi.webdriver-binaries"

group = "hibernate.filter.example"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = 'https://repo.grails.org/grails/restricted' }
}

dependencies {
    implementation platform("org.apache.grails:grails-bom:$grailsVersion")

    implementation project(':hibernate-filter-plugin')

    implementation "org.apache.grails:grails-core"
    implementation "org.apache.grails:grails-logging"
    implementation "org.apache.grails:grails-databinding"
    implementation "org.apache.grails:grails-interceptors"
    implementation "org.apache.grails:grails-rest-transforms"
    implementation "org.apache.grails:grails-services"
    implementation "org.apache.grails:grails-url-mappings"
    implementation "org.apache.grails:grails-web-boot"
    implementation "org.apache.grails:grails-gsp"
    implementation "org.apache.grails:grails-data-hibernate5"
    implementation "org.apache.grails:grails-scaffolding"
    implementation "org.hibernate:hibernate-core-jakarta"
    implementation "org.springframework.boot:spring-boot-autoconfigure"
    implementation "org.springframework.boot:spring-boot-starter"
    implementation "org.springframework.boot:spring-boot-starter-actuator"
    implementation "org.springframework.boot:spring-boot-starter-logging"
    implementation "org.springframework.boot:spring-boot-starter-tomcat"
    implementation "org.springframework.boot:spring-boot-starter-validation"

    console "org.apache.grails:grails-console"
    assets  "org.apache.grails:grails-dependencies-assets"

    runtimeOnly "com.h2database:h2"
    runtimeOnly "org.apache.tomcat:tomcat-jdbc"
    runtimeOnly "org.fusesource.jansi:jansi"

    testImplementation "org.apache.grails.testing:grails-testing-support-core"
    testImplementation "org.apache.grails:grails-testing-support-datamapping"
    testImplementation "org.apache.grails:grails-testing-support-web"
    testImplementation "org.apache.grails:grails-geb"
    testImplementation "org.spockframework:spock-core"
    testImplementation "net.bytebuddy:byte-buddy"
    testRuntimeOnly    "net.bytebuddy:byte-buddy-agent"
    testImplementation "org.seleniumhq.selenium:selenium-api"
    testImplementation "org.seleniumhq.selenium:selenium-remote-driver"
    testImplementation "org.seleniumhq.selenium:selenium-support"
    testRuntimeOnly    "org.seleniumhq.selenium:selenium-chrome-driver"
    testRuntimeOnly    "org.seleniumhq.selenium:selenium-firefox-driver"
    testRuntimeOnly    "org.seleniumhq.selenium:selenium-safari-driver"
}

application {
    mainClass.set("hibernate.filter.example.Application")
}

compileJava.options.release = 17

tasks.withType(GroovyCompile).configureEach {
    groovyOptions.optimizationOptions.indy = false
    groovyOptions.forkOptions.jvmArgs = ['-Xmx1024m']
}

bootRun {
    ignoreExitValue true
    jvmArgs('-Dspring.output.ansi.enabled=always', '-XX:TieredStopAtLevel=1', '-Xmx1024m')
    sourceResources sourceSets.main
    String springProfilesActive = 'spring.profiles.active'
    systemProperty springProfilesActive, System.getProperty(springProfilesActive)
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
    systemProperty "geb.env", System.getProperty('geb.env')
    systemProperty "geb.build.reportsDir", reporting.file("geb/integrationTest")
    if (!System.getenv().containsKey('GITHUB_ACTIONS')) {
        systemProperty 'webdriver.chrome.driver', System.getProperty('webdriver.chrome.driver')
        systemProperty 'webdriver.gecko.driver', System.getProperty('webdriver.gecko.driver')
    } else {
        systemProperty 'webdriver.chrome.driver', "${System.getenv('CHROMEWEBDRIVER')}/chromedriver"
        systemProperty 'webdriver.gecko.driver', "${System.getenv('GECKOWEBDRIVER')}/geckodriver"
    }
}

webdriverBinaries {
    chromedriver '122.0.6260.0'
    geckodriver '0.33.0'
    edgedriver '110.0.1587.57'
}

assets {
    minifyJs = true
    minifyCss = true
}
```

Notes:

- `-noverify` removed from `bootRun` JVM args; it is a no-op that warns on Java 13+ and is removed in later JDKs.
- The two duplicated `tasks.withType(Test)` blocks are merged into one.
- `webdriver-binaries-gradle-plugin` classpath coordinate must be confirmed on first resolve; if it fails, the fallback is to keep a `pluginManagement { plugins { id "com.github.erdi.webdriver-binaries" version "3.2" } }` in `settings.gradle` and apply that one via `plugins {}`.
- Hibernate pin `hibernate-core:5.6.11.Final` (which already mismatched the plugin's 5.6.15) is removed.

### 4.8 `HibernateFilterGrailsPlugin.groovy`

```diff
-    def grailsVersion = "6.2.0 > *"
+    def grailsVersion = "7.0.0 > *"
```

`loadAfter = ['hibernate']` stays. The GORM hibernate5 plugin descriptor in Grails 7 is still named `hibernate`; verify by opening `META-INF/grails-plugin.xml` inside the resolved `grails-data-hibernate5` jar after the first successful resolve.

### 4.9 `hibernate-filter-example/grails-app/views/error.gsp`

```diff
-<g:elseif test="${request.getAttribute('javax.servlet.error.exception')}">
-    <g:renderException exception="${request.getAttribute('javax.servlet.error.exception')}" />
+<g:elseif test="${request.getAttribute('jakarta.servlet.error.exception')}">
+    <g:renderException exception="${request.getAttribute('jakarta.servlet.error.exception')}" />
```

Servlet 6 renamed the standard request attributes along with the packages.

### 4.10 Documentation

- `README.md`: add a "for Grails 7.x" install block with `implementation "org.grails.plugins:hibernate-filter-plugin:7.0.0-M1"`, note Java 17 minimum, note it targets Grails 7.0.x / Hibernate 5.
- `CLAUDE.md`: update the stack line (Grails 7.0.16, Spring Boot 3.5, Java 17, Gradle 8.14.4) and the note about `settings.gradle` / `buildSrc` pinning, which no longer applies.
- `.travis.yml`: `jdk: oraclejdk8` → `openjdk17` (or delete the file; Travis is not used for GitHub Packages publishing).
- `CHANGELOG`: add a 7.0.0-M1 entry.

## 5. Logic changes

**None identified.** Each candidate area was checked:

| Area | Finding |
|------|---------|
| Plugin Hibernate imports | All `org.hibernate.*`, unchanged in `hibernate-core-jakarta` 5.6. No `javax.persistence`. |
| `HibernateFilterBinder implements MetadataContributor` with `org.jboss.jandex.IndexView` | Interface unchanged in Hibernate 5.6 jakarta. |
| `HibernateFilterSecondPass implements SecondPass` | Unchanged. |
| `hibernateConnectionSourceFactory` bean ref in `doWithSpring` | Bean name still defined by GORM 9 `HibernateDatastoreSpringInitializer`. Verify at first boot. |
| `metadataContributor` bean pickup | GORM hibernate5 collects all `MetadataContributor` beans. Verify at first boot by confirming filters exist in `FilterSpec`. |
| Groovy 4 | `args.collect { it }`, `matcher.each { match -> match[1] }`, spread `entity.addFilter(*filterArgs)`, `metaClass.static.x = {}` all valid. `@CompileStatic` classes have no dynamic constructs. |
| GORM 9 empty-string → null | No domain declares `blank: true`. |
| HQL | Only in `CollegeFilterSpec`; explicit `JOIN`s, no positional params, no bulk `delete`. |
| Removed Grails APIs | None used. |
| `UserControllerSpec` reading `model` after implicit returns | Left as-is on purpose; run first, fix only if it fails. |

## 6. Execution order

1. Tag check passed (`6.0-M1` on HEAD). Commit `CLAUDE.md` first so the migration diff is clean.
2. Apply §4.2, §4.3, §4.4–4.7 (build files), then §4.1 wrapper regeneration.
3. `./gradlew hibernate-filter-plugin:compileGroovy` — fix coordinate resolution issues here before touching anything else.
4. Apply §4.8, §4.9.
5. `./gradlew hibernate-filter-example:compileGroovy compileIntegrationTestGroovy`
6. Run tests (§7).
7. Apply §4.10 docs.
8. Commit as "Upgrade plugin to Grails 7.0.16 (7.0.0-M1)".

## 7. Verification

```bash
./gradlew --version                                                    # Gradle 8.14.4, JVM 17
./gradlew clean
./gradlew hibernate-filter-plugin:compileGroovy
./gradlew hibernate-filter-example:compileGroovy
./gradlew hibernate-filter-example:test                                # UserControllerSpec
./gradlew hibernate-filter-example:integrationTest                     # FilterSpec, CollegeFilterSpec, ParamsMultipleUseSpec
./gradlew hibernate-filter-plugin:jar
unzip -l hibernate-filter-plugin/build/libs/*.jar | grep -E 'grails-plugin.xml|HibernateFilterInterceptor'
./gradlew hibernate-filter-plugin:publishToMavenLocal
./gradlew hibernate-filter-example:bootRun                             # smoke: GET /user should list only active users
```

Interceptor check: after `jar`, run `javap -p` on `HibernateFilterInterceptor.class` from `build/classes`. It must list `grails.artefact.Interceptor` in its interfaces. If it shows only `GroovyObject`, `grails-interceptors` is missing from the compile classpath.

## 8. Known risks and where to look if something breaks

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Could not find org.apache.grails:<x>:.` (empty version) | Wrong group for that artifact | Check §3 table; groups are `org.apache.grails`, `.web`, `.views`, `.testing`, `.data`, `.profiles` |
| `Plugin with id 'org.apache.grails.gradle.grails-plugin' not found` | `restricted` repo missing from `buildscript.repositories` | Add `https://repo.grails.org/grails/restricted` |
| Duplicate `org.hibernate.*` classes / `NoSuchMethodError` at boot | Both `hibernate-core` and `hibernate-core-jakarta` present | Ensure no non-jakarta pin remains, run `dependencies --configuration runtimeClasspath` |
| Filters silently absent in `FilterSpec` | `metadataContributor` bean not picked up, or `hibernateConnectionSourceFactory` renamed | Inspect GORM 9 `HibernateDatastoreSpringInitializer`; propose a logic change |
| Interceptor never fires | `grails-interceptors` missing | Add dependency (§4.5) |
| `CannotCreateMockException ... byte-buddy` | Missing test dep | Already included in §4.5/4.7 |
| `model` is `[:]` in `UserControllerSpec` | Grails 7 implicit-return change | Capture `def m = controller.index()` and assert on `m` (logic change, needs approval) |
| `webdriver-binaries` classpath fails to resolve | Coordinate guess wrong | Fallback described in §4.7 notes |
| Groovy 4 `ClassCastException` in `HibernateFilterBuilder.methodMissing` | `metaClass.getMetaMethod('addFilter', Class[])` arg matching changed | Would be a logic change; propose replacement with explicit `addFilter(name, args[0] as Map)` dispatch |

Policy note: this change touches no authentication, cryptography, payments, or PII code paths. Publishing credentials remain property/env driven and unchanged.

## 9. Follow-on: Grails 7.1.x / 7.2.x with Hibernate 6 (out of scope here)

Required logic changes when that time comes:

1. `HibernateFilterBuilder.addFilter`: replace `mappings.getTypeResolver().basic(typeName)` with Hibernate 6 type resolution (`mappings.getTypeConfiguration().getBasicTypeRegistry().getRegisteredType(typeName)`), and construct `FilterDefinition` with the Hibernate 6 parameter-type map (`Map<String, JdbcMapping>`).
2. Verify `PersistentClass.addFilter` / `Collection.addManyToManyFilter` five-argument signatures against Hibernate 6.4.
3. Re-run `CollegeFilterSpec` HQL; already uses explicit joins so it should pass.
4. Bump `grailsVersion` to the chosen 7.1.x/7.2.x and `hibernate-core-jakarta` → `hibernate-core` (Hibernate 6 has a single jakarta-only artifact).
