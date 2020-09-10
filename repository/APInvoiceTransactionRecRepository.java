package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader;

public interface APInvoiceTransactionRecRepository extends JpaRepository<APInvoiceTransactionRecHeader, String> {
  
  APInvoiceTransactionRecHeader findByInvoiceNumber(String invoiceNumber);

}
