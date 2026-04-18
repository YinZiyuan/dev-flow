# DevFlow Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete DevFlow backend — Spring Boot 3 REST API + WebSocket + Pipeline state machine + Claude AI integration + Docker test sandbox.

**Architecture:** Spring Boot 3 monolith with domain-driven package structure. Each pipeline stage has a dedicated executor that calls Claude API (streaming) and advances the state machine. WebSocket (STOMP) pushes real-time events to the frontend.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, Spring WebSocket/STOMP, Spring Security + JWT, PostgreSQL 17, Flyway migrations, Docker Java SDK, Anthropic Java SDK (HTTP client), JUnit 5 + Testcontainers

---

## File Map

```
devflow-backend/
├── build.gradle (or pom.xml)
├── docker-compose.yml
├── src/main/java/com/devflow/
│   ├── DevFlowApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java
│   │   ├── WebSocketConfig.java
│   │   └── AppConfig.java
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── AuthService.java
│   │   ├── JwtUtils.java
│   │   └── VerificationCodeService.java
│   ├── domain/
│   │   ├── user/
│   │   │   ├── User.java
│   │   │   ├── UserRepository.java
│   │   │   └── UserController.java
│   │   ├── project/
│   │   │   ├── Project.java
│   │   │   ├── ProjectRepository.java
│   │   │   ├── ProjectService.java
│   │   │   └── ProjectController.java
│   │   └── pipeline/
│   │       ├── model/
│   │       │   ├── PipelineRun.java
│   │       │   ├── StageRun.java
│   │       │   ├── Message.java
│   │       │   ├── Artifact.java
│   │       │   ├── CodeFile.java
│   │       │   └── AgentConfig.java
│   │       ├── repository/
│   │       │   ├── PipelineRunRepository.java
│   │       │   ├── StageRunRepository.java
│   │       │   ├── MessageRepository.java
│   │       │   ├── ArtifactRepository.java
│   │       │   ├── CodeFileRepository.java
│   │       │   └── AgentConfigRepository.java
│   │       ├── dto/
│   │       │   ├── CreatePipelineRequest.java
│   │       │   ├── AnswerRequest.java
│   │       │   ├── ChoiceRequest.java
│   │       │   └── RevisionRequest.java
│   │       ├── PipelineController.java
│   │       └── StageController.java
│   ├── engine/
│   │   ├── PipelineEngine.java
│   │   ├── StageExecutor.java
│   │   ├── RequirementsExecutor.java
│   │   ├── PlanningExecutor.java
│   │   ├── CodingExecutor.java
│   │   └── TestingExecutor.java
│   ├── ai/
│   │   ├── ClaudeClient.java
│   │   └── ContextBuilder.java
│   ├── realtime/
│   │   └── EventPublisher.java
│   └── sandbox/
│       └── DockerSandbox.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V1__init_schema.sql
│       └── V2__seed_agent_configs.sql
└── src/test/java/com/devflow/
    ├── engine/PipelineEngineTest.java
    ├── engine/StateMachineTest.java
    ├── auth/AuthServiceTest.java
    └── project/ProjectServiceTest.java
```

---

## Task 1: Project Scaffold

**Files:**
- Create: `devflow-backend/build.gradle`
- Create: `devflow-backend/src/main/java/com/devflow/DevFlowApplication.java`
- Create: `devflow-backend/src/main/resources/application.yml`
- Create: `devflow-backend/docker-compose.yml`

- [ ] **Step 1: Create Gradle build file**

```gradle
// devflow-backend/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.devflow'
version = '0.0.1-SNAPSHOT'
sourceCompatibility = '21'

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
    runtimeOnly 'org.postgresql:postgresql'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    // Docker SDK for test sandbox
    implementation 'com.github.docker-java:docker-java-core:3.3.4'
    implementation 'com.github.docker-java:docker-java-transport-httpclient5:3.3.4'
    // HTTP client for Claude API
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.testcontainers:postgresql:1.19.7'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
}
```

- [ ] **Step 2: Create main application class**

```java
// src/main/java/com/devflow/DevFlowApplication.java
package com.devflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DevFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(DevFlowApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application.yml**

```yaml
# src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/devflow
    username: devflow
    password: devflow
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    locations: classpath:db/migration

server:
  port: 8080

app:
  jwt:
    secret: ${JWT_SECRET:devflow-secret-key-change-in-production-minimum-32-chars}
    expiration-ms: 86400000  # 24h
  claude:
    api-key: ${CLAUDE_API_KEY:}
    model: claude-opus-4-7
    max-tokens: 8192
  verification-code:
    expiration-minutes: 10
```

- [ ] **Step 4: Create docker-compose.yml**

```yaml
# devflow-backend/docker-compose.yml
version: '3.8'
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: devflow
      POSTGRES_USER: devflow
      POSTGRES_PASSWORD: devflow
    ports:
      - "5432:5432"
    volumes:
      - devflow_data:/var/lib/postgresql/data

volumes:
  devflow_data:
```

- [ ] **Step 5: Start database and verify**

```bash
cd devflow-backend
docker compose up -d postgres
# Wait 5 seconds, then:
docker compose ps
# Expected: postgres container "healthy" or "running"
```

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "chore: spring boot 3 project scaffold"
```

---

## Task 2: Database Schema (Flyway Migrations)

**Files:**
- Create: `src/main/resources/db/migration/V1__init_schema.sql`
- Create: `src/main/resources/db/migration/V2__seed_agent_configs.sql`

- [ ] **Step 1: Write V1 migration**

```sql
-- src/main/resources/db/migration/V1__init_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "user" (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE verification_code (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE project (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tech_stack VARCHAR(255),
    repo_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TYPE pipeline_status AS ENUM ('running', 'waiting_human', 'completed', 'failed');
CREATE TYPE stage_type AS ENUM ('requirements', 'planning', 'coding', 'testing');
CREATE TYPE stage_status AS ENUM (
    'pending', 'running', 'waiting_answer', 'waiting_choice',
    'waiting_approval', 'waiting_revision', 'completed', 'failed', 'skipped'
);
CREATE TYPE message_role AS ENUM ('user', 'assistant', 'system');
CREATE TYPE message_type AS ENUM ('text', 'question', 'choice_request', 'choice_response');
CREATE TYPE artifact_type AS ENUM ('prd', 'plan', 'code', 'test_result');

CREATE TABLE pipeline_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    requirement TEXT NOT NULL,
    status pipeline_status NOT NULL DEFAULT 'running',
    current_stage stage_type NOT NULL DEFAULT 'requirements',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE stage_run (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_run(id) ON DELETE CASCADE,
    stage_type stage_type NOT NULL,
    status stage_status NOT NULL DEFAULT 'pending',
    order_index INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_run_id UUID NOT NULL REFERENCES stage_run(id) ON DELETE CASCADE,
    role message_role NOT NULL,
    content TEXT NOT NULL,
    type message_type NOT NULL DEFAULT 'text',
    options JSONB,
    selected_option VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE artifact (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_run_id UUID NOT NULL REFERENCES stage_run(id) ON DELETE CASCADE,
    type artifact_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE code_file (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    artifact_id UUID NOT NULL REFERENCES artifact(id) ON DELETE CASCADE,
    path VARCHAR(500) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    language VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE agent_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stage_type stage_type UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    system_prompt TEXT NOT NULL,
    model VARCHAR(100) NOT NULL DEFAULT 'claude-opus-4-7',
    max_tokens INT NOT NULL DEFAULT 8192,
    max_retries INT NOT NULL DEFAULT 3,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_pipeline_run_project ON pipeline_run(project_id);
CREATE INDEX idx_stage_run_pipeline ON stage_run(pipeline_run_id);
CREATE INDEX idx_message_stage_run ON message(stage_run_id);
CREATE INDEX idx_artifact_stage_run ON artifact(stage_run_id);
CREATE INDEX idx_code_file_artifact ON code_file(artifact_id);
```

- [ ] **Step 2: Write V2 seed migration**

```sql
-- src/main/resources/db/migration/V2__seed_agent_configs.sql
INSERT INTO agent_config (stage_type, name, system_prompt, model, max_tokens, max_retries) VALUES
('requirements', '需求分析 Agent',
'You are a senior product manager specializing in requirements analysis.
Your job: analyze the user requirement, ask clarifying questions ONE AT A TIME, propose solution approaches when there are meaningful trade-offs, and produce a concise PRD document.

Rules:
- Ask at most 3 clarifying questions total. If you have a question, emit JSON: {"type":"question","content":"your question here"}
- If you have 2-3 meaningfully different approaches, emit JSON: {"type":"choice","options":[{"id":"a","label":"Option A","description":"..."},{"id":"b","label":"Option B","description":"..."}]}
- When ready to produce the PRD, emit the full Markdown document prefixed with: {"type":"artifact","title":"PRD: <feature name>"}
- Keep the PRD concise: problem statement, user stories (3-5), acceptance criteria, out of scope.
- Language: match the user''s language.',
'claude-opus-4-7', 8192, 3),

('planning', '实施规划 Agent',
'You are a senior software architect.
Input: a PRD document and the project tech stack.
Your job: produce a detailed implementation plan.

Output format — emit this JSON when done: {"type":"artifact","title":"Implementation Plan: <feature>"}
Then the Markdown content:
## Architecture Overview
(2-3 paragraphs on approach)
## File Structure
(tree of files to create/modify)
## Task Breakdown
(numbered list of tasks, each with: what to build, key logic, estimated complexity)
## API Changes
(new/modified endpoints)

Rules:
- Be specific to the given tech stack.
- If something is ambiguous, ask ONE clarifying question: {"type":"question","content":"..."}
- Language: match the user''s language.',
'claude-opus-4-7', 8192, 3),

('coding', '代码生成 Agent',
'You are a senior full-stack engineer.
Input: PRD, implementation plan, project tech stack, and any existing code context.
Your job: generate all required code files.

For EACH file, emit:
{"type":"file","path":"relative/path/to/File.java","language":"java"}
```
<full file content here>
```

Rules:
- Generate complete, working files. No placeholders, no TODOs.
- Follow the tech stack exactly (e.g. Spring Boot 3 + Java 21 if specified).
- Include unit tests for service/utility classes.
- After all files, emit: {"type":"artifact","title":"Code: <feature name>"} with a brief summary.
- If you need clarification: {"type":"question","content":"..."}
- Language for comments: match user''s language. Code identifiers: English.',
'claude-opus-4-7', 16384, 3),

('testing', '测试验证 Agent',
'You are a QA engineer analyzing test failures.
Input: test execution output (stdout/stderr) and the code files that were tested.
Your job: identify the root cause of each failure and specify EXACT file changes to fix them.

Output format:
{"type":"fix","files":[{"path":"relative/path","content":"<complete new file content>"}]}

Rules:
- Only output the fix JSON. No explanation outside the JSON.
- Provide COMPLETE file content (not diffs).
- If all tests passed, output: {"type":"all_passed"}
- Fix the minimal set of files needed.',
'claude-opus-4-7', 8192, 3);
```

- [ ] **Step 3: Run migrations**

```bash
./gradlew bootRun &
# Wait for "Started DevFlowApplication" in logs, then Ctrl+C
# Or run migrations only:
./gradlew flywayMigrate
# Expected: Flyway output showing V1 and V2 applied successfully
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/
git commit -m "feat(db): flyway migrations — schema + agent config seeds"
```

---

## Task 3: JWT Auth + User

**Files:**
- Create: `src/main/java/com/devflow/auth/JwtUtils.java`
- Create: `src/main/java/com/devflow/auth/VerificationCodeService.java`
- Create: `src/main/java/com/devflow/auth/AuthService.java`
- Create: `src/main/java/com/devflow/auth/AuthController.java`
- Create: `src/main/java/com/devflow/domain/user/User.java`
- Create: `src/main/java/com/devflow/domain/user/UserRepository.java`
- Create: `src/main/java/com/devflow/config/SecurityConfig.java`
- Test: `src/test/java/com/devflow/auth/AuthServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/devflow/auth/AuthServiceTest.java
package com.devflow.auth;

import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock VerificationCodeService verificationCodeService;
    @Mock JwtUtils jwtUtils;
    @InjectMocks AuthService authService;

    @Test
    void verifyCode_existingUser_returnsToken() {
        var user = new User();
        user.setEmail("test@example.com");
        user.setName("Test User");

        when(verificationCodeService.verify("test@example.com", "123456")).thenReturn(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken("test@example.com")).thenReturn("jwt-token");

        var result = authService.verifyCode("test@example.com", "123456");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.user().email()).isEqualTo("test@example.com");
    }

    @Test
    void verifyCode_invalidCode_throwsException() {
        when(verificationCodeService.verify("test@example.com", "000000")).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> authService.verifyCode("test@example.com", "000000")
        );
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (classes don't exist)**

```bash
./gradlew test --tests "com.devflow.auth.AuthServiceTest"
# Expected: compilation error — AuthService, JwtUtils etc. not found
```

- [ ] **Step 3: Implement User entity**

```java
// src/main/java/com/devflow/domain/user/User.java
package com.devflow.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "\"user\"")
@Getter @Setter
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(unique = true, nullable = false)
    private String email;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

```java
// src/main/java/com/devflow/domain/user/UserRepository.java
package com.devflow.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}
```

- [ ] **Step 4: Implement JwtUtils**

```java
// src/main/java/com/devflow/auth/JwtUtils.java
package com.devflow.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtils {
    @Value("${app.jwt.secret}") private String secret;
    @Value("${app.jwt.expiration-ms}") private long expirationMs;

    public String generateToken(String email) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean isValid(String token) {
        try { extractEmail(token); return true; }
        catch (JwtException e) { return false; }
    }
}
```

- [ ] **Step 5: Implement VerificationCodeService**

```java
// src/main/java/com/devflow/auth/VerificationCodeService.java
package com.devflow.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

@Service
public class VerificationCodeService {

    @PersistenceContext private EntityManager em;
    @Value("${app.verification-code.expiration-minutes}") private int expirationMinutes;

    @Transactional
    public String generate(String email) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        em.createNativeQuery(
            "INSERT INTO verification_code(id,email,code,expires_at) VALUES(?,?,?,?)")
            .setParameter(1, UUID.randomUUID())
            .setParameter(2, email)
            .setParameter(3, code)
            .setParameter(4, Instant.now().plusSeconds(expirationMinutes * 60L))
            .executeUpdate();
        return code;
    }

    @Transactional
    public boolean verify(String email, String code) {
        int updated = em.createNativeQuery(
            "UPDATE verification_code SET used=true WHERE email=? AND code=? " +
            "AND used=false AND expires_at > NOW()")
            .setParameter(1, email)
            .setParameter(2, code)
            .executeUpdate();
        return updated > 0;
    }
}
```

- [ ] **Step 6: Implement AuthService**

```java
// src/main/java/com/devflow/auth/AuthService.java
package com.devflow.auth;

import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final JwtUtils jwtUtils;

    public record AuthResult(String token, UserDto user) {}
    public record UserDto(String email, String name) {}

    public void sendCode(String email) {
        String code = verificationCodeService.generate(email);
        // In production: send via email service. For now: log it.
        System.out.println("VERIFICATION CODE for " + email + ": " + code);
    }

    @Transactional
    public AuthResult verifyCode(String email, String code) {
        if (!verificationCodeService.verify(email, code)) {
            throw new IllegalArgumentException("Invalid or expired verification code");
        }
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            var u = new User();
            u.setEmail(email);
            u.setName(email.split("@")[0]);
            return userRepository.save(u);
        });
        String token = jwtUtils.generateToken(email);
        return new AuthResult(token, new UserDto(user.getEmail(), user.getName()));
    }

    public UserDto getMe(String email) {
        return userRepository.findByEmail(email)
            .map(u -> new UserDto(u.getEmail(), u.getName()))
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
```

- [ ] **Step 7: Implement SecurityConfig + AuthController**

```java
// src/main/java/com/devflow/config/SecurityConfig.java
package com.devflow.config;

import com.devflow.auth.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration @EnableWebSecurity @RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtils jwtUtils;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**", "/ws/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public OncePerRequestFilter jwtFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                    HttpServletResponse res, FilterChain chain)
                    throws IOException, jakarta.servlet.ServletException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    if (jwtUtils.isValid(token)) {
                        String email = jwtUtils.extractEmail(token);
                        var auth = new UsernamePasswordAuthenticationToken(
                            email, null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }
}
```

```java
// src/main/java/com/devflow/auth/AuthController.java
package com.devflow.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record SendCodeRequest(String email) {}
    public record VerifyCodeRequest(String email, String code) {}

    @PostMapping("/send-code")
    public ResponseEntity<Void> sendCode(@RequestBody SendCodeRequest req) {
        authService.sendCode(req.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-code")
    public ResponseEntity<AuthService.AuthResult> verifyCode(@RequestBody VerifyCodeRequest req) {
        return ResponseEntity.ok(authService.verifyCode(req.email(), req.code()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthService.UserDto> me(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.getMe(email));
    }
}
```

- [ ] **Step 8: Run tests — expect PASS**

```bash
./gradlew test --tests "com.devflow.auth.AuthServiceTest"
# Expected: 2 tests passing
```

- [ ] **Step 9: Commit**

```bash
git add src/
git commit -m "feat(auth): JWT auth + email verification code + user entity"
```

---

## Task 4: Project CRUD API

**Files:**
- Create: `src/main/java/com/devflow/domain/project/Project.java`
- Create: `src/main/java/com/devflow/domain/project/ProjectRepository.java`
- Create: `src/main/java/com/devflow/domain/project/ProjectService.java`
- Create: `src/main/java/com/devflow/domain/project/ProjectController.java`
- Test: `src/test/java/com/devflow/project/ProjectServiceTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/devflow/project/ProjectServiceTest.java
package com.devflow.project;

import com.devflow.domain.project.*;
import com.devflow.domain.user.User;
import com.devflow.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ProjectService projectService;

    @Test
    void createProject_validUser_savesAndReturns() {
        var user = new User();
        user.setEmail("test@example.com");

        var req = new ProjectService.CreateProjectRequest("My App", "desc", "Java Spring Boot", "/code/myapp");
        var saved = new Project();
        saved.setId(UUID.randomUUID());
        saved.setName("My App");
        saved.setUser(user);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.save(any())).thenReturn(saved);

        var result = projectService.create("test@example.com", req);

        assertThat(result.name()).isEqualTo("My App");
    }

    @Test
    void listProjects_returnsOnlyUsersProjects() {
        var user = new User();
        user.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(projectRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        var result = projectService.list("test@example.com");
        assertThat(result).isEmpty();
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew test --tests "com.devflow.project.ProjectServiceTest"
# Expected: compilation error
```

- [ ] **Step 3: Implement Project entity + repository**

```java
// src/main/java/com/devflow/domain/project/Project.java
package com.devflow.domain.project;

import com.devflow.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false) private String name;
    private String description;
    private String techStack;
    private String repoPath;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
```

```java
// src/main/java/com/devflow/domain/project/ProjectRepository.java
package com.devflow.domain.project;

import com.devflow.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByUserOrderByCreatedAtDesc(User user);
}
```

- [ ] **Step 4: Implement ProjectService + ProjectController**

```java
// src/main/java/com/devflow/domain/project/ProjectService.java
package com.devflow.domain.project;

import com.devflow.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public record CreateProjectRequest(String name, String description, String techStack, String repoPath) {}
    public record ProjectDto(UUID id, String name, String description, String techStack, String repoPath, Instant createdAt) {}

    private ProjectDto toDto(Project p) {
        return new ProjectDto(p.getId(), p.getName(), p.getDescription(), p.getTechStack(), p.getRepoPath(), p.getCreatedAt());
    }

    @Transactional
    public ProjectDto create(String email, CreateProjectRequest req) {
        var user = userRepository.findByEmail(email).orElseThrow();
        var p = new Project();
        p.setUser(user);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setTechStack(req.techStack());
        p.setRepoPath(req.repoPath());
        return toDto(projectRepository.save(p));
    }

    public List<ProjectDto> list(String email) {
        var user = userRepository.findByEmail(email).orElseThrow();
        return projectRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toDto).toList();
    }

    public ProjectDto get(String email, UUID id) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        return toDto(p);
    }

    @Transactional
    public ProjectDto update(String email, UUID id, CreateProjectRequest req) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        p.setName(req.name()); p.setDescription(req.description());
        p.setTechStack(req.techStack()); p.setRepoPath(req.repoPath());
        p.setUpdatedAt(Instant.now());
        return toDto(projectRepository.save(p));
    }

    @Transactional
    public void delete(String email, UUID id) {
        var p = projectRepository.findById(id).orElseThrow();
        if (!p.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        projectRepository.delete(p);
    }
}
```

```java
// src/main/java/com/devflow/domain/project/ProjectController.java
package com.devflow.domain.project;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectService.ProjectDto> list(@AuthenticationPrincipal String email) {
        return projectService.list(email);
    }

    @PostMapping
    public ProjectService.ProjectDto create(@AuthenticationPrincipal String email,
                                            @RequestBody ProjectService.CreateProjectRequest req) {
        return projectService.create(email, req);
    }

    @GetMapping("/{id}")
    public ProjectService.ProjectDto get(@AuthenticationPrincipal String email, @PathVariable UUID id) {
        return projectService.get(email, id);
    }

    @PutMapping("/{id}")
    public ProjectService.ProjectDto update(@AuthenticationPrincipal String email,
                                            @PathVariable UUID id,
                                            @RequestBody ProjectService.CreateProjectRequest req) {
        return projectService.update(email, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal String email, @PathVariable UUID id) {
        projectService.delete(email, id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

```bash
./gradlew test --tests "com.devflow.project.ProjectServiceTest"
# Expected: 2 tests passing
```

- [ ] **Step 6: Commit**

```bash
git add src/
git commit -m "feat(project): project CRUD API"
```

---

## Task 5: Pipeline Domain Entities

**Files:**
- Create: `src/main/java/com/devflow/domain/pipeline/model/` (all 6 entities)
- Create: `src/main/java/com/devflow/domain/pipeline/repository/` (all 6 repositories)

- [ ] **Step 1: Create pipeline enums + PipelineRun entity**

```java
// src/main/java/com/devflow/domain/pipeline/model/PipelineRun.java
package com.devflow.domain.pipeline.model;

import com.devflow.domain.project.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class PipelineRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Project project;
    @Column(nullable = false, columnDefinition = "TEXT") private String requirement;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "pipeline_status") private PipelineStatus status = PipelineStatus.running;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type") private StageType currentStage = StageType.requirements;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public enum PipelineStatus { running, waiting_human, completed, failed }
}
```

```java
// src/main/java/com/devflow/domain/pipeline/model/StageType.java
package com.devflow.domain.pipeline.model;
public enum StageType { requirements, planning, coding, testing }
```

```java
// src/main/java/com/devflow/domain/pipeline/model/StageRun.java
package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class StageRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private PipelineRun pipelineRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type", nullable = false) private StageType stageType;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_status", nullable = false) private StageStatus status = StageStatus.pending;

    private int orderIndex = 0;
    private Instant startedAt;
    private Instant completedAt;

    public enum StageStatus {
        pending, running, waiting_answer, waiting_choice,
        waiting_approval, waiting_revision, completed, failed, skipped
    }
}
```

```java
// src/main/java/com/devflow/domain/pipeline/model/Message.java
package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private StageRun stageRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "message_role") private Role role;
    @Column(columnDefinition = "TEXT", nullable = false) private String content;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "message_type") private MessageType type = MessageType.text;

    @JdbcTypeCode(SqlTypes.JSON)
    private String options; // JSON: [{id, label, description}]
    private String selectedOption;
    private Instant createdAt = Instant.now();

    public enum Role { user, assistant, system }
    public enum MessageType { text, question, choice_request, choice_response }
}
```

```java
// src/main/java/com/devflow/domain/pipeline/model/Artifact.java
package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class Artifact {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private StageRun stageRun;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "artifact_type") private ArtifactType type;
    private String title;
    @Column(columnDefinition = "TEXT") private String content = "";
    private Instant approvedAt;
    private Instant createdAt = Instant.now();

    public enum ArtifactType { prd, plan, code, test_result }
}
```

```java
// src/main/java/com/devflow/domain/pipeline/model/CodeFile.java
package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class CodeFile {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(nullable = false) private Artifact artifact;
    @Column(nullable = false) private String path;
    @Column(columnDefinition = "TEXT") private String content = "";
    private String language;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
```

```java
// src/main/java/com/devflow/domain/pipeline/model/AgentConfig.java
package com.devflow.domain.pipeline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Getter @Setter
public class AgentConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "stage_type", unique = true) private StageType stageType;
    private String name;
    @Column(columnDefinition = "TEXT") private String systemPrompt;
    private String model = "claude-opus-4-7";
    private int maxTokens = 8192;
    private int maxRetries = 3;
    private Instant updatedAt = Instant.now();
}
```

- [ ] **Step 2: Create repositories**

```java
// src/main/java/com/devflow/domain/pipeline/repository/PipelineRunRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.PipelineRun;
import com.devflow.domain.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    List<PipelineRun> findByProjectOrderByCreatedAtDesc(Project project);
}
```

```java
// src/main/java/com/devflow/domain/pipeline/repository/StageRunRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface StageRunRepository extends JpaRepository<StageRun, UUID> {
    List<StageRun> findByPipelineRunOrderByOrderIndexAsc(PipelineRun run);
    Optional<StageRun> findTopByPipelineRunAndStageTypeOrderByOrderIndexDesc(PipelineRun run, StageType type);
}
```

```java
// src/main/java/com/devflow/domain/pipeline/repository/MessageRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByStageRunOrderByCreatedAtAsc(StageRun stageRun);
}
```

```java
// src/main/java/com/devflow/domain/pipeline/repository/ArtifactRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    Optional<Artifact> findByStageRun(StageRun stageRun);
}
```

```java
// src/main/java/com/devflow/domain/pipeline/repository/CodeFileRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface CodeFileRepository extends JpaRepository<CodeFile, UUID> {
    List<CodeFile> findByArtifactOrderByPathAsc(Artifact artifact);
}
```

```java
// src/main/java/com/devflow/domain/pipeline/repository/AgentConfigRepository.java
package com.devflow.domain.pipeline.repository;
import com.devflow.domain.pipeline.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AgentConfigRepository extends JpaRepository<AgentConfig, UUID> {
    Optional<AgentConfig> findByStageType(StageType stageType);
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileJava
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat(pipeline): domain entities + repositories"
```

---

## Task 6: Pipeline State Machine

**Files:**
- Create: `src/main/java/com/devflow/engine/PipelineEngine.java`
- Test: `src/test/java/com/devflow/engine/StateMachineTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/devflow/engine/StateMachineTest.java
package com.devflow.engine;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StateMachineTest {

    @Mock StageRunRepository stageRunRepository;
    @Mock PipelineRunRepository pipelineRunRepository;
    @Mock MessageRepository messageRepository;
    @Mock ArtifactRepository artifactRepository;
    @Mock EventPublisherMock eventPublisher;
    @InjectMocks PipelineEngine engine;

    // Simple mock for EventPublisher
    interface EventPublisherMock {
        void publish(Object event);
    }

    private StageRun stageRunWith(StageStatus status) {
        var sr = new StageRun();
        sr.setId(UUID.randomUUID());
        sr.setStatus(status);
        sr.setStageType(StageType.requirements);
        var run = new PipelineRun();
        run.setId(UUID.randomUUID());
        sr.setPipelineRun(run);
        return sr;
    }

    @Test
    void transitionToWaitingAnswer_fromRunning_succeeds() {
        var sr = stageRunWith(StageStatus.running);
        when(stageRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.transitionTo(sr, StageStatus.waiting_answer);

        assertThat(sr.getStatus()).isEqualTo(StageStatus.waiting_answer);
    }

    @Test
    void transitionToRunning_fromPending_succeeds() {
        var sr = stageRunWith(StageStatus.pending);
        when(stageRunRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        engine.transitionTo(sr, StageStatus.running);

        assertThat(sr.getStatus()).isEqualTo(StageStatus.running);
    }

    @Test
    void illegalTransition_fromCompleted_throws() {
        var sr = stageRunWith(StageStatus.completed);

        assertThatThrownBy(() -> engine.transitionTo(sr, StageStatus.running))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition");
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
./gradlew test --tests "com.devflow.engine.StateMachineTest"
# Expected: compilation error — PipelineEngine not found
```

- [ ] **Step 3: Implement PipelineEngine**

```java
// src/main/java/com/devflow/engine/PipelineEngine.java
package com.devflow.engine;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PipelineEngine {

    private final StageRunRepository stageRunRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final EventPublisher eventPublisher;
    private final List<StageExecutor> executors;

    // Valid transitions: from -> allowed destinations
    private static final Map<StageStatus, Set<StageStatus>> TRANSITIONS = Map.of(
        StageStatus.pending,          Set.of(StageStatus.running, StageStatus.skipped),
        StageStatus.running,          Set.of(StageStatus.waiting_answer, StageStatus.waiting_choice,
                                            StageStatus.waiting_approval, StageStatus.failed),
        StageStatus.waiting_answer,   Set.of(StageStatus.running),
        StageStatus.waiting_choice,   Set.of(StageStatus.running),
        StageStatus.waiting_approval, Set.of(StageStatus.completed, StageStatus.waiting_revision),
        StageStatus.waiting_revision, Set.of(StageStatus.running),
        StageStatus.completed,        Set.of(),  // terminal
        StageStatus.failed,           Set.of(StageStatus.running),  // allow manual retry
        StageStatus.skipped,          Set.of()   // terminal
    );

    @Transactional
    public void transitionTo(StageRun stageRun, StageStatus newStatus) {
        StageStatus current = stageRun.getStatus();
        Set<StageStatus> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                "Cannot transition from " + current + " to " + newStatus);
        }
        stageRun.setStatus(newStatus);
        if (newStatus == StageStatus.running && stageRun.getStartedAt() == null) {
            stageRun.setStartedAt(Instant.now());
        }
        if (newStatus == StageStatus.completed || newStatus == StageStatus.failed) {
            stageRun.setCompletedAt(Instant.now());
        }
        stageRunRepository.save(stageRun);
        eventPublisher.publishStageUpdate(stageRun);
        updatePipelineRunStatus(stageRun.getPipelineRun());
    }

    @Transactional
    public StageRun createAndStartStage(PipelineRun pipelineRun, StageType stageType, int orderIndex) {
        var sr = new StageRun();
        sr.setPipelineRun(pipelineRun);
        sr.setStageType(stageType);
        sr.setOrderIndex(orderIndex);
        sr.setStatus(StageStatus.pending);
        sr = stageRunRepository.save(sr);
        transitionTo(sr, StageStatus.running);
        return sr;
    }

    @Async
    public void executeStage(StageRun stageRun) {
        StageExecutor executor = executors.stream()
            .filter(e -> e.supports(stageRun.getStageType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No executor for " + stageRun.getStageType()));
        try {
            executor.execute(stageRun);
        } catch (Exception e) {
            log.error("Stage execution failed: {}", stageRun.getId(), e);
            transitionTo(stageRun, StageStatus.failed);
        }
    }

    @Transactional
    public void onStageCompleted(StageRun stageRun) {
        transitionTo(stageRun, StageStatus.completed);
        PipelineRun run = stageRun.getPipelineRun();

        // Advance to next stage
        StageType next = nextStage(stageRun.getStageType());
        if (next == null) {
            run.setStatus(PipelineRun.PipelineStatus.completed);
            pipelineRunRepository.save(run);
            eventPublisher.publishPipelineUpdate(run);
            return;
        }

        run.setCurrentStage(next);
        pipelineRunRepository.save(run);

        int nextIndex = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun nextRun = createAndStartStage(run, next, nextIndex);
        executeStage(nextRun);
    }

    private StageType nextStage(StageType current) {
        return switch (current) {
            case requirements -> StageType.planning;
            case planning     -> StageType.coding;
            case coding       -> StageType.testing;
            case testing      -> null;
        };
    }

    private void updatePipelineRunStatus(PipelineRun run) {
        boolean anyWaiting = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(run).stream()
            .anyMatch(sr -> sr.getStatus().name().startsWith("waiting_"));
        if (anyWaiting) {
            run.setStatus(PipelineRun.PipelineStatus.waiting_human);
        } else {
            run.setStatus(PipelineRun.PipelineStatus.running);
        }
        run.setUpdatedAt(Instant.now());
        pipelineRunRepository.save(run);
    }
}
```

- [ ] **Step 4: Create StageExecutor interface**

```java
// src/main/java/com/devflow/engine/StageExecutor.java
package com.devflow.engine;

import com.devflow.domain.pipeline.model.StageRun;
import com.devflow.domain.pipeline.model.StageType;

public interface StageExecutor {
    boolean supports(StageType type);
    void execute(StageRun stageRun);
}
```

- [ ] **Step 5: Create EventPublisher stub**

```java
// src/main/java/com/devflow/realtime/EventPublisher.java
package com.devflow.realtime;

import com.devflow.domain.pipeline.model.PipelineRun;
import com.devflow.domain.pipeline.model.StageRun;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class EventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishStageUpdate(StageRun stageRun) {
        messaging.convertAndSend(
            "/topic/pipeline/" + stageRun.getPipelineRun().getId() + "/stage",
            new StageUpdateEvent(stageRun.getId(), stageRun.getStatus().name())
        );
    }

    public void publishPipelineUpdate(PipelineRun run) {
        messaging.convertAndSend(
            "/topic/pipeline/" + run.getId(),
            new PipelineUpdateEvent(run.getId(), run.getStatus().name())
        );
    }

    public void publishStreamChunk(java.util.UUID stageRunId, String chunk) {
        messaging.convertAndSend(
            "/topic/stage/" + stageRunId + "/stream",
            new StreamChunkEvent(chunk)
        );
    }

    public void publishCodeFile(java.util.UUID stageRunId,
                                 java.util.UUID fileId, String path, String language) {
        messaging.convertAndSend(
            "/topic/stage/" + stageRunId + "/file",
            new CodeFileEvent(fileId, path, language)
        );
    }

    public record StageUpdateEvent(java.util.UUID stageRunId, String status) {}
    public record PipelineUpdateEvent(java.util.UUID pipelineRunId, String status) {}
    public record StreamChunkEvent(String chunk) {}
    public record CodeFileEvent(java.util.UUID fileId, String path, String language) {}
}
```

- [ ] **Step 6: Create WebSocketConfig**

```java
// src/main/java/com/devflow/config/WebSocketConfig.java
package com.devflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
```

- [ ] **Step 7: Run state machine tests — expect PASS**

```bash
./gradlew test --tests "com.devflow.engine.StateMachineTest"
# Expected: 3 tests passing
```

- [ ] **Step 8: Commit**

```bash
git add src/
git commit -m "feat(engine): pipeline state machine + WebSocket event publisher"
```

---

## Task 7: Pipeline REST API + Human-in-the-Loop Endpoints

**Files:**
- Create: `src/main/java/com/devflow/domain/pipeline/PipelineController.java`
- Create: `src/main/java/com/devflow/domain/pipeline/StageController.java`
- Create: `src/main/java/com/devflow/domain/pipeline/dto/*.java`

- [ ] **Step 1: Create DTOs**

```java
// src/main/java/com/devflow/domain/pipeline/dto/CreatePipelineRequest.java
package com.devflow.domain.pipeline.dto;
public record CreatePipelineRequest(String requirement) {}
```

```java
// src/main/java/com/devflow/domain/pipeline/dto/AnswerRequest.java
package com.devflow.domain.pipeline.dto;
public record AnswerRequest(String content) {}
```

```java
// src/main/java/com/devflow/domain/pipeline/dto/ChoiceRequest.java
package com.devflow.domain.pipeline.dto;
public record ChoiceRequest(String optionId) {}
```

```java
// src/main/java/com/devflow/domain/pipeline/dto/RevisionRequest.java
package com.devflow.domain.pipeline.dto;
public record RevisionRequest(String feedback) {}
```

- [ ] **Step 2: Implement PipelineController**

```java
// src/main/java/com/devflow/domain/pipeline/PipelineController.java
package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.dto.CreatePipelineRequest;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.domain.project.ProjectRepository;
import com.devflow.domain.user.UserRepository;
import com.devflow.engine.PipelineEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController @RequestMapping("/api/projects/{projectId}/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineRunRepository pipelineRunRepository;
    private final StageRunRepository stageRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final CodeFileRepository codeFileRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PipelineEngine pipelineEngine;

    @PostMapping
    public ResponseEntity<PipelineRunDto> create(
            @AuthenticationPrincipal String email,
            @PathVariable UUID projectId,
            @RequestBody CreatePipelineRequest req) {

        var project = projectRepository.findById(projectId).orElseThrow();
        if (!project.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");

        var run = new PipelineRun();
        run.setProject(project);
        run.setRequirement(req.requirement());
        run = pipelineRunRepository.save(run);

        // Start requirements stage async
        StageRun firstStage = pipelineEngine.createAndStartStage(run, StageType.requirements, 0);
        pipelineEngine.executeStage(firstStage);

        return ResponseEntity.ok(toDto(run));
    }

    @GetMapping
    public List<PipelineRunDto> list(@AuthenticationPrincipal String email,
                                     @PathVariable UUID projectId) {
        var project = projectRepository.findById(projectId).orElseThrow();
        if (!project.getUser().getEmail().equals(email)) throw new SecurityException("Forbidden");
        return pipelineRunRepository.findByProjectOrderByCreatedAtDesc(project)
            .stream().map(this::toDto).toList();
    }

    @GetMapping("/{runId}")
    public PipelineRunDto get(@AuthenticationPrincipal String email,
                               @PathVariable UUID projectId,
                               @PathVariable UUID runId) {
        var run = pipelineRunRepository.findById(runId).orElseThrow();
        if (!run.getProject().getId().equals(projectId)) throw new IllegalArgumentException();
        return toDto(run);
    }

    @GetMapping("/{runId}/stages")
    public List<StageRunDto> stages(@PathVariable UUID runId) {
        var run = pipelineRunRepository.findById(runId).orElseThrow();
        return stageRunRepository.findByPipelineRunOrderByOrderIndexAsc(run)
            .stream().map(this::toStageDto).toList();
    }

    @GetMapping("/{runId}/stages/{stageId}/messages")
    public List<MessageDto> messages(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return messageRepository.findByStageRunOrderByCreatedAtAsc(stage)
            .stream().map(this::toMessageDto).toList();
    }

    @GetMapping("/{runId}/stages/{stageId}/artifact")
    public ResponseEntity<ArtifactDto> artifact(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return artifactRepository.findByStageRun(stage)
            .map(this::toArtifactDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{runId}/stages/{stageId}/files")
    public List<CodeFileDto> files(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        return artifactRepository.findByStageRun(stage)
            .map(a -> codeFileRepository.findByArtifactOrderByPathAsc(a)
                .stream().map(this::toFileDto).toList())
            .orElse(List.of());
    }

    // --- DTOs ---
    public record PipelineRunDto(UUID id, String requirement, String status,
                                  String currentStage, java.time.Instant createdAt) {}
    public record StageRunDto(UUID id, String stageType, String status,
                               int orderIndex, java.time.Instant startedAt, java.time.Instant completedAt) {}
    public record MessageDto(UUID id, String role, String content, String type,
                              String options, String selectedOption, java.time.Instant createdAt) {}
    public record ArtifactDto(UUID id, String type, String title, String content,
                               java.time.Instant approvedAt, java.time.Instant createdAt) {}
    public record CodeFileDto(UUID id, String path, String language, java.time.Instant updatedAt) {}

    private PipelineRunDto toDto(PipelineRun r) {
        return new PipelineRunDto(r.getId(), r.getRequirement(),
            r.getStatus().name(), r.getCurrentStage().name(), r.getCreatedAt());
    }
    private StageRunDto toStageDto(StageRun sr) {
        return new StageRunDto(sr.getId(), sr.getStageType().name(),
            sr.getStatus().name(), sr.getOrderIndex(), sr.getStartedAt(), sr.getCompletedAt());
    }
    private MessageDto toMessageDto(Message m) {
        return new MessageDto(m.getId(), m.getRole().name(), m.getContent(),
            m.getType().name(), m.getOptions(), m.getSelectedOption(), m.getCreatedAt());
    }
    private ArtifactDto toArtifactDto(Artifact a) {
        return new ArtifactDto(a.getId(), a.getType().name(), a.getTitle(),
            a.getContent(), a.getApprovedAt(), a.getCreatedAt());
    }
    private CodeFileDto toFileDto(CodeFile f) {
        return new CodeFileDto(f.getId(), f.getPath(), f.getLanguage(), f.getUpdatedAt());
    }
}
```

- [ ] **Step 3: Implement StageController (Human-in-the-Loop)**

```java
// src/main/java/com/devflow/domain/pipeline/StageController.java
package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.dto.*;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.engine.PipelineEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageRunRepository stageRunRepository;
    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final CodeFileRepository codeFileRepository;
    private final PipelineEngine pipelineEngine;

    /** User answers a question asked by the Agent */
    @PostMapping("/{stageId}/answer")
    public ResponseEntity<Void> answer(@PathVariable UUID stageId,
                                        @RequestBody AnswerRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_answer) {
            return ResponseEntity.badRequest().build();
        }
        // Save user answer as message
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent(req.content());
        msg.setType(Message.MessageType.text);
        messageRepository.save(msg);

        // Transition back to running and re-execute
        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** User selects an option from a choice presented by the Agent */
    @PostMapping("/{stageId}/choose")
    public ResponseEntity<Void> choose(@PathVariable UUID stageId,
                                        @RequestBody ChoiceRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_choice) {
            return ResponseEntity.badRequest().build();
        }
        // Record selection
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent("Selected option: " + req.optionId());
        msg.setType(Message.MessageType.choice_response);
        msg.setSelectedOption(req.optionId());
        messageRepository.save(msg);

        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** User approves the artifact and advances to next stage */
    @PostMapping("/{stageId}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID stageId) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_approval) {
            return ResponseEntity.badRequest().build();
        }
        artifactRepository.findByStageRun(stage).ifPresent(a -> {
            a.setApprovedAt(Instant.now());
            artifactRepository.save(a);
        });
        pipelineEngine.onStageCompleted(stage);
        return ResponseEntity.ok().build();
    }

    /** User requests revision with feedback */
    @PostMapping("/{stageId}/revise")
    public ResponseEntity<Void> revise(@PathVariable UUID stageId,
                                        @RequestBody RevisionRequest req) {
        var stage = stageRunRepository.findById(stageId).orElseThrow();
        if (stage.getStatus() != StageStatus.waiting_approval) {
            return ResponseEntity.badRequest().build();
        }
        var msg = new Message();
        msg.setStageRun(stage);
        msg.setRole(Message.Role.user);
        msg.setContent("Revision requested: " + req.feedback());
        msg.setType(Message.MessageType.text);
        messageRepository.save(msg);

        pipelineEngine.transitionTo(stage, StageStatus.waiting_revision);
        pipelineEngine.transitionTo(stage, StageStatus.running);
        pipelineEngine.executeStage(stage);
        return ResponseEntity.ok().build();
    }

    /** Update a code file manually (during WAITING_APPROVAL of coding stage) */
    @PatchMapping("/files/{fileId}")
    public ResponseEntity<Void> updateFile(@PathVariable UUID fileId,
                                            @RequestBody UpdateFileRequest req) {
        var file = codeFileRepository.findById(fileId).orElseThrow();
        file.setContent(req.content());
        file.setUpdatedAt(Instant.now());
        codeFileRepository.save(file);
        return ResponseEntity.ok().build();
    }

    public record UpdateFileRequest(String content) {}
}
```

- [ ] **Step 4: Compile check**

```bash
./gradlew compileJava
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 5: Commit**

```bash
git add src/
git commit -m "feat(api): pipeline REST endpoints + human-in-the-loop actions"
```

---

## Task 8: Claude API Client

**Files:**
- Create: `src/main/java/com/devflow/ai/ClaudeClient.java`
- Create: `src/main/java/com/devflow/ai/ContextBuilder.java`

- [ ] **Step 1: Implement ClaudeClient (streaming)**

```java
// src/main/java/com/devflow/ai/ClaudeClient.java
package com.devflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component @RequiredArgsConstructor @Slf4j
public class ClaudeClient {

    @Value("${app.claude.api-key}") private String apiKey;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record ChatMessage(String role, String content) {}

    /**
     * Stream a response from Claude. Calls onChunk for each text delta,
     * onComplete with the full accumulated text when done.
     */
    public void streamChat(String model, int maxTokens, String systemPrompt,
                            List<ChatMessage> messages,
                            Consumer<String> onChunk,
                            Consumer<String> onComplete) {
        try {
            var body = mapper.writeValueAsString(Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "stream", true,
                "messages", messages.stream()
                    .map(m -> Map.of("role", m.role(), "content", m.content()))
                    .toList()
            ));

            var request = new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Claude API error: " + response.code()
                        + " " + response.body().string());
                }
                var sb = new StringBuilder();
                try (var reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        try {
                            var node = mapper.readTree(data);
                            if ("content_block_delta".equals(node.path("type").asText())) {
                                String chunk = node.path("delta").path("text").asText("");
                                if (!chunk.isEmpty()) {
                                    sb.append(chunk);
                                    onChunk.accept(chunk);
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                onComplete.accept(sb.toString());
            }
        } catch (Exception e) {
            log.error("Claude API streaming failed", e);
            throw new RuntimeException("Claude API error: " + e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 2: Implement ContextBuilder**

```java
// src/main/java/com/devflow/ai/ContextBuilder.java
package com.devflow.ai;

import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component @RequiredArgsConstructor
public class ContextBuilder {

    private final MessageRepository messageRepository;
    private final ArtifactRepository artifactRepository;
    private final StageRunRepository stageRunRepository;

    /**
     * Build the full message history for the current stage + context from prior stages.
     */
    public List<ClaudeClient.ChatMessage> build(StageRun stageRun) {
        var messages = new ArrayList<ClaudeClient.ChatMessage>();

        // Add prior completed artifacts as context
        var allStages = stageRunRepository
            .findByPipelineRunOrderByOrderIndexAsc(stageRun.getPipelineRun());
        for (StageRun prior : allStages) {
            if (prior.getId().equals(stageRun.getId())) break;
            if (prior.getStatus() == StageRun.StageStatus.completed) {
                artifactRepository.findByStageRun(prior).ifPresent(artifact ->
                    messages.add(new ClaudeClient.ChatMessage("user",
                        "[" + artifact.getType().name().toUpperCase() + " - " + artifact.getTitle() + "]\n"
                        + artifact.getContent()))
                );
            }
        }

        // Add current stage conversation
        messageRepository.findByStageRunOrderByCreatedAtAsc(stageRun).forEach(m ->
            messages.add(new ClaudeClient.ChatMessage(m.getRole().name(), m.getContent()))
        );

        return messages;
    }

    /** Build the initial user message for a stage */
    public String buildInitialPrompt(StageRun stageRun) {
        String requirement = stageRun.getPipelineRun().getRequirement();
        String techStack = stageRun.getPipelineRun().getProject().getTechStack();
        return "Project tech stack: " + techStack + "\n\n"
             + "User requirement:\n" + requirement;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/
git commit -m "feat(ai): Claude streaming client + context builder"
```

---

## Task 9: Stage Executors (Requirements + Planning)

**Files:**
- Create: `src/main/java/com/devflow/engine/RequirementsExecutor.java`
- Create: `src/main/java/com/devflow/engine/PlanningExecutor.java`
- Create: `src/main/java/com/devflow/engine/BaseStageExecutor.java`

- [ ] **Step 1: Implement BaseStageExecutor**

```java
// src/main/java/com/devflow/engine/BaseStageExecutor.java
package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j @RequiredArgsConstructor
public abstract class BaseStageExecutor implements StageExecutor {

    protected final ClaudeClient claudeClient;
    protected final ContextBuilder contextBuilder;
    protected final PipelineEngine pipelineEngine;
    protected final MessageRepository messageRepository;
    protected final ArtifactRepository artifactRepository;
    protected final AgentConfigRepository agentConfigRepository;
    protected final EventPublisher eventPublisher;
    protected final ObjectMapper mapper = new ObjectMapper();

    @Override
    @Transactional
    public void execute(StageRun stageRun) {
        AgentConfig config = agentConfigRepository.findByStageType(stageRun.getStageType())
            .orElseThrow(() -> new IllegalStateException("No agent config for " + stageRun.getStageType()));

        // Build initial message if this is the first run for this stage
        var history = contextBuilder.build(stageRun);
        if (history.isEmpty() || isAllPriorContext(stageRun, history)) {
            String initial = contextBuilder.buildInitialPrompt(stageRun);
            var initMsg = new Message();
            initMsg.setStageRun(stageRun);
            initMsg.setRole(Message.Role.user);
            initMsg.setContent(initial);
            initMsg.setType(Message.MessageType.text);
            messageRepository.save(initMsg);
            history.add(new ClaudeClient.ChatMessage("user", initial));
        }

        // Stream response from Claude
        StringBuilder fullResponse = new StringBuilder();
        claudeClient.streamChat(
            config.getModel(), config.getMaxTokens(), config.getSystemPrompt(),
            history,
            chunk -> {
                fullResponse.append(chunk);
                eventPublisher.publishStreamChunk(stageRun.getId(), chunk);
            },
            complete -> {}
        );

        // Save assistant message
        var assistantMsg = new Message();
        assistantMsg.setStageRun(stageRun);
        assistantMsg.setRole(Message.Role.assistant);
        assistantMsg.setContent(fullResponse.toString());
        messageRepository.save(assistantMsg);

        // Parse response and determine next state
        handleResponse(stageRun, fullResponse.toString(), config);
    }

    protected abstract void handleResponse(StageRun stageRun, String response, AgentConfig config);

    /**
     * Parse the first JSON object embedded in the response text.
     * Agents output control JSON like {"type":"question","content":"..."} or {"type":"artifact",...}
     */
    protected JsonNode parseControlJson(String response) {
        // Find first { ... } block in response
        int start = response.indexOf('{');
        if (start == -1) return null;
        // Find matching closing brace
        int depth = 0, end = start;
        for (int i = start; i < response.length(); i++) {
            if (response.charAt(i) == '{') depth++;
            else if (response.charAt(i) == '}') { depth--; if (depth == 0) { end = i; break; } }
        }
        try {
            return mapper.readTree(response.substring(start, end + 1));
        } catch (Exception e) { return null; }
    }

    protected void saveArtifact(StageRun stageRun, Artifact.ArtifactType type,
                                 String title, String content) {
        var artifact = new Artifact();
        artifact.setStageRun(stageRun);
        artifact.setType(type);
        artifact.setTitle(title);
        artifact.setContent(content);
        artifactRepository.save(artifact);
    }

    private boolean isAllPriorContext(StageRun stageRun, List<ClaudeClient.ChatMessage> history) {
        return messageRepository.findByStageRunOrderByCreatedAtAsc(stageRun).isEmpty();
    }
}
```

- [ ] **Step 2: Implement RequirementsExecutor**

```java
// src/main/java/com/devflow/engine/RequirementsExecutor.java
package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.stereotype.Component;

@Component
public class RequirementsExecutor extends BaseStageExecutor {

    public RequirementsExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr, EventPublisher ep) {
        super(c, cb, e, mr, ar, acr, ep);
    }

    @Override public boolean supports(StageType type) { return type == StageType.requirements; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        var json = parseControlJson(response);
        if (json == null) {
            // No structured output — treat as continuation, ask Claude again
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            return;
        }

        String type = json.path("type").asText();
        switch (type) {
            case "question" -> {
                // Save as question message so frontend can render input box
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            }
            case "choice" -> {
                var choiceMsg = new Message();
                choiceMsg.setStageRun(stageRun);
                choiceMsg.setRole(Message.Role.assistant);
                choiceMsg.setContent(response);
                choiceMsg.setType(Message.MessageType.choice_request);
                choiceMsg.setOptions(json.path("options").toString());
                messageRepository.save(choiceMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_choice);
            }
            case "artifact" -> {
                String title = json.path("title").asText("PRD Document");
                // Content is the text after the JSON block
                String content = response.substring(response.indexOf('}') + 1).trim();
                saveArtifact(stageRun, Artifact.ArtifactType.prd, title, content);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            }
            default -> pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
```

- [ ] **Step 3: Implement PlanningExecutor**

```java
// src/main/java/com/devflow/engine/PlanningExecutor.java
package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.stereotype.Component;

@Component
public class PlanningExecutor extends BaseStageExecutor {

    public PlanningExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr, EventPublisher ep) {
        super(c, cb, e, mr, ar, acr, ep);
    }

    @Override public boolean supports(StageType type) { return type == StageType.planning; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        var json = parseControlJson(response);
        if (json == null) { pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer); return; }

        String type = json.path("type").asText();
        switch (type) {
            case "question" -> {
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
            }
            case "artifact" -> {
                String title = json.path("title").asText("Implementation Plan");
                String content = response.substring(response.indexOf('}') + 1).trim();
                saveArtifact(stageRun, Artifact.ArtifactType.plan, title, content);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            }
            default -> pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat(engine): requirements + planning stage executors"
```

---

## Task 10: Coding Stage Executor

**Files:**
- Create: `src/main/java/com/devflow/engine/CodingExecutor.java`

- [ ] **Step 1: Implement CodingExecutor**

```java
// src/main/java/com/devflow/engine/CodingExecutor.java
package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class CodingExecutor extends BaseStageExecutor {

    private final CodeFileRepository codeFileRepository;

    public CodingExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr,
            EventPublisher ep, CodeFileRepository cfr) {
        super(c, cb, e, mr, ar, acr, ep);
        this.codeFileRepository = cfr;
    }

    @Override public boolean supports(StageType type) { return type == StageType.coding; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        // Coding Agent emits multiple file blocks + a final artifact JSON
        List<CodeFile> files = new ArrayList<>();
        Artifact codeArtifact = new Artifact();
        codeArtifact.setStageRun(stageRun);
        codeArtifact.setType(Artifact.ArtifactType.code);
        codeArtifact.setTitle("Generated Code");
        codeArtifact = artifactRepository.save(codeArtifact);

        // Parse file blocks: {"type":"file","path":"...","language":"..."}\n```\n<content>\n```
        var filePattern = Pattern.compile(
            "\\{\"type\":\"file\",\"path\":\"([^\"]+)\",\"language\":\"([^\"]+)\"\\}\\n```[^\\n]*\\n([\\s\\S]*?)\\n```",
            Pattern.MULTILINE
        );
        var matcher = filePattern.matcher(response);
        while (matcher.find()) {
            String path = matcher.group(1);
            String language = matcher.group(2);
            String content = matcher.group(3);

            // Check if file already exists (revision case) — update instead of insert
            var existingOpt = codeFileRepository.findByArtifactOrderByPathAsc(codeArtifact)
                .stream().filter(f -> f.getPath().equals(path)).findFirst();

            CodeFile file;
            if (existingOpt.isPresent()) {
                file = existingOpt.get();
                file.setContent(content);
                file.setUpdatedAt(Instant.now());
            } else {
                file = new CodeFile();
                file.setArtifact(codeArtifact);
                file.setPath(path);
                file.setLanguage(language);
                file.setContent(content);
            }
            file = codeFileRepository.save(file);
            // Push to frontend in real-time
            eventPublisher.publishCodeFile(stageRun.getId(), file.getId(), path, language);
        }

        // Check for control JSON
        var json = parseControlJson(response);
        if (json != null) {
            String type = json.path("type").asText();
            if ("question".equals(type)) {
                var qMsg = new Message();
                qMsg.setStageRun(stageRun);
                qMsg.setRole(Message.Role.assistant);
                qMsg.setContent(json.path("content").asText());
                qMsg.setType(Message.MessageType.question);
                messageRepository.save(qMsg);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
                return;
            }
            if ("artifact".equals(type)) {
                String summary = response.substring(response.lastIndexOf('}') + 1).trim();
                codeArtifact.setContent(summary);
                artifactRepository.save(codeArtifact);
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
                return;
            }
        }
        // If no explicit artifact signal but files were generated, go to approval
        if (!codeFileRepository.findByArtifactOrderByPathAsc(codeArtifact).isEmpty()) {
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
        } else {
            pipelineEngine.transitionTo(stageRun, StageStatus.waiting_answer);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/
git commit -m "feat(engine): coding stage executor with real-time file streaming"
```

---

## Task 11: Testing Stage Executor + Docker Sandbox

**Files:**
- Create: `src/main/java/com/devflow/sandbox/DockerSandbox.java`
- Create: `src/main/java/com/devflow/engine/TestingExecutor.java`

- [ ] **Step 1: Implement DockerSandbox**

```java
// src/main/java/com/devflow/sandbox/DockerSandbox.java
package com.devflow.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component @Slf4j
public class DockerSandbox {

    private final DockerClient docker;

    public DockerSandbox() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .maxConnections(10)
            .connectionTimeout(Duration.ofSeconds(30))
            .responseTimeout(Duration.ofMinutes(30))
            .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    public record RunResult(int exitCode, String output) {}

    /**
     * Write code files to a temp directory, run tests inside a Docker container,
     * stream output to onOutput, return exit code + full output.
     */
    public RunResult runTests(Map<String, String> files, String techStack,
                               Consumer<String> onOutput) throws Exception {
        // Write files to temp directory
        Path workDir = Files.createTempDirectory("devflow-test-" + UUID.randomUUID());
        try {
            for (var entry : files.entrySet()) {
                Path filePath = workDir.resolve(entry.getKey());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, entry.getValue());
            }

            // Determine Docker image and test command based on tech stack
            String image = resolveImage(techStack);
            List<String> cmd = resolveTestCommand(techStack);

            // Create container
            var container = docker.createContainerCmd(image)
                .withCmd(cmd)
                .withWorkingDir("/workspace")
                .withHostConfig(HostConfig.newHostConfig()
                    .withBinds(new Bind(workDir.toAbsolutePath().toString(),
                        new Volume("/workspace")))
                    .withMemory(2L * 1024 * 1024 * 1024) // 2GB
                    .withNanoCPUs(2_000_000_000L)) // 2 CPUs
                .exec();

            String containerId = container.getId();
            docker.startContainerCmd(containerId).exec();

            // Stream logs
            StringBuilder output = new StringBuilder();
            docker.logContainerCmd(containerId)
                .withStdOut(true).withStdErr(true).withFollowStream(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override public void onNext(Frame frame) {
                        String line = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        output.append(line);
                        onOutput.accept(line);
                    }
                }).awaitCompletion(30, TimeUnit.MINUTES);

            // Get exit code
            int exitCode = docker.inspectContainerCmd(containerId)
                .exec().getState().getExitCodeLong().intValue();

            docker.removeContainerCmd(containerId).withForce(true).exec();

            return new RunResult(exitCode, output.toString());
        } finally {
            deleteDirectory(workDir);
        }
    }

    private String resolveImage(String techStack) {
        if (techStack == null) return "maven:3.9-eclipse-temurin-21";
        String lower = techStack.toLowerCase();
        if (lower.contains("java") || lower.contains("spring")) return "maven:3.9-eclipse-temurin-21";
        if (lower.contains("node") || lower.contains("next")) return "node:20-alpine";
        if (lower.contains("python")) return "python:3.12-slim";
        if (lower.contains("go")) return "golang:1.22-alpine";
        return "ubuntu:22.04";
    }

    private List<String> resolveTestCommand(String techStack) {
        if (techStack == null) return List.of("mvn", "test");
        String lower = techStack.toLowerCase();
        if (lower.contains("java") || lower.contains("spring")) return List.of("mvn", "test", "-q");
        if (lower.contains("node")) return List.of("npm", "test");
        if (lower.contains("python")) return List.of("python", "-m", "pytest");
        if (lower.contains("go")) return List.of("go", "test", "./...");
        return List.of("mvn", "test");
    }

    private void deleteDirectory(Path path) {
        try {
            Files.walk(path).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 2: Implement TestingExecutor**

```java
// src/main/java/com/devflow/engine/TestingExecutor.java
package com.devflow.engine;

import com.devflow.ai.ClaudeClient;
import com.devflow.ai.ContextBuilder;
import com.devflow.domain.pipeline.model.*;
import com.devflow.domain.pipeline.model.StageRun.StageStatus;
import com.devflow.domain.pipeline.repository.*;
import com.devflow.realtime.EventPublisher;
import com.devflow.sandbox.DockerSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component @Slf4j
public class TestingExecutor extends BaseStageExecutor {

    private final CodeFileRepository codeFileRepository;
    private final DockerSandbox sandbox;
    private final StageRunRepository stageRunRepository2; // alias to avoid name clash

    public TestingExecutor(ClaudeClient c, ContextBuilder cb, PipelineEngine e,
            MessageRepository mr, ArtifactRepository ar, AgentConfigRepository acr,
            EventPublisher ep, CodeFileRepository cfr, DockerSandbox sb,
            StageRunRepository srr) {
        super(c, cb, e, mr, ar, acr, ep);
        this.codeFileRepository = cfr;
        this.sandbox = sb;
        this.stageRunRepository2 = srr;
    }

    @Override public boolean supports(StageType type) { return type == StageType.testing; }

    @Override
    protected void handleResponse(StageRun stageRun, String response, AgentConfig config) {
        // Testing stage doesn't use LLM directly — it runs tests in Docker
        // Get the latest coding artifact files
        var allStages = stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(stageRun.getPipelineRun());
        Optional<StageRun> latestCoding = allStages.stream()
            .filter(sr -> sr.getStageType() == StageType.coding
                && sr.getStatus() == StageStatus.completed)
            .reduce((a, b) -> b); // last one

        if (latestCoding.isEmpty()) {
            pipelineEngine.transitionTo(stageRun, StageStatus.failed);
            return;
        }

        // Build file map for sandbox
        Map<String, String> files = new HashMap<>();
        artifactRepository.findByStageRun(latestCoding.get()).ifPresent(artifact ->
            codeFileRepository.findByArtifactOrderByPathAsc(artifact)
                .forEach(f -> files.put(f.getPath(), f.getContent()))
        );

        String techStack = stageRun.getPipelineRun().getProject().getTechStack();

        try {
            var result = sandbox.runTests(files, techStack,
                line -> eventPublisher.publishStreamChunk(stageRun.getId(), line));

            // Save test result artifact
            saveArtifact(stageRun, Artifact.ArtifactType.test_result,
                "Test Results", result.output());

            if (result.exitCode() == 0) {
                // All tests passed
                pipelineEngine.transitionTo(stageRun, StageStatus.waiting_approval);
            } else {
                // Tests failed — check retry count
                long testingIterations = allStages.stream()
                    .filter(sr -> sr.getStageType() == StageType.testing).count();

                AgentConfig cfg = agentConfigRepository.findByStageType(StageType.testing).orElseThrow();
                if (testingIterations >= cfg.getMaxRetries()) {
                    log.warn("Max retries ({}) reached for pipeline {}",
                        cfg.getMaxRetries(), stageRun.getPipelineRun().getId());
                    pipelineEngine.transitionTo(stageRun, StageStatus.failed);
                    return;
                }

                // Ask LLM to produce fixes
                triggerAutoFix(stageRun, result.output(), files, config);
            }
        } catch (Exception e) {
            log.error("Test execution failed", e);
            pipelineEngine.transitionTo(stageRun, StageStatus.failed);
        }
    }

    @Override
    public void execute(StageRun stageRun) {
        // Testing executor runs Docker directly, not via LLM streaming
        handleResponse(stageRun, "", null);
    }

    private void triggerAutoFix(StageRun testingStageRun, String testOutput,
                                  Map<String, String> currentFiles, AgentConfig config) {
        AgentConfig codingConfig = agentConfigRepository
            .findByStageType(StageType.coding).orElseThrow();

        // Build fix prompt
        String fixPrompt = "Test execution failed. Output:\n```\n" + testOutput + "\n```\n\n"
            + "Current files:\n" + currentFiles.entrySet().stream()
                .map(e -> "File: " + e.getKey() + "\n```\n" + e.getValue() + "\n```")
                .reduce("", (a, b) -> a + "\n" + b);

        var fixMessages = List.of(new ClaudeClient.ChatMessage("user", fixPrompt));

        StringBuilder fixResponse = new StringBuilder();
        claudeClient.streamChat(
            codingConfig.getModel(), codingConfig.getMaxTokens(), codingConfig.getSystemPrompt(),
            fixMessages,
            chunk -> {
                fixResponse.append(chunk);
                eventPublisher.publishStreamChunk(testingStageRun.getId(), chunk);
            },
            complete -> {}
        );

        // Parse fix JSON and create new coding stage run
        var json = parseControlJson(fixResponse.toString());
        if (json == null || !"fix".equals(json.path("type").asText())) {
            pipelineEngine.transitionTo(testingStageRun, StageStatus.failed);
            return;
        }

        // Create new coding stage run with fixes applied
        PipelineRun run = testingStageRun.getPipelineRun();
        int nextIndex = (int) stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun fixRun = pipelineEngine.createAndStartStage(run, StageType.coding, nextIndex);

        // Create artifact and apply file fixes
        var fixArtifact = new Artifact();
        fixArtifact.setStageRun(fixRun);
        fixArtifact.setType(Artifact.ArtifactType.code);
        fixArtifact.setTitle("Auto-fix Iteration");
        fixArtifact = artifactRepository.save(fixArtifact);

        // Start with current files, apply fixes on top
        Map<String, String> mergedFiles = new HashMap<>(currentFiles);
        for (var fileNode : json.path("files")) {
            mergedFiles.put(fileNode.path("path").asText(), fileNode.path("content").asText());
        }
        for (var entry : mergedFiles.entrySet()) {
            var cf = new com.devflow.domain.pipeline.model.CodeFile();
            cf.setArtifact(fixArtifact);
            cf.setPath(entry.getKey());
            cf.setContent(entry.getValue());
            codeFileRepository.save(cf);
        }

        // Mark fix coding run as completed, then create new testing run
        pipelineEngine.transitionTo(fixRun, StageStatus.waiting_approval);
        pipelineEngine.transitionTo(fixRun, StageStatus.completed);

        int testIndex = (int) stageRunRepository2.findByPipelineRunOrderByOrderIndexAsc(run).size();
        StageRun newTestRun = pipelineEngine.createAndStartStage(run, StageType.testing, testIndex);
        pipelineEngine.executeStage(newTestRun);
    }
}
```

- [ ] **Step 3: Compile check**

```bash
./gradlew compileJava
# Expected: BUILD SUCCESSFUL
```

- [ ] **Step 4: Commit**

```bash
git add src/
git commit -m "feat(engine): testing executor + Docker sandbox + auto-fix loop"
```

---

## Task 12: Agent Config API + Global Exception Handler

**Files:**
- Create: `src/main/java/com/devflow/domain/pipeline/AgentConfigController.java`
- Create: `src/main/java/com/devflow/common/GlobalExceptionHandler.java`

- [ ] **Step 1: Implement AgentConfigController**

```java
// src/main/java/com/devflow/domain/pipeline/AgentConfigController.java
package com.devflow.domain.pipeline;

import com.devflow.domain.pipeline.model.AgentConfig;
import com.devflow.domain.pipeline.model.StageType;
import com.devflow.domain.pipeline.repository.AgentConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/agent-configs")
@RequiredArgsConstructor
public class AgentConfigController {

    private final AgentConfigRepository repo;

    @GetMapping
    public List<AgentConfig> list() { return repo.findAll(); }

    @PutMapping("/{id}")
    public AgentConfig update(@PathVariable UUID id, @RequestBody UpdateRequest req) {
        var config = repo.findById(id).orElseThrow();
        config.setSystemPrompt(req.systemPrompt());
        config.setModel(req.model());
        config.setMaxTokens(req.maxTokens());
        config.setMaxRetries(req.maxRetries());
        config.setUpdatedAt(Instant.now());
        return repo.save(config);
    }

    public record UpdateRequest(String systemPrompt, String model, int maxTokens, int maxRetries) {}
}
```

- [ ] **Step 2: Implement GlobalExceptionHandler**

```java
// src/main/java/com/devflow/common/GlobalExceptionHandler.java
package com.devflow.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String message, int status, Instant timestamp) {}

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(e.getMessage(), 400, Instant.now()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("Forbidden", 403, Instant.now()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(java.util.NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("Not found", 404, Instant.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("Internal server error: " + e.getMessage(), 500, Instant.now()));
    }
}
```

- [ ] **Step 3: Full build + all tests**

```bash
./gradlew build
# Expected: BUILD SUCCESSFUL, all tests green
```

- [ ] **Step 4: Smoke test with Docker**

```bash
# Start everything
docker compose up -d
./gradlew bootRun &

# Wait for app to start (look for "Started DevFlowApplication" in output), then:
# Send verification code
curl -X POST http://localhost:8080/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com"}'
# Expected: 200 OK, check console for the code

# (Replace 123456 with the code from console)
curl -X POST http://localhost:8080/api/auth/verify-code \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","code":"123456"}'
# Expected: {"token":"eyJ...","user":{"email":"test@example.com","name":"test"}}
```

- [ ] **Step 5: Final commit**

```bash
git add src/
git commit -m "feat(api): agent config API + global exception handler + smoke test passing"
```

---

## Self-Review

**Spec coverage check:**

| Spec Section | Covered by Task |
|---|---|
| User entity + auth (§5) | Task 3 |
| Project CRUD (§5) | Task 4 |
| Pipeline domain entities (§5) | Task 5 |
| State machine all transitions (§4.3) | Task 6 |
| Human-in-the-loop endpoints (§7.3) | Task 7 |
| Claude API streaming (§8.1) | Task 8 |
| Requirements + Planning executors (§3.2) | Task 9 |
| Code file real-time push (§8.2) | Task 10 |
| Testing sandbox Docker (§8.3) | Task 11 |
| Auto-fix loop (§4.3) | Task 11 |
| Agent config API (§3.2) | Task 12 |
| Context passing between stages (§8.4) | Task 8 (ContextBuilder) |
| WebSocket STOMP config | Task 6 (EventPublisher + WebSocketConfig) |
| Docker Compose | Task 1 |
| Flyway migrations | Task 2 |

**All spec requirements covered. No TBDs or placeholders.**

**Type consistency:** All types defined in Task 5 entities are used consistently across Tasks 6-12. `StageRun.StageStatus` enum values match TRANSITIONS map in PipelineEngine.

---

## API Summary

```
POST   /api/auth/send-code
POST   /api/auth/verify-code
GET    /api/auth/me

GET    /api/projects
POST   /api/projects
GET    /api/projects/{id}
PUT    /api/projects/{id}
DELETE /api/projects/{id}

POST   /api/projects/{projectId}/pipelines
GET    /api/projects/{projectId}/pipelines
GET    /api/projects/{projectId}/pipelines/{runId}
GET    /api/projects/{projectId}/pipelines/{runId}/stages
GET    /api/projects/{projectId}/pipelines/{runId}/stages/{stageId}/messages
GET    /api/projects/{projectId}/pipelines/{runId}/stages/{stageId}/artifact
GET    /api/projects/{projectId}/pipelines/{runId}/stages/{stageId}/files

POST   /api/stages/{stageId}/answer
POST   /api/stages/{stageId}/choose
POST   /api/stages/{stageId}/approve
POST   /api/stages/{stageId}/revise
PATCH  /api/stages/files/{fileId}

GET    /api/agent-configs
PUT    /api/agent-configs/{id}

WS     /ws  (STOMP)
  SUB  /topic/pipeline/{runId}          → PipelineUpdateEvent
  SUB  /topic/pipeline/{runId}/stage    → StageUpdateEvent
  SUB  /topic/stage/{stageId}/stream   → StreamChunkEvent
  SUB  /topic/stage/{stageId}/file     → CodeFileEvent
```
