
package com.serviceco.coex.payment.model.invoice.reference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INV_PAYMENT_TERMS_LOV")
@Getter
@Setter
public class InvPaymentTermsLov extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "INVOICE_TYPE", nullable = false, length = 50)
  private String invoiceType;


  @Column(name = "SCHEME_PARTICIPANT_TYPE", nullable = false, length = 100)
  private String schemeParticipantType;

  @Column(name = "PAYMENT_GROUP", nullable = false, length = 100)
  private String paymentGroup;

  
  @Column(name = "VALUE", nullable = false, length = 100)
  private String value;

}
