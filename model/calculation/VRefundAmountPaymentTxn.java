package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.serviceco.coex.model.Model;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "V_REFUND_AMOUNT_PAYMENT_TXN")
@Getter
@Setter
public class VRefundAmountPaymentTxn implements Model {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @Column(name = "CONSUMER_ID", length = 200)
  private String consumerId;

  @Column(name = "CONSUMER_NAME")
  private String consumerName;

  @Column(name = "PROCESSOR_ID", length = 200)
  private String processorId;

  @Column(name = "TRANSACTION_DAY", length = 11)
  private String transactionDay;

  @Column(name = "MATERIAL_TYPE_ID", nullable = false, length = 50)
  private String materialTypeId;

  @Column(name = "GROSS_AMOUNT_SUM")
  private BigDecimal grossAmountSum;

  @Column(name = "TAXABLE_AMOUNT_SUM")
  private BigDecimal taxableAmountSum;

  @Column(name = "GST_AMOUNT_SUM")
  private BigDecimal gstAmountSum;

  @Column(name = "VOLUME")
  private BigDecimal volume;

  @Column(name = "TXN_HEADERS")
  private String transactionHeaders;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

}
