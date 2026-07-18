# build-platform

## MODIFIED Requirements

### Requirement: The project builds on a supported Spring Boot line
The project SHALL declare a Spring Boot parent version that is within its open-source support window at the time the change lands, and SHALL NOT pin versions of dependencies that the Boot parent already manages. Deliberate exception: Lombok (compile-compatibility floor).

#### Scenario: Supported parent version
- **WHEN** `pom.xml` is inspected
- **THEN** the `spring-boot-starter-parent` version is a 4.1.x release, and no Boot-managed dependency carries an explicit version pin other than the documented Lombok exception

#### Scenario: Parser uses Boot-managed Jackson 3
- **WHEN** the dependency tree is inspected after the parser fix
- **THEN** no `com.fasterxml.jackson.core:jackson-databind` pin remains; the parser binds via Boot-managed `tools.jackson` artifacts
