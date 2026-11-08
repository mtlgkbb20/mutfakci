# Database schema

Flyway migration `V1__foundation.sql` creates users, progress, recipes, sessions, observations, feedback, actions and XP transaction tables with user/session indexes and ownership foreign keys. Flexible food state and recipe stages are stored as JSON text while API records define the stable contract.
