package com.diplomat.repository;

import com.diplomat.model.Constitution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConstitutionRepository extends JpaRepository<Constitution, Long> {
    List<Constitution> findByFinalizedTrue();
    List<Constitution> findByCreatedByOrderByCreatedAtDesc(String createdBy);
}
