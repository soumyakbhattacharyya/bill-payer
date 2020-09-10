package com.serviceco.coex.payment.model.invoice.ar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.PaymentInvoiceStatus;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * A database entity representing a single transaction 'line' for an Accounts Receivable transaction.
 *
 */
@Getter
@Setter
@Entity
@Table(name = "INVOICE_AR_TRANSACTION_REC")
public class InvoiceARTransactionRec extends EntityBase {

  private static final long serialVersionUID = 1L;

  public InvoiceARTransactionRec() {

  }

  @Column(name = "BUSINESS_UNIT_NAME", nullable = false)
  private String businessUnitName;

  @Column(name = "BATCH_SOURCE", nullable = false)
  private String batchSource;

  @Column(name = "TRANSACTION_TYPE", nullable = false)
  private String transactionType;

  @Column(name = "TRANSACTION_TYPE_ID", nullable = false)
  private String transactionTypeId;

  @Column(name = "PAYMENT_TERMS", nullable = true)
  private String paymentTerms;

  @Column(name = "TRANSACTION_DATE", nullable = false)
  private String transactionDate;

  @Column(name = "BASE_DUE_DATE", nullable = true)
  private boolean baseDueDateOnTransactionDate;

  @Column(name = "TXN_NUMBER", nullable = false)
  private String transactionNumber;

  @Column(name = "BILL_TO_CUST_ACC_NUMBER", nullable = false)
  private String billToCustomerAccountNumber;

  @Column(name = "BILL_TO_CUST_SITE_NUMBER", nullable = false)
  private String billToCustomerSiteNumber;

  @Column(name = "SOLD_TO_CUST_ACC_NUMBER", nullable = false)
  private String soldToCustomerAccountNumber;

  @Column(name = "TXN_LINE_TYPE", nullable = false)
  private String transactionLineType;

  @Column(name = "TXN_LINE_DESCRIPTION", nullable = false)
  private String transactionLineDescription;

  @Column(name = "CURRENCY_CODE", nullable = false)
  private String currencyCode;

  @Column(name = "CURRENCY_CONVERSION_TYPE", nullable = true)
  private String currencyConversionType;

  @Column(name = "TXN_LINE_AMOUNT", nullable = false)
  private BigDecimal transactionLineAmount;

  @Column(name = "TXN_LINE_QUANTITY", nullable = false)
  private BigDecimal transactionLineQuantity;

  @Column(name = "UNIT_SELLING_PRICE", nullable = false)
  private BigDecimal unitSellingPrice;

  @Column(name = "INVOICE_SOURCE", nullable = false)
  private String invoiceSource;

  @Column(name = "INVOICE_LINE_NUMBER", nullable = false)
  private String invoiceLineNumber;

  @Column(name = "INVOICE_LINE_TYPE", nullable = false)
  private String invoiceLineType;

  @Column(name = "CLASSIFICATION_ON_CODE", nullable = false)
  private String taxClassificationCode;

  @Column(name = "LEGAL_ENTITY_IDENTIFIER", nullable = false)
  private String legalEntityIdentifier;

  @Column(name = "UOM", nullable = false)
  private String unitOfMeasureCode;

  @Column(name = "DEFAULT_TAXATION_COUNTRY", nullable = false)
  private String defaultTaxationCountry;

  @Column(name = "LINE_AMOUNT_INCLUDES_TAX", nullable = false)
  private boolean lineAmountIncludesTax;

  @Column(name = "INVENTORY_ITEM_NUMBER", nullable = false)
  private String inventoryItemNumber;

  @Column(name = "PERIOD_START_DATE", nullable = false)
  private String paymentPeriodStartDate;

  @Column(name = "PERIOD_END_DATE", nullable = false)
  private String paymentPeriodEndDate;

  @Column(name = "AUCTION_DATE")
  private String auctionDate;

  @Column(name = "ACCOUNT_CLASS", nullable = false)
  private String accountClass;

  @Column(name = "AMOUNT_PERCENTAGE", nullable = false)
  private BigDecimal amountPercentage;

  @Column(name = "ENTITY", nullable = false)
  private String entity;

  @Column(name = "MATERIAL_TYPE_CODE", nullable = false)
  private String materialType;

  @Column(name = "COST_CENTRE", nullable = false)
  private String costCentre;

  @Column(name = "NATURAL_ACC", nullable = false)
  private String naturalAcc;

  @Column(name = "INTER_CO_ACC", nullable = false)
  private String interCoAcc;

  @Column(name = "SPARE", nullable = false)
  private String spare;

  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "STATUS")
  private PaymentInvoiceStatus paymentInvoiceStatus;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PAYMENT_TXN_REC_ID")
  private PaymentTransactionRec paymentTransactionRec;

  @Column(name = "INVOICE_BATCH_ID")
  private String invoiceBatchId;
  
  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

  @Column(name = "SYNCED")
  private String synced;  
}
