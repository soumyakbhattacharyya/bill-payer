package com.serviceco.coex.payment.model.invoice.ap;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.PaymentInvoiceStatus;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INVOICE_AP_TXN_DTL")
@Getter
@Setter
public class APInvoiceTransactionRecDetail extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "LINE_NUMBER", nullable = false)
  private BigDecimal lineNumber;

  @Column(name = "LINE_TYPE", nullable = false, length = 50)
  private String lineType;

  @Column(name = "AMOUNT", nullable = false)
  private BigDecimal amount;

  @Column(name = "QUANTITY", nullable = false)
  private BigDecimal quantity;

  @Column(name = "UNIT_PRICE", nullable = false)
  private BigDecimal unitPrice;

  @Column(name = "UNIT_OF_MEASURE", nullable = false, length = 50)
  private String unitOfMeasure;

  @Column(name = "DESCRIPTION", nullable = false, length = 50)
  private String description;

  @Column(name = "ITEM_DESCRIPTION", nullable = false, length = 50)
  private String itemDescription;

  @Column(name = "FINAL_MATCH", nullable = false, length = 1)
  private boolean finalMatch;

  @Column(name = "DISTRIBUTION_COMBINATION", nullable = false, length = 50)
  private String distributionCombination;

  @Column(name = "TAX_CLASSIFICATION_CODE", nullable = false, length = 50)
  private String taxClassificationCode;

  @Column(name = "PRORATE_ACROSS_ALL_LINE_ITEMS", nullable = false, length = 1)
  private boolean prorateAcrossAllLineItems;

  @Column(name = "LINE_GROUP_NUMBER", nullable = false)
  private BigDecimal lineGroupNumber;

  @Column(name = "TRACK_AS_ASSET", nullable = false, length = 1)
  private boolean trackAsAsset;

  @Column(name = "PRICE_CORRECTION_LINE", nullable = false, length = 1)
  private boolean priceCorrectionLine;

  @Column(name = "PAYMENT_PERIOD_START_DATE", nullable = false, length = 50)
  private String paymentPeriodStartDate;

  @Column(name = "PAYMENT_PERIOD_END_DATE", nullable = false, length = 50)
  private String paymentPeriodEndDate;

  @Column(name = "AUCTION_DATE")
  private String auctionDate;

  @ManyToOne
  @JoinColumn(name = "INVOICE_HDR_REF", referencedColumnName = "ID")
  private APInvoiceTransactionRecHeader invoiceApTxnHdr;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "STATUS")
  private PaymentInvoiceStatus status;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PAYMENT_TXN_REC_ID")
  private PaymentTransactionRec paymentTransactionRec;
  
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
