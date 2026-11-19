package com.roadresq.auth.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Service
public class NotificationDeviceClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeviceClient.class);
    private final RestClient client;

    public NotificationDeviceClient(@Value("${app.notification-service-url}") String url,
                                    @Value("${app.api-key}") String apiKey) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(2));
        client = RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeader("X-Service-Type", "ROAD_RESQ").defaultHeader("X-Api-Key", apiKey).build();
    }

    public boolean register(String installationId, NotificationDeviceRequest device, String accessToken) {
        if (installationId == null || installationId.isBlank()) return false;
        try {
            UUID id = UUID.fromString(installationId);
            if (!id.toString().equalsIgnoreCase(installationId)) return false;
            return client.put().uri("/api/v1/notifications/private/devices/{id}", id)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON).body(device).retrieve().toBodilessEntity()
                    .getStatusCode().is2xxSuccessful();
        } catch (IllegalArgumentException | RestClientException ex) {
            log.warn("Device registration deferred ({})", ex.getClass().getSimpleName());
            return false;
        }
    }
}