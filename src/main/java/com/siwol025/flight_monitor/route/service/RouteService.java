package com.siwol025.flight_monitor.route.service;

import com.siwol025.flight_monitor.route.domain.Route;
import com.siwol025.flight_monitor.route.domain.RouteKey;
import com.siwol025.flight_monitor.route.repository.RouteRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 노선 키로 노선을 찾고, 없으면 만든다. 키는 이미 정규화·검증된 상태라 같은 노선은 항상 같은 행이 된다.
 *
 * <p>같은 새 노선을 두 요청이 동시에 만들면 한쪽은 유일 제약을 위반한다. 그때 기존 노선을 다시 조회해 돌려준다.
 * insert와 재조회는 모두 별도 트랜잭션(REQUIRES_NEW)에서 한다.</p>
 * <ul>
 *   <li>insert: 호출자 트랜잭션 안에서 위반하면 그 트랜잭션이 rollback-only가 되어 재조회도 실패한다.</li>
 *   <li>재조회: MySQL 기본 격리 수준(REPEATABLE READ)에서는 호출자 트랜잭션이 첫 읽기 시점의 스냅숏을 계속 보므로,
 *       방금 다른 트랜잭션이 커밋한 노선이 보이지 않는다.</li>
 * </ul>
 * <p>그래서 돌려주는 노선은 호출자의 영속성 컨텍스트 입장에서 분리 상태다. 호출자는 id로 참조만 한다.</p>
 */
@Slf4j
@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final TransactionTemplate newTransaction;

    public RouteService(RouteRepository routeRepository, PlatformTransactionManager transactionManager) {
        this.routeRepository = routeRepository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Route findOrCreate(RouteKey key) {
        Optional<Route> existing = find(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return newTransaction.execute(status -> routeRepository.saveAndFlush(new Route(key)));
        } catch (DataIntegrityViolationException e) {
            log.info("[RouteService] 같은 노선이 동시에 생성되어 기존 노선을 재사용합니다: {}", key);
            return newTransaction.execute(status -> find(key).orElseThrow());
        }
    }

    /**
     * 업체가 노선 자체를 거부하면(422) 무효로 돌려 감시와 새 구독에서 뺀다.
     */
    @Transactional
    public void markInvalid(Long routeId) {
        routeRepository.findById(routeId).ifPresent(Route::markInvalid);
    }

    private Optional<Route> find(RouteKey key) {
        return routeRepository.findByDepartureAirportAndArrivalAirportAndDepartureDateAndSeatGrade(
                key.departureAirport(), key.arrivalAirport(), key.departureDate(), key.seatGrade());
    }
}
