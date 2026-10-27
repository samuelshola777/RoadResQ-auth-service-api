package com.roadresq.auth;

import static org.assertj.core.api.Assertions.*;

import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

import java.util.*;
import java.util.concurrent.*;

@Import(TestMailConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "app.internal-key=test-internal-key-with-at-least-32-characters",
            "app.jwt-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "app.seed.enabled=true",
            "app.seed.email=super@example.com",
            "app.seed.password=Test-super-password-123!",
            "app.rate-limit=1000",
            "spring.mail.host=localhost",
            "app.upload-dir=${java.io.tmpdir}/road-resq-test-images"
        })
class AccountIntegrationTest {
    static MongoDBContainer mongo;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        String uri = System.getenv("TEST_MONGODB_URI");
        if (uri == null) {
            mongo = new MongoDBContainer("mongo:7.0.26");
            mongo.start();
            uri = mongo.getReplicaSetUrl();
        }
        String finalUri = uri;
        r.add("spring.data.mongodb.uri", () -> finalUri);
    }

    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate db;
    final String password = "Customer-password-123!";

    ResponseEntity<Map> call(HttpMethod method, String path, Object body, String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Internal-Key", "test-internal-key-with-at-least-32-characters");
        if (bearer != null) h.setBearerAuth(bearer);
        return http.exchange("/api/v1/us-s" + path, method, new HttpEntity<>(body, h), Map.class);
    }

    Map data(ResponseEntity<Map> r) {
        return (Map) r.getBody().get("data");
    }

    String action(String email, String type) {
        Document mail =
                db.getCollection("test_mail")
                        .find(new Document("email", email).append("type", type))
                        .sort(new Document("_id", -1))
                        .first();
        assertThat(mail).isNotNull();
        return mail.getString("token");
    }

    String register(String role) {
        String email = UUID.randomUUID() + "@example.com";
        var r =
                call(
                        HttpMethod.POST,
                        "/auth/public/register/" + role,
                        Map.of(
                                "firstName",
                                "Ada",
                                "lastName",
                                "Okoro",
                                "email",
                                email,
                                "password",
                                password),
                        null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().toString()).doesNotContain(password, "token");
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/login",
                                        Map.of("email", email, "password", password),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/verify-email",
                                        Map.of("token", action(email, "VERIFY_EMAIL")),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return email;
    }

    Map login(String email, String pass) {
        var r =
                call(
                        HttpMethod.POST,
                        "/auth/public/login",
                        Map.of("email", email, "password", pass),
                        null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(r);
    }

    String access(Map login) {
        return (String) login.get("accessToken");
    }

    String superToken() {
        return access(login("super@example.com", "Test-super-password-123!"));
    }

    @Test
    void customerOnboardsAndCannotReadOtherCustomers() {
        String email = register("customer"), other = register("customer");
        String token = access(login(email, password));
        Map mine = data(call(HttpMethod.GET, "/account/me", null, token));
        assertThat(mine.get("userId")).isNotEqualTo(mine.get("profileId"));
        String otherId =
                (String)
                        data(call(
                                        HttpMethod.GET,
                                        "/account/me",
                                        null,
                                        access(login(other, password))))
                                .get("profileId");
        assertThat(call(HttpMethod.GET, "/customers/" + otherId, null, token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        call(
                                        HttpMethod.PUT,
                                        "/account/me",
                                        Map.of(
                                                "firstName",
                                                "Ada",
                                                "lastName",
                                                "Okoro",
                                                "phone",
                                                "+2348012345678",
                                                "vehicleMake",
                                                "Toyota",
                                                "vehicleModel",
                                                "Corolla",
                                                "vehiclePlate",
                                                "ABC123"),
                                        token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(data(call(HttpMethod.GET, "/account/me", null, token)).get("onboardingComplete"))
                .isEqualTo(true);
    }

    @Test
    void providerCannotMakeItselfVerifiedOrAdmin() {
        String token = access(login(register("provider"), password));
        var r =
                call(
                        HttpMethod.PUT,
                        "/account/me",
                        Map.of(
                                "firstName",
                                "Bola",
                                "lastName",
                                "Ade",
                                "phone",
                                "+2348012345678",
                                "businessName",
                                "Rescue",
                                "services",
                                List.of("TOWING"),
                                "coverageAreas",
                                List.of("Abuja"),
                                "equipment",
                                "Tow truck",
                                "eligibility",
                                "VERIFIED",
                                "role",
                                "ADMIN"),
                        token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        call(
                                        HttpMethod.PUT,
                                        "/account/me",
                                        Map.of(
                                                "firstName",
                                                "Bola",
                                                "lastName",
                                                "Ade",
                                                "phone",
                                                "+2348012345678",
                                                "businessName",
                                                "Rescue",
                                                "services",
                                                List.of("TOWING"),
                                                "coverageAreas",
                                                List.of("Abuja"),
                                                "equipment",
                                                "Tow truck"),
                                        token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Map me = data(call(HttpMethod.GET, "/account/me", null, token));
        assertThat(me.get("role")).isEqualTo("PROVIDER");
        assertThat(((Map) me.get("profile")).get("eligibility")).isEqualTo("PENDING");
        assertThat(call(HttpMethod.GET, "/admins", null, token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void refreshIsSingleUseAndLogoutRevokesAccess() throws Exception {
        Map session = login(register("customer"), password);
        String refresh = (String) session.get("refreshToken");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<ResponseEntity<Map>>> calls =
                    List.of(
                            () ->
                                    call(
                                            HttpMethod.POST,
                                            "/auth/public/refresh",
                                            Map.of("refreshToken", refresh),
                                            null),
                            () ->
                                    call(
                                            HttpMethod.POST,
                                            "/auth/public/refresh",
                                            Map.of("refreshToken", refresh),
                                            null));
            var results =
                    pool.invokeAll(calls).stream()
                            .map(
                                    f -> {
                                        try {
                                            return f.get();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .toList();
            assertThat(results.stream().map(r -> r.getStatusCode().value()).toList())
                    .containsExactlyInAnyOrder(200, 401);
            Map next =
                    data(
                            results.stream()
                                    .filter(r -> r.getStatusCode().value() == 200)
                                    .findFirst()
                                    .orElseThrow());
            assertThat(
                            call(HttpMethod.POST, "/auth/private/logout", null, access(next))
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(call(HttpMethod.GET, "/account/me", null, access(next)).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void passwordResetIsOneTimeAndRevokesAllSessions() {
        String email = register("customer"), token = access(login(email, password));
        var forgot =
                call(HttpMethod.POST, "/auth/public/forgot-password", Map.of("email", email), null);
        assertThat(forgot.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(forgot.getBody().toString()).doesNotContain("token");
        String reset = action(email, "PASSWORD_RESET"), newPass = "New-customer-password-123!";
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/reset-password",
                                        Map.of("token", reset, "password", newPass),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/reset-password",
                                        Map.of("token", reset, "password", newPass),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/account/me", null, token).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/login",
                                        Map.of("email", email, "password", password),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        login(email, newPass);
    }

    @Test
    void adminInvitationAndAccountCrudEnforcePrivilegesAndState() {
        String st = superToken(), email = UUID.randomUUID() + "@example.com";
        var invite =
                call(
                        HttpMethod.POST,
                        "/admins/invitations",
                        Map.of("firstName", "Admin", "lastName", "One", "email", email),
                        st);
        assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invitation = action(email, "ADMIN_INVITE");
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/accept-invite",
                                        Map.of("token", invitation, "password", password),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/accept-invite",
                                        Map.of("token", invitation, "password", password),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        String at = access(login(email, password));
        assertThat(call(HttpMethod.GET, "/admins", null, at).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String ce = register("customer"), ct = access(login(ce, password));
        String id = (String) data(call(HttpMethod.GET, "/account/me", null, ct)).get("profileId");
        assertThat(call(HttpMethod.GET, "/customers/" + id, null, at).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        "/customers/" + id + "/status",
                                        Map.of("active", false),
                                        at)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/account/me", null, ct).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.DELETE, "/customers/" + id, null, at).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/customers/" + id, null, at).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void internalKeyAndBearerAreRequired() {
        assertThat(
                        http.postForEntity(
                                        "/api/v1/us-s/auth/public/login",
                                        Map.of("email", "a@example.com", "password", password),
                                        Map.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/account/me", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        call(HttpMethod.POST, "/auth/public/register/admin", Map.of(), null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/google",
                                        Map.of("idToken", "invalid", "role", "CUSTOMER"),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void googleCreatesRolesAndRequiresAuthenticatedLinkingForExistingEmail() {
        String email = register("customer"), google = "fixture:" + UUID.randomUUID() + ":" + email;
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/google",
                                        Map.of("idToken", google, "role", "CUSTOMER"),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        String token = access(login(email, password));
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/private/google/link",
                                        Map.of("token", google),
                                        token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Map linked =
                data(
                        call(
                                HttpMethod.POST,
                                "/auth/public/google",
                                Map.of("idToken", google, "role", "PROVIDER"),
                                null));
        assertThat(((Map) linked.get("account")).get("role")).isEqualTo("CUSTOMER");
        String newEmail = UUID.randomUUID() + "@example.com",
                newGoogle = "fixture:" + UUID.randomUUID() + ":" + newEmail;
        var created =
                call(
                        HttpMethod.POST,
                        "/auth/public/google",
                        Map.of("idToken", newGoogle, "role", "PROVIDER"),
                        null);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) data(created).get("account")).get("role")).isEqualTo("PROVIDER");
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/google",
                                        Map.of(
                                                "idToken",
                                                "fixture:"
                                                        + UUID.randomUUID()
                                                        + ":"
                                                        + UUID.randomUUID()
                                                        + "@example.com",
                                                "role",
                                                "ADMIN"),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void passwordChangeAndLogoutAllRevokeExistingSessions() {
        String email = register("customer"),
                one = access(login(email, password)),
                two = access(login(email, password));
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/private/change-password",
                                        Map.of(
                                                "currentPassword",
                                                "wrong-password",
                                                "newPassword",
                                                "Another-password-123!"),
                                        one)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/private/change-password",
                                        Map.of(
                                                "currentPassword",
                                                password,
                                                "newPassword",
                                                "Another-password-123!"),
                                        one)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/account/me", null, two).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String next = access(login(email, "Another-password-123!"));
        assertThat(call(HttpMethod.POST, "/auth/private/logout-all", null, next).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, "/account/me", null, next).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminCanCreateUpdateListAndDeleteProvidersAndCustomers() {
        String st = superToken();
        for (String resource : List.of("customers", "providers")) {
            String email = UUID.randomUUID() + "@example.com";
            var created =
                    call(
                            HttpMethod.POST,
                            "/" + resource,
                            Map.of(
                                    "firstName",
                                    "Test",
                                    "lastName",
                                    "User",
                                    "email",
                                    email,
                                    "password",
                                    password),
                            st);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String id = (String) data(created).get("profileId");
            assertThat(
                            call(
                                            HttpMethod.PUT,
                                            "/" + resource + "/" + id,
                                            Map.of(
                                                    "firstName",
                                                    "Updated",
                                                    "lastName",
                                                    "User",
                                                    "phone",
                                                    "+2348012345678"),
                                            st)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(
                            data(call(HttpMethod.GET, "/" + resource + "/" + id, null, st))
                                    .get("firstName"))
                    .isEqualTo("Updated");
            assertThat(call(HttpMethod.GET, "/" + resource + "?size=51", null, st).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(call(HttpMethod.GET, "/" + resource, null, st).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(call(HttpMethod.DELETE, "/" + resource + "/" + id, null, st).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        String superId =
                (String) data(call(HttpMethod.GET, "/account/me", null, st)).get("profileId");
        assertThat(call(HttpMethod.DELETE, "/admins/" + superId, null, st).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        call(
                                        HttpMethod.PATCH,
                                        "/admins/" + superId + "/status",
                                        Map.of("active", false),
                                        st)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    ResponseEntity<Map> upload(byte[] bytes, String filename) {
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add(
                "file",
                new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                });
        HttpHeaders h = new HttpHeaders();
        h.set("X-Internal-Key", "test-internal-key-with-at-least-32-characters");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange(
                "/api/v1/us-s/utilities/public/profile-picture",
                HttpMethod.POST,
                new HttpEntity<>(body, h),
                Map.class);
    }

    @Test
    void anonymousUploadValidatesContentAndCanBeAssignedToProfile() throws Exception {
        var image =
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", output);
        var uploaded = upload(output.toByteArray(), "../../evil.php");
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) data(uploaded).get("id");
        assertThat(id).matches("[a-f0-9-]{36}");
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Key", "test-internal-key-with-at-least-32-characters");
        var read =
                http.exchange(
                        "/api/v1/us-s/utilities/public/profile-images/" + id,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        byte[].class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(
                        upload("<svg><script>alert(1)</script></svg>".getBytes(), "image.png")
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(new byte[6 * 1024 * 1024], "large.png").getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        String token = access(login(register("customer"), password));
        assertThat(
                        call(
                                        HttpMethod.PUT,
                                        "/account/me",
                                        Map.of(
                                                "firstName",
                                                "Ada",
                                                "lastName",
                                                "Okoro",
                                                "profilePictureId",
                                                id),
                                        token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        call(
                                        HttpMethod.PUT,
                                        "/account/me",
                                        Map.of(
                                                "firstName",
                                                "Ada",
                                                "lastName",
                                                "Okoro",
                                                "profilePictureId",
                                                "../../secrets"),
                                        token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void googleLinkAcceptsRealisticTokenLength() {
        String email = register("customer"), bearer = access(login(email, password));
        String google = "fixture:" + UUID.randomUUID() + ":" + email + ":" + "x".repeat(1200);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/private/google/link",
                                        Map.of("token", google),
                                        bearer)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        call(
                                        HttpMethod.POST,
                                        "/auth/public/google",
                                        Map.of("idToken", google, "role", "CUSTOMER"),
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentResetIssuanceSendsOnlyOneUsableToken() throws Exception {
        String email = register("customer");
        String uid =
                (String)
                        data(call(
                                        HttpMethod.GET,
                                        "/account/me",
                                        null,
                                        access(login(email, password))))
                                .get("userId");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 8; round++) {
                db.getCollection("actions")
                        .deleteMany(new Document("userId", uid).append("type", "PASSWORD_RESET"));
                db.getCollection("test_mail")
                        .deleteMany(new Document("email", email).append("type", "PASSWORD_RESET"));
                var start = new CyclicBarrier(2);
                Callable<ResponseEntity<Map>> task =
                        () -> {
                            start.await(5, TimeUnit.SECONDS);
                            return call(
                                    HttpMethod.POST,
                                    "/auth/public/forgot-password",
                                    Map.of("email", email),
                                    null);
                        };
                var responses = pool.invokeAll(List.of(task, task));
                for (var response : responses)
                    assertThat(response.get().getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(
                                db.getCollection("test_mail")
                                        .countDocuments(
                                                new Document("email", email)
                                                        .append("type", "PASSWORD_RESET")))
                        .isEqualTo(1);
                assertThat(
                                db.getCollection("actions")
                                        .countDocuments(
                                                new Document("userId", uid)
                                                        .append("type", "PASSWORD_RESET")
                                                        .append("consumed", false)))
                        .isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
