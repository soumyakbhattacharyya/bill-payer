package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.PaymentBatchGenericHdrRel;

public interface PaymentBatchGenericHdrRelRepository extends JpaRepository<PaymentBatchGenericHdrRel, String> {

}
