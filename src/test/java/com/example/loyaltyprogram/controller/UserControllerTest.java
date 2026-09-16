package com.example.loyaltyprogram.controller;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateUserRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateUserRequest;
import com.example.loyaltyprogram.dto.response.BalanceResponse;
import com.example.loyaltyprogram.dto.response.ProgramSummaryResponse;
import com.example.loyaltyprogram.dto.response.UserResponse;
import com.example.loyaltyprogram.exception.MembershipAlreadyExistsException;
import com.example.loyaltyprogram.exception.ProgramNotFoundException;
import com.example.loyaltyprogram.exception.UserNotFoundException;
import com.example.loyaltyprogram.service.UserService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private UserService userService;

    @Test
    void createUser_dataCorrect_returnsCreatedUserWithLocationHeader() throws Exception {
        //given
        CreateUserRequest command = new CreateUserRequest("john.doe@example.com", "John", "Doe", 10L);
        ProgramSummaryResponse programSummary = new ProgramSummaryResponse(10L, "Gold Program");
        UserResponse response = new UserResponse(1L, "john.doe@example.com", "John", "Doe", List.of(programSummary));
        ArgumentCaptor<CreateUserRequest> commandCaptor = ArgumentCaptor.forClass(CreateUserRequest.class);
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(response);
        //when + then
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.programs[0].id").value(10L))
                .andExpect(jsonPath("$.programs[0].name").value("Gold Program"));

        Mockito.verify(userService).createUser(commandCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals("john.doe@example.com", commandCaptor.getValue().email()),
                () -> Assertions.assertEquals("John", commandCaptor.getValue().firstName()),
                () -> Assertions.assertEquals("Doe", commandCaptor.getValue().lastName()),
                () -> Assertions.assertEquals(10L, commandCaptor.getValue().programId())
        );
    }

    @Test
    void getUsers_dataCorrect_returnsPagedUsers() throws Exception {
        //given
        UserResponse user = new UserResponse(1L, "john.doe@example.com", "John", "Doe", List.of());
        PageDto<UserResponse> pageResponse = new PageDto<>(List.of(user), 0, 10, 1L, 1);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> lastNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<PageRequestDto> pageRequestCaptor = ArgumentCaptor.forClass(PageRequestDto.class);
        when(userService.searchUsers(eq("john.doe@example.com"), eq("Doe"), any(PageRequestDto.class)))
                .thenReturn(pageResponse);

        //when + then
        mockMvc.perform(get("/users")
                        .param("email", "john.doe@example.com")
                        .param("lastName", "Doe")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        Mockito.verify(userService).searchUsers(emailCaptor.capture(), lastNameCaptor.capture(), pageRequestCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals("john.doe@example.com", emailCaptor.getValue()),
                () -> Assertions.assertEquals("Doe", lastNameCaptor.getValue())
        );
    }

    @Test
    void getUserPrograms_userExists_returnsList() throws Exception {
        //given
        BalanceResponse balance = new BalanceResponse(100L, 1L, 10L, "Gold Program", 500);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(userService.getUserPrograms(1L)).thenReturn(List.of(balance));
        //when + then
        mockMvc.perform(get("/users/{userId}/programs", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].membershipId").value(100L))
                .andExpect(jsonPath("$[0].userId").value(1L))
                .andExpect(jsonPath("$[0].programId").value(10L))
                .andExpect(jsonPath("$[0].programName").value("Gold Program"))
                .andExpect(jsonPath("$[0].pointsBalance").value(500));

        Mockito.verify(userService).getUserPrograms(userIdCaptor.capture());
        Assertions.assertEquals(1L, userIdCaptor.getValue());
    }

    @Test
    void getUserPrograms_userNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        when(userService.getUserPrograms(999L)).thenThrow(new UserNotFoundException(999L));
        //when + then
        mockMvc.perform(get("/users/{userId}/programs", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User not found: id=999")));
    }

    @Test
    void getUser_userExists_returnsUser() throws Exception {
        //given
        UserResponse response = new UserResponse(1L, "john.doe@example.com", "John", "Doe", List.of());
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(userService.getUser(1L)).thenReturn(response);
        //when + then
        mockMvc.perform(get("/users/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));

        Mockito.verify(userService).getUser(userIdCaptor.capture());
        Assertions.assertEquals(1L, userIdCaptor.getValue());
    }

    @Test
    void getUser_userNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        when(userService.getUser(999L)).thenThrow(new UserNotFoundException(999L));
        //when + then
        mockMvc.perform(get("/users/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User not found: id=999")));
    }

    @Test
    void updateUser_dataCorrect_returnsUpdatedUser() throws Exception {
        //given
        UpdateUserRequest command = new UpdateUserRequest("Johnny", "Doe-Updated");
        UserResponse response = new UserResponse(1L, "john.doe@example.com", "Johnny", "Doe-Updated", List.of());
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<UpdateUserRequest> commandCaptor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        when(userService.update(eq(1L), any(UpdateUserRequest.class))).thenReturn(response);
        //when + then
        mockMvc.perform(put("/users/{userId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.firstName").value("Johnny"))
                .andExpect(jsonPath("$.lastName").value("Doe-Updated"));

        Mockito.verify(userService).update(userIdCaptor.capture(), commandCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, userIdCaptor.getValue()),
                () -> Assertions.assertEquals("Johnny", commandCaptor.getValue().firstName()),
                () -> Assertions.assertEquals("Doe-Updated", commandCaptor.getValue().lastName())
        );
    }

    @Test
    void updateUser_userNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        UpdateUserRequest command = new UpdateUserRequest("Johnny", "Doe-Updated");
        when(userService.update(eq(999L), any(UpdateUserRequest.class)))
                .thenThrow(new UserNotFoundException(999L));
        //when + then
        mockMvc.perform(put("/users/{userId}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User not found: id=999")));
    }

    @Test
    void delete_userExists_returnsNoContent() throws Exception {
        //given
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        //when + then
        mockMvc.perform(delete("/users/{userId}", 1L))
                .andExpect(status().isNoContent());

        Mockito.verify(userService).delete(userIdCaptor.capture());
        Assertions.assertEquals(1L, userIdCaptor.getValue());
    }

    @Test
    void delete_userNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        Mockito.doThrow(new UserNotFoundException(999L)).when(userService).delete(999L);
        //when + then
        mockMvc.perform(delete("/users/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User not found: id=999")));
    }

    @Test
    void joinProgram_dataCorrect_returnsCreatedBalance() throws Exception {
        //given
        BalanceResponse balance = new BalanceResponse(100L, 1L, 10L, "Gold Program", 0);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> programIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(userService.joinProgram(1L, 10L)).thenReturn(balance);
        //when + then
        mockMvc.perform(post("/users/{userId}/programs/{programId}", 1L, 10L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membershipId").value(100L))
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.programId").value(10L))
                .andExpect(jsonPath("$.programName").value("Gold Program"))
                .andExpect(jsonPath("$.pointsBalance").value(0));

        Mockito.verify(userService, Mockito.times(1)).joinProgram(userIdCaptor.capture(), programIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, userIdCaptor.getValue()),
                () -> Assertions.assertEquals(10L, programIdCaptor.getValue())
        );
    }

    @Test
    void joinProgram_alreadyJoined_returnsConflictWithMessage() throws Exception {
        //given
        when(userService.joinProgram(1L, 10L)).thenThrow(new MembershipAlreadyExistsException(1L, 10L));
        //when + then
        mockMvc.perform(post("/users/{userId}/programs/{programId}", 1L, 10L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEMBERSHIP_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User id=1 already belongs to program id=10")));
    }

    @Test
    void joinProgram_programNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        when(userService.joinProgram(1L, 999L)).thenThrow(new ProgramNotFoundException(999L));
        //when + then
        mockMvc.perform(post("/users/{userId}/programs/{programId}", 1L, 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PROGRAM_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("Program not found: id=999")));
    }

    @Test
    void leaveProgram_dataCorrect_returnsNoContent() throws Exception {
        //given
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> programIdCaptor = ArgumentCaptor.forClass(Long.class);
        //when + then
        mockMvc.perform(delete("/users/{userId}/programs/{programId}", 1L, 10L))
                .andExpect(status().isNoContent());

        Mockito.verify(userService).leaveProgram(userIdCaptor.capture(), programIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, userIdCaptor.getValue()),
                () -> Assertions.assertEquals(10L, programIdCaptor.getValue())
        );
    }

    @Test
    void leaveProgram_userNotExists_returnsNotFoundWithMessage() throws Exception {
        //given
        Mockito.doThrow(new UserNotFoundException(999L)).when(userService).leaveProgram(999L, 10L);
        //when + then
        mockMvc.perform(delete("/users/{userId}/programs/{programId}", 999L, 10L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message", Matchers.containsString("User not found: id=999")));
    }
}