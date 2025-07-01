package com.kopo.vita.prometheus.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PodDTO {

    private String podName;

    private String podId;

    private String nameSpace;

    private double cpu;              // 단위: cores

    private double memory;           // 단위: MiB

    private double networkReceive;   // 단위: bytes/sec

    private double networkTransmit;  // 단위: bytes/sec

    private String nodeName;         // 파드가 실행 중인 노드 이름

    private String uid;              // 파드 UID

    private String createdAt;        // 생성 시간 (ISO 8601 문자열)

    private String status;           // 상태 (Running, Pending 등)

    private long restartCount;       // 재시작 횟수

}
