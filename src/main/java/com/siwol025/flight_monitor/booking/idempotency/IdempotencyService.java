package com.siwol025.flight_monitor.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Fresh;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.KeyReused;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Processing;
import com.siwol025.flight_monitor.booking.idempotency.IdempotentResult.Replay;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * 우리 API의 Idempotency-Key를 한 번만 처리하고 결과를 저장해 같은 키 재요청에 그대로 돌려준다.
 *
 * <p>서버가 여럿이어도 한 곳만 처리하도록 키·상태는 앱 메모리가 아니라 DB(idempotency_keys)에 둔다. 선점·결과 저장은 각각
 * 짧은 트랜잭션으로 바로 커밋하고, 처리(외부 호출 포함)는 트랜잭션 밖에서 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public <T> IdempotentResult<T> execute(long userId, IdempotencyScope scope, String key, String requestHash,
                                           Function<IdempotencyClaim, ResponseEntity<T>> work) {
        // 재요청(이미 있는 키)은 INSERT 없이 읽는다. 처음 온 키는 유니크 제약으로 한 서버만 선점한다
        Optional<IdempotencyKey> existing = find(userId, scope, key);
        if (existing.isEmpty() && claimNew(userId, scope, key, requestHash)) {
            return process(new IdempotencyClaim(find(userId, scope, key).orElseThrow().getId(), null), work);
        }
        IdempotencyKey stored = existing.or(() -> find(userId, scope, key)).orElse(null);
        if (stored == null) {
            // 먼저 선점한 요청이 일시 오류로 키를 막 지웠다. 같은 키로 다시 보내면 처음부터 처리한다
            return new Processing<>();
        }
        if (!stored.getRequestHash().equals(requestHash)) {
            return new KeyReused<>();
        }
        if (stored.getStatus() != IdempotencyStatus.PROCESSING) {
            return new Replay<>(stored.getResponseStatus(), stored.getResponseBody());
        }
        if (repository.reclaimStale(stored.getId(), requestHash, properties.getStaleAfter().toSeconds()) == 0) {
            return new Processing<>();
        }
        // 처리권을 가져온 뒤 다시 읽는다: 멈춘 처리가 남긴 예매가 있으면 넘겨준다
        Long bookingId = find(userId, scope, key).orElseThrow().getBookingId();
        return process(new IdempotencyClaim(stored.getId(), bookingId), work);
    }

    /** 이 요청이 만든 예매를 키에 남긴다. 예매 행 INSERT와 같은 트랜잭션에서 부른다 */
    public void attachBooking(IdempotencyClaim claim, long bookingId) {
        repository.attachBooking(claim.keyId(), bookingId);
    }

    private boolean claimNew(long userId, IdempotencyScope scope, String key, String requestHash) {
        try {
            repository.insertProcessing(userId, scope.name(), key, requestHash);
            return true;
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return false;
        }
    }

    // 처리(외부 호출 포함)는 트랜잭션 밖에서 하고, 결과는 짧은 트랜잭션으로 저장한다
    private <T> IdempotentResult<T> process(IdempotencyClaim claim, Function<IdempotencyClaim, ResponseEntity<T>> work) {
        ResponseEntity<T> response = work.apply(claim);
        saveResult(claim.keyId(), response);
        return new Fresh<>(response);
    }

    private Optional<IdempotencyKey> find(long userId, IdempotencyScope scope, String key) {
        return repository.findByUserIdAndScopeAndIdemKey(userId, scope, key);
    }

    /** 2xx(결과를 모르는 202 포함)는 COMPLETED, 4xx는 FAILED로 저장해 재생한다. 그 밖(우리 쪽 일시 오류)은 키를 지워 같은 키로 다시 처리하게 한다 */
    private void saveResult(long keyId, ResponseEntity<?> response) {
        HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
        if (!status.is2xxSuccessful() && !status.is4xxClientError()) {
            repository.deleteProcessing(keyId);
            return;
        }
        IdempotencyStatus result = status.is2xxSuccessful() ? IdempotencyStatus.COMPLETED : IdempotencyStatus.FAILED;
        if (repository.saveResult(keyId, result.name(), status.value(), toJson(response.getBody())) == 0) {
            log.warn("멱등키 결과를 저장하지 못했습니다(이미 처리 중이 아님). keyId={}, status={}", keyId, status.value());
        }
    }

    private String toJson(Object body) {
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("멱등키 응답 본문을 저장할 수 없습니다", e);
        }
    }
}
