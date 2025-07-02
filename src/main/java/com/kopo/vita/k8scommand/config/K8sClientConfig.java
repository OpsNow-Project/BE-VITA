package com.kopo.vita.k8scommand.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class K8sClientConfig {
    @Value("${k8s.kubeconfig}")
    private String kubeconfigPath;
    @Bean
    public KubernetesClient kubernetesClient() throws IOException {
        String yaml = Files.readString(Paths.get(kubeconfigPath));
        Config config = Config.fromKubeconfig(yaml);
        return new DefaultKubernetesClient(config);
    }
}
