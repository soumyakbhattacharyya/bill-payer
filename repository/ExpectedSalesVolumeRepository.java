package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.ForecastedSalesVolume;

public interface ExpectedSalesVolumeRepository extends JpaRepository<ForecastedSalesVolume, String> {

}
