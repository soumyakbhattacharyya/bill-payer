package com.serviceco.coex.payment.model.invoice.reference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INV_LEGAL_ENTITY_LOV")
@Getter
@Setter
public class InvLegalIdentifierLov extends EntityBase {

  @Column(name = "INVOICE_TYPE", nullable = false, length = 50)
  private String invoiceType;
  
  @Column(name = "SCHEME_PARTICIPANT_TYPE", nullable = false, length = 100)
  private String schemeParticipantType;
  
  @Column(name = "PAYMENT_TYPE", nullable = false, length = 100)
  private String paymentType;

  @Column(name = "VALUE", nullable = false, length = 100)
  private String value;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

}
