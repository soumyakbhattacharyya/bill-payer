package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.PaymentBatchConsumerHdrRel;

public interface ConsumerPaymentSupportDataRepository extends JpaRepository<PaymentBatchConsumerHdrRel, String> {

  List<PaymentBatchConsumerHdrRel> findByConsumerIdAndPaymentBatchIdIn(String consumerId, List<String> paymentBatchIds);

}
