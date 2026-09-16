package com.example.loyaltyprogram.service;

import com.example.loyaltyprogram.dto.PageDto;
import com.example.loyaltyprogram.dto.request.CreateProgramRequest;
import com.example.loyaltyprogram.dto.request.PageRequestDto;
import com.example.loyaltyprogram.dto.request.UpdateProgramRequest;
import com.example.loyaltyprogram.dto.response.BalanceResponse;
import com.example.loyaltyprogram.dto.response.ProgramResponse;
import com.example.loyaltyprogram.exception.ConflictException;
import com.example.loyaltyprogram.exception.ProgramNotFoundException;
import com.example.loyaltyprogram.mapper.LoyaltyProgramMapper;
import com.example.loyaltyprogram.mapper.MembershipMapper;
import com.example.loyaltyprogram.mapper.PageRequestMapper;
import com.example.loyaltyprogram.model.LoyaltyProgram;
import com.example.loyaltyprogram.model.Period;
import com.example.loyaltyprogram.repository.LoyaltyProgramRepository;
import com.example.loyaltyprogram.repository.MembershipRepository;
import com.example.loyaltyprogram.validation.Validate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramService {
    private final LoyaltyProgramRepository programRepository;
    private final MembershipRepository membershipRepository;
    private final LoyaltyProgramMapper programMapper;
    private final PageRequestMapper pageRequestMapper;
    private final MembershipMapper membershipMapper;

    @Transactional
    public ProgramResponse createProgram(CreateProgramRequest request) {
        log.debug("Attempting to create loyalty program with name='{}'", request.name());
        if (programRepository.existsByName(request.name())) {
            log.error("Cannot create program. Name '{}' is already taken", request.name());
            throw new ConflictException("PROGRAM_NAME_TAKEN", "Program name already in use: " + request.name());
        }

        LoyaltyProgram program = programMapper.toEntity(request);
        program.setPeriod(new Period(request.startDate(), request.endDate()));
        LoyaltyProgram saved = programRepository.save(program);
        log.info("Created program id={} name='{}'", saved.getId(), saved.getName());
        return programMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageDto<ProgramResponse> listPrograms(Boolean active, boolean includeExpired, PageRequestDto pageRequest) {
        log.debug("Fetching programs page with parameters: active={}, includeExpired={}", active, includeExpired);
        Pageable pageable = pageRequestMapper.toPageable(pageRequest);
        LocalDateTime now = LocalDateTime.now();
        Page<LoyaltyProgram> page;

        if (Boolean.TRUE.equals(active)) {
            page = programRepository.findActiveAt(now, pageable);
        } else if (includeExpired) {
            page = programRepository.findAll(pageable);
        } else {
            page = programRepository.findNotExpired(now, pageable);
        }

        log.debug("Found {} programs matching criteria", page.getTotalElements());
        return PageDto.from(page.map(programMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProgramResponse getProgram(Long programId) {
        log.debug("Fetching program details for programId={}", programId);
        return programMapper.toResponse(findProgramById(programId));
    }

    @Transactional
    public ProgramResponse updateProgram(Long programId, UpdateProgramRequest request) {
        log.debug("Attempting to update program programId={}", programId);
        LoyaltyProgram program = findProgramById(programId);

        if (!program.getName().equals(request.name()) && programRepository.existsByName(request.name())) {
            log.error("Cannot update program programId={}. Name '{}' is already taken", programId, request.name());
            throw new ConflictException("PROGRAM_NAME_TAKEN", "Program name already in use: " + request.name());
        }

        LoyaltyProgram updated = program.update(request);
        log.info("Program updated successfully for programId={}", programId);
        return programMapper.toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> getProgramMembers(Long programId) {
        findProgramById(programId);
        return membershipRepository.findByProgramId(programId).stream()
                .map(membershipMapper::toBalanceResponse)
                .toList();
    }

    @Transactional
    public void deleteProgram(Long programId) {
        log.debug("Attempting to delete program programId={}", programId);
        LoyaltyProgram program = findProgramById(programId);

        if (!membershipRepository.findByProgramId(programId).isEmpty()) {
            log.error("Cannot delete program programId={}. Program has active memberships", programId);
            throw new ConflictException("PROGRAM_HAS_MEMBERSHIPS", "Cannot delete program with active memberships");
        }

        programRepository.delete(program);
        log.info("Deleted program id={}", programId);
    }

    private LoyaltyProgram findProgramById(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> {
                    log.error("Loyalty program not found for programId={}", programId);
                    return new ProgramNotFoundException(programId);
                });
    }
}