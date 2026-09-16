package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.request.CreateRewardRequest;
import com.example.loyaltyprogram.dto.request.UpdateRewardRequest;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.dto.response.RewardResponse;
import com.example.loyaltyprogram.service.RewardService;
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

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
public class RewardController {
    private final RewardService rewardService;

    @Operation(summary = "Create a reward for a program",
            description = "Rejected (409) if the program has expired.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reward created",
                    content = @Content(schema = @Schema(implementation = RewardResponse.class))),
            @ApiResponse(responseCode = "400", description = "Input validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program has expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/programs/{programId}/rewards")
    public RewardResponse createReward(
            @Parameter(description = "Program id") @PathVariable Long programId,
            @RequestBody CreateRewardRequest request) {
        log.info("Received POST request to create reward for programId={}", programId);
        return rewardService.createReward(programId, request);
    }

    @Operation(summary = "List rewards for a program")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of rewards"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/programs/{programId}/rewards")
    public List<RewardResponse> listRewards(
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received GET request to list rewards for programId={}", programId);
        return rewardService.listRewards(programId);
    }

    @Operation(summary = "Get a reward by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reward found"),
            @ApiResponse(responseCode = "404", description = "Reward does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/rewards/{rewardId}")
    public RewardResponse getReward(
            @Parameter(description = "Reward id") @PathVariable Long rewardId) {
        log.info("Received GET request for rewardId={}", rewardId);
        return rewardService.getReward(rewardId);
    }

    @Operation(summary = "Update a reward",
            description = "Changing the points cost does not retroactively affect already redeemed transactions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reward updated"),
            @ApiResponse(responseCode = "404", description = "Reward does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/rewards/{rewardId}")
    public RewardResponse updateReward(
            @Parameter(description = "Reward id") @PathVariable Long rewardId,
            @RequestBody UpdateRewardRequest request) {
        log.info("Received POST request to update rewardId={}", rewardId);
        return rewardService.updateReward(rewardId, request);
    }

    @Operation(summary = "Delete a reward")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reward deleted"),
            @ApiResponse(responseCode = "404", description = "Reward does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/rewards/{rewardId}")
    public void deleteReward(
            @Parameter(description = "Reward id") @PathVariable Long rewardId) {
        log.info("Received DELETE request for rewardId={}", rewardId);
        rewardService.deleteReward(rewardId);
    }
}