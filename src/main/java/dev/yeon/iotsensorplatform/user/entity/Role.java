package dev.yeon.iotsensorplatform.user.entity;

public enum Role {
    SYSTEM_ADMIN,   // 전체 시스템 관리 (모든 공장·장치)
    ORG_ADMIN,      // 조직(공장) 단위 관리 (소속 공장의 그룹·사용자)
    MEMBER,         // 소속 그룹 읽기+쓰기 (장치 관리·데이터 입력·분석)
    VIEWER          // 소속 그룹 읽기 전용
}
