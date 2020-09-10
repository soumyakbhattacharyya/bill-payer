package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecDetail;

public interface APInvoiceTransactionRecDetailRepository extends JpaRepository<APInvoiceTransactionRecDetail, String> {

}
