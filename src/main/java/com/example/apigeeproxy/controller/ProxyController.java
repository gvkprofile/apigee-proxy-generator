package com.example.apigeeproxy.controller;

import com.example.apigeeproxy.service.ProxyService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @PostMapping("/generate-proxy")
    public ResponseEntity<String> generateProxy(
            @RequestParam String oasUrl,
            @RequestParam String repoUrl) {
        try {
            String res = proxyService.generateProxyAndPush(oasUrl, repoUrl);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error: " + e.getMessage());
        }
    }
}
