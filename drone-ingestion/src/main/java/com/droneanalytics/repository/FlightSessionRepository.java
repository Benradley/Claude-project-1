package com.droneanalytics.repository;

import com.droneanalytics.entity.FlightSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlightSessionRepository extends JpaRepository<FlightSession, Long> {

    /** Paginated list for a specific user, newest first. */
    Page<FlightSession> findByUserEmailOrderByUploadedAtDesc(String email, Pageable pageable);

    /** Fetch a single session only if it belongs to the given user (security check). */
    Optional<FlightSession> findByIdAndUserEmail(Long id, String email);

    /** Count sessions for a user (used in profile/stats). */
    long countByUserEmail(String email);
}
