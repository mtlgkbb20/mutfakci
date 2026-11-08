# API specification

Implemented endpoints are under `/api/v1`: `POST /auth/register`, `POST /auth/login`, `GET /auth/me`, `GET /dashboard`, `POST/GET /recipes`, `GET /recipes/{id}`, `POST /cooking-sessions`, `GET /cooking-sessions`, `GET /cooking-sessions/{id}`, completion/abandonment endpoints, recovery observation/feedback endpoints, and `GET /gamification/progress`. Protected endpoints require `Authorization: Bearer <JWT>`.
