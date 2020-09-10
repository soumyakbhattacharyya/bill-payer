package com.serviceco.coex.payment.model.invoice;

import java.util.List;

import com.serviceco.coex.payment.api.request.InvoicingRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The result of an asynchronous invoice generation operation which is posted back to a
 * callback URL.
 * 
 * <p>This includes the invoice batch ID, the original request and a list of errors which have occurred.</p>
 *
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AsyncCallbackRequest {

  private String invoicesBatchId;

  private List<String> errors;

  private InvoicingRequest computeParameters;
  
  private String schemeId;

  public AsyncCallbackRequest(String invoiceBatchId, List<String> errors, String schemeId) {
    this.invoicesBatchId = invoiceBatchId;
    this.errors = errors;
    this.schemeId = schemeId;
  }
}
