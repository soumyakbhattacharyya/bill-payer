package com.serviceco.coex.payment.model.invoice;

import java.io.Serializable;

public interface InvoiceTransaction extends Serializable {

  int getPriorityOrder();

}
