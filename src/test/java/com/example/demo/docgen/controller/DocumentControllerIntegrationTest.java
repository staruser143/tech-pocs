package com.example.demo.docgen.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DocumentControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void generate_withMissingBaseTemplate_returnsBadRequestJson() {
        String url = "http://localhost:" + port + "/api/documents/generate";

        Map<String, Object> request = new HashMap<>();
        request.put("templateId", "composite-enrollment");

        Map<String, Object> data = new HashMap<>();
        Map<String, Object> application = new HashMap<>();
        application.put("state", "ZZ_NOT_EXIST");
        application.put("productType", "DENTAL");
        data.put("application", application);

        request.put("data", data);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKeys("code", "description");
        assertThat(response.getBody().get("code")).isEqualTo("TEMPLATE_NOT_FOUND");
        assertThat(response.getBody().get("description")).asString().contains("Template not found");
    }
}
