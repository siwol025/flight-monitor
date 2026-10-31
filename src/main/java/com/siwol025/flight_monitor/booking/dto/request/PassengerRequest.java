package com.siwol025.flight_monitor.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 승객(성인 1명). Duffel 주문 생성의 passengers[] 필드와 값 형식을 따른다. 로그인 사용자 정보와 별개로 받는다.
 * 성인 여부(출발일 기준 만 18세 이상)는 출발일을 알아야 하므로 서비스가 재조회한 offer로 판정한다.
 */
@Schema(description = "승객 정보", name = "PassengerRequest")
public record PassengerRequest(
        @Schema(description = "호칭 (mr, ms, mrs, miss, dr)", example = "mr")
        @NotNull(message = "title은 필수입니다.")
        @Pattern(regexp = "mr|ms|mrs|miss|dr", message = "title은 mr, ms, mrs, miss, dr 중 하나여야 합니다.")
        String title,

        @Schema(description = "이름", example = "Gildong")
        @NotBlank(message = "givenName은 필수입니다.")
        @Size(max = 100, message = "givenName은 100자 이하여야 합니다.")
        String givenName,

        @Schema(description = "성", example = "Hong")
        @NotBlank(message = "familyName은 필수입니다.")
        @Size(max = 100, message = "familyName은 100자 이하여야 합니다.")
        String familyName,

        @Schema(description = "성별 (m, f)", example = "m")
        @NotNull(message = "gender는 필수입니다.")
        @Pattern(regexp = "m|f", message = "gender는 m, f 중 하나여야 합니다.")
        String gender,

        @Schema(description = "생년월일 (출발일 기준 만 18세 이상)", example = "1990-01-01")
        @NotNull(message = "bornOn은 필수입니다.")
        @Past(message = "bornOn은 과거 날짜여야 합니다.")
        LocalDate bornOn,

        @Schema(description = "이메일", example = "gildong@test.com")
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "전화번호 (E.164)", example = "+821012345678")
        @NotNull(message = "phoneNumber는 필수입니다.")
        @Pattern(regexp = "\\+[1-9]\\d{1,14}", message = "phoneNumber는 E.164 형식이어야 합니다(예: +821012345678).")
        String phoneNumber
) {
}
