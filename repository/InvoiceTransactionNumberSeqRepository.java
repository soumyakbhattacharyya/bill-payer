package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.InvoiceTransactionNumberSeq;

public interface InvoiceTransactionNumberSeqRepository extends JpaRepository<InvoiceTransactionNumberSeq, String> {

  // List<InvoiceTransactionNumberSeq> findBySchemeParticipant(SchemeParticipant schemeParticipantId);

}
