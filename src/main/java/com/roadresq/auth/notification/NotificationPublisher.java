package com.roadresq.auth.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationPublisher {
    private final MongoTemplate mongo;

    // Commit the business event with the rescue or payment change. The relay sees committed records.
    public boolean record(String eventKey, String type, String requestId, String appType,
                          String recipientField, String recipientId, Instant expiresAt) {
        String id = eventKey + ":" + appType + ":" + recipientId;
        Instant now = Instant.now();
        Update event = new Update().setOnInsert("eventType", type).setOnInsert("requestId", requestId)
                .setOnInsert("appType", appType).setOnInsert(recipientField, recipientId)
                .setOnInsert("createdAt", now).setOnInsert("expiresAt", expiresAt)
                .setOnInsert("processed", false).setOnInsert("publishAfter", now);
        return mongo.upsert(Query.query(Criteria.where("_id").is(id)), event, "notification_outbox")
                .getUpsertedId() != null;
    }
}