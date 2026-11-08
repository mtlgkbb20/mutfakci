# Security

Passwords use BCrypt. Protected routes require short-lived JWT access tokens, CORS is restricted to the configured local frontend origin, uploads are limited to JPEG/PNG/WebP and 10 MB, filenames are not trusted, and resource lookups filter by authenticated user UUID. Refresh-token rotation and production object storage remain delivery-phase work.
