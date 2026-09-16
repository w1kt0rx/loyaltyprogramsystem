package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.EarnPointsRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.response.EarnPointsResponse;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.dto.response.PointsHistoryResponse;
import com.example.loyaltyprogram.service.PointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users/{userId}/points")
@RequiredArgsConstructor
public class PointsController {
    private final PointsService pointsService;

    @Operation(summary = "Earn points for an event",
            description = "Accepts either (eventType + programId) or earningRuleId, never both or neither. " +
                    "Idempotent by referenceId within the membership - a repeated request with the same referenceId " +
                    "returns the original transaction instead of awarding points again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Points earned (or original transaction returned if already processed)",
                    content = @Content(schema = @Schema(implementation = EarnPointsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or ambiguous request payload",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "User does not belong to the target program",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program inactive or no matching earning rule",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/earn")
    public EarnPointsResponse earn(
            @Parameter(description = "User id") @PathVariable Long userId,
            @RequestBody EarnPointsRequest request) {
        log.info("Received POST request to earn points for userId={}", userId);
        return pointsService.earnPoints(userId, request);
    }

    @Operation(summary = "Get points transaction history",
            description = "Returns a paginated history of points transactions for the given user within the given program, sorted by date descending.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of transactions",
                    content = @Content(schema = @Schema(implementation = PageDto.class))),
            @ApiResponse(responseCode = "403", description = "User does not belong to the given program",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/history")
    public PageDto<PointsHistoryResponse> history(
            @Parameter(description = "User id") @PathVariable Long userId,
            @Parameter(description = "Program id") @RequestParam Long programId,
            PageRequestDto pageRequest) {
        log.info("Received GET request for points history: userId={}, programId={}, page={}, size={}",
                userId, programId, pageRequest.page(), pageRequest.size());
        return pointsService.getHistory(userId, programId, pageRequest);
    }
}