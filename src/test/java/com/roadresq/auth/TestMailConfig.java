package com.roadresq.auth;

import com.roadresq.auth.common.ApiException;
import com.roadresq.auth.mail.MailDelivery;
import com.roadresq.auth.security.GoogleIdentity;

import org.bson.Document;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;

@TestConfiguration
public class TestMailConfig {
    @Bean
    @Primary
    GoogleIdentity fixtureGoogle() {
        return token -> {
            if (!token.startsWith("fixture:"))
                throw new ApiException(401, "Invalid Google identity token");
            String[] parts = token.split(":", 4);
            return new GoogleIdentity.Identity(parts[1], parts[2], "Google", "User");
        };
    }

    @Bean
    @Primary
    MailDelivery recordingMail(MongoTemplate mongo) {
        return (email, type, token) ->
                mongo.getCollection("test_mail")
                        .insertOne(
                                new Document("email", email)
                                        .append("type", type)
                                        .append("token", token));
    }
}
