package com.km.worker.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * F4 整合（commit #24）：Admin 客户端（抽出独立文件，硬规则要求）。
 * 原为 WorkerApplication.java 单文件内嵌类。
 */
@Service
public class AdminClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final String adminBaseUrl;

    public AdminClient(@Value("${km.admin-base-url}") String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }

    public boolean claim(Long taskId, String token) {
        return bool("/internal/km/tasks/" + taskId + "/claim", token, "claimed");
    }

    public boolean heartbeat(Long taskId, String token) {
        return bool("/internal/km/tasks/" + taskId + "/heartbeat", token, "active");
    }

    private boolean bool(String path, String token, String field) {
        Map<String, String> body = Collections.singletonMap("claimToken", token);
        ResponseEntity<Map> res = restTemplate.postForEntity(adminBaseUrl + path, new HttpEntity<>(body), Map.class);
        Object v = res.getBody() == null ? false : res.getBody().get(field);
        return Boolean.TRUE.equals(v);
    }
}