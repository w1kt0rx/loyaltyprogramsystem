package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateUserRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateUserRequest;
import com.example.loyaltyprogram.dto.response.BalanceResponse;
import com.example.loyaltyprogram.dto.response.ErrorResponse;
import com.example.loyaltyprogram.dto.response.UserResponse;
import com.example.loyaltyprogram.service.UserService;
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
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(summary = "Create a new user",
            description = "Registers a user. If programId is provided, a Membership with balance 0 is created in the same transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Input validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Program with given id does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use or program expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public UserResponse createUser(@RequestBody CreateUserRequest request) {
        log.info("Received POST request to create new user with email={}", request.email());
        return userService.createUser(request);
    }

    @Operation(summary = "Search users",
            description = "Returns a paginated list of users, optionally filtered by email and last name.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of results",
                    content = @Content(schema = @Schema(implementation = PageDto.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    public PageDto<UserResponse> getUsers(
            @Parameter(description = "Filter by email fragment (case-insensitive)")
            @RequestParam(required = false) String email,
            @Parameter(description = "Filter by last name fragment (case-insensitive)")
            @RequestParam(required = false) String lastName,
            @ModelAttribute PageRequestDto pageRequest
    ) {
        log.info("Received GET request to search users: email={}, lastName={}, page={}, size={}",
                email, lastName, pageRequest.page(), pageRequest.size());
        return userService.searchUsers(email, lastName, pageRequest);
    }

    @Operation(summary = "Get a user's program memberships",
            description = "Returns the list of memberships (points balance per program) for the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of memberships"),
            @ApiResponse(responseCode = "404", description = "User does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{userId}/programs")
    public List<BalanceResponse> getUserPrograms(
            @Parameter(description = "User id") @PathVariable Long userId) {
        log.info("Received GET request for user programs: userId={}", userId);
        return userService.getUserPrograms(userId);
    }

    @Operation(summary = "Get a user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{userId}")
    public UserResponse getUser(
            @Parameter(description = "User id") @PathVariable Long userId) {
        log.info("Received GET request for userId={}", userId);
        return userService.getUser(userId);
    }

    @Operation(summary = "Update a user's profile data",
            description = "Updates first and last name only. Points balance and program memberships cannot be changed via this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "404", description = "User does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/{userId}")
    public UserResponse updateUser(
            @Parameter(description = "User id") @PathVariable Long userId,
            @RequestBody UpdateUserRequest request) {
        log.info("Received PUT request to update userId={}", userId);
        return userService.update(userId, request);
    }

    @Operation(summary = "Delete (deactivate) a user",
            description = "Soft delete - sets the deactivated flag instead of hard-deleting the record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deactivated"),
            @ApiResponse(responseCode = "404", description = "User does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{userId}")
    public void delete(
            @Parameter(description = "User id") @PathVariable Long userId) {
        log.info("Received DELETE request for userId={}", userId);
        userService.delete(userId);
    }

    @Operation(summary = "Join a user to a program",
            description = "Creates a Membership with balance 0. Rejected if the user already belongs to the program or the program has expired.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Membership created"),
            @ApiResponse(responseCode = "404", description = "User or program does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "User already a member / program inactive",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{userId}/programs/{programId}")
    public BalanceResponse joinProgram(
            @Parameter(description = "User id") @PathVariable Long userId,
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received POST request for userId={} to join programId={}", userId, programId);
        return userService.joinProgram(userId, programId);
    }

    @Operation(summary = "Remove a user's membership in a program",
            description = "Rejected (409) if the points balance is non-zero - the points would be lost.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Membership removed"),
            @ApiResponse(responseCode = "404", description = "Membership does not exist",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Non-zero points balance",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{userId}/programs/{programId}")
    public void leaveProgram(
            @Parameter(description = "User id") @PathVariable Long userId,
            @Parameter(description = "Program id") @PathVariable Long programId) {
        log.info("Received DELETE request for userId={} to leave programId={}", userId, programId);
        userService.leaveProgram(userId, programId);
    }
}