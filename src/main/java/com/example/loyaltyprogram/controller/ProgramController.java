package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateProgramRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateProgramRequest;
import com.example.loyaltyprogram.dto.response.BalanceResponse;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.dto.response.ProgramResponse;
import com.example.loyaltyprogram.service.ProgramService;
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
@RestController
@RequestMapping("/programs")
@RequiredArgsConstructor
public class ProgramController {
    private final ProgramService programService;

    @Operation(summary = "Create a new loyalty program",
            description = "Fails with 409 if a program with the same name already exists, or 400 if endDate is before startDate.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Program created",
                    content = @Content(schema = @Schema(implementation = ProgramResponse.class))),
            @ApiResponse(responseCode = "400", description = "Input validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program name already taken",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ProgramResponse createProgram(@RequestBody CreateProgramRequest request) {
        log.info("Received POST request to create new program");
        return programService.createProgram(request);
    }

    @Operation(summary = "List loyalty programs",
            description = "Supports filtering by active status and including expired programs.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of programs",
                    content = @Content(schema = @Schema(implementation = PageDto.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public PageDto<ProgramResponse> getPrograms(
            @Parameter(description = "If true, returns only programs active at the current moment")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "If true, includes programs whose period has already ended")
            @RequestParam(defaultValue = "false") boolean includeExpired,
            PageRequestDto pageRequest) {
        log.info("Received GET request to list programs: active={}, includeExpired={}, page={}, size={}",
                active, includeExpired, pageRequest.page(), pageRequest.size());
        return programService.listPrograms(active, includeExpired, pageRequest);
    }

    @Operation(summary = "List members of a program",
            description = "Returns the points balance of every user who is a member of the given program.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of member balances"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{programId}/users")
    public List<BalanceResponse> getProgramUsers(
            @Parameter(description = "Program id") @PathVariable Long programId) {
        return programService.getProgramMembers(programId);
    }

    @Operation(summary = "Get a program by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Program found"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{programId}")
    public ProgramResponse getProgram(
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received GET request for programId={}", programId);
        return programService.getProgram(programId);
    }

    @Operation(summary = "Update a program",
            description = "Fails with 409 if the new name is already taken by another program.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Program updated"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program name already taken",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{programId}")
    public ProgramResponse updateProgram(
            @Parameter(description = "Program id") @PathVariable Long programId,
            @RequestBody UpdateProgramRequest request) {
        log.info("Received PUT request to update programId={}", programId);
        return programService.updateProgram(programId, request);
    }

    @Operation(summary = "Delete a program",
            description = "Rejected (409) if the program still has active memberships.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Program deleted"),
            @ApiResponse(responseCode = "404", description = "Program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Program has active memberships",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{programId}")
    public void deleteProgram(
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received DELETE request for programId={}", programId);
        programService.deleteProgram(programId);
    }
}