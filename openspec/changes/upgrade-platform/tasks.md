## 1. Hop to Spring Boot 2.7.18

- [x] 1.1 Bump parent to 2.7.18, set `java.version` to 17, remove the manual `thymeleaf`/`thymeleaf-spring5` version pins (Boot 2.7 manages 3.0.x) (verify: `mvnw clean verify` green)
  - Done at 2.7.18. Also required removing @EnableWebMvc from GeneralConfiguration and ThymeleafConfiguration: it disabled Boot MVC autoconfig and collided with Framework 5.3's own localeResolver bean (context failed to load).
- [x] 1.2 Rewrite `WebSecurityConfiguration` to a `SecurityFilterChain` bean + `InMemoryUserDetailsManager`, preserving the exact users, roles, permitAll list, and httpBasic; delete the commented-out form-login block only if it blocks compilation, otherwise leave (verify: `mvnw clean verify` green; app boots; `/` and `/upload` reachable unauthenticated, other paths challenge with basic auth)
  - Done. SecurityFilterChain + InMemoryUserDetailsManager; probes: / /upload /home 200 unauthenticated, /actuator/health 401 then 200 with admin:admin. Commented form-login block kept.
- [x] 1.3 Commit hop 1 (verify: `git status` clean)
  - Done: commit 9d8d6b7.

## 2. Hop to Spring Boot 3.5.16

- [x] 2.1 Bump parent to 3.5.16; migrate `javax.servlet` → `jakarta.servlet` imports; `antMatchers` → `requestMatchers`; resolve whatever else fails to compile per the Boot 3.x migration guide (verify: `mvnw clean verify` green)
  - Done at 3.5.16. jakarta imports; authorizeHttpRequests/requestMatchers; BaseControllerPrePostInterceptor moved from removed HandlerInterceptorAdapter to HandlerInterceptor interface.
- [x] 2.2 Reassess `ThymeleafConfiguration`: if the starter autoconfiguration covers the manual wiring, delete the class; templates must render unchanged (verify: app boots; `/` and `/upload` render)
  - Done. Class was an empty @Configuration (wiring already commented out); deleted. / and /upload render via starter autoconfig (Upload view title verified).
- [x] 2.3 Commit hop 2 (verify: `git status` clean)
  - Done: commit 19b6bc4.

## 3. Hop to Spring Boot 4.1.x

- [x] 3.1 Read the Boot 4.0 and 4.1 release notes/migration guide; bump parent to latest 4.1.x patch; adjust starters per the notes (verify: `mvnw clean verify` green)
  - Done at 4.1.0 (latest 4.1.x on Central). starter-web -> starter-webmvc. Security autoconfig excludes dropped from main class (moved packages in Boot 4; redundant anyway - they back off to the app's own beans). Parser kept on Jackson 2 via explicit jackson-databind 2.22.1; Jackson 3 migration deferred to parser-fix change.
- [x] 3.2 Switch packaging war → jar; drop the `provided` Tomcat starter; remove the maven-resources-plugin version pin keeping `useDefaultDelimiters` (verify: `mvnw clean package` produces an executable jar; `version.properties` inside it has `${...}` placeholders resolved)
  - Done. Executable jar builds; version.properties inside jar has ${...} resolved; fixtures absent from jar (closes the deferred check from add-characterization-tests).
- [x] 3.3 Smoke-test the packaged jar: `java -jar`, check `/`, `/upload`, an authenticated path prompts basic auth, one actuator endpoint (verify: all respond as before the upgrade)
  - Done via java -jar probes: / /upload /home 200, /actuator/health 401 unauthenticated / 200 with admin:admin, Thymeleaf renders. Identical to pre-upgrade behavior.
- [x] 3.4 Commit hop 3 (verify: `git status` clean)
  - Done: commit 9fcf9d0.

## 4. Gate and reconcile

- [x] 4.1 Confirm the characterization suite passed every hop with zero assertion edits (verify: `git log -p -- src/test/java/**/UploadControllerCharacterizationTest.java` shows no changes in this change's commits)
  - Done. Zero commits touched UploadControllerCharacterizationTest across all hops; suite green at every hop.
- [x] 4.2 Reconcile `build-platform` spec scenarios against reality (exact landed version, any starter surprises); correct deltas if needed and validate (verify: `openspec validate upgrade-platform` passes)
  - Done. Spec amended: Jackson 2 pin added as documented exception alongside Lombok; landed version is 4.1.0.
