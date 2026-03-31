package com.vish.fno.reader.shoonya.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vish.fno.reader.shoonya.exception.ShoonyaApiException;
import com.vish.fno.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class ShoonyaHttpClient {

    private static final String BASE_URL = "https://api.shoonya.com/NorenWClientTP/";
    private static final String STAT_OK = "Ok";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ShoonyaHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = JsonUtils.createObjectMapper();
    }

    public JsonNode post(String endpoint, Map<String, Object> payload) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8);
        return executePost(endpoint, body);
    }

    public JsonNode postAuthenticated(String endpoint, Map<String, Object> payload,
                                      String sessionToken) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8)
                + "&jKey=" + URLEncoder.encode(sessionToken, StandardCharsets.UTF_8);
        return executePost(endpoint, body);
    }

    public String postAuthenticatedRaw(String endpoint, Map<String, Object> payload,
                                       String sessionToken) throws IOException {
        String jData = objectMapper.writeValueAsString(payload);
        String body = "jData=" + URLEncoder.encode(jData, StandardCharsets.UTF_8)
                + "&jKey=" + URLEncoder.encode(sessionToken, StandardCharsets.UTF_8);
        return executePostRaw(endpoint, body);
    }

    public byte[] download(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP " + response.statusCode() + " for URL: " + url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted for URL: " + url, e);
        }
    }

    private JsonNode executePost(String endpoint, String body) throws IOException {
        String responseBody = executePostRaw(endpoint, body);
        JsonNode node = objectMapper.readTree(responseBody);
        checkForError(node, endpoint);
        return node;
    }

    private String executePostRaw(String endpoint, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Shoonya {} response status: {}", endpoint, response.statusCode());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted for endpoint: " + endpoint, e);
        }
    }

    private void checkForError(JsonNode node, String endpoint) {
        JsonNode statNode = node.get("stat");
        if (statNode != null && !STAT_OK.equals(statNode.asText())) {
            String errorMsg = node.has("emsg") ? node.get("emsg").asText() : "Unknown error";
            throw new ShoonyaApiException("Shoonya API error on " + endpoint + ": " + errorMsg);
        }
    }

    public static String encodeSymbol(String tradingSymbol) {
        return URLEncoder.encode(tradingSymbol, StandardCharsets.UTF_8);
    }
}
