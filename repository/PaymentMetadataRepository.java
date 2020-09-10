package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;

public interface PaymentMetadataRepository extends JpaRepository<PaymentMetadata, String> {
  
  List<PaymentMetadata> findBySchemeParticipantType(SchemeParticipantType type);

}
