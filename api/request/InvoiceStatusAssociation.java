package com.serviceco.coex.payment.api.request;

import com.serviceco.coex.payment.model.invoice.InvoiceStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * Associates an invoice to the desired status
 */
public class InvoiceStatusAssociation {
  private String invoiceNumber;
  private InvoiceStatus status;
}
