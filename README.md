# Road ResQ Auth Service

Independent Java 17 / Spring Boot 3.5.16 Maven project. Owns MongoDB, account CRUD, authentication, customer/provider onboarding, admin invitations, super-admin seed and profile images. Default port 8081.

Run `./mvnw verify` with Docker available for Testcontainers, or set `TEST_MONGODB_URI` to a dedicated Mongo replica-set test database. Run `./mvnw spring-boot:run` after exporting configuration.

Required environment: `JWT_SECRET` (base64, at least 32 random bytes), `INTERNAL_SERVICE_KEY` (32+ random characters), `MONGODB_URI` (replica set). Configure SMTP, `MAIL_FROM`, `FRONTEND_BASE_URL`, `PUBLIC_BASE_URL`, `UPLOAD_DIR`, and `GOOGLE_CLIENT_IDS` as needed. Seeding requires `SEED_SUPER_ADMIN=true`, `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD` (12+ characters).

API prefix: `/api/v1/us-s`. Every API call, including public routes, requires `X-Internal-Key`; protected calls also require a valid Bearer access token and live session. Only the gateway should call these routes in deployment. `GET /health` is a minimal database readiness check without a key.

The root README contains local Compose setup and the shared API contract. `application.yml` lists all defaults; there are no committed credentials.
