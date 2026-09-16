package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateProgramRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateProgramRequest;
import com.example.loyaltyprogram.dto.response.ProgramResponse;
import com.example.loyaltyprogram.exception.ConflictException;
import com.example.loyaltyprogram.exception.InvalidRequestException;
import com.example.loyaltyprogram.exception.ProgramNotFoundException;
import com.example.loyaltyprogram.mapper.LoyaltyProgramMapper;
import com.example.loyaltyprogram.mapper.MembershipMapper;
import com.example.loyaltyprogram.mapper.PageRequestMapper;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Membership;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import com.example.loyaltyprogram.repository.MembershipRepository;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ProgramServiceTest {

    private ProgramService programService;
    private LoyaltyProgramRepository programRepository;
    private MembershipRepository membershipRepository;
    private LoyaltyProgramMapper programMapper;
    private PageRequestMapper pageRequestMapper;
    private MembershipMapper membershipMapper;

    @BeforeEach
    void setup() {
        this.programRepository = Mockito.mock(LoyaltyProgramRepository.class);
        this.membershipRepository = Mockito.mock(MembershipRepository.class);
        this.programMapper = Mappers.getMapper(LoyaltyProgramMapper.class);
        this.pageRequestMapper = Mappers.getMapper(PageRequestMapper.class);
        this.membershipMapper = Mappers.getMapper(MembershipMapper.class);

        this.programService = new ProgramService(
                programRepository,
                membershipRepository,
                programMapper,
                pageRequestMapper,
                membershipMapper
        );
    }

    @Test
    void createProgram_dataCorrect_programCreated() {
        // given
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusMonths(1);
        CreateProgramRequest request = new CreateProgramRequest("Gold Loyalty", "Gold tier description", start, end);
        LoyaltyProgram savedProgram = new LoyaltyProgram();
        savedProgram.setId(1L);
        savedProgram.setName("Gold Loyalty");
        savedProgram.setDescription("Gold tier description");
        savedProgram.setPeriod(new Period(start, end));
        ArgumentCaptor<LoyaltyProgram> programCaptor = ArgumentCaptor.forClass(LoyaltyProgram.class);
        when(programRepository.existsByName("Gold Loyalty")).thenReturn(false);
        when(programRepository.save(any(LoyaltyProgram.class))).thenReturn(savedProgram);
        // when
        ProgramResponse response = programService.createProgram(request);
        // then
        Mockito.verify(programRepository).save(programCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("Gold Loyalty", response.name()),
                () -> Assertions.assertEquals("Gold Loyalty", programCaptor.getValue().getName()),
                () -> Assertions.assertEquals("Gold tier description", programCaptor.getValue().getDescription()),
                () -> Assertions.assertEquals(start, programCaptor.getValue().getPeriod().getStartDate()),
                () -> Assertions.assertEquals(end, programCaptor.getValue().getPeriod().getEndDate())
        );
    }

    @Test
    void createProgram_nameAlreadyExists_throwsConflictException() {
        // given
        CreateProgramRequest request = new CreateProgramRequest("Existing Program", "Description", LocalDateTime.now(), null);
        when(programRepository.existsByName("Existing Program")).thenReturn(true);
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> programService.createProgram(request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("PROGRAM_NAME_TAKEN", ex.getErrorCode()),
                () -> Assertions.assertEquals("Program name already in use: Existing Program", ex.getMessage())
        );
        Mockito.verify(programRepository, Mockito.never()).save(any());
    }

    @Test
    void listPrograms_activeTrue_callsFindActiveAt() {
        // given
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, "id,asc");
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Active Program");
        Page<LoyaltyProgram> page = new PageImpl<>(List.of(program), PageRequest.of(0, 10), 1);
        when(programRepository.findActiveAt(any(LocalDateTime.class), any(Pageable.class))).thenReturn(page);
        // when
        PageDto<ProgramResponse> result = programService.listPrograms(true, false, pageRequestDto);
        // then
        Mockito.verify(programRepository).findActiveAt(any(LocalDateTime.class), any(Pageable.class));
        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.content().size()),
                () -> Assertions.assertEquals("Active Program", result.content().getFirst().name())
        );
    }

    @Test
    void listPrograms_activeFalseAndIncludeExpiredTrue_callsFindAll() {
        // given
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, "id,asc");
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Any Program");
        Page<LoyaltyProgram> page = new PageImpl<>(List.of(program), PageRequest.of(0, 10), 1);
        when(programRepository.findAll(any(Pageable.class))).thenReturn(page);
        // when
        PageDto<ProgramResponse> result = programService.listPrograms(false, true, pageRequestDto);
        // then
        Mockito.verify(programRepository).findAll(any(Pageable.class));
        Assertions.assertEquals(1, result.content().size());
    }

    @Test
    void listPrograms_activeNullAndIncludeExpiredFalse_callsFindNotExpired() {
        // given
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, "id,asc");
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Not Expired Program");
        Page<LoyaltyProgram> page = new PageImpl<>(List.of(program), PageRequest.of(0, 10), 1);
        when(programRepository.findNotExpired(any(LocalDateTime.class), any(Pageable.class))).thenReturn(page);
        // when
        PageDto<ProgramResponse> result = programService.listPrograms(null, false, pageRequestDto);
        // then
        Mockito.verify(programRepository).findNotExpired(any(LocalDateTime.class), any(Pageable.class));
        Assertions.assertEquals(1, result.content().size());
    }

    @Test
    void getProgram_programExists_returnsProgramResponse() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Gold Program");
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        // when
        ProgramResponse response = programService.getProgram(1L);
        // thn
        Assertions.assertAll(
                () -> Assertions.assertEquals(1L, response.id()),
                () -> Assertions.assertEquals("Gold Program", response.name())
        );
    }

    @Test
    void getProgram_programNotExists_throwsProgramNotFoundException() {
        // given
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> programService.getProgram(999L)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
    }

    @Test
    void updateProgram_sameName_updatesSuccessfully() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Gold Program");
        program.setDescription("Old Description");
        UpdateProgramRequest request = new UpdateProgramRequest("Gold Program", "Updated Description");
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        // when
        ProgramResponse response = programService.updateProgram(1L, request);
        // then
        Assertions.assertEquals("Gold Program", response.name());
        Mockito.verify(programRepository, Mockito.never()).existsByName(any());
    }

    @Test
    void updateProgram_newNameNotTaken_updatesSuccessfully() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Old Name");
        program.setDescription("Old Description");
        UpdateProgramRequest request = new UpdateProgramRequest("New Name", "New Description");
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(programRepository.existsByName("New Name")).thenReturn(false);
        // when
        ProgramResponse response = programService.updateProgram(1L, request);
        // then
        Assertions.assertEquals("New Name", response.name());
    }

    @Test
    void updateProgram_newNameAlreadyTaken_throwsConflictException() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setName("Old Name");
        UpdateProgramRequest request = new UpdateProgramRequest("Occupied Name", "New Description");
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(programRepository.existsByName("Occupied Name")).thenReturn(true);
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> programService.updateProgram(1L, request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("PROGRAM_NAME_TAKEN", ex.getErrorCode()),
                () -> Assertions.assertEquals("Program name already in use: Occupied Name", ex.getMessage())
        );
    }

    @Test
    void updateProgram_programNotExists_throwsProgramNotFoundException() {
        // given
        UpdateProgramRequest request = new UpdateProgramRequest("New Name", "New Description");
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> programService.updateProgram(999L, request)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
    }

    @Test
    void deleteProgram_noMemberships_deletesProgram() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(membershipRepository.findByProgramId(1L)).thenReturn(List.of());
        // when
        programService.deleteProgram(1L);
        // then
        Mockito.verify(programRepository).delete(program);
    }

    @Test
    void deleteProgram_hasActiveMemberships_throwsConflictException() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(membershipRepository.findByProgramId(1L)).thenReturn(List.of(new Membership()));
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> programService.deleteProgram(1L)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("PROGRAM_HAS_MEMBERSHIPS", ex.getErrorCode()),
                () -> Assertions.assertEquals("Cannot delete program with active memberships", ex.getMessage())
        );
        Mockito.verify(programRepository, Mockito.never()).delete(any());
    }

    @Test
    void deleteProgram_programNotExists_throwsProgramNotFoundException() {
        // given
        when(programRepository.findById(999L)).thenReturn(Optional.empty());

        // when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> programService.deleteProgram(999L)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(programRepository, Mockito.never()).delete(any());
    }
}