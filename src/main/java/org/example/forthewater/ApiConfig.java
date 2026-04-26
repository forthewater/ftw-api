package org.example.forthewater;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiConfig {

    @Value("${app.overpass.url}")
    private String overpassUrl;

    @Value("${app.copernicus.stat-api-url}")
    private String copernicusUrl;

    @Bean
    public RestClient overpassRestClient() {
        return RestClient.builder()
                .baseUrl(overpassUrl)
                .build();
    }

    @Bean
    public RestClient copernicusRestClient() {
        return RestClient.builder()
                .baseUrl(copernicusUrl)
                .build();
    }
}