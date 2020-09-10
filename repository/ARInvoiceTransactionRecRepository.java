package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec;

public interface ARInvoiceTransactionRecRepository extends JpaRepository<InvoiceARTransactionRec, String> {
  
  List<InvoiceARTransactionRec> findByTransactionNumber(String transactionNumber);

}
