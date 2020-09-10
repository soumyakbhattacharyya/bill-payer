package com.serviceco.coex.payment.model.invoice;

public enum InvoiceSortOrder {
  Scheme_Contribution_for(1), Scheme_Contribution_Estimate_for(2), Adjusted_Scheme_Contribution_for(3), Audit_Adjustment_for(4);

  private int order;

  private InvoiceSortOrder(int order) {

    this.order = order;
  }

  public int getOrder() {

    return this.order;
  }
}
