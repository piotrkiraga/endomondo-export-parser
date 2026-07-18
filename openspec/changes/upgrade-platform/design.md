## Context

Spring Boot 2.3.5, Java 9 target, war packaging. Migration surface (surveyed):

- `GlobalDefaultExceptionHandler`, `BaseControllerPrePostInterceptor`: `javax.servlet` imports → `jakarta.servlet` at the Boot 3 hop.
- `WebSecurityConfiguration`: `WebSecurityConfigurerAdapter` (removed in Spring Security 6) with in-memory users (`user`/`password` → USER, `admin`/`admin` → USER+ADMIN), five `permitAll` paths (`/`, `/error`, `/home`, `/upload`, `/upload/process`), `anyRequest().authenticated()`, `httpBasic()`. A large commented-out form-login block travels along.
- `ThymeleafConfiguration`: manually wires `SpringTemplateEngine`/resolvers that the starter would autoconfigure; pom additionally pins `thymeleaf` and `thymeleaf-spring5` 3.0.11 (would break Boot 3+, which uses `thymeleaf-spring6`).
- `JspConiguration`: fully commented-out dead class; not touched by the upgrade.
- `pom.xml`: maven-resources-plugin 2.6 pinned for `useDefaultDelimiters` (the `version.properties` `${...}` filtering depends on it), war packaging, `provided` Tomcat starter, devtools, actuator, quartz, validation, spring-session-core.
- Verified safety net: 8 green tests (7 characterization + context loads).

As of July 2026: Boot 3.5 OSS support ended June 30, 2026; supported lines are 4.0.x (to Dec 2026) and 4.1.x (to Jul 2027). Boot 4 requires Java 17+ and restructures some starters. Installed JDKs: 17 only.

## Goals / Non-Goals

**Goals:**
- Land on Boot 4.1.x latest patch, Java 17, executable jar, with the unmodified characterization suite green.
- Reach each intermediate hop (2.7.18, 3.5.16) in a compiling, tested state so failures bisect cleanly.

**Non-Goals:**
- Parser fixes, feature work, CI, cosmetic cleanups (dead `JspConiguration`, typo names), Java 21 (no JDK installed).

## Decisions

- **Three hops (2.7.18 → 3.5.16 → 4.1.x), one commit per hop** — each hop isolates one class of breakage: 2.7 = deprecations + security DSL; 3.5 = jakarta namespace + Security 6 + `thymeleaf-spring6`; 4.1 = Framework 7 / starter restructuring. Alternative (straight to 4.1) rejected: conflates three failure classes; bisecting a broken jump costs more than three small hops. OpenRewrite recipes rejected for this codebase size — 18 small files; reading release notes beats configuring recipes.
- **Rewrite security at the 2.7 hop, not 3.5** — Security 5.7 (in Boot 2.7) already supports `SecurityFilterChain` + lambda DSL, so the rewrite happens while the old adapter still exists as a fallback reference, and the 3.5 hop then only changes imports (`antMatchers` → `requestMatchers`). Same rules, verified by hitting the endpoints.
- **Drop `ThymeleafConfiguration` manual wiring in favor of starter autoconfiguration when the 3.5 hop proves it redundant** — the manual `thymeleaf-spring5` engine cannot survive Boot 3 anyway; forced, not cosmetic. Templates stay untouched.
- **war → jar at the final hop** — nothing deploys the war externally (no `SpringBootServletInitializer` exists, so the war was never container-deployable anyway); the executable jar is the deliverable smoke-tested via `java -jar`.
- **Keep resource filtering semantics** — remove the resources-plugin version pin but preserve `useDefaultDelimiters` so `version.properties` keeps resolving `${...}` placeholders; verify by checking the filtered file in the built artifact.
- **Land on 4.1.x, not 4.0.x** — 4.0 hits EOL in five months; same migration cost either way.

## Risks / Trade-offs

- [Boot 4 starter restructuring not fully covered by pre-cutoff knowledge] → consult 4.0/4.1 release notes and migration guide at apply time before touching the pom; treat compile errors as the map.
- [Actuator/session/security defaults shifted across four major lines (e.g. endpoint exposure, CSRF behavior)] → smoke-test the running app at the final hop (`/`, `/upload`, an authenticated path, one actuator endpoint) and compare against pre-upgrade behavior on the 2.3.5 commit if anything looks off.
- [devtools or quartz misbehaving on Boot 4] → both are optional to the app's function; if either blocks the hop, note it and decide with the user rather than silently dropping.
- [Characterization suite is parser-only; web-layer regressions are invisible to it] → accepted; manual smoke test covers the thin web layer, and the parser-fix change will add web-layer tests.

## Migration Plan

Each hop: bump parent → fix compile errors per release notes → `mvnw clean verify` → boot smoke check → commit. Rollback = revert to the previous hop's commit.

## Open Questions

- None blocking. Exact 4.1 starter names resolved at apply time from release notes.
