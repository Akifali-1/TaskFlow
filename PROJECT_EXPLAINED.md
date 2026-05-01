# DevTrack — Project Explained (In-Depth)

## Table of Contents
1. [What Does This Project Do?](#1-what-does-this-project-do)
2. [How Is It Structured?](#2-how-is-it-structured)
3. [The Complete Request Lifecycle](#3-the-complete-request-lifecycle)
4. [Security — How JWT Authentication Works](#4-security--how-jwt-authentication-works)
5. [Code Walkthrough — Key Files Explained](#5-code-walkthrough--key-files-explained)
6. [RBAC — Who Can Do What?](#6-rbac--who-can-do-what)
7. [Database — Entities & Relationships](#7-database--entities--relationships)
8. [Issue Status Workflow](#8-issue-status-workflow)
9. [API Flow Examples (End-to-End)](#9-api-flow-examples-end-to-end)
10. [Design Patterns Used](#10-design-patterns-used)
11. [Key Java Concepts in Action](#11-key-java-concepts-in-action)

---

## 1. What Does This Project Do?

DevTrack is a **REST API** for tracking issues/bugs across software projects — think of it as a simplified backend for tools like Jira or GitHub Issues.

**What it allows:**
- **Managers** can create projects, assign issues to developers, and manage everything
- **Developers** can create issues, update their assigned issues, and add comments
- Every request is **authenticated** — users must log in and get a JWT token first
- Issues follow a **strict workflow**: OPEN → IN_PROGRESS → IN_REVIEW → RESOLVED → CLOSED

**What it does NOT have:**
- No frontend/UI — it's a pure backend API tested via Postman
- No email notifications or real-time features — it's focused on core CRUD + security

---

## 2. How Is It Structured?

The project follows a **layered architecture** — each layer has a single responsibility:

```
   Postman (Client)
        │
        ▼
┌─────────────────┐
│   Controller    │  ← Receives HTTP requests, validates input, returns responses
│   (REST Layer)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│    Service      │  ← Contains ALL business logic, RBAC checks, validation
│  (Logic Layer)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Repository    │  ← Talks to the database (MySQL) via JPA/Hibernate
│   (Data Layer)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│     MySQL       │  ← Stores all data (users, projects, issues, comments)
│   (Database)    │
└─────────────────┘
```

**Why this matters:** Each layer only talks to the layer directly below it. Controllers never touch the database directly. Services never send HTTP responses directly. This makes the code testable, maintainable, and easy to modify.

### Package Structure

```
com.devtrack/
├── config/         → App configuration (Security rules, beans)
├── controller/     → REST endpoints (what URLs the API exposes)
├── service/        → Business logic (the "brains" of the app)
├── repository/     → Database queries (Spring Data JPA handles most of this)
├── entity/         → Database table models (mapped to MySQL tables)
├── dto/            → Data Transfer Objects (what the API sends/receives as JSON)
│   ├── request/    → What the client SENDS to us
│   └── response/   → What we SEND BACK to the client
├── enums/          → Fixed sets of values (Role, IssueStatus, IssuePriority)
├── exception/      → Custom errors and error handling
└── security/       → JWT token generation, validation, and filter
```

### Why DTOs? Why not just send Entities directly?

**Entities** are database models — they contain relationships, passwords, and internal IDs we don't want to expose. **DTOs** control exactly what data goes in and out:

```
Client sends:   RegisterRequest { name, email, password, role }
                        ↓
Service creates: User entity (hashes password, saves to DB)
                        ↓
Client receives: AuthResponse { token, name, email, role }
                 (no password, no internal ID exposed)
```

---

## 3. The Complete Request Lifecycle

When Postman sends `POST /api/projects` with a JWT token, here's exactly what happens:

```
1. HTTP Request arrives at Spring Boot's embedded Tomcat server
        │
2. ┌────▼─────────────────────────────────────┐
   │  JwtFilter (runs BEFORE any controller)  │
   │  - Extracts "Bearer xxxxx" from header   │
   │  - Calls JwtUtil.extractUsername(token)   │
   │  - Loads user from DB via UserDetails     │
   │  - Validates token (not expired, matches) │
   │  - Sets SecurityContext = authenticated   │
   └────┬─────────────────────────────────────┘
        │
3. ┌────▼─────────────────────────────────────┐
   │  SecurityConfig checks authorization     │
   │  - Is this URL protected? YES            │
   │  - Does user have ROLE_MANAGER? YES      │
   │  - ✅ Allow through                      │
   └────┬─────────────────────────────────────┘
        │
4. ┌────▼─────────────────────────────────────┐
   │  ProjectController.createProject()       │
   │  - @Valid validates the request body     │
   │  - Gets user email from Authentication  │
   │  - Calls projectService.createProject()  │
   └────┬─────────────────────────────────────┘
        │
5. ┌────▼─────────────────────────────────────┐
   │  ProjectService.createProject()          │
   │  - Looks up user by email                │
   │  - Checks role == MANAGER                │
   │  - Builds Project entity                 │
   │  - Saves via projectRepository.save()    │
   │  - Maps entity → ProjectResponse DTO    │
   └────┬─────────────────────────────────────┘
        │
6. ┌────▼─────────────────────────────────────┐
   │  ProjectRepository.save()                │
   │  - Hibernate generates SQL:              │
   │    INSERT INTO projects (name, desc,     │
   │    owner_id, created_at) VALUES (...)    │
   │  - MySQL executes it                     │
   └────┬─────────────────────────────────────┘
        │
7. Response travels back up the chain:
   ProjectResponse → ResponseEntity<>(201 CREATED) → JSON → Postman
```

---

## 4. Security — How JWT Authentication Works

### What is JWT?

JWT (JSON Web Token) is a **self-contained token** that proves who you are. Instead of the server storing sessions, the client sends this token with every request.

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huQGVtYWlsLmNvbSIsInJvbGUiOiJST0xFX01BTkFHRVIi
└──── Header ────┘ └──────────── Payload (your email, role, expiry) ──────────────┘
                   └──── Signature (proves it hasn't been tampered with) ────┘
```

### The Authentication Flow

```
Step 1: REGISTER
    POST /api/auth/register
    { "name": "John", "email": "john@email.com", "password": "pass123", "role": "MANAGER" }
    
    → Password is hashed with BCrypt (never stored as plain text)
    → User saved to database
    → JWT token generated and returned

Step 2: LOGIN (for subsequent sessions)
    POST /api/auth/login
    { "email": "john@email.com", "password": "pass123" }
    
    → AuthenticationManager verifies credentials against DB
    → JWT token generated and returned

Step 3: USE THE TOKEN (for every other request)
    GET /api/projects
    Header: Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
    
    → JwtFilter extracts and validates token
    → Request proceeds if valid
    → 401 Unauthorized if invalid/expired
```

### Why Stateless?

Traditional apps use **sessions** (server remembers who you are via cookies). DevTrack uses **stateless JWT** — the server doesn't remember anything. Every request carries its own proof of identity. This is better for APIs because:
- No server memory wasted on sessions
- Easy to scale horizontally (any server can validate the token)
- Perfect for mobile/frontend apps

---

## 5. Code Walkthrough — Key Files Explained

### JwtUtil.java — The Token Factory
```java
// WHAT IT DOES: Creates and validates JWT tokens

public String generateToken(UserDetails userDetails) {
    // 1. Put the user's role into the token's payload
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
    
    // 2. Build the token:
    //    - Subject = user's email
    //    - IssuedAt = now
    //    - Expiration = now + 24 hours
    //    - Signed with our secret key (HMAC-SHA256)
    return Jwts.builder()
            .setClaims(claims)
            .setSubject(userDetails.getUsername())
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
}

public Boolean isTokenValid(String token, UserDetails userDetails) {
    // Token is valid if:
    // 1. The email in the token matches the user we loaded from DB
    // 2. The token hasn't expired
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
}
```

### JwtFilter.java — The Security Gate
```java
// WHAT IT DOES: Intercepts EVERY HTTP request before it reaches any controller.
// It checks: "Does this request have a valid JWT token?"

@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    // 1. Get the Authorization header
    final String authHeader = request.getHeader("Authorization");

    // 2. No token? Skip this filter (Spring Security will handle it — 
    //    public endpoints pass, protected ones get 401)
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;
    }

    // 3. Extract token and get the email from it
    final String jwt = authHeader.substring(7);  // Remove "Bearer " prefix
    final String username = jwtUtil.extractUsername(jwt);

    // 4. If we got an email AND no one is authenticated yet:
    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
        // 5. Load the full user from database
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // 6. Validate the token
        if (jwtUtil.isTokenValid(jwt, userDetails)) {
            // 7. ✅ Mark this request as authenticated
            //    Spring Security will now let it through
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
    }

    // 8. Continue to the next filter / controller
    filterChain.doFilter(request, response);
}
```

### SecurityConfig.java — The Rules Engine
```java
// WHAT IT DOES: Defines WHO can access WHAT.

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)          // Disable CSRF (not needed for APIs)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll() // Anyone can register/login
            .requestMatchers(HttpMethod.DELETE, "/api/projects/**").hasRole("MANAGER")  // Only managers delete
            .requestMatchers(HttpMethod.POST, "/api/projects").hasRole("MANAGER")       // Only managers create
            .anyRequest().authenticated()                // Everything else needs a valid token
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)  // No sessions — pure JWT
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        //  ↑ Our JWT filter runs BEFORE Spring's default auth filter
    return http.build();
}
```

### IssueService.java — Business Logic Highlights

**Status Transition Validation:**
```java
// WHAT IT DOES: Enforces that issues can only move forward in the workflow.
// You can't go from OPEN directly to CLOSED, or move backwards.

private void validateStatusTransition(IssueStatus current, IssueStatus newStatus) {
    boolean valid = switch (current) {
        case OPEN        -> newStatus == IssueStatus.IN_PROGRESS;  // Can only start work
        case IN_PROGRESS -> newStatus == IssueStatus.IN_REVIEW;    // Can only submit for review
        case IN_REVIEW   -> newStatus == IssueStatus.RESOLVED;     // Can only mark resolved
        case RESOLVED    -> newStatus == IssueStatus.CLOSED;       // Can only close
        case CLOSED      -> false;                                  // Can't change once closed
    };

    if (!valid) {
        throw new IllegalArgumentException(
            String.format("Invalid status transition from %s to %s", current, newStatus));
    }
}
```

**Dynamic Filtering with JPA Specifications:**
```java
// WHAT IT DOES: Builds a database query dynamically based on which filters
// the client provides. If they only filter by status, only status is checked.
// If they filter by status + priority + assignee, all three are checked.

private Specification<Issue> buildSpecification(Long projectId, IssueStatus status,
                                                 IssuePriority priority, Long assigneeId) {
    return (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        // Always filter by project
        predicates.add(cb.equal(root.get("project").get("id"), projectId));

        // Only add these filters if the client provided them
        if (status != null)     predicates.add(cb.equal(root.get("status"), status));
        if (priority != null)   predicates.add(cb.equal(root.get("priority"), priority));
        if (assigneeId != null) predicates.add(cb.equal(root.get("assignee").get("id"), assigneeId));

        // Combine all predicates with AND
        return cb.and(predicates.toArray(new Predicate[0]));
    };
}

// This generates SQL like:
// SELECT * FROM issues 
// WHERE project_id = 1 AND status = 'OPEN' AND priority = 'HIGH'
// LIMIT 10 OFFSET 0
```

### GlobalExceptionHandler.java — Centralized Error Handling
```java
// WHAT IT DOES: Catches exceptions thrown ANYWHERE in the app and converts them 
// into clean, consistent JSON error responses instead of ugly stack traces.

@RestControllerAdvice  // Applies to ALL controllers automatically
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        // When any service throws ResourceNotFoundException, the client gets:
        // { "timestamp": "2026-04-23 22:30:00", "status": 404, 
        //   "error": "Not Found", "message": "Project with id 99 not found" }
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(ex.getMessage())
                .build();
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(...) {
        // When @Valid fails (e.g., blank name), collects ALL field errors:
        // { "status": 400, "message": "name: Name is required, email: Invalid email format" }
    }
}
```

---

## 6. RBAC — Who Can Do What?

RBAC = **Role-Based Access Control**. There are two roles:

| Action | MANAGER | DEVELOPER | How It's Enforced |
|--------|---------|-----------|-------------------|
| Register/Login | ✅ | ✅ | `SecurityConfig` — `/api/auth/**` is public |
| Create Project | ✅ | ❌ | `SecurityConfig` — `POST /api/projects` requires `ROLE_MANAGER` |
| Delete Project | ✅ | ❌ | `SecurityConfig` — `DELETE /api/projects/**` requires `ROLE_MANAGER` |
| View Projects | ✅ | ✅ | `SecurityConfig` — `anyRequest().authenticated()` |
| Create Issue | ✅ | ✅ | `IssueService` — no role check, just authenticated |
| Assign Issue | ✅ | ❌ | `IssueService` — checks `user.getRole() != Role.MANAGER` |
| Transition Status | ✅ | ✅ (own only) | `IssueService` — DEVELOPER must be the assignee |
| Delete Issue | ✅ | ❌ | `IssueService` — checks `user.getRole() != Role.MANAGER` |
| Add Comment | ✅ | ✅ | No role check, just authenticated |
| Delete Comment | ✅ (own) | ✅ (own) | `CommentService` — checks `comment.getAuthor().getId().equals(user.getId())` |

**Two enforcement levels:**
1. **SecurityConfig** (URL-level) — blocks requests before they reach the controller
2. **Service layer** (logic-level) — finer checks like "is this YOUR issue?"

---

## 7. Database — Entities & Relationships

```
┌──────────┐       ┌──────────────┐       ┌──────────┐       ┌──────────────┐
│  users   │──1:N──│   projects   │──1:N──│  issues  │──1:N──│   comments   │
│          │       │              │       │          │       │              │
│ id       │       │ id           │       │ id       │       │ id           │
│ name     │       │ name         │       │ title    │       │ content      │
│ email    │       │ description  │       │ desc     │       │ created_at   │
│ password │       │ created_at   │       │ status   │       │ issue_id(FK) │
│ role     │       │ owner_id(FK) │       │ priority │       │ author_id(FK)│
└──────────┘       └──────────────┘       │ proj_id  │       └──────────────┘
      │                                    │ assignee │
      │                                    │ created  │
      └──────────────1:N──────────────────│ updated  │
         (user can be assigned             └──────────┘
          to many issues)
```

### How JPA Maps This to Java

```java
// In Issue.java:
@ManyToOne(fetch = FetchType.LAZY)       // Many issues belong to ONE project
@JoinColumn(name = "project_id")          // Creates a "project_id" column in the issues table
private Project project;

@OneToMany(mappedBy = "issue",            // One issue has MANY comments
           cascade = CascadeType.ALL,     // Delete issue → delete all its comments
           orphanRemoval = true)          // Remove comment from list → delete from DB
private List<Comment> comments;
```

**`FetchType.LAZY`** = Don't load related data until you actually access it. Loading an issue won't automatically query all its comments — only when you call `issue.getComments()`.

**`CascadeType.ALL`** = Any operation on the parent propagates to children. Delete a project → all its issues (and their comments) get deleted too.

---

## 8. Issue Status Workflow

```
  ┌──────┐     ┌─────────────┐      ┌───────────┐     ┌──────────┐     ┌────────┐
  │ OPEN │────▶│ IN_PROGRESS│────▶│ IN_REVIEW │────▶│ RESOLVED │────▶│ CLOSED │
  └──────┘     └─────────────┘      └───────────┘     └──────────┘     └────────┘
  
  ✅ Valid: OPEN → IN_PROGRESS
  ❌ Invalid: OPEN → RESOLVED (skipping steps)
  ❌ Invalid: CLOSED → OPEN (going backwards)
  ❌ Invalid: CLOSED → anything (terminal state)
```

**How it's called:**
```
PATCH /api/projects/1/issues/5/status
Body: { "status": "IN_PROGRESS" }
```

The service checks:
1. Does this issue exist in this project?
2. Is the current status → new status transition valid?
3. Is the user allowed? (MANAGER can transition any, DEVELOPER only their own)

---

## 9. API Flow Examples (End-to-End)

### Example 1: Manager Creates a Project and Assigns an Issue

```
Step 1: Register a Manager
    POST /api/auth/register
    Body: { "name": "Alice", "email": "alice@dev.com", "password": "pass123", "role": "MANAGER" }
    Response: { "token": "eyJ...", "name": "Alice", "role": "MANAGER" }

Step 2: Register a Developer
    POST /api/auth/register
    Body: { "name": "Bob", "email": "bob@dev.com", "password": "pass123", "role": "DEVELOPER" }
    Response: { "token": "eyJ...", "name": "Bob", "role": "DEVELOPER" }

Step 3: Manager Creates a Project (using Alice's token)
    POST /api/projects
    Headers: Authorization: Bearer <alice_token>
    Body: { "name": "DevTrack API", "description": "Issue tracking system" }
    Response: { "id": 1, "name": "DevTrack API", "ownerName": "Alice" }

Step 4: Manager Creates an Issue and Assigns to Bob (user ID 2)
    POST /api/projects/1/issues
    Headers: Authorization: Bearer <alice_token>
    Body: { "title": "Fix login bug", "description": "JWT expiry issue", 
            "priority": "HIGH", "assigneeId": 2 }
    Response: { "id": 1, "status": "OPEN", "assigneeName": "Bob" }

Step 5: Bob Transitions the Issue (using Bob's token)
    PATCH /api/projects/1/issues/1/status
    Headers: Authorization: Bearer <bob_token>
    Body: { "status": "IN_PROGRESS" }
    Response: { "id": 1, "status": "IN_PROGRESS" }

Step 6: Bob Adds a Comment
    POST /api/issues/1/comments
    Headers: Authorization: Bearer <bob_token>
    Body: { "content": "Found the root cause, fixing now." }
    Response: { "id": 1, "content": "Found the root cause...", "authorName": "Bob" }
```

### Example 2: Developer Tries to Create a Project (Gets 403)

```
    POST /api/projects
    Headers: Authorization: Bearer <bob_token>   ← Bob is a DEVELOPER
    Body: { "name": "My Project" }
    
    Response (403 Forbidden):
    Spring Security blocks this BEFORE it even reaches the controller,
    because SecurityConfig says: POST /api/projects → hasRole("MANAGER")
```

### Example 3: Invalid Status Transition (Gets 400)

```
    PATCH /api/projects/1/issues/1/status
    Body: { "status": "CLOSED" }     ← Issue is currently OPEN
    
    Response (400 Bad Request):
    { "status": 400, "error": "Bad Request",
      "message": "Invalid status transition from OPEN to CLOSED" }
```

---

## 10. Design Patterns Used

| Pattern | Where | What It Does |
|---------|-------|-------------|
| **Repository Pattern** | `*Repository.java` | Abstracts database access — you call `findById()`, JPA writes the SQL |
| **DTO Pattern** | `dto/request/`, `dto/response/` | Separates API data shape from database shape |
| **Builder Pattern** | `@Builder` on entities/DTOs | Clean object construction: `User.builder().name("John").build()` |
| **Filter Chain Pattern** | `JwtFilter.java` | Intercepts requests in a chain before reaching controllers |
| **Template Method** | `OncePerRequestFilter` | JwtFilter overrides `doFilterInternal()` — the framework handles the rest |
| **Layered Architecture** | Controller → Service → Repository | Each layer has one job, talks only to adjacent layers |
| **Factory Method** | `SecurityFilterChain` bean | Spring creates the security chain from our configuration |
| **Strategy Pattern** | `JpaSpecificationExecutor` | Different filtering strategies (by status, priority, etc.) composed dynamically |

---

## 11. Key Java Concepts in Action

### Generics
```java
// JpaRepository<Issue, Long> — works with Issue entities, using Long as the ID type
// Page<IssueResponse> — a paginated list of IssueResponse objects
// ResponseEntity<T> — HTTP response wrapper for any type
public ResponseEntity<Page<ProjectResponse>> getAllProjects(Pageable pageable) { ... }
```

### Collections & Streams
```java
// Collecting validation errors into a comma-separated string
String message = ex.getBindingResult().getFieldErrors().stream()
    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
    .collect(Collectors.joining(", "));
// Result: "name: Name is required, email: Invalid email format"
```

### Enums with Switch Expressions (Java 17)
```java
// Pattern matching switch — exhaustive, no default needed
boolean valid = switch (currentStatus) {
    case OPEN        -> newStatus == IssueStatus.IN_PROGRESS;
    case IN_PROGRESS -> newStatus == IssueStatus.IN_REVIEW;
    case IN_REVIEW   -> newStatus == IssueStatus.RESOLVED;
    case RESOLVED    -> newStatus == IssueStatus.CLOSED;
    case CLOSED      -> false;
};
```

### Custom Exceptions
```java
// ResourceNotFoundException auto-maps to 404 via @ResponseStatus AND GlobalExceptionHandler
throw new ResourceNotFoundException("Project", id);
// → "Project with id 5 not found" → 404 response with structured ErrorResponse JSON
```

### Dependency Injection (Constructor-based via Lombok)
```java
@RequiredArgsConstructor  // Lombok generates constructor for all 'final' fields
public class IssueService {
    private final IssueRepository issueRepository;      // Injected by Spring
    private final ProjectRepository projectRepository;  // Injected by Spring
    private final UserRepository userRepository;        // Injected by Spring
    // No manual wiring — Spring finds the beans and injects them automatically
}
```

### JPA Annotations
```java
@Entity                          // This class maps to a database table
@Table(name = "issues")          // Table name in MySQL
@Id                              // Primary key
@GeneratedValue(IDENTITY)        // Auto-increment
@Column(nullable = false)        // NOT NULL constraint
@Enumerated(EnumType.STRING)     // Store enum as "OPEN", not 0
@CreationTimestamp               // Auto-set to current time on INSERT
@UpdateTimestamp                  // Auto-set to current time on UPDATE
@ManyToOne(fetch = LAZY)         // Relationship: many issues → one project
@JoinColumn(name = "project_id") // FK column name
```

### Validation Annotations
```java
@NotBlank(message = "Title is required")    // Not null, not empty, not whitespace
@NotNull(message = "Priority is required")  // Not null (for enums/objects)
@Email(message = "Invalid email format")    // Must be valid email format
@Size(min = 6, message = "...")             // Minimum length
// These are checked automatically when @Valid is on the controller parameter
```

---

## Quick Reference: File → Purpose

| File | One-Line Purpose |
|------|-----------------|
| `DevtrackApplication.java` | Starts the Spring Boot app |
| `SecurityConfig.java` | Defines URL access rules + JWT filter placement |
| `JwtUtil.java` | Creates and validates JWT tokens |
| `JwtFilter.java` | Intercepts every request to check for valid JWT |
| `UserDetailsServiceImpl.java` | Loads user from DB for Spring Security |
| `AuthService.java` | Registers users (BCrypt) and logs them in (JWT) |
| `ProjectService.java` | Project CRUD with MANAGER-only create/delete |
| `IssueService.java` | Issue CRUD + dynamic filtering + status transitions |
| `CommentService.java` | Add/list/delete-own comments |
| `GlobalExceptionHandler.java` | Catches all exceptions → clean JSON errors |
| `AppConfig.java` | Registers ModelMapper bean |
| `application.properties` | MySQL connection, JWT secret, server config |
