package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.constant.SchemeParticipantType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "PAYMENT_METADATA")
public class PaymentMetadata extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "SCHEME_PARTICIPANT_TYPE", nullable = false)
  @Enumerated(EnumType.STRING)
  private SchemeParticipantType schemeParticipantType;
  

  @Column(name = "MODE_OF_PAYMENT", nullable = true)
  private String modeOfPayment;

  @Column(name = "TRANSACTION_TYPE", nullable = false)
  private String transactionType;

  @Column(name = "FREQUENCY", nullable = false)
  private String frequency;

  @Column(name = "INVOICE_TYPE", nullable = false)
  private String invoiceType;

  @Column(name = "LEDGER", nullable = false)
  private String ledger;
}
