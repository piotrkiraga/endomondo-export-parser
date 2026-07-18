## 1. Hop to Spring Boot 2.7.18

- [ ] 1.1 Bump parent to 2.7.18, set `java.version` to 17, remove the manual `thymeleaf`/`thymeleaf-spring5` version pins (Boot 2.7 manages 3.0.x) (verify: `mvnw clean verify` green)
- [ ] 1.2 Rewrite `WebSecurityConfiguration` to a `SecurityFilterChain` bean + `InMemoryUserDetailsManager`, preserving the exact users, roles, permitAll list, and httpBasic; delete the commented-out form-login block only if it blocks compilation, otherwise leave (verify: `mvnw clean verify` green; app boots; `/` and `/upload` reachable unauthenticated, other paths challenge with basic auth)
- [ ] 1.3 Commit hop 1 (verify: `git status` clean)

## 2. Hop to Spring Boot 3.5.16

- [ ] 2.1 Bump parent to 3.5.16; migrate `javax.servlet` → `jakarta.servlet` imports; `antMatchers` → `requestMatchers`; resolve whatever else fails to compile per the Boot 3.x migration guide (verify: `mvnw clean verify` green)
- [ ] 2.2 Reassess `ThymeleafConfiguration`: if the starter autoconfiguration covers the manual wiring, delete the class; templates must render unchanged (verify: app boots; `/` and `/upload` render)
- [ ] 2.3 Commit hop 2 (verify: `git status` clean)

## 3. Hop to Spring Boot 4.1.x

- [ ] 3.1 Read the Boot 4.0 and 4.1 release notes/migration guide; bump parent to latest 4.1.x patch; adjust starters per the notes (verify: `mvnw clean verify` green)
- [ ] 3.2 Switch packaging war → jar; drop the `provided` Tomcat starter; remove the maven-resources-plugin version pin keeping `useDefaultDelimiters` (verify: `mvnw clean package` produces an executable jar; `version.properties` inside it has `${...}` placeholders resolved)
- [ ] 3.3 Smoke-test the packaged jar: `java -jar`, check `/`, `/upload`, an authenticated path prompts basic auth, one actuator endpoint (verify: all respond as before the upgrade)
- [ ] 3.4 Commit hop 3 (verify: `git status` clean)

## 4. Gate and reconcile

- [ ] 4.1 Confirm the characterization suite passed every hop with zero assertion edits (verify: `git log -p -- src/test/java/**/UploadControllerCharacterizationTest.java` shows no changes in this change's commits)
- [ ] 4.2 Reconcile `build-platform` spec scenarios against reality (exact landed version, any starter surprises); correct deltas if needed and validate (verify: `openspec validate upgrade-platform` passes)
