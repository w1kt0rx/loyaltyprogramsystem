package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.request.CreateEarningRuleRequest;
import com.example.loyaltyprogram.dto.request.UpdateEarningRuleRequest;
import com.example.loyaltyprogram.dto.response.EarningRuleResponse;
import com.example.loyaltyprogram.exception.ConflictException;
import com.example.loyaltyprogram.exception.EarningRuleNotFoundException;
import com.example.loyaltyprogram.exception.ProgramExpiredException;
import com.example.loyaltyprogram.exception.ProgramNotFoundException;
import com.example.loyaltyprogram.mapper.EarningRuleMapper;
import com.example.loyaltyprogram.model.EarningEventType;
import com.example.loyaltyprogram.model.EarningRule;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.repository.EarningRuleRepository;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EarningRuleServiceTest {
    private EarningRuleService earningRuleService;
    private EarningRuleRepository earningRuleRepository;
    private LoyaltyProgramRepository programRepository;
    private EarningRuleMapper earningRuleMapper;

    @BeforeEach
    void setup() {
        this.earningRuleRepository = Mockito.mock(EarningRuleRepository.class);
        this.programRepository = Mockito.mock(LoyaltyProgramRepository.class);
        this.earningRuleMapper = Mappers.getMapper(EarningRuleMapper.class);
        this.earningRuleService = new EarningRuleService(
                earningRuleRepository,
                programRepository,
                earningRuleMapper
        );
    }

    @Test
    void createRule_dataCorrect_ruleCreated() {
        // given
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(30);
        CreateEarningRuleRequest request = new CreateEarningRuleRequest(EarningEventType.PURCHASE, 100, start, end);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setPeriod(new Period(LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(60)));
        EarningRule savedRule = new EarningRule();
        savedRule.setId(10L);
        savedRule.setEventType(EarningEventType.PURCHASE);
        savedRule.setPoints(100);
        savedRule.setPeriod(new Period(start, end));
        savedRule.setProgram(program);
        ArgumentCaptor<EarningRule> ruleCaptor = ArgumentCaptor.forClass(EarningRule.class);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE)).thenReturn(List.of());
        when(earningRuleRepository.save(any(EarningRule.class))).thenReturn(savedRule);
        // when
        EarningRuleResponse response = earningRuleService.createRule(1L, request);
        // then
        Mockito.verify(earningRuleRepository).save(ruleCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals(EarningEventType.PURCHASE, response.eventType()),
                () -> Assertions.assertEquals(100, response.points()),
                () -> Assertions.assertEquals(EarningEventType.PURCHASE, ruleCaptor.getValue().getEventType()),
                () -> Assertions.assertEquals(100, ruleCaptor.getValue().getPoints()),
                () -> Assertions.assertEquals(start, ruleCaptor.getValue().getPeriod().getStartDate()),
                () -> Assertions.assertEquals(end, ruleCaptor.getValue().getPeriod().getEndDate()),
                () -> Assertions.assertEquals(program, ruleCaptor.getValue().getProgram())
        );
    }

    @Test
    void createRule_programNotExists_throwsProgramNotFoundException() {
        // given
        CreateEarningRuleRequest request = new CreateEarningRuleRequest(EarningEventType.PURCHASE, 100, LocalDateTime.now(), null);
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> earningRuleService.createRule(999L, request)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(earningRuleRepository, Mockito.never()).save(any());
    }

    @Test
    void createRule_programExpired_throwsProgramExpiredException() {
        // given
        CreateEarningRuleRequest request = new CreateEarningRuleRequest(EarningEventType.PURCHASE, 100, LocalDateTime.now(), null);
        LoyaltyProgram expiredProgram = new LoyaltyProgram();
        expiredProgram.setId(1L);
        expiredProgram.setPeriod(new Period(LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(10)));
        when(programRepository.findById(1L)).thenReturn(Optional.of(expiredProgram));
        // when + then
        ProgramExpiredException ex = Assertions.assertThrows(
                ProgramExpiredException.class,
                () -> earningRuleService.createRule(1L, request)
        );
        Assertions.assertEquals("Program is not active: id=1", ex.getMessage());
        Mockito.verify(earningRuleRepository, Mockito.never()).save(any());
    }

    @Test
    void createRule_overlappingPeriod_throwsConflictException() {
        // given
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        CreateEarningRuleRequest request = new CreateEarningRuleRequest(EarningEventType.PURCHASE, 100, start, end);
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setPeriod(new Period(LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(60)));
        EarningRule existingRule = new EarningRule();
        existingRule.setId(5L);
        existingRule.setEventType(EarningEventType.PURCHASE);
        existingRule.setPeriod(new Period(start.minusDays(2), end.plusDays(2)));
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE))
                .thenReturn(List.of(existingRule));
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> earningRuleService.createRule(1L, request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("EARNING_RULE_OVERLAP", ex.getErrorCode()),
                () -> Assertions.assertEquals("An active earning rule for event PURCHASE already exists in an overlapping period", ex.getMessage())
        );
        Mockito.verify(earningRuleRepository, Mockito.never()).save(any());
    }

    @Test
    void listRules_programExists_returnsRulesList() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        EarningRule rule1 = new EarningRule();
        rule1.setId(10L);
        rule1.setEventType(EarningEventType.PURCHASE);
        EarningRule rule2 = new EarningRule();
        rule2.setId(20L);
        rule2.setEventType(EarningEventType.SIGN_UP);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(earningRuleRepository.findByProgramId(1L)).thenReturn(List.of(rule1, rule2));
        // when
        List<EarningRuleResponse> responses = earningRuleService.listRules(1L);
        // then
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, responses.size()),
                () -> Assertions.assertEquals(10L, responses.getFirst().id()),
                () -> Assertions.assertEquals(EarningEventType.PURCHASE, responses.getFirst().eventType()),
                () -> Assertions.assertEquals(20L, responses.get(1).id()),
                () -> Assertions.assertEquals(EarningEventType.SIGN_UP, responses.get(1).eventType())
        );
    }

    @Test
    void listRules_programNotExists_throwsProgramNotFoundException() {
        // given
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> earningRuleService.listRules(999L)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(earningRuleRepository, Mockito.never()).findByProgramId(any());
    }

    @Test
    void getEarningRule_ruleExists_returnsRuleResponse() {
        // given
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setEventType(EarningEventType.PURCHASE);
        rule.setPoints(150);
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        // when
        EarningRuleResponse response = earningRuleService.getEarningRule(10L);
        // then
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals(EarningEventType.PURCHASE, response.eventType()),
                () -> Assertions.assertEquals(150, response.points())
        );
    }

    @Test
    void getEarningRule_ruleNotExists_throwsEarningRuleNotFoundException() {
        // given
        when(earningRuleRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        EarningRuleNotFoundException ex = Assertions.assertThrows(
                EarningRuleNotFoundException.class,
                () -> earningRuleService.getEarningRule(999L)
        );
        Assertions.assertEquals("Earning rule not found: id=999", ex.getMessage());
    }

    @Test
    void updateEarningRule_noOverlap_updatesSuccessfully() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        LocalDateTime now = LocalDateTime.now();
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setEventType(EarningEventType.PURCHASE);
        rule.setPoints(50);
        rule.setPeriod(new Period(now, now.plusDays(10)));
        rule.setProgram(program);
        UpdateEarningRuleRequest request = new UpdateEarningRuleRequest(null, 100, now.plusDays(20), now.plusDays(30));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE)).thenReturn(List.of(rule));
        // when
        EarningRuleResponse response = earningRuleService.updateEarningRule(10L, request);
        // then
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals(100, response.points()),
                () -> Assertions.assertEquals(100, rule.getPoints()),
                () -> Assertions.assertEquals(request.startDate(), rule.getPeriod().getStartDate()),
                () -> Assertions.assertEquals(request.endDate(), rule.getPeriod().getEndDate())
        );
    }

    @Test
    void updateEarningRule_overlapsWithAnotherRule_throwsConflictException() {
        // given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        LocalDateTime now = LocalDateTime.now();
        EarningRule ruleToUpdate = new EarningRule();
        ruleToUpdate.setId(10L);
        ruleToUpdate.setEventType(EarningEventType.PURCHASE);
        ruleToUpdate.setPeriod(new Period(now, now.plusDays(5)));
        ruleToUpdate.setProgram(program);
        EarningRule otherRule = new EarningRule();
        otherRule.setId(20L);
        otherRule.setEventType(EarningEventType.PURCHASE);
        otherRule.setPeriod(new Period(now.plusDays(10), now.plusDays(20)));
        otherRule.setProgram(program);
        UpdateEarningRuleRequest request = new UpdateEarningRuleRequest(null, 100, now.plusDays(8), now.plusDays(15));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(ruleToUpdate));
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE))
                .thenReturn(List.of(ruleToUpdate, otherRule));
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> earningRuleService.updateEarningRule(10L, request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("EARNING_RULE_OVERLAP", ex.getErrorCode()),
                () -> Assertions.assertEquals("An active earning rule for event PURCHASE already exists in an overlapping period", ex.getMessage())
        );
    }

    @Test
    void updateEarningRule_ruleNotExists_throwsEarningRuleNotFoundException() {
        // given
        UpdateEarningRuleRequest request = new UpdateEarningRuleRequest(null, 100, LocalDateTime.now(), null);
        when(earningRuleRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        EarningRuleNotFoundException ex = Assertions.assertThrows(
                EarningRuleNotFoundException.class,
                () -> earningRuleService.updateEarningRule(999L, request)
        );
        Assertions.assertEquals("Earning rule not found: id=999", ex.getMessage());
    }

    @Test
    void delete_ruleExists_deletesRule() {
        // given
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        ArgumentCaptor<EarningRule> ruleCaptor = ArgumentCaptor.forClass(EarningRule.class);
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        // when
        earningRuleService.delete(10L);
        // then
        Mockito.verify(earningRuleRepository).delete(ruleCaptor.capture());
        Assertions.assertEquals(rule, ruleCaptor.getValue());
    }

    @Test
    void delete_ruleNotExists_throwsEarningRuleNotFoundException() {
        // given
        when(earningRuleRepository.findById(999L)).thenReturn(Optional.empty());
        // when + then
        EarningRuleNotFoundException ex = Assertions.assertThrows(
                EarningRuleNotFoundException.class,
                () -> earningRuleService.delete(999L)
        );
        Assertions.assertEquals("Earning rule not found: id=999", ex.getMessage());
        Mockito.verify(earningRuleRepository, Mockito.never()).delete(any());
    }
}