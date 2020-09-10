package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.PaymentBatch;

/**
 * Repository class for accessing {@link com.serviceco.coex.payment.model.calculation.PaymentBatch} entities.
 *
 */
public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, String> {

}
