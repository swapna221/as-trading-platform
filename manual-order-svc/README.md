
# Intraday Trading Platform – Microservices Architecture

## Overview
A scalable microservice-based intraday trading platform built with Java 17 and Spring Boot, featuring Kafka integration, service registry, Redis caching, and centralized authentication.

---

## ✅ Services Implemented

### 1. Auth Service (`auth-service`)
Handles user registration, login (JWT), and Dhan credentials storage.

**Features:**
- JWT authentication with `userId` in claims.
- Endpoints:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/user/dhan-credentials`
  - `GET /api/user/dhan-credentials`
- One-to-one mapping of `User` ↔ `DhanCredential`.

### 2. Manual Order Service (`manual-order-service`)
Builds and places intraday orders using stored credentials.

**Features:**
- Extracts `userId` from JWT.
- Fetches or caches Dhan credentials from `auth-service` or Redis.
- Endpoint:
  - `POST /api/manual-order/buildProcess`

### 3. API Gateway (`api-gateway`)
Spring Cloud Gateway-based centralized API routing and JWT validation.

**Features:**
- Custom `JwtAuthFilter`
- Routes:
  - `/api/auth/**` → `auth-service`
  - `/api/manual-order/**` → `manual-order-service`
- Sets `X-User-Id` header

### 4. Eureka Server (`eureka-server`)
Manages service discovery and registration.

**Features:**
- Annotated with `@EnableEurekaServer`
- Other services use `@EnableDiscoveryClient`

### 5. Redis Cache Integration
Caches Dhan credentials for faster retrieval.

**Features:**
- `@Cacheable("dhanCreds")` based on `userId`
- Fallback: If not in cache → fetch from auth-service

---

## 🔐 Authentication Flow
1. Login via `auth-service` → receive JWT token
2. Send token to `api-gateway`
3. `JwtAuthFilter` verifies JWT → extracts `userId`
4. Header `X-User-Id` is added and forwarded

---

## 🔌 Service Communication

| From                  | To                  | Method   | Purpose                            |
|-----------------------|---------------------|----------|------------------------------------|
| manual-order-service  | auth-service        | REST     | Fetch Dhan credentials             |
| manual-order-service  | Redis               | Local    | Cache credentials                  |
| api-gateway           | All services        | Gateway  | Route + authentication             |
| all services          | eureka-server       | Registry | Service discovery                  |

---

## 🛠️ Dependencies Summary

| Module                | Key Dependencies                                       |
|-----------------------|--------------------------------------------------------|
| Common                | Spring Boot 3, Lombok, Java 17                         |
| auth-service          | Spring Security, JWT, Spring Data JPA                 |
| manual-order-service  | Spring Web, Redis, Spring Cache                       |
| api-gateway           | Spring Cloud Gateway (Reactive), JWT                  |
| eureka-server         | Spring Cloud Netflix Eureka                           |

---

## ✅ Next Suggestions
- Add more services: PnL, Decision Engine, Kafka Producer/Consumer.
- Add Swagger/OpenAPI UI
- Add distributed tracing (Zipkin/ELK)
- Add health-check endpoints and monitoring

---
