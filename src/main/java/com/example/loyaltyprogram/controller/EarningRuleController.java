package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.request.CreateEarningRuleRequest;
import com.example.loyaltyprogram.dto.request.UpdateEarningRuleRequest;
import com.example.loyaltyprogram.dto.response.EarningRuleResponse;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.service.EarningRuleService;
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
public class EarningRuleController {
    private final EarningRuleService earningRuleService;

    @Operation(summary = "Create an earning rule for a program",
            description = "Rejected (409) if the program has expired, or if an active rule for the same event type already exists in an overlapping period.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Earning rule created",
                    content = @Content(schema = @Schema(implementation = EarningRuleResponse.class))),
            @ApiResponse(responseCode = "400", description = "Input validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program expired or overlapping rule exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/programs/{programId}/earning-rules")
    public EarningRuleResponse createEarningRule(
            @Parameter(description = "Program id") @PathVariable Long programId,
            @RequestBody CreateEarningRuleRequest request) {
        log.info("Received POST request to create earning rule for programId={}", programId);
        return earningRuleService.createRule(programId, request);
    }

    @Operation(summary = "List earning rules for a program")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of earning rules"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/programs/{programId}/earning-rules")
    public List<EarningRuleResponse> listRules(
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received GET request to list earning rules for programId={}", programId);
        return earningRuleService.listRules(programId);
    }

    @Operation(summary = "Get an earning rule by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Earning rule found"),
            @ApiResponse(responseCode = "404", description = "Earning rule does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/earning-rules/{ruleId}")
    public EarningRuleResponse getEarningRule(
            @Parameter(description = "Earning rule id") @PathVariable Long ruleId) {
        log.info("Received GET request for ruleId={}", ruleId);
        return earningRuleService.getEarningRule(ruleId);
    }

    @Operation(summary = "Update an earning rule",
            description = "The program assignment cannot be changed after creation (400 if programId differs from the current one).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Earning rule updated"),
            @ApiResponse(responseCode = "400", description = "Attempted to change program assignment or invalid dates",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Earning rule does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Overlapping rule exists for the same event type",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/earning-rules/{ruleId}")
    public EarningRuleResponse updateEarningRule(
            @Parameter(description = "Earning rule id") @PathVariable Long ruleId,
            @RequestBody UpdateEarningRuleRequest request) {
        log.info("Received PUT request to update ruleId={}", ruleId);
        return earningRuleService.updateEarningRule(ruleId, request);
    }

    @Operation(summary = "Delete an earning rule")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Earning rule deleted"),
            @ApiResponse(responseCode = "404", description = "Earning rule does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/earning-rules/{ruleId}")
    public void deleteEarningRule(
            @Parameter(description = "Earning rule id") @PathVariable Long ruleId) {
        log.info("Received DELETE request for ruleId={}", ruleId);
        earningRuleService.delete(ruleId);
    }
}