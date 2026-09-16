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

public class RewardServiceTest {
    private RewardService rewardService;
    private RewardRepository rewardRepository;
    private LoyaltyProgramRepository programRepository;
    private RewardMapper rewardMapper;

    @BeforeEach
    void setup() {
        this.rewardRepository = Mockito.mock(RewardRepository.class);
        this.programRepository = Mockito.mock(LoyaltyProgramRepository.class);
        this.rewardMapper = Mappers.getMapper(RewardMapper.class);
        this.rewardService = new RewardService(rewardRepository, programRepository, rewardMapper);
    }

    @Test
    void createReward_dataCorrect_rewardCreated() {
        //given
        LocalDateTime now = LocalDateTime.now();
        CreateRewardRequest request = new CreateRewardRequest("Free Coffee", 100, 50, now.minusDays(1), now.plusDays(10));
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        program.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().plusDays(10)));
        Reward savedReward = new Reward();
        savedReward.setId(10L);
        savedReward.setName("Free Coffee");
        savedReward.setPointsCost(100);
        savedReward.setAvailableQuantity(50);
        savedReward.setPeriod(new Period(request.startDate(), request.endDate()));
        savedReward.setProgram(program);
        ArgumentCaptor<Reward> rewardCaptor = ArgumentCaptor.forClass(Reward.class);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(rewardRepository.save(any(Reward.class))).thenReturn(savedReward);
        //when
        RewardResponse response = rewardService.createReward(1L, request);
        //then
        Mockito.verify(rewardRepository).save(rewardCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals("Free Coffee", response.name()),
                () -> Assertions.assertEquals(100, response.pointsCost()),
                () -> Assertions.assertEquals("Free Coffee", rewardCaptor.getValue().getName()),
                () -> Assertions.assertEquals(100, rewardCaptor.getValue().getPointsCost()),
                () -> Assertions.assertEquals(50, rewardCaptor.getValue().getAvailableQuantity()),
                () -> Assertions.assertEquals(program, rewardCaptor.getValue().getProgram()),
                () -> Assertions.assertEquals(request.startDate(), rewardCaptor.getValue().getPeriod().getStartDate()),
                () -> Assertions.assertEquals(request.endDate(), rewardCaptor.getValue().getPeriod().getEndDate())
        );
    }

    @Test
    void createReward_programNotExists_throwsProgramNotFoundException() {
        //given
        CreateRewardRequest request = new CreateRewardRequest("Coffee", 100, 10, LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(10));
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> rewardService.createReward(999L, request)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(rewardRepository, Mockito.never()).save(any());
    }

    @Test
    void createReward_programExpired_throwsProgramExpiredException() {
        //given
        CreateRewardRequest request = new CreateRewardRequest("Coffee", 100, 10, LocalDateTime.now().minusDays(10), LocalDateTime.now().plusDays(10));
        LoyaltyProgram expiredProgram = new LoyaltyProgram();
        expiredProgram.setId(1L);
        expiredProgram.setPeriod(new Period(LocalDateTime.now().minusDays(20), LocalDateTime.now().minusDays(10)));
        when(programRepository.findById(1L)).thenReturn(Optional.of(expiredProgram));
        //when + then
        ProgramExpiredException ex = Assertions.assertThrows(
                ProgramExpiredException.class,
                () -> rewardService.createReward(1L, request)
        );
        Assertions.assertEquals("Program is not active: id=1", ex.getMessage());
        Mockito.verify(rewardRepository, Mockito.never()).save(any());
    }

    @Test
    void listRewards_programExists_returnsRewardList() {
        //given
        LoyaltyProgram program = new LoyaltyProgram();
        program.setId(1L);
        Reward reward1 = new Reward();
        reward1.setId(10L);
        reward1.setName("Reward 1");
        Reward reward2 = new Reward();
        reward2.setId(20L);
        reward2.setName("Reward 2");
        ArgumentCaptor<Long> programIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(programRepository.findById(1L)).thenReturn(Optional.of(program));
        when(rewardRepository.findByProgramId(1L)).thenReturn(List.of(reward1, reward2));
        //when
        List<RewardResponse> responses = rewardService.listRewards(1L);
        //then
        Mockito.verify(rewardRepository).findByProgramId(programIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, responses.size()),
                () -> Assertions.assertEquals(10L, responses.getFirst().id()),
                () -> Assertions.assertEquals("Reward 1", responses.getFirst().name()),
                () -> Assertions.assertEquals(20L, responses.get(1).id()),
                () -> Assertions.assertEquals("Reward 2", responses.get(1).name()),
                () -> Assertions.assertEquals(1L, programIdCaptor.getValue())
        );
    }

    @Test
    void listRewards_programNotExists_throwsProgramNotFoundException() {
        //given
        when(programRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        ProgramNotFoundException ex = Assertions.assertThrows(
                ProgramNotFoundException.class,
                () -> rewardService.listRewards(999L)
        );
        Assertions.assertEquals("Program not found: id=999", ex.getMessage());
        Mockito.verify(rewardRepository, Mockito.never()).findByProgramId(any());
    }

    @Test
    void getReward_rewardExists_returnsRewardResponse() {
        //given
        Reward reward = new Reward();
        reward.setId(10L);
        reward.setName("Discount Coupon");
        reward.setPointsCost(50);
        ArgumentCaptor<Long> rewardIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(rewardRepository.findById(10L)).thenReturn(Optional.of(reward));
        //when
        RewardResponse response = rewardService.getReward(10L);

        //then
        Mockito.verify(rewardRepository).findById(rewardIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals("Discount Coupon", response.name()),
                () -> Assertions.assertEquals(50, response.pointsCost()),
                () -> Assertions.assertEquals(10L, rewardIdCaptor.getValue())
        );
    }

    @Test
    void getReward_rewardNotExists_throwsRewardNotFoundException() {
        //given
        when(rewardRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        RewardNotFoundException ex = Assertions.assertThrows(
                RewardNotFoundException.class,
                () -> rewardService.getReward(999L)
        );
        Assertions.assertEquals("Reward not found: id=999", ex.getMessage());
    }

    @Test
    void updateReward_rewardExists_returnsUpdatedRewardResponse() {
        //given
        Reward reward = new Reward();
        reward.setId(10L);
        reward.setName("Old Name");
        reward.setPointsCost(50);
        reward.setAvailableQuantity(10);
        UpdateRewardRequest request = new UpdateRewardRequest(
                "New Name",
                150,
                20
        );
        ArgumentCaptor<Long> rewardIdCaptor = ArgumentCaptor.forClass(Long.class);
        when(rewardRepository.findById(10L)).thenReturn(Optional.of(reward));
        //when
        RewardResponse response = rewardService.updateReward(10L, request);

        //then
        Mockito.verify(rewardRepository).findById(rewardIdCaptor.capture());
        Assertions.assertAll(
                () -> Assertions.assertEquals(10L, response.id()),
                () -> Assertions.assertEquals("New Name", response.name()),
                () -> Assertions.assertEquals(150, response.pointsCost()),
                () -> Assertions.assertEquals("New Name", reward.getName()),
                () -> Assertions.assertEquals(150, reward.getPointsCost()),
                () -> Assertions.assertEquals(20, reward.getAvailableQuantity()),
                () -> Assertions.assertEquals(10L, rewardIdCaptor.getValue())
        );
    }

    @Test
    void updateReward_rewardNotExists_throwsRewardNotFoundException() {
        //given
        UpdateRewardRequest request = new UpdateRewardRequest("New Name", 100, 5);
        when(rewardRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        RewardNotFoundException ex = Assertions.assertThrows(
                RewardNotFoundException.class,
                () -> rewardService.updateReward(999L, request)
        );
        Assertions.assertEquals("Reward not found: id=999", ex.getMessage());
    }

    @Test
    void deleteReward_rewardExists_rewardDeleted() {
        //given
        Reward reward = new Reward();
        reward.setId(10L);
        ArgumentCaptor<Reward> rewardCaptor = ArgumentCaptor.forClass(Reward.class);
        when(rewardRepository.findById(10L)).thenReturn(Optional.of(reward));
        //when
        rewardService.deleteReward(10L);
        //then
        Mockito.verify(rewardRepository).delete(rewardCaptor.capture());
        Assertions.assertEquals(reward, rewardCaptor.getValue());
    }

    @Test
    void deleteReward_rewardNotExists_throwsRewardNotFoundException() {
        //given
        when(rewardRepository.findById(999L)).thenReturn(Optional.empty());
        //when + then
        RewardNotFoundException ex = Assertions.assertThrows(
                RewardNotFoundException.class,
                () -> rewardService.deleteReward(999L)
        );
        Assertions.assertEquals("Reward not found: id=999", ex.getMessage());
        Mockito.verify(rewardRepository, Mockito.never()).delete(any());
    }
}