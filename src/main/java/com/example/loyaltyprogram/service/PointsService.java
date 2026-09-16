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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final EarningRuleRepository earningRuleRepository;
    private final PointsTransactionRepository transactionRepository;
    private final CampaignRepository campaignRepository;
    private final PointsTransactionMapper pointsTransactionMapper;
    private final PageRequestMapper pageRequestMapper;

    @Transactional
    public EarnPointsResponse earnPoints(Long userId, EarnPointsRequest request) {
        log.debug("Attempting to earn points for userId={}, referenceId={}", userId, request.referenceId());
        validatePayloadVariants(userId, request);

        User user = findActiveUser(userId);
        Membership membership = resolveMembership(user, request);
        LocalDateTime now = LocalDateTime.now();
        LoyaltyProgram program = membership.getProgram();

        validateProgramIsActive(program, userId, now);

        var existingResponse = transactionRepository
                .findByMembershipIdAndReferenceId(membership.getId(), request.referenceId())
                .map(transaction -> {
                    log.info("Duplicate earn request ignored (idempotent): membershipId={} referenceId={}",
                            membership.getId(), request.referenceId());
                    return pointsTransactionMapper.toEarnResponse(transaction);
                });

        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        EarningRule rule = resolveEarningRule(request, program, now);
        int finalPoints = calculateFinalPoints(rule, program, now);

        PointsTransaction savedTransaction = processTransactionWithIdempotencyFallback(
                membership, rule, finalPoints, request.referenceId(), now
        );

        log.info("User id={} earned {} points in program id={} (rule event={}, referenceId={})",
                userId, finalPoints, program.getId(), rule.getEventType(), request.referenceId());

        return pointsTransactionMapper.toEarnResponse(savedTransaction);
    }

    @Transactional(readOnly = true)
    public PageDto<PointsHistoryResponse> getHistory(Long userId, Long programId, PageRequestDto pageRequest) {
        log.debug("Fetching points history for userId={}, programId={}", userId, programId);
        Pageable pageable = pageRequestMapper.toPageable(pageRequest);

        Membership membership = membershipRepository.findByUserIdAndProgramId(userId, programId)
                .orElseThrow(() -> {
                    log.error("User id={} does not belong to program id={}", userId, programId);
                    return new ForbiddenOperationException(
                            "User id=" + userId + " does not belong to program id=" + programId);
                });

        return PageDto.from(transactionRepository
                .findByMembershipIdOrderByOccurredAtDesc(membership.getId(), pageable)
                .map(pointsTransactionMapper::toHistoryResponse));
    }

    private void validatePayloadVariants(Long userId, EarnPointsRequest request) {
        boolean variantA = request.eventType() != null && request.programId() != null;
        boolean variantB = request.earningRuleId() != null;

        if (variantA == variantB) {
            log.error("Invalid earn request payload for userId={}. Provide either (eventType and programId) or earningRuleId", userId);
            throw new InvalidRequestException(
                    "Provide either (eventType and programId) or earningRuleId, not both or neither");
        }
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("User not found for userId={}", userId);
                    return new UserNotFoundException(userId);
                });

        if (user.isDeactivated()) {
            log.error("Cannot earn points. User id={} is deactivated", user.getId());
            throw new ConflictException("USER_DEACTIVATED", "User id=" + user.getId() + " is deactivated");
        }
        return user;
    }

    private void validateProgramIsActive(LoyaltyProgram program, Long userId, LocalDateTime now) {
        if (!program.isActiveAt(now)) {
            log.error("Cannot earn points. Loyalty program id={} is inactive for userId={}", program.getId(), userId);
            throw new ProgramExpiredException(program.getId());
        }
    }

    private int calculateFinalPoints(EarningRule rule, LoyaltyProgram program, LocalDateTime now) {
        int basePoints = rule.getPoints();
        return applyCampaigns(basePoints, program, rule.getEventType(), now);
    }

    private PointsTransaction processTransactionWithIdempotencyFallback(
            Membership membership,
            EarningRule rule,
            int finalPoints,
            String referenceId,
            LocalDateTime now
    ) {
        PointsTransaction transaction = createTransactionObject(rule, finalPoints, referenceId, now);

        try {
            membership.addTransaction(transaction);
            PointsTransaction saved = transactionRepository.save(transaction);
            membership.setPointsBalance(membership.getPointsBalance() + finalPoints);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.info("Concurrent duplicate earn request detected (referenceId={}), returning existing transaction", referenceId);
            return transactionRepository
                    .findByMembershipIdAndReferenceId(membership.getId(), referenceId)
                    .orElseThrow(() -> ex);
        }
    }

    private PointsTransaction createTransactionObject(EarningRule rule, int finalPoints, String referenceId, LocalDateTime now) {
        PointsTransaction transaction = new PointsTransaction();
        transaction.setType(TransactionType.EARN);
        transaction.setPoints(finalPoints);
        transaction.setDescription("Earned via rule '" + rule.getEventType() + "' (ref=" + referenceId + ")");
        transaction.setReferenceId(referenceId);
        transaction.setOccurredAt(now);
        return transaction;
    }

    private Membership resolveMembership(User user, EarnPointsRequest request) {
        if (request.earningRuleId() != null) {
            return resolveMembershipByEarningRule(user, request.earningRuleId());
        }

        if (request.programId() != null) {
            return findMembership(user.getId(), request.programId());
        }

        return resolveSingleMembershipForUser(user.getId());
    }

    private Membership resolveMembershipByEarningRule(User user, Long earningRuleId) {
        EarningRule rule = earningRuleRepository.findById(earningRuleId)
                .orElseThrow(() -> {
                    log.error("Earning rule not found: id={}", earningRuleId);
                    return new ConflictException("EARNING_RULE_NOT_FOUND", "Earning rule not found: id=" + earningRuleId);
                });

        return findMembership(user.getId(), rule.getProgram().getId());
    }

    private Membership resolveSingleMembershipForUser(Long userId) {
        List<Membership> memberships = membershipRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            log.error("User id={} has no memberships in any program", userId);
            throw new ForbiddenOperationException("User id=" + userId + " has no memberships");
        }
        if (memberships.size() > 1) {
            log.error("Ambiguous program resolution: User id={} belongs to {} programs", userId, memberships.size());
            throw new InvalidRequestException("User belongs to multiple programs; programId must be specified");
        }
        return memberships.getFirst();
    }

    private Membership findMembership(Long userId, Long programId) {
        return membershipRepository.findByUserIdAndProgramId(userId, programId)
                .orElseThrow(() -> {
                    log.error("User id={} does not belong to program id={}", userId, programId);
                    return new ForbiddenOperationException(
                            "User id=" + userId + " does not belong to program id=" + programId);
                });
    }

    private EarningRule resolveEarningRule(EarnPointsRequest request, LoyaltyProgram program, LocalDateTime now) {
        if (request.earningRuleId() != null) {
            return findAndValidateEarningRuleById(request.earningRuleId(), program.getId(), now);
        }

        return findEarningRuleByEventType(program.getId(), request.eventType(), now);
    }

    private EarningRule findAndValidateEarningRuleById(Long earningRuleId, Long programId, LocalDateTime now) {
        EarningRule rule = earningRuleRepository.findById(earningRuleId)
                .orElseThrow(() -> {
                    log.warn("Earning rule not found: id={}", earningRuleId);
                    return new ConflictException("EARNING_RULE_NOT_FOUND", "Earning rule not found: id=" + earningRuleId);
                });

        if (rule.getPeriod() != null && !rule.getPeriod().isActiveAt(now)) {
            log.warn("Earning rule id={} is not active at present time", rule.getId());
            throw new NoEarningRuleException(programId, rule.getEventType().name());
        }
        return rule;
    }

    private EarningRule findEarningRuleByEventType(Long programId, EarningEventType eventType, LocalDateTime now) {
        return earningRuleRepository.findByProgramIdAndEventType(programId, eventType).stream()
                .filter(r -> r.getPeriod() == null || r.getPeriod().isActiveAt(now))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("No active earning rule found for programId={} and eventType={}", programId, eventType);
                    return new NoEarningRuleException(programId, eventType.name());
                });
    }

    private int applyCampaigns(int basePoints, LoyaltyProgram program, EarningEventType eventType, LocalDateTime now) {
        List<Campaign> activeCampaigns = campaignRepository.findActiveCampaigns(eventType, program.getId(), now);
        if (activeCampaigns.isEmpty()) {
            return basePoints;
        }

        BigDecimal totalMultiplier = activeCampaigns.stream()
                .map(Campaign::getMultiplier)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(BigDecimal.valueOf(activeCampaigns.size() - 1));

        int calculatedPoints = BigDecimal.valueOf(basePoints)
                .multiply(totalMultiplier)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        log.debug("Applied {} active campaigns for programId={}. Base points: {}, Multiplied points: {}",
                activeCampaigns.size(), program.getId(), basePoints, calculatedPoints);
        return calculatedPoints;
    }
}