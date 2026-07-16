package dev.bugi.sensor.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.internal.progress.ThreadSafeMockingProgress;

/**
 * 전 테스트 공통(auto-detect) 후처리: Mockito 의 스레드-로컬 stubbing 진행 상태를 매 테스트 뒤 리셋한다.
 *
 * 배경(발견된 기존 버그, 이 브랜치 소관 아님):
 *   AlertServiceTest / SensorDataServiceTest 는 strict-stubbing 모드에서 미완료 stubbing 진행 상태를
 *   스레드-로컬에 남긴다. Gradle 은 한 JVM·한 스레드에서 테스트 클래스를 순차 실행하므로, 그 폴루션이
 *   바로 다음 클래스(AccessControlServiceTest)로 새어 UnfinishedStubbingException 을 유발한다.
 *   기존 브랜치에선 클래스 실행 순서가 우연히 폴루터를 피해 통과했을 뿐이다(잠복 버그).
 *
 * 이 가드는 클래스 경계로 상태가 새지 않게 막아 순서 의존성을 제거한다. 정상 테스트엔 무해(no-op)하다.
 * 폴루터인 mock 테스트가 제대로 고쳐지면(다른 브랜치 test/unit-cleanup) 이 가드는 제거해도 된다.
 */
public class MockitoProgressGuardExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        ThreadSafeMockingProgress.mockingProgress().reset();
    }
}
