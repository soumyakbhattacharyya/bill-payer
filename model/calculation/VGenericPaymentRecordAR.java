package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.annotations.Immutable;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity(name = "V_GENERIC_PAYMENT_REC_AR")
@Setter
@Getter
@Immutable
@ToString
public class VGenericPaymentRecordAR implements View {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @Column(name = "SCHEME_PARTICIPANT_ID")
  private String schemeParticipantId;

  @Column(name = "SCHEME_PARTICIPANT_TYPE")
  private String schemeParticipantType;

  @Column(name = "PAYMENT_TRANSACTION_ID")
  private String paymentTransactionId;

  @Column(name = "PAYMENT_TYPE")
  private String paymentType;

  @Column(name = "STATUS")
  private String status;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

}
