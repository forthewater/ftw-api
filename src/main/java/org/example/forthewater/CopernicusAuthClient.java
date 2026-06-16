package org.example.forthewater;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class CopernicusAuthClient {

    private final RestClient restClient;

    @Value("${app.copernicus.auth-url}")
    private String authUrl;

    @Value("${app.copernicus.client-id:}")
    private String clientId;

    @Value("${app.copernicus.client-secret:}")
    private String clientSecret;

    @Value("${app.copernicus.username:}")
    private String username;

    @Value("${app.copernicus.password:}")
    private String password;

    public CopernicusAuthClient() {
        this.restClient = RestClient.builder().build();
    }

    public String fetchAccessToken() {
        log.info("Fetching OAuth2 Token from Copernicus Keycloak...");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (isConfigured(clientId) && isConfigured(clientSecret)) {
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            formData.add("grant_type", "client_credentials");
        } else if (isConfigured(username) && isConfigured(password)) {
            formData.add("client_id", "cdse-public");
            formData.add("username", username);
            formData.add("password", password);
            formData.add("grant_type", "password");
        } else {
            throw new RuntimeException("Copernicus credentials are not configured.");
        }

        TokenResponse response = restClient.post()
                .uri(authUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new RuntimeException("Failed to fetch access token from Copernicus!");
        }

        log.info("Successfully fetched Copernicus Access Token!");
        return response.accessToken();
    }

    private boolean isConfigured(String value) {
        return value != null
                && !value.isBlank()
                && !value.startsWith("YOUR_COPERNICUS");
    }
}
