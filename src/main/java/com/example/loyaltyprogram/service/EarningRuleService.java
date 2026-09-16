package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.request.CreateEarningRuleRequest;
import com.example.loyaltyprogram.dto.request.UpdateEarningRuleRequest;
import com.example.loyaltyprogram.dto.response.EarningRuleResponse;
import com.example.loyaltyprogram.exception.*;
import com.example.loyaltyprogram.mapper.EarningRuleMapper;
import com.example.loyaltyprogram.model.EarningEventType;
import com.example.loyaltyprogram.model.EarningRule;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.repository.EarningRuleRepository;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import com.example.loyaltyprogram.validation.Validate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EarningRuleService {
    private final EarningRuleRepository earningRuleRepository;
    private final LoyaltyProgramRepository programRepository;
    private final EarningRuleMapper earningRuleMapper;

    @Transactional
    public EarningRuleResponse createRule(Long programId, CreateEarningRuleRequest request) {
        log.debug("Attempting to create earning rule for programId={}, eventType={}", programId, request.eventType());
        LoyaltyProgram program = findProgramById(programId);

        if (!program.isActiveAt(LocalDateTime.now())) {
            log.error("Cannot create earning rule. Program programId={} is expired or inactive", programId);
            throw new ProgramExpiredException(program.getId());
        }

        Period newPeriod = new Period(request.startDate(), request.endDate());
        checkNoOverlap(programId, request.eventType(), newPeriod, null);
        EarningRule earningRule = earningRuleMapper.toEntity(request);
        earningRule.setPeriod(newPeriod);
        program.addEarningRule(earningRule);
        EarningRule saved = earningRuleRepository.save(earningRule);
        log.info("Earning rule created successfully with id={} for programId={} and eventType={}",
                saved.getId(), programId, request.eventType());
        return earningRuleMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EarningRuleResponse> listRules(Long programId) {
        log.debug("Listing earning rules for programId={}", programId);
        findProgramById(programId);
        List<EarningRuleResponse> rules = earningRuleRepository.findByProgramId(programId).stream()
                .map(earningRuleMapper::toResponse)
                .toList();
        log.debug("Found {} earning rules for programId={}", rules.size(), programId);
        return rules;
    }

    @Transactional(readOnly = true)
    public EarningRuleResponse getEarningRule(Long earningRuleId) {
        log.debug("Fetching earning rule for earningRuleId={}", earningRuleId);
        return earningRuleMapper.toResponse(findEarningRuleById(earningRuleId));
    }

    @Transactional
    public EarningRuleResponse updateEarningRule(Long earningRuleId, UpdateEarningRuleRequest request) {
        log.debug("Attempting to update earning rule earningRuleId={}", earningRuleId);
        EarningRule earningRule = findEarningRuleById(earningRuleId);

        if (request.programId() != null && !request.programId().equals(earningRule.getProgram().getId())) {
            throw new FieldValidationException("programId", "cannot be changed after creation", request.programId());
        }

        Period newPeriod = new Period(request.startDate(), request.endDate());
        checkNoOverlap(earningRule.getProgram().getId(), earningRule.getEventType(), newPeriod, earningRule.getId());
        EarningRule updatedRule = earningRule.applyUpdate(request);
        log.info("Earning rule updated successfully for earningRuleId={}", earningRuleId);
        return earningRuleMapper.toResponse(updatedRule);
    }

    @Transactional
    public void delete(Long earningRuleID) {
        log.debug("Attempting to delete earning rule earningRuleId={}", earningRuleID);
        EarningRule earningRule = findEarningRuleById(earningRuleID);
        earningRuleRepository.delete(earningRule);
        log.info("Earning rule deleted successfully for earningRuleId={}", earningRuleID);
    }

    private LoyaltyProgram findProgramById(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> {
                    log.error("Loyalty program not found for programId={}", programId);
                    return new ProgramNotFoundException(programId);
                });
    }

    private EarningRule findEarningRuleById(Long earningRuleId) {
        return earningRuleRepository.findById(earningRuleId)
                .orElseThrow(() -> {
                    log.error("Earning rule not found for earningRuleId={}", earningRuleId);
                    return new EarningRuleNotFoundException(earningRuleId);
                });
    }

    private void checkNoOverlap(Long programId, EarningEventType eventType, Period newPeriod, Long excludeRuleId) {
        List<EarningRule> sameEventRules = earningRuleRepository.findByProgramIdAndEventType(programId, eventType);
        boolean overlaps = sameEventRules.stream()
                .filter(existing -> !existing.getId().equals(excludeRuleId))
                .anyMatch(existing -> existing.getPeriod().overlaps(newPeriod));

        if (overlaps) {
            log.error("Earning rule overlap detected for programId={}, eventType={}, period={}",
                    programId, eventType, newPeriod);
            throw new ConflictException("EARNING_RULE_OVERLAP",
                    "An active earning rule for event " + eventType + " already exists in an overlapping period");
        }
    }
}