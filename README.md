# DevTrack — Issue Tracking REST API

A production-grade REST API for issue/bug tracking across projects. Built with Spring Boot, Spring Security (JWT), MySQL, and Docker.

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?style=flat-square&logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8-blue?style=flat-square&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?style=flat-square&logo=githubactions)

---

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Postman    │────▶│  Controller  │────▶│   Service    │────▶│  Repository  │
│   (Client)   │     │    Layer     │     │    Layer     │     │    Layer     │
└──────────────┘     └──────────────┘     └──────────────┘     └──────┬───────┘
                           │                                          │
                     ┌─────┴─────┐                             ┌──────┴───────┐
                     │ JWT Filter│                             │   MySQL 8    │
                     │ (Security)│                             │  (Docker)    │
                     └───────────┘                             └──────────────┘
```

---

## API Endpoints

### Authentication
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/register` | Register a new user | No |
| POST | `/api/auth/login` | Login and get JWT token | No |

### Projects
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/projects` | Create project | MANAGER |
| GET | `/api/projects` | List all projects (paginated) | Yes |
| GET | `/api/projects/{id}` | Get project by ID | Yes |
| PUT | `/api/projects/{id}` | Update project | MANAGER/Owner |
| DELETE | `/api/projects/{id}` | Delete project | MANAGER |

### Issues
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/projects/{projectId}/issues` | Create issue | Yes |
| GET | `/api/projects/{projectId}/issues` | List issues (filtered + paginated) | Yes |
| GET | `/api/projects/{projectId}/issues/{id}` | Get issue by ID | Yes |
| PUT | `/api/projects/{projectId}/issues/{id}` | Update issue | Yes |
| PATCH | `/api/projects/{projectId}/issues/{id}/status` | Transition issue status | Yes |
| DELETE | `/api/projects/{projectId}/issues/{id}` | Delete issue | MANAGER |

**Issue Filters**: `?status=OPEN&priority=HIGH&assigneeId=2&page=0&size=10`

### Comments
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/issues/{issueId}/comments` | Add comment | Yes |
| GET | `/api/issues/{issueId}/comments` | List comments (paginated) | Yes |
| DELETE | `/api/issues/{issueId}/comments/{id}` | Delete own comment | Yes |

---

## Run Locally (Maven + Docker MySQL)

### Prerequisites
- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start MySQL
```bash
docker-compose up mysql -d
```

### 2. Run the Application
```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`

---

## Run with Docker Compose (Full Stack)

```bash
# Build the JAR first
mvn clean package -DskipTests

# Start everything
docker-compose up --build
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/devtrack_db` | MySQL connection URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | MySQL username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | MySQL password |
| `JWT_SECRET` | (configured in properties) | JWT signing secret |
| `JWT_EXPIRATION` | `86400000` | JWT expiration in ms (24h) |

---

## Issue Status Workflow

```
OPEN → IN_PROGRESS → IN_REVIEW → RESOLVED → CLOSED
```

---

## RBAC Matrix

| Action | MANAGER | DEVELOPER |
|--------|---------|-----------|
| Create Project | ✅ | ❌ |
| Delete Project | ✅ | ❌ |
| Create Issue | ✅ | ✅ |
| Assign Issue | ✅ | ❌ |
| Transition Status | ✅ | ✅ (own issues) |
| Delete Issue | ✅ | ❌ |
| Add Comment | ✅ | ✅ |
| Delete Own Comment | ✅ | ✅ |

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (jjwt 0.11.5) |
| ORM | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Build | Maven |
| Utilities | Lombok, ModelMapper |
| Validation | Spring Validation |
| Containerization | Docker + Docker Compose |
| CI | GitHub Actions |
