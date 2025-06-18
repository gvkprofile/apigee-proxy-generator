package com.example.apigeeproxy.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.*;
import java.util.stream.*;

@Service
public class ProxyService {

    @Value("${app.github.username}") private String ghUser;
    @Value("${app.github.token}") private String ghToken;

    private final ResourceLoader loader;

    public ProxyService(ResourceLoader loader) {
        this.loader = loader;
    }

    public String generateProxyAndPush(String oasUrl, String repoUrl) throws Exception {
        var oasContent = new String(new java.net.URL(oasUrl).openStream().readAllBytes());

        Path tmp = Files.createTempDirectory("apigee-");
        Resource tpl = loader.getResource("classpath:templates/apiproxy");
        Path root = tmp.resolve("apiproxy");
        Files.createDirectories(root);
        try (var walk = Files.walk(Paths.get(tpl.getURI()))) {
            walk.filter(Files::isRegularFile).forEach(src -> {
                try {
                    Path rel = Paths.get(tpl.getURI()).relativize(src);
                    Path dest = root.resolve(rel.toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest);
                } catch (Exception e) {}
            });
        }

        Files.writeString(root.resolve("oas.json"), oasContent);

        Git git = Git.init().setDirectory(tmp.toFile()).call();
        git.add().addFilepattern(".").call();
        git.commit().setMessage("Generate Apigee proxy").call();
        git.remoteAdd()
           .setName("origin")
           .setUri(new URIish(repoUrl))
           .call();
        git.push()
           .setCredentialsProvider(
               new UsernamePasswordCredentialsProvider(ghUser, ghToken))
           .call();

        return "Pushed to " + repoUrl;
    }
}
