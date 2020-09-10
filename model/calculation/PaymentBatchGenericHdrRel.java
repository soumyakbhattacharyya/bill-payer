package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Table(name = "PAYMENT_BATCH_GENERIC_HDR_REL")
@Entity
public class PaymentBatchGenericHdrRel extends EntityBase {

  @Column(name = "PAYMENT_BATCH_ID")
  private String paymentBatchId;

  @Column(name = "SCHEME_PARTICIPANT_ID")
  private String schemeParticipantId;

  @Column(name = "TXN_HEADER_ID")
  private String txnHeaderId;
  
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

}
