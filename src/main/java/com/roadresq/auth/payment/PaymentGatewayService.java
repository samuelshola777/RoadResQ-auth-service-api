package com.roadresq.auth.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.roadresq.auth.utils.exception.ErrorProcessingRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class PaymentGatewayService {

    private final RestClient paystack;
    private final RestClient flutterwave;
    private final ObjectReader reader;
    private final String callbackUrl;
    private final String paystackKey;
    private final String flutterwaveKey;

    public PaymentGatewayService(ObjectMapper mapper,
                                 @Value("${paystack.secret.key:}") String paystackKey,
                                 @Value("${flutterwave.secret.key:}") String flutterwaveKey,
                                 @Value("${payment.callback.url:}") String callbackUrl) {
        this.paystackKey = paystackKey;
        this.flutterwaveKey = flutterwaveKey;
        this.callbackUrl = callbackUrl;
        reader = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        paystack = client("https://api.paystack.co", paystackKey);
        flutterwave = client("https://api.flutterwave.com/v3", flutterwaveKey);
    }

    private static RestClient client(String url, String key) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(url).requestFactory(factory)
                .defaultHeaders(headers -> headers.setBearerAuth(key)).build();
    }

    public void checkConfiguration(PaymentProvider provider) {
        String key = provider == PaymentProvider.PAYSTACK ? paystackKey : flutterwaveKey;
        if (key.isBlank() || !callbackUrl.startsWith("https://")) {
            throw new ErrorProcessingRequestException("Payment provider or HTTPS return URL is not configured");
        }
    }

    public String initializePayment(Transaction tx, String email) {
        boolean isPaystack = tx.getProvider() == PaymentProvider.PAYSTACK;
        RestClient.RequestHeadersSpec<?> call;
        if (isPaystack) {
            call = paystack.post().uri("/transaction/initialize").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "amount", tx.getAmount().movePointRight(2).longValueExact(),
                            "currency", tx.getCurrency(), "reference", tx.getReference(), "callback_url", callbackUrl));
        } else {
            call = flutterwave.post().uri("/payments").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("customer", Map.of("email", email), "amount", tx.getAmount().toPlainString(),
                            "currency", tx.getCurrency(), "tx_ref", tx.getReference(), "redirect_url", callbackUrl));
        }
        JsonNode data = data(call, isPaystack);
        if (isPaystack && !tx.getReference().equals(data.path("reference").asText())) {
            throw new ErrorProcessingRequestException("Unexpected checkout reference");
        }
        String link = data.path(isPaystack ? "authorization_url" : "link").asText("");
        URI url = URI.create(link);
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
            throw new ErrorProcessingRequestException("Payment provider returned no checkout URL");
        }
        return link;
    }

    public JsonNode verifyPayment(Transaction tx) {
        if (tx.getProvider() == PaymentProvider.PAYSTACK) {
            return data(paystack.get().uri("/transaction/verify/{ref}", tx.getReference()), true);
        }
        return data(flutterwave.get().uri("/transactions/verify_by_reference?tx_ref={ref}", tx.getReference()), false);
    }

    private JsonNode data(RestClient.RequestHeadersSpec<?> call, boolean isPaystack) {
        try {
            String body = call.retrieve().body(String.class);
            JsonNode json = body == null ? null : reader.readTree(body);
            if (json != null && json.path("data").isObject()
                    && (isPaystack ? json.path("status").asBoolean(false) : "success".equals(json.path("status").asText()))) {
                return json.get("data");
            }
        } catch (RestClientException | JsonProcessingException exception) {
            // Provider response bodies can contain customer details.
            throw new ErrorProcessingRequestException("Payment provider could not confirm the operation");
        }
        throw new ErrorProcessingRequestException("Invalid payment provider response");
    }
}