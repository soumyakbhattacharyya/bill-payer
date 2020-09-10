package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.PaymentPlan;

public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, String> {

}
