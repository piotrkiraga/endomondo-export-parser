## Why

The app runs on Spring Boot 2.3.5 (EOL since 2020) with dependencies that predate the Jakarta namespace change. As of July 2026, the only Spring Boot lines still receiving open-source fixes are 4.0.x (until Dec 2026) and 4.1.x (until Jul 2027) — even 3.5 left OSS support on June 30, 2026. With the characterization suite now green, the platform can be modernized while proving at every hop that behavior is unchanged.

## What Changes

- Upgrade Spring Boot 2.3.5 → 2.7.18 → 3.5.16 → 4.1.x (latest patch), verifying the full test suite at each hop. Land on 4.1.x, the only line supported past 2026.
- Java target 9 → 17 (the installed JDK; the minimum for Boot 4).
- **BREAKING** (source-level, not behavioral): `javax.servlet` → `jakarta.servlet` imports; `WebSecurityConfigurerAdapter` → `SecurityFilterChain` bean with `requestMatchers`; manual `thymeleaf`/`thymeleaf-spring5` pins removed in favor of Boot-managed versions.
- Packaging `war` → executable `jar`; drop the `provided` Tomcat starter. Nothing deploys this war to an external container.
- Drop the pinned maven-resources-plugin 2.6, keeping the `useDefaultDelimiters` filtering that `version.properties` depends on.

## Capabilities

### New Capabilities
- `build-platform`: the toolchain contract — supported Spring Boot line, Java 17 target, executable jar packaging, and the green characterization suite as the gate for every platform change.

### Modified Capabilities

(none — `workout-json-parsing` requirements, pinned defects included, MUST still hold verbatim after the upgrade; that is the entire point of upgrading with characterization tests in place. `export-data-management` is unaffected.)

## Non-goals

- No parser fixes — the characterization tests must pass **unmodified**; a red test means the upgrade broke something, full stop.
- No new features, no dependency additions beyond what version alignment forces.
- No cosmetic refactoring beyond what removed APIs force (e.g. the security config rewrite is forced; renaming the `JspConiguration` typo is not).
- No CI setup (worth its own small change later).

## Preserved vs. changed behavior

- **Preserved**: all runtime behavior — parsing (including pinned defects), security rules, i18n messages, Thymeleaf views, actuator endpoints. The security config rewrite must express the exact same rules in the new DSL.
- **Changed**: startup mechanics only (executable jar with embedded Tomcat instead of war layout).

## Impact

- `pom.xml`: parent version, java.version 17, dependency cleanups, packaging.
- `WebSecurityConfiguration` (rewritten in the new DSL), `GlobalDefaultExceptionHandler` and `BaseControllerPrePostInterceptor` (jakarta imports), `ThymeleafConfiguration`/`JspConiguration` (whatever Boot 4's Thymeleaf/web autoconfiguration forces — assessed per hop).
- Boot 4 renames/restructures some starters; exact pom shape follows the 4.0/4.1 release notes at apply time.
- Verification: `mvnw clean verify` green at every hop; app boots and serves `/` and `/upload` at the final hop.