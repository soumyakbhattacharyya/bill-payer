package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.SeasonalityIndex;

public interface SeasonalityIndexRepository extends JpaRepository<SeasonalityIndex, String> {

}
