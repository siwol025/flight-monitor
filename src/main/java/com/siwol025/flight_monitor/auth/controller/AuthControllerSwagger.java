package com.siwol025.flight_monitor.auth.controller;

import com.siwol025.flight_monitor.auth.dto.request.LoginRequest;
import com.siwol025.flight_monitor.auth.dto.response.LoginResponse;
import com.siwol025.flight_monitor.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증 API")
public interface AuthControllerSwagger {
    @Operation(
            summary = "구글 로그인 api",
            description = "id token 기반 구글 소셜 로그인을 진행한다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 예시",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(
                                    name = "success",
                                    summary = "로그인 성공",
                                    value = """
                                            {
                                              "accessToken": "jwt-access",
                                              "refreshToken": "jwt-refresh",
                                              "isNewMember": false
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "실패 예시",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "invalid id token",
                                            summary = "올바르지 않은 id token",
                                            value = """
                                                    {
                                                    	"tag": "ID_TOKEN_NOT_VALID",
                                                    	"message": "유효하지 않은 id token입니다."
                                                    }
                                                    """
                                    )
                            }
                    )
            )
    })
    ResponseEntity<LoginResponse> login(LoginRequest request);
}
