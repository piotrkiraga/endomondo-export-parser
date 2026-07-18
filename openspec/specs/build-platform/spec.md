# build-platform Specification

## Purpose
TBD - created by archiving change upgrade-platform. Update Purpose after archive.
## Requirements
### Requirement: The project builds on a supported Spring Boot line
The project SHALL declare a Spring Boot parent version that is within its open-source support window at the time the change lands, and SHALL NOT pin versions of dependencies that the Boot parent already manages. Deliberate exception: Lombok (compile-compatibility floor).

#### Scenario: Supported parent version
- **WHEN** `pom.xml` is inspected
- **THEN** the `spring-boot-starter-parent` version is a 4.1.x release, and no Boot-managed dependency carries an explicit version pin other than the documented Lombok exception

#### Scenario: Parser uses Boot-managed Jackson 3
- **WHEN** the dependency tree is inspected after the parser fix
- **THEN** no `com.fasterxml.jackson.core:jackson-databind` pin remains; the parser binds via Boot-managed `tools.jackson` artifacts

### Requirement: The project compiles and tests on Java 17
The build SHALL target Java 17 and complete `mvnw clean verify` successfully on a Java 17 JDK.

#### Scenario: Clean verify on JDK 17
- **WHEN** `mvnw clean verify` runs on JDK 17
- **THEN** the build succeeds with all tests green

### Requirement: Platform changes are gated by the unmodified characterization suite
Any change to the platform (framework, Java target, packaging, dependency versions) SHALL keep the characterization tests passing without modification to their assertions.

#### Scenario: Characterization suite unchanged and green after upgrade
- **WHEN** the upgrade is complete
- **THEN** `git diff` shows no changes to `UploadControllerCharacterizationTest` assertions and `mvnw test` is green

### Requirement: The application packages as an executable jar
The build SHALL produce an executable Spring Boot jar with embedded Tomcat that starts and serves the home and upload pages.

#### Scenario: Boot from the packaged jar
- **WHEN** the packaged jar is started with `java -jar`
- **THEN** the application starts and responds on `/` and `/upload`

