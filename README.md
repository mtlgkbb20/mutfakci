# Kitchen AI

Kitchen AI is an AI-assisted cooking companion. The project uses a Java 21/Spring Boot API and a React/TypeScript/Vite web client.

## Run locally

1. Copy `.env.example` to `.env` and set a strong `JWT_SECRET`.
2. Start PostgreSQL: `docker compose up -d postgres`.
3. Start the API: `./mvnw spring-boot:run` (or `mvn spring-boot:run`).
4. Start the web client: `cd frontend && npm install && npm run dev`.
5. Open `http://localhost:5173`.

The default AI mode is an isolated development recipe provider. To enable Runware, set a
Runware API key and activate the provider profile:

```bash
RUNWARE_API_KEY=your-key \
SPRING_PROFILES_ACTIVE=runware \
mvn spring-boot:run
```

`RUNWARE_MODEL` can override the default model (`deepseek:v4.1@flash`). The backend sends
decomposition and component-generation requests through Runware's OpenAI-compatible API,
validates every typed response, and assembles the public recipe response itself. Provider
credentials are never committed.

## Native mobile app

The Expo/React Native client is in [`mobile/`](mobile/). Set `EXPO_PUBLIC_API_URL` to the Mac's LAN IP, then run `npm install` and `npx expo start`. The existing `frontend/` remains the browser client.

## Verification

- Backend: `mvn test`
- Frontend: `cd frontend && npm run build`

See [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) for the verified implementation boundary and remaining phases.
