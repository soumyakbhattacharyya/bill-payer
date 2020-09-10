package com.serviceco.coex.payment.model.invoice;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;

import lombok.Getter;
import lombok.Setter;

/**
 * An entity containing an invoice status for a particular payment transaction record {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec}. 
 * This also links to the associated invoice.
 * 
 * <p>This maps to the PAYMENT_INVOICE_STATUS database table.</p>
 *
 */
@Entity
@Table(name = "PAYMENT_INVOICE_STATUS")
@Getter
@Setter
public class PaymentInvoiceStatus extends EntityBase {

  @Column(name = "INVOICE_ID", nullable = false, length = 50)
  private String invoiceId;

  @Column(name = "STATUS", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)  
  private InvoiceStatus status;

  @ManyToOne
  @JoinColumn(name = "PAYMENT_TXN_ID", referencedColumnName = "ID")
  private PaymentTransactionRec payment;

  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
  
}
