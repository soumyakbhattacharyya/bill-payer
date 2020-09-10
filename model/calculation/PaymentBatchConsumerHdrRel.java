package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Table(name = "PAYMENT_BATCH_CONSUMER_HDR_REL")
@Entity
public class PaymentBatchConsumerHdrRel extends EntityBase {

  @Column(name = "PAYMENT_BATCH_ID")
  private String paymentBatchId;

  @Column(name = "CONSUMER_ID")
  private String consumerId;

  @Column(name = "CONSUMER_REFUND_TXN_HEADER_ID")
  private String consumerRefundTxnHeaderId;

  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
}
