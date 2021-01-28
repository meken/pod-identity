package org.example.demo.podidentity;

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.MSICredentials;
import com.microsoft.azure.keyvault.KeyVaultClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public KeyVaultClient keyVaultClient() {
		MSICredentials credentials = new MSICredentials(AzureEnvironment.AZURE);
		return new KeyVaultClient(credentials);
	}

}
