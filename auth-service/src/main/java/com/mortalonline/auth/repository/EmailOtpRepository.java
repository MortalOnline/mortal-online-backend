package com.mortalonline.auth.repository;

import com.mortalonline.auth.entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findFirstByUserIdAndPurposeOrderByIdDesc(Long userId, String purpose);
    void deleteByUserIdAndPurpose(Long userId, String purpose);
}
