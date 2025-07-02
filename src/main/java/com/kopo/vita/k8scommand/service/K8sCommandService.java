package com.kopo.vita.k8scommand.service;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class K8sCommandService {

    private final KubernetesClient client;

    public K8sCommandService(KubernetesClient client) {
        this.client = client;
    }

    /**
     * kubectl 스타일 명령어 파싱 및 실행 디스패치
     */
    public Object execute(String cmd) {
        String[] parts = cmd.trim().split("\\s+");
        if (parts.length < 2 || !"kubectl".equals(parts[0])) {
            throw new IllegalArgumentException("명령어는 반드시 'kubectl' 로 시작해야 합니다.");
        }

        String subcommand = parts[1];

        switch (subcommand) {
            case "get":
                return handleGet(parts);
            case "describe":
                return handleDescribe(parts);
            case "logs":
                return handleLogs(parts);
            case "exec":
                return handleExec(parts);
            case "patch":
                return handlePatch(parts);
            case "scale":
                return handleScale(parts);
            case "set":
                return handleSetEnv(parts);
            case "rollout":
                return handleRollout(parts);
            default:
                throw new IllegalArgumentException("지원하지 않는 kubectl 서브커맨드: " + subcommand);
        }
    }

    private Object handleGet(String[] p) {
        String resource = p[2];
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        switch (resource) {
            case "pods":
                return client.pods().inNamespace(ns).list();
            case "deployments":
                return client.apps().deployments().inNamespace(ns).list();
            case "services":
                return client.services().inNamespace(ns).list();
            default:
                throw new IllegalArgumentException("get 리소스 지원범위: pods, deployments, services");
        }
    }

    private Object handleDescribe(String[] p) {
        String resource = p[2];
        String name = p[3];
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        switch (resource) {
            case "pod":
                return client.pods().inNamespace(ns).withName(name).get();
            case "deployment":
                return client.apps().deployments().inNamespace(ns).withName(name).get();
            default:
                throw new IllegalArgumentException("describe 지원범위: pod, deployment");
        }
    }

    private Object handleLogs(String[] p) {
        String podName = p[2];
        String ns = findOptionValue(p, "-n", "--namespace", "default");
        int tailLines = 100;

        if (containsOption(p, "--tail")) {
            tailLines = Integer.parseInt(findOptionValue(p, "--tail", null, "100"));
        }

        return Map.of(
                "pod", podName,
                "namespace", ns,
                "logs", client.pods()
                        .inNamespace(ns)
                        .withName(podName)
                        .tailingLines(tailLines)
                        .getLog()
        );
    }

    private Object handleExec(String[] p) {
        String podName = p[2];
        String container = findOptionValue(p, "-c", "--container", null);
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        int idx = Arrays.asList(p).indexOf("--");
        if (idx < 0 || idx == p.length - 1) {
            throw new IllegalArgumentException("exec 명령 뒤에 실행할 커맨드를 '--' 구분자로 붙여주세요.");
        }
        String[] cmd = Arrays.copyOfRange(p, idx + 1, p.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        client.pods().inNamespace(ns)
                .withName(podName)
                .inContainer(container)
                .writingOutput(out)
                .exec(cmd);

        return Map.of(
                "pod", podName,
                "namespace", ns,
                "container", container,
                "command", String.join(" ", cmd),
                "stdout", out.toString(StandardCharsets.UTF_8)
        );
    }

    private Object handlePatch(String[] p) {
        String kind = p[2];
        String name = p[3];
        String ns = findOptionValue(p, "-n", "--namespace", "default");
        String patchJson = extractAfter(p, "--patch");

        if ("deployment".equals(kind)) {
            Deployment result = client.apps().deployments()
                    .inNamespace(ns)
                    .withName(name)
                    .patch(PatchContext.of(PatchType.JSON), patchJson);
            return Map.of(
                    "patched", true,
                    "kind", "deployment",
                    "name", name,
                    "namespace", ns,
                    "result", result
            );
        }
        throw new IllegalArgumentException("patch 지원 리소스: deployment");
    }

    private Object handleScale(String[] p) {
        String kind = p[2];
        String name = p[3];
        int replicas = Integer.parseInt(findOptionValue(p, "--replicas", null, "1"));
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        if ("deployment".equals(kind)) {
            Deployment result = client.apps().deployments()
                    .inNamespace(ns)
                    .withName(name)
                    .scale(replicas, true);
            return Map.of(
                    "scaled", true,
                    "kind", "deployment",
                    "name", name,
                    "namespace", ns,
                    "replicas", replicas,
                    "result", result
            );
        }
        throw new IllegalArgumentException("scale 지원 리소스: deployment");
    }

    private Object handleSetEnv(String[] p) {
        if (!"env".equals(p[2])) {
            throw new IllegalArgumentException("지원하지 않는 set 하위 명령어: " + p[2]);
        }
        String deployment = p[3].split("/")[1];
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        // 예: kubectl set env deployment/my-deploy KEY=VALUE
        Map<String, String> env = new HashMap<>();
        for (int i = 4; i < p.length; i++) {
            if (!p[i].startsWith("-")) {
                String[] kv = p[i].split("=", 2);
                if (kv.length == 2) {
                    env.put(kv[0], kv[1]);
                }
            }
        }

        Deployment result = client.apps().deployments()
                .inNamespace(ns)
                .withName(deployment)
                .edit(d -> {
                    d.getSpec().getTemplate().getSpec().getContainers().forEach(c -> {
                        env.forEach((k, v) -> {
                            c.getEnv().removeIf(e -> e.getName().equals(k));
                            c.getEnv().add(new io.fabric8.kubernetes.api.model.EnvVar(k, v, null));
                        });
                    });
                    return d;
                });

        return Map.of(
                "setEnv", true,
                "deployment", deployment,
                "namespace", ns,
                "newEnv", env,
                "result", result
        );
    }

    private Object handleRollout(String[] p) {
        if (!"restart".equals(p[2])) {
            throw new IllegalArgumentException("지원하지 않는 rollout 하위 명령어: " + p[2]);
        }
        String[] parts = p[3].split("/");
        if (parts.length != 2 || !"deployment".equals(parts[0])) {
            throw new IllegalArgumentException("지원하는 rollout 대상은 deployment 뿐입니다.");
        }

        String deployment = parts[1];
        String ns = findOptionValue(p, "-n", "--namespace", "default");

        client.apps().deployments()
                .inNamespace(ns)
                .withName(deployment)
                .rolling().restart();

        return Map.of(
                "rolledOut", true,
                "deployment", deployment,
                "namespace", ns
        );
    }

    // 옵션값 탐색 헬퍼
    private String findOptionValue(String[] parts, String shortOpt, String longOpt, String defaultVal) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(shortOpt) || (longOpt != null && parts[i].equals(longOpt))) {
                if (i + 1 < parts.length) return parts[i + 1];
            }
            if (longOpt != null && parts[i].startsWith(longOpt + "=")) {
                return parts[i].substring(longOpt.length() + 1);
            }
        }
        return defaultVal;
    }

    private String extractAfter(String[] parts, String opt) {
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals(opt) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        throw new IllegalArgumentException(opt + " 옵션 뒤에 값이 없습니다.");
    }

    private boolean containsOption(String[] parts, String opt) {
        return Arrays.asList(parts).contains(opt);
    }
}
