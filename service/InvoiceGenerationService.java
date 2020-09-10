package com.serviceco.coex.payment.service;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.persistence.PersistenceService;

public interface InvoiceGenerationService extends PersistenceService {

  InvoiceTransactionWrapper generateInvoices(InvoicingRequest request, Scheme scheme);

}
