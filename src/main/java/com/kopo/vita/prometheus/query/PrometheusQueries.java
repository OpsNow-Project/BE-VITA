package com.kopo.vita.prometheus.query;

public class PrometheusQueries {

    public static final String NODE_READY_COUNT = "count(kube_node_status_condition{condition=\"Ready\", status=\"true\"})";
    public static final String NODE_CPU_CAPACITY = "sum(kube_node_status_capacity_cpu_cores)";
    public static final String NODE_MEMORY_CAPACITY = "sum(kube_node_status_capacity_memory_bytes)";
    public static final String NODE_CPU_ALLOCATABLE = "sum(kube_node_status_allocatable_cpu_cores)";
    public static final String NODE_MEMORY_ALLOCATABLE = "sum(kube_node_status_allocatable_memory_bytes)";
    public static final String NODE_CPU_USAGE = "sum(rate(container_cpu_usage_seconds_total[5m])) by (node)";
    public static final String NODE_MEMORY_USAGE =  "sum(container_memory_usage_bytes) by (node)";
    public static final String POD_TOTAL_COUNT = "count(kube_pod_info)";
    public static final String POD_RUNNING_COUNT = "count(kube_pod_status_phase{phase=\"Running\"})";
    public static final String POD_PENDING_COUNT = "count(kube_pod_status_phase{phase=\"Pending\"})";
    public static final String POD_FAILED_COUNT = "count(kube_pod_status_phase{phase=\"Failed\"})";
    public static final String VOLUME_USED_BYTES = "kubelet_volume_stats_used_bytes";
    public static final String VOLUME_CAPACITY_BYTES = "kubelet_volume_stats_capacity_bytes";
    public static final String NETWORK_TRANSMIT = "sum(rate(container_network_transmit_bytes_total[5m])) by (node)";
    public static final String NETWORK_RECEIVE = "sum(rate(container_network_receive_bytes_total[5m])) by (node)";

}
