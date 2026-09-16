package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.request.CreateRewardRequest;
import com.example.loyaltyprogram.dto.request.UpdateRewardRequest;
import com.example.loyaltyprogram.dto.response.RewardResponse;
import com.example.loyaltyprogram.exception.ProgramExpiredException;
import com.example.loyaltyprogram.exception.ProgramNotFoundException;
import com.example.loyaltyprogram.exception.RewardNotFoundException;
import com.example.loyaltyprogram.mapper.RewardMapper;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.model.Reward;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import com.example.loyaltyprogram.repository.RewardRepository;
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
public class RewardService {
    private final RewardRepository rewardRepository;
    private final LoyaltyProgramRepository programRepository;
    private final RewardMapper rewardMapper;

    @Transactional
    public RewardResponse createReward(Long programId, CreateRewardRequest request) {
        log.debug("Attempting to create reward name='{}' for programId={}", request.name(), programId);
        LoyaltyProgram program = findProgramById(programId);

        if (!program.isActiveAt(LocalDateTime.now())) {
            log.error("Cannot create reward. Program programId={} is expired or inactive", programId);
            throw new ProgramExpiredException(programId);
        }

        Reward reward = rewardMapper.toEntity(request);
        reward.setPeriod(new Period(request.startDate(), request.endDate()));
        program.addReward(reward);
        Reward saved = rewardRepository.save(reward);
        log.info("Reward created successfully with id={} for programId={}", saved.getId(), programId);
        return rewardMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RewardResponse> listRewards(Long programId) {
        log.debug("Listing rewards for programId={}", programId);
        findProgramById(programId);
        List<RewardResponse> rewards = rewardRepository.findByProgramId(programId).stream()
                .map(rewardMapper::toResponse)
                .toList();
        log.debug("Found {} rewards for programId={}", rewards.size(), programId);
        return rewards;
    }

    @Transactional(readOnly = true)
    public RewardResponse getReward(Long rewardId) {
        log.debug("Fetching reward details for rewardId={}", rewardId);
        return rewardMapper.toResponse(findRewardById(rewardId));
    }

    @Transactional
    public RewardResponse updateReward(Long rewardId, UpdateRewardRequest request) {
        log.debug("Attempting to update reward for rewardId={}", rewardId);
        Reward reward = findRewardById(rewardId);
        Reward updated = reward.update(request);
        log.info("Reward updated successfully for rewardId={}", rewardId);
        return rewardMapper.toResponse(updated);
    }

    @Transactional
    public void deleteReward(Long rewardId) {
        log.debug("Attempting to delete reward for rewardId={}", rewardId);
        Reward reward = findRewardById(rewardId);
        rewardRepository.delete(reward);
        log.info("Reward deleted successfully for rewardId={}", rewardId);
    }

    private LoyaltyProgram findProgramById(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> {
                    log.error("Loyalty program not found for programId={}", programId);
                    return new ProgramNotFoundException(programId);
                });
    }

    private Reward findRewardById(Long rewardId) {
        return rewardRepository.findById(rewardId)
                .orElseThrow(() -> {
                    log.error("Reward not found for rewardId={}", rewardId);
                    return new RewardNotFoundException(rewardId);
                });
    }
}