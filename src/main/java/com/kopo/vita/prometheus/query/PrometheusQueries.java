package com.kopo.vita.prometheus.query;

public class PrometheusQueries {

    // 현재 "Ready" 상태인 노드 수
    public static final String NODE_READY_COUNT = "count(kube_node_status_condition{condition=\"Ready\", status=\"true\"})";

    // 전체 노드의 CPU 용량 (코어 수 합계)
    public static final String NODE_CPU_CAPACITY = "sum(kube_node_status_capacity{resource=\"cpu\"})";

    // 전체 노드의 메모리 용량 (바이트 단위 합계)
    public static final String NODE_MEMORY_CAPACITY = "sum(kube_node_status_capacity{resource=\"memory\"})";

    // 전체 노드의 할당 가능한 CPU (파드에 할당 가능한 코어 수 합계)
    public static final String NODE_CPU_ALLOCATABLE = "sum(kube_node_status_allocatable{resource=\"cpu\"})";

    // 전체 노드의 할당 가능한 메모리 (파드에 할당 가능한 메모리 용량 합계)
    public static final String NODE_MEMORY_ALLOCATABLE = "sum(kube_node_status_allocatable{resource=\"memory\"})";

    // 노드별 CPU 사용량 (5분 평균, 초 단위 CPU 사용률)
    public static final String NODE_CPU_USAGE = "sum(rate(container_cpu_usage_seconds_total[5m])) by (node)";

    // 노드별 메모리 사용량 (바이트 단위)
    public static final String NODE_MEMORY_USAGE =  "sum(container_memory_usage_bytes) by (node)";

    // 전체 파드 수
    public static final String POD_TOTAL_COUNT = "count(kube_pod_info)";

    // 현재 Running 상태인 파드 수
    public static final String POD_RUNNING_COUNT = "count(kube_pod_status_phase{phase=\"Running\"})";

    // 현재 Pending 상태인 파드 수
    public static final String POD_PENDING_COUNT = "count(kube_pod_status_phase{phase=\"Pending\"})";

    // Failed 상태인 파드 수
    public static final String POD_FAILED_COUNT = "count(kube_pod_status_phase{phase=\"Failed\"})";

    // 볼륨 사용량 (바이트 단위)
    public static final String VOLUME_USED_BYTES = "kubelet_volume_stats_used_bytes";

    // 볼륨 총 용량 (바이트 단위)
    public static final String VOLUME_CAPACITY_BYTES = "kubelet_volume_stats_capacity_bytes";

    // 노드별 네트워크 송신량 (5분간 전송 바이트)
    public static final String NETWORK_TRANSMIT = "sum(rate(container_network_transmit_bytes_total[5m])) by (node)";

    // 노드별 네트워크 수신량 (5분간 수신 바이트)
    public static final String NETWORK_RECEIVE = "sum(rate(container_network_receive_bytes_total[5m])) by (node)";

    // 파드 정보 전체 리스트
    public static final String POD_LIST = "kube_pod_info";

    // 파드 cpu 사용량
    public static final String POD_CPU_USAGE = "sum(rate(container_cpu_usage_seconds_total{pod=\"%s\", namespace=\"%s\", container!=\"\"}[5m]))";

    // 파드 메모리 사용량
    public static final String POD_MEMORY_USAGE = "sum(container_memory_usage_bytes{pod=\"%s\", namespace=\"%s\", container!=\"\"}) / 1024 / 1024";

    // 파드 네트워크 송신량
    public static final String POD_NETWORK_TRANSMIT = "sum(rate(container_network_transmit_bytes_total{pod=\"%s\", namespace=\"%s\"}[5m]))";

    // 파드 네트워크 수신량
    public static final String POD_NETWORK_RECEIVE = "sum(rate(container_network_receive_bytes_total{pod=\"%s\", namespace=\"%s\"}[5m]))";

    // 파드가 실행 중인 노드 및 UID 정보
    public static final String POD_INFO = "kube_pod_info{pod=\"%s\", namespace=\"%s\"}";

    // 파드 생성 시간 (Unix timestamp)
    public static final String POD_CREATED = "kube_pod_created{pod=\"%s\", namespace=\"%s\"}";

    // 파드 상태 (Running인지 확인)
    public static final String POD_STATUS_RUNNING = "kube_pod_status_phase{pod=\"%s\", namespace=\"%s\", phase=\"Running\"}";

    // 파드 재시작 횟수 (모든 컨테이너 합산)
    public static final String POD_RESTART_COUNT = "sum(kube_pod_container_status_restarts_total{pod=\"%s\", namespace=\"%s\"})";
}
