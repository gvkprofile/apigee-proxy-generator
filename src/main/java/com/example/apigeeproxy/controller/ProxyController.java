package com.example.apigeeproxy.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.apigeeproxy.service.ProxyService;

@RestController
@RequestMapping("/proxy")
public class ProxyController {

    @Autowired
    private ProxyService proxyService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateProxy(
        @RequestParam String oasPath,
        @RequestParam String repoUrl,
        @RequestParam String ghUser,
        @RequestParam String ghToken
    ) {
        try {
            String result = proxyService.generateProxyBundleAndPush(oasPath, repoUrl, ghUser, ghToken);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }


    @PostMapping("/generate-spring")
    public String generateSpringCode(@RequestParam String oasPath, @RequestParam String outputDir) {
        try {
            return proxyService.generateSpringClasses(oasPath, outputDir);
        } catch (Exception e) {
            return "Error generating Spring code: " + e.getMessage();
        }
    }
}
