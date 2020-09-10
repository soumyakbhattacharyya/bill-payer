package com.serviceco.coex.payment.service;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;

public interface GenericServiceAsync {

  void generateInvoices(InvoicingRequest request, Scheme scheme);

  InvoiceTransactionWrapper findAll(String invoiceBatchId, Integer pageNumber, Integer pageSize);

}
