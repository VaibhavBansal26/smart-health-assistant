package com.healthcare.app.risk;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskRepository extends JpaRepository<RiskAssessment, Long> {

    Page<RiskAssessment> findByUserId(Long userId, Pageable pageable);

    Page<RiskAssessment> findByUserIdAndLevel(Long userId, RiskAssessment.Level level, Pageable pageable);

    Optional<RiskAssessment> findByIdAndUserId(Long id, Long userId);

    long deleteByUserId(Long userId);
}
