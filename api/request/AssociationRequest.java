package com.serviceco.coex.payment.api.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * Associates a payment batch to process instance
 */
public class AssociationRequest {
  private String paymentBatchId;
  private String processInstanceId;
}
