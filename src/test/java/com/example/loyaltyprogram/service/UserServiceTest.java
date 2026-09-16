package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateUserRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateUserRequest;
import com.example.loyaltyprogram.dto.response.BalanceResponse;
import com.example.loyaltyprogram.dto.response.UserResponse;
import com.example.loyaltyprogram.exception.*;
import com.example.loyaltyprogram.mapper.MembershipMapper;
import com.example.loyaltyprogram.mapper.PageRequestMapper;
import com.example.loyaltyprogram.mapper.UserMapper;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Membership;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.model.User;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import com.example.loyaltyprogram.repository.MembershipRepository;
import com.example.loyaltyprogram.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class UserServiceTest {
    private UserService userService;
    private UserRepository userRepository;
    private UserMapper userMapper;
    private MembershipMapper membershipMapper;
    private PageRequestMapper pageRequestMapper;
    private LoyaltyProgramRepository programRepository;
    private MembershipRepository membershipRepository;

    @BeforeEach
    void setup() {
        this.userRepository = Mockito.mock(UserRepository.class);
        this.userMapper = Mappers.getMapper(UserMapper.class);
        this.membershipMapper = Mappers.getMapper(MembershipMapper.class);
        this.pageRequestMapper = Mappers.getMapper(PageRequestMapper.class);
        this.programRepository = Mockito.mock(LoyaltyProgramRepository.class);
        this.membershipRepository = Mockito.mock(MembershipRepository.class);
        this.userService = new UserService(
                userRepository,
                userMapper,
                membershipMapper,
                pageRequestMapper,
                programRepository,
                membershipRepository
        );
    }

    @Test
    void createUser_dataCorrectWithoutProgram_userCreated() {
        //given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John", "Doe", null);
        User userToSave = new User();
        userToSave.setEmail("john.doe@example.com");
        userToSave.setFirstName("John");
        userToSave.setLastName("Doe");
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail("john.doe@example.com");
        savedUser.setFirstName("John");
        savedUser.setLastName("Doe");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        //when
        UserResponse response = userService.createUser(request);
        //then
        Mockito.verify(userRepository).save(userCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("john.doe@example.com", response.email()),
                () -> Assertions.assertEquals("John", response.firstName()),
                () -> Assertions.assertEquals("Doe", response.lastName()),
                () -> Assertions.assertEquals("john.doe@example.com", userCaptor.getValue().getEmail()),
                () -> Assertions.assertEquals("John", userCaptor.getValue().getFirstName()),
                () -> Assertions.assertEquals("Doe", userCaptor.getValue().getLastName())
        );
    }

    @Test
    void createUser_dataCorrectWithActiveProgram_userCreatedWithMembership() {
        //given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John", "Doe", 10L);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(10L);
        program.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().plusDays(10)));
        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setEmail("john.doe@example.com");
        savedUser.setFirstName("John");
        savedUser.setLastName("Doe");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        //when
        UserResponse response = userService.createUser(request);
        //then
        Mockito.verify(userRepository).save(userCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("john.doe@example.com", response.email()),
                () -> Assertions.assertEquals(1, userCaptor.getValue().getMemberships().size()),
                () -> Assertions.assertEquals(program, userCaptor.getValue().getMemberships().getFirst().getProgram())
        );
    }

    @Test
    void createUser_emailAlreadyExists_throwsConflictException() {
        //given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John", "Doe", null);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);
        //when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> userService.createUser(request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("EMAIL_ALREADY_EXISTS", ex.getErrorCode()),
                () -> Assertions.assertEquals("Email already in use: john.doe@example.com", ex.getMessage())
        );
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void createUser_programNotExists_throwsProgramNotFoundException() {
        //given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John", "Doe", 999L);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> userService.createUser(request)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void createUser_programExpired_throwsProgramExpiredException() {
        //given
        CreateUserRequest request = new CreateUserRequest("john.doe@example.com", "John", "Doe", 10L);
        LoyaltyProgram expiredProgram = new LoyaltyProgram();
        expiredProgram.setId(10L);
        expiredProgram.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().minusDays(10)));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(programRepository.findById(10L)).thenReturn(Optional.of(expiredProgram));
        //when + then
        ProgramExpiredException ex = Assertions.assertThrows(
                ProgramExpiredException.class,
                () -> userService.createUser(request)
        );
        Assertions.assertEquals("Program is not active: id=10", ex.getMessage());
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void getUserPrograms_userExists_returnsList() {
        //given
        User user = new User();
        user.setId(1L);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(10L);
        program.setName("Gold Program");
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setUser(user);
        membership.setProgram(program);
        membership.setPointsBalance(250);
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(1L)).thenReturn(List.of(membership));
        //when
        List<BalanceResponse> responses = userService.getUserPrograms(1L);
        //then
        Mockito.verify(membershipRepository).findByUserId(userIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1, responses.size()),
                () -> Assertions.assertEquals(100L, responses.getFirst().membershipId()),
                () -> Assertions.assertEquals(1L, responses.getFirst().userId()),
                () -> Assertions.assertEquals(10L, responses.getFirst().programId()),
                () -> Assertions.assertEquals("Gold Program", responses.getFirst().programName()),
                () -> Assertions.assertEquals(250, responses.getFirst().pointsBalance()),
                () -> Assertions.assertEquals(1L, userIdCaptor.getValue())
        );
    }

    @Test
    void getUserPrograms_userNotExists_throwsUserNotFoundException() {
        //given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        UserNotFoundException ex = Assertions.assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserPrograms(999L)
        );
        Assertions.assertEquals("User not found: id=999", ex.getMessage());
        Mockito.verify(membershipRepository, Mockito.never()).findByUserId(any());
    }

    @Test
    void searchUsers_dataCorrect_returnsPageDto() {
        //given
        User user1 = new User();
        user1.setId(1L);
        user1.setEmail("john.doe@example.com");
        user1.setFirstName("John");
        user1.setLastName("Doe");
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, "id,asc");
        Page<User> userPage = new PageImpl<>(List.of(user1), PageRequest.of(0, 10), 1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.findByEmailContainingIgnoreCaseAndLastNameContainingIgnoreCase(
                "john", "doe", pageRequestMapper.toPageable(pageRequestDto)
        )).thenReturn(userPage);
        //when
        PageDto<UserResponse> result = userService.searchUsers("john", "doe", pageRequestDto);
        //then
        Mockito.verify(userRepository).findByEmailContainingIgnoreCaseAndLastNameContainingIgnoreCase(
                Mockito.eq("john"), Mockito.eq("doe"), pageableCaptor.capture()
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.content().size()),
                () -> Assertions.assertEquals(1L, result.content().getFirst().id()),
                () -> Assertions.assertEquals("john.doe@example.com", result.content().getFirst().email()),
                () -> Assertions.assertEquals(1, result.totalElements()),
                () -> Assertions.assertEquals(0, pageableCaptor.getValue().getPageNumber()),
                () -> Assertions.assertEquals(10, pageableCaptor.getValue().getPageSize())
        );
    }

    @Test
    void searchUsers_nullFilters_usesEmptyStrings() {
        //given
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, "id,asc");
        Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(userRepository.findByEmailContainingIgnoreCaseAndLastNameContainingIgnoreCase(
                Mockito.eq(""), Mockito.eq(""), any(Pageable.class)
        )).thenReturn(emptyPage);
        //when
        PageDto<UserResponse> result = userService.searchUsers(null, null, pageRequestDto);
        //then
        Mockito.verify(userRepository).findByEmailContainingIgnoreCaseAndLastNameContainingIgnoreCase(
                Mockito.eq(""), Mockito.eq(""), any(Pageable.class)
        );
        Assertions.assertEquals(0, result.content().size());
    }

    @Test
    void getUser_userExists_returnsUserResponse() {
        //given
        User user = new User();
        user.setId(1L);
        user.setEmail("john.doe@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        //when
        UserResponse response = userService.getUser(1L);
        //then
        Mockito.verify(userRepository).findById(idCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("john.doe@example.com", response.email()),
                () -> Assertions.assertEquals("John", response.firstName()),
                () -> Assertions.assertEquals("Doe", response.lastName()),
                () -> Assertions.assertEquals(1L, idCaptor.getValue())
        );
    }

    @Test
    void getUser_userNotExists_throwsUserNotFoundException() {
        //given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        UserNotFoundException ex = Assertions.assertThrows(
                UserNotFoundException.class,
                () -> userService.getUser(999L)
        );
        Assertions.assertEquals("User not found: id=999", ex.getMessage());
    }

    @Test
    void update_userExists_updatedUserReturned() {
        //given
        User user = new User();
        user.setId(1L);
        user.setEmail("john.doe@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        UpdateUserRequest request = new UpdateUserRequest("Johnny", "Doe-Updated");
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        //when
        UserResponse response = userService.update(1L, request);
        //then
        Mockito.verify(userRepository).findById(idCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("Johnny", response.firstName()),
                () -> Assertions.assertEquals("Doe-Updated", response.lastName()),
                () -> Assertions.assertEquals("Johnny", user.getFirstName()),
                () -> Assertions.assertEquals("Doe-Updated", user.getLastName()),
                () -> Assertions.assertEquals(1L, idCaptor.getValue())
        );
    }

    @Test
    void update_userNotExists_throwsUserNotFoundException() {
        //given
        UpdateUserRequest request = new UpdateUserRequest("Johnny", "Doe-Updated");
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        UserNotFoundException ex = Assertions.assertThrows(
                UserNotFoundException.class,
                () -> userService.update(999L, request)
        );
        Assertions.assertEquals("User not found: id=999", ex.getMessage());
    }

    @Test
    void delete_userExists_userDeactivated() {
        //given
        User user = new User();
        user.setId(1L);
        user.setDeactivated(false);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        //when
        userService.delete(1L);
        //then
        Mockito.verify(userRepository).findById(idCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertTrue(user.isDeactivated()),
                () -> Assertions.assertEquals(1L, idCaptor.getValue())
        );
    }

    @Test
    void delete_userNotExists_throwsUserNotFoundException() {
        //given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        UserNotFoundException ex = Assertions.assertThrows(
                UserNotFoundException.class,
                () -> userService.delete(999L)
        );
        Assertions.assertEquals("User not found: id=999", ex.getMessage());
    }

    @Test
    void joinProgram_dataCorrect_membershipSavedAndReturned() {
        //given
        User user = new User();
        user.setId(1L);
        user.setDeactivated(false);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(10L);
        program.setName("Gold Program");
        program.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().plusDays(10)));
        Membership savedMembership = new Membership();
        savedMembership.setId(100L);
        savedMembership.setUser(user);
        savedMembership.setProgram(program);
        savedMembership.setPointsBalance(0);
        ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserIdAndProgramId(1L, 10L)).thenReturn(false);
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(membershipRepository.save(any(Membership.class))).thenReturn(savedMembership);
        //when
        BalanceResponse response = userService.joinProgram(1L, 10L);
        //then
        Mockito.verify(membershipRepository).save(membershipCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(100L, response.membershipId()),
                () -> Assertions.assertEquals(1L, response.userId()),
                () -> Assertions.assertEquals(10L, response.programId()),
                () -> Assertions.assertEquals("Gold Program", response.programName()),
                () -> Assertions.assertEquals(0, response.pointsBalance()),
                () -> Assertions.assertEquals(user, membershipCaptor.getValue().getUser()),
                () -> Assertions.assertEquals(program, membershipCaptor.getValue().getProgram())
        );
    }

    @Test
    void joinProgram_userDeactivated_throwsConflictException() {
        //given
        User user = new User();
        LoyaltyProgram program = new LoyaltyProgram();
        user.setId(1L);
        user.setDeactivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        //when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> userService.joinProgram(1L, 10L)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("USER_DEACTIVATED", ex.getErrorCode()),
                () -> Assertions.assertEquals("User id=1 is deactivated", ex.getMessage())
        );
        Mockito.verify(membershipRepository, Mockito.never()).save(any());
    }

    @Test
    void joinProgram_membershipAlreadyExists_throwsMembershipAlreadyExistsException() {
        //given
        User user = new User();
        user.setId(1L);
        user.setDeactivated(false);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(programRepository.findById(10L)).thenReturn(Optional.of(program));
        when(membershipRepository.existsByUserIdAndProgramId(1L, 10L)).thenReturn(true);

        //when + then
        MembershipAlreadyExistsException ex = Assertions.assertThrows(
                MembershipAlreadyExistsException.class,
                () -> userService.joinProgram(1L, 10L)
        );
        Assertions.assertEquals("User id="+ 1L + " already belongs to program id=" + 10L, ex.getMessage());
        Mockito.verify(membershipRepository, Mockito.never()).save(any());
    }

    @Test
    void joinProgram_programExpired_throwsProgramExpiredException() {
        //given
        User user = new User();
        user.setId(1L);
        user.setDeactivated(false);
        LoyaltyProgram expiredProgram = new LoyaltyProgram();
        expiredProgram.setId(10L);
        expiredProgram.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().minusDays(10)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserIdAndProgramId(1L, 10L)).thenReturn(false);
        when(programRepository.findById(10L)).thenReturn(Optional.of(expiredProgram));
        //when + then
        ProgramExpiredException ex = Assertions.assertThrows(
                ProgramExpiredException.class,
                () -> userService.joinProgram(1L, 10L)
        );
        Assertions.assertEquals("Program is not active: id=10", ex.getMessage());
        Mockito.verify(membershipRepository, Mockito.never()).save(any());
    }

    @Test
    void leaveProgram_zeroBalance_membershipDeleted() {
        //given
        User user = new User();
        user.setId(1L);
        user.setMemberships(new ArrayList<>());
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(10L);
        program.setMemberships(new ArrayList<>());
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setPointsBalance(0);
        membership.setUser(user);
        membership.setProgram(program);
        user.getMemberships().add(membership);
        program.getMemberships().add(membership);
        ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
        when(membershipRepository.findByUserIdAndProgramId(1L, 10L)).thenReturn(Optional.of(membership));
        //when
        userService.leaveProgram(1L, 10L);
        //then
        Mockito.verify(membershipRepository).delete(membershipCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(membership, membershipCaptor.getValue()),
                () -> Assertions.assertFalse(user.getMemberships().contains(membership)),
                () -> Assertions.assertFalse(program.getMemberships().contains(membership))
        );
    }

    @Test
    void leaveProgram_membershipNotFound_throwsMembershipNotFoundException() {
        //given
        when(membershipRepository.findByUserIdAndProgramId(1L, 10L)).thenReturn(Optional.empty());
        //when + then
        MembershipNotFoundException ex = Assertions.assertThrows(
                MembershipNotFoundException.class,
                () -> userService.leaveProgram(1L, 10L)
        );
        Assertions.assertEquals("User id=" + 1L + " is not a member of program id=" + 10L, ex.getMessage());
        Mockito.verify(membershipRepository, Mockito.never()).delete(any());
    }
}