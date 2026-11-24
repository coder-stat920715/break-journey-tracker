package com.souptik.breakjourney.repository;

import com.souptik.breakjourney.entity.Journey;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    @Override
    @EntityGraph(attributePaths = "legs")
    List<Journey> findAll();

    @Override
    @EntityGraph(attributePaths = "legs")
    Optional<Journey> findById(Long id);
}
