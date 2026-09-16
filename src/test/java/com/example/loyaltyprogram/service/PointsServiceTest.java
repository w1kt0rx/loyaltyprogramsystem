package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.EarnPointsRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.response.EarnPointsResponse;
import com.example.loyaltyprogram.dto.response.PointsHistoryResponse;
import com.example.loyaltyprogram.exception.*;
import com.example.loyaltyprogram.mapper.PageRequestMapper;
import com.example.loyaltyprogram.mapper.PointsTransactionMapper;
import com.example.loyaltyprogram.model.*;
import com.example.loyaltyprogram.repository.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class PointsServiceTest {
    private PointsService pointsService;
    private UserRepository userRepository;
    private MembershipRepository membershipRepository;
    private EarningRuleRepository earningRuleRepository;
    private PointsTransactionRepository transactionRepository;
    private CampaignRepository campaignRepository;
    private PointsTransactionMapper pointsTransactionMapper;
    private PageRequestMapper pageRequestMapper;

    @BeforeEach
    void setup() {
        this.userRepository = Mockito.mock(UserRepository.class);
        this.membershipRepository = Mockito.mock(MembershipRepository.class);
        this.earningRuleRepository = Mockito.mock(EarningRuleRepository.class);
        this.transactionRepository = Mockito.mock(PointsTransactionRepository.class);
        this.campaignRepository = Mockito.mock(CampaignRepository.class);
        this.pointsTransactionMapper = Mappers.getMapper(PointsTransactionMapper.class);
        this.pageRequestMapper = Mockito.mock(PageRequestMapper.class);
        this.pointsService = new PointsService(
                userRepository,
                membershipRepository,
                earningRuleRepository,
                transactionRepository,
                campaignRepository,
                pointsTransactionMapper,
                pageRequestMapper
        );
    }

    @Test
    void earnPoints_bothVariantsProvided_throwsInvalidRequestException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, 10L, "REF-123");
        // when + then
        InvalidRequestException ex = Assertions.assertThrows(
                InvalidRequestException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("Provide either (eventType and programId) or earningRuleId, not both or neither", ex.getMessage());
    }

    @Test
    void earnPoints_neitherVariantProvided_throwsInvalidRequestException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(null, null, null, "REF-123");
        // when + then
        InvalidRequestException ex = Assertions.assertThrows(
                InvalidRequestException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("Provide either (eventType and programId) or earningRuleId, not both or neither", ex.getMessage());
    }

    @Test
    void earnPoints_userNotFound_throwsUserNotFoundException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        // when + then
        UserNotFoundException ex = Assertions.assertThrows(
                UserNotFoundException.class,
                () -> pointsService.earnPoints(99L, request)
        );
        Assertions.assertEquals("User not found: id=99", ex.getMessage());
    }

    @Test
    void earnPoints_userDeactivated_throwsConflictException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        User user = new User();
        user.setId(1L);
        user.setDeactivated(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("USER_DEACTIVATED", ex.getErrorCode()),
                () -> Assertions.assertEquals("User id=1 is deactivated", ex.getMessage())
        );
    }

    @Test
    void earnPoints_resolveMembershipByEarningRuleId_ruleNotFound_throwsConflictException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(null, null, 10L, "REF-123");
        User user = createActiveUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.empty());
        // when + then
        ConflictException ex = Assertions.assertThrows(
                ConflictException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals("EARNING_RULE_NOT_FOUND", ex.getErrorCode()),
                () -> Assertions.assertEquals("Earning rule not found: id=10", ex.getMessage())
        );
    }

    @Test
    void earnPoints_resolveMembershipByEarningRuleId_userNotInProgram_throwsForbiddenOperationException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(null, null, 10L, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(5L);
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setProgram(program);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(membershipRepository.findByUserIdAndProgramId(1L, 5L)).thenReturn(Optional.empty());
        // when + then
        ForbiddenOperationException ex = Assertions.assertThrows(
                ForbiddenOperationException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("User id=1 does not belong to program id=5", ex.getMessage());
    }

    @Test
    void earnPoints_resolveMembershipByProgramId_userNotInProgram_throwsForbiddenOperationException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 5L, null, "REF-123");
        User user = createActiveUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProgramId(1L, 5L)).thenReturn(Optional.empty());
        // when + then
        ForbiddenOperationException ex = Assertions.assertThrows(
                ForbiddenOperationException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("User id=1 does not belong to program id=5", ex.getMessage());
    }

    @Test
    void earnPoints_programExpired_throwsProgramExpiredException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram expiredProgram = new LoyaltyProgram();
        expiredProgram.setId(1L);
        expiredProgram.setPeriod(new Period(LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(1)));
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setProgram(expiredProgram);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        // when + then
        ProgramExpiredException ex = Assertions.assertThrows(
                ProgramExpiredException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("Program is not active: id=1", ex.getMessage());
    }

    @Test
    void earnPoints_duplicateRequest_returnsExistingTransactionIdempotently() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(1L);
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setProgram(program);
        PointsTransaction existingTx = new PointsTransaction();
        existingTx.setId(500L);
        existingTx.setPoints(100);
        existingTx.setReferenceId("REF-123");
        existingTx.setType(TransactionType.EARN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdAndReferenceId(100L, "REF-123"))
                .thenReturn(Optional.of(existingTx));
        // when
        EarnPointsResponse response = pointsService.earnPoints(1L, request);
        // then
        Assertions.assertEquals(500L, response.transactionId());
        Assertions.assertEquals(100, response.pointsEarned());
        Assertions.assertEquals("REF-123", response.referenceId());
        Mockito.verify(transactionRepository, Mockito.never()).save(any());
    }

    @Test
    void earnPoints_byEarningRuleId_rulePeriodInactive_throwsNoEarningRuleException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(null, null, 10L, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(1L);
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setProgram(program);
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setEventType(EarningEventType.PURCHASE);
        rule.setProgram(program);
        rule.setPeriod(new Period(LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdAndReferenceId(100L, "REF-123")).thenReturn(Optional.empty());
        // when + then
        NoEarningRuleException ex = Assertions.assertThrows(
                NoEarningRuleException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("No active earning rule for program id=1 and event=PURCHASE", ex.getMessage());
    }

    @Test
    void earnPoints_byEventType_noMatchingRule_throwsNoEarningRuleException() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(1L);
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setProgram(program);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdAndReferenceId(100L, "REF-123")).thenReturn(Optional.empty());
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE)).thenReturn(List.of());
        // when + then
        NoEarningRuleException ex = Assertions.assertThrows(
                NoEarningRuleException.class,
                () -> pointsService.earnPoints(1L, request)
        );
        Assertions.assertEquals("No active earning rule for program id=1 and event=PURCHASE", ex.getMessage());
    }

    @Test
    void earnPoints_variantA_withoutCampaigns_pointsEarnedAndBalanceUpdated() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(EarningEventType.PURCHASE, 1L, null, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(1L);
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setPointsBalance(50);
        membership.setProgram(program);
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setPoints(100);
        rule.setEventType(EarningEventType.PURCHASE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdAndReferenceId(100L, "REF-123")).thenReturn(Optional.empty());
        when(earningRuleRepository.findByProgramIdAndEventType(1L, EarningEventType.PURCHASE)).thenReturn(List.of(rule));
        when(campaignRepository.findActiveCampaigns(eq(EarningEventType.PURCHASE), eq(1L), any())).thenReturn(List.of());
        when(transactionRepository.save(any(PointsTransaction.class))).thenAnswer(invocation -> {
            PointsTransaction tx = invocation.getArgument(0);
            tx.setId(1000L);
            return tx;
        });
        // when
        EarnPointsResponse response = pointsService.earnPoints(1L, request);
        // then
        ArgumentCaptor<PointsTransaction> txCaptor = ArgumentCaptor.forClass(PointsTransaction.class);
        Mockito.verify(transactionRepository).save(txCaptor.capture());
        PointsTransaction savedTx = txCaptor.getValue();
        Assertions.assertAll(
                () -> Assertions.assertEquals(1000L, response.transactionId()),
                () -> Assertions.assertEquals(100, response.pointsEarned()),
                () -> Assertions.assertEquals(150, response.newBalance()),
                () -> Assertions.assertEquals(150, membership.getPointsBalance()),
                () -> Assertions.assertEquals(TransactionType.EARN, savedTx.getType()),
                () -> Assertions.assertEquals(100, savedTx.getPoints()),
                () -> Assertions.assertEquals("Earned via rule 'PURCHASE' (ref=REF-123)", savedTx.getDescription()),
                () -> Assertions.assertEquals("REF-123", savedTx.getReferenceId())
        );
    }

    @Test
    void earnPoints_withMultipleActiveCampaigns_multipliersAppliedCorrectly() {
        // given
        EarnPointsRequest request = new EarnPointsRequest(null, null, 10L, "REF-123");
        User user = createActiveUser(1L);
        LoyaltyProgram program = createActiveProgram(1L);
        Membership membership = new Membership();
        membership.setId(100L);
        membership.setPointsBalance(0);
        membership.setProgram(program);
        EarningRule rule = new EarningRule();
        rule.setId(10L);
        rule.setPoints(100);
        rule.setEventType(EarningEventType.PURCHASE);
        rule.setProgram(program);
        Campaign c1 = new Campaign();
        c1.setMultiplier(BigDecimal.valueOf(2.0));
        Campaign c2 = new Campaign();
        c2.setMultiplier(BigDecimal.valueOf(1.5));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(earningRuleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(membershipRepository.findByUserIdAndProgramId(1L, 1L)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdAndReferenceId(100L, "REF-123")).thenReturn(Optional.empty());
        when(campaignRepository.findActiveCampaigns(eq(EarningEventType.PURCHASE), eq(1L), any()))
                .thenReturn(List.of(c1, c2));
        when(transactionRepository.save(any(PointsTransaction.class))).thenAnswer(i -> {
            PointsTransaction tx = i.getArgument(0);
            tx.setId(1000L);
            return tx;
        });
        // when
        EarnPointsResponse response = pointsService.earnPoints(1L, request);
        // then
        Assertions.assertAll(
                () -> Assertions.assertEquals(250, response.pointsEarned()),
                () -> Assertions.assertEquals(250, response.newBalance()),
                () -> Assertions.assertEquals(250, membership.getPointsBalance())
        );
    }

    @Test
    void getHistory_userInProgram_returnsPagedTransactionsHistory() {
        // given
        Long userId = 1L;
        Long programId = 10L;
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, null);
        Pageable pageable = PageRequest.of(0, 10);
        Membership membership = new Membership();
        membership.setId(100L);
        PointsTransaction tx1 = new PointsTransaction();
        tx1.setId(1L);
        tx1.setType(TransactionType.EARN);
        tx1.setPoints(100);
        tx1.setOccurredAt(LocalDateTime.now());
        PointsTransaction tx2 = new PointsTransaction();
        tx2.setId(2L);
        tx2.setType(TransactionType.REDEEM);
        tx2.setPoints(50);
        tx2.setOccurredAt(LocalDateTime.now().minusHours(1));
        when(pageRequestMapper.toPageable(pageRequestDto)).thenReturn(pageable);
        when(membershipRepository.findByUserIdAndProgramId(userId, programId)).thenReturn(Optional.of(membership));
        when(transactionRepository.findByMembershipIdOrderByOccurredAtDesc(100L, pageable))
                .thenReturn(new PageImpl<>(List.of(tx1, tx2), pageable, 2));
        // when
        PageDto<PointsHistoryResponse> history = pointsService.getHistory(userId, programId, pageRequestDto);
        // then
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, history.content().size()),
                () -> Assertions.assertEquals(1L, history.content().getFirst().id()),
                () -> Assertions.assertEquals(100, history.content().getFirst().points()),
                () -> Assertions.assertEquals(2L, history.content().get(1).id()),
                () -> Assertions.assertEquals(50, history.content().get(1).points())
        );
    }

    @Test
    void getHistory_userNotInProgram_throwsForbiddenOperationException() {
        // given
        Long userId = 1L;
        Long programId = 10L;
        PageRequestDto pageRequestDto = new PageRequestDto(0, 10, null);
        when(pageRequestMapper.toPageable(pageRequestDto)).thenReturn(PageRequest.of(0, 10));
        when(membershipRepository.findByUserIdAndProgramId(userId, programId)).thenReturn(Optional.empty());
        // when + then
        ForbiddenOperationException ex = Assertions.assertThrows(
                ForbiddenOperationException.class,
                () -> pointsService.getHistory(userId, programId, pageRequestDto)
        );
        Assertions.assertEquals("User id=1 does not belong to program id=10", ex.getMessage());
    }

    private User createActiveUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setDeactivated(false);
        return user;
    }

    private LoyaltyProgram createActiveProgram(Long id) {
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(id);
        program.setPeriod(new Period(LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(30)));
        return program;
    }
}