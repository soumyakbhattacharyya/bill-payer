package com.serviceco.coex.payment.model.calculation;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.math.BigDecimal;

@Entity(name = "V_GENERIC_PMT_STATUS_REC")
@Setter
@Getter
@Immutable
@ToString
public class VGenericPaymentStatusRecord implements View {

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

  @Column(name = "GROSS_AMOUNT")
  private BigDecimal grossAmount;

  @Column(name = "PAYMENT_TYPE")
  private String paymentType;

  @Column(name = "STATUS")
  private String status;

}
