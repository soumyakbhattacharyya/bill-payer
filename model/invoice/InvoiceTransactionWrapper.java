package com.serviceco.coex.payment.model.invoice;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contains the result of an invoice generation or search action. This includes the invoices generated, a list of errors (if there were any) and the invoice batch ID.
 *
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InvoiceTransactionWrapper {

  private List<InvoiceTransaction> invoices;
  private List<String> errors;

  @JsonIgnore
  private String invoiceBatchId;
  
  private String schemeId;

  public InvoiceTransactionWrapper(List<InvoiceTransaction> invoices, String schemeId) {
    this.invoices = invoices;
    this.schemeId = schemeId;
  }

}
