package org.example.demo.podidentity;

import java.time.Instant;

import com.microsoft.azure.keyvault.KeyVaultClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KeyVaultController {
    @Autowired
    private KeyVaultClient client;

    @Value("${keyvault.url}")
    private String keyVaultUrl;

    @GetMapping("/ping")
    public String hello() {
        return "Hello, World @"+ Instant.now() + "!";
    }

    @GetMapping("/secret/{name}")
    public String secret(@PathVariable String name) {
        return client.getSecret(keyVaultUrl, name).value();
    }
}
