package com.serviceco.coex.payment.model.invoice.reference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INV_TXN_INVOICE_LINE_TYPE_LOV")
@Getter
@Setter
public class InvTxnInvoiceLineTypeLov extends EntityBase {
  @Column(name = "INVOICE_TYPE", nullable = false, length = 50)
  private String invoiceType;

  @Column(name = "PAYMENT_TYPE", nullable = false, length = 100)
  private String paymentType;

  @Column(name = "LINE_TYPE", nullable = false, length = 100)
  private String lineType;

  @Column(name = "VALUE", nullable = false, length = 100)
  private String value;

}
