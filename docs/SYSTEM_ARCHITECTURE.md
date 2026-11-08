# System architecture

The API is a Spring Boot 3.3 application under `com.kitchenai`, using Spring Security/JWT, Spring Data JPA, Flyway and PostgreSQL. The React/Vite client calls `/api/v1` with a bearer token. AI entry points are interfaces in `com.kitchenai.ai`; the default local provider is explicitly a recipe-development stub. Recovery storage is local in development and keyed by user UUID.
