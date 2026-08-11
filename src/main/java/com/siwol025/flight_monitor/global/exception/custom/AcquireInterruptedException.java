package com.siwol025.flight_monitor.monitor.worker;

/**
 * permit 획득 대기 중 인터럽트가 발생했음을 알리는 언체크 예외.
 *
 * <p>이 예외가 던져진 시점에는 permit을 <b>보유하지 않은</b> 상태다.
 * 따라서 이 예외를 받은 호출자는 {@code release()}를 호출해서는 안 된다(세마포어 카운트 손상 방지).</p>
 */
public class AcquireInterruptedException extends RuntimeException {

    public AcquireInterruptedException(Throwable cause) {
        super(cause);
    }
}
