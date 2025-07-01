package com.kopo.vita.loganalysis.query;

public class LogAnalysisQueries {

    private LogAnalysisQueries() {
    }

    // CPU 사용률 (%)
    public static final String CPU_USAGE =
            "100 * process_cpu_usage{job=\"testapp\"}";

    // heap 메모리 사용률 (%)
    public static final String MEMORY_USAGE =
            "100 * (sum(jvm_memory_used_bytes{job=\"testapp\",area=\"heap\"})/ sum(jvm_memory_max_bytes{job=\"testapp\",area=\"heap\"}))";

    // 디스크 사용률 (%)
    public static final String DISK_USAGE =
            "100 * (1 - (disk_free_bytes{job=\"testapp\",path=\"/app/.\"} / disk_total_bytes{job=\"testapp\",path=\"/app/.\"}))";

    // HTTP 요청 처리량
    public static final String  HTTP_TRAFFIC =
            "rate(http_server_requests_seconds_count{job=\"testapp\"}[1m])";
}
