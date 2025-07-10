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
      //배포시 주석 처리
//    @Value("${k8s.kubeconfig}")
//    private String kubeconfigPath;

    @Bean
    public KubernetesClient kubernetesClient() throws IOException {
        // 배포시 주석 처리
//        if (kubeconfigPath != null && !kubeconfigPath.isBlank() && Files.exists(Paths.get(kubeconfigPath))) {
//            String yaml = Files.readString(Paths.get(kubeconfigPath));
//            Config config = Config.fromKubeconfig(yaml);
//            return new DefaultKubernetesClient(config);
//        } else {
//            // 클러스터 내에서는 자동 인증
//            return new DefaultKubernetesClient();
//        }
         // 배포시 주석 해제
         return new DefaultKubernetesClient();
    }

}
