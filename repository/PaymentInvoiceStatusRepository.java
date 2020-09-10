package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.PaymentInvoiceStatus;

public interface PaymentInvoiceStatusRepository extends JpaRepository<PaymentInvoiceStatus, String> {

}
