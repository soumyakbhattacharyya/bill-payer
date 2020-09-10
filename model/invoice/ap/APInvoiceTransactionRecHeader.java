package com.serviceco.coex.payment.model.invoice.ap;

import java.math.BigDecimal;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INVOICE_AP_TXN_HDR")
@Getter
@Setter
public class APInvoiceTransactionRecHeader extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "INVOICE_BATCH_ID")
  private String invoiceBatchId;

  @Column(name = "INVOICE_NUMBER", nullable = false, length = 50)
  private String invoiceNumber;

  @Column(name = "BUSINESS_UNIT", nullable = false, length = 50)
  private String businessUnit;

  @Column(name = "INVOICE_SOURCE", nullable = false, length = 50)
  private String invoiceSource;

  @Column(name = "INVOICE_AMOUNT", nullable = false)
  private BigDecimal invoiceAmount;

  @Column(name = "INVOICE_DATE", nullable = false, length = 50)
  private String invoiceDate;

  @Column(name = "SUPPLIER_NUMBER", nullable = false, length = 50)
  private String supplierNumber;

  @Column(name = "SUPPLIER_SITE", nullable = false, length = 50)
  private String supplierSite;

  @Column(name = "INVOICE_CURRENCY", nullable = false, length = 50)
  private String invoiceCurrency;

  @Column(name = "PAYMENT_CURRENCY", nullable = false, length = 50)
  private String paymentCurrency;

  @Column(name = "DESCRIPTION", nullable = false, length = 50)
  private String description;

  @Column(name = "INVOICE_TYPE", nullable = false, length = 50)
  private String invoiceType;

  @Column(name = "LEGAL_ENTITY", nullable = false, length = 50)
  private String legalEntity;

  @Column(name = "PAYMENT_TERMS", nullable = false, length = 50)
  private String paymentTerms;

  @Column(name = "PAY_GROUP", nullable = false, length = 50)
  private String payGroup;

  @Column(name = "CALCULATE_TAX_DURING_IMPORT", nullable = false, length = 1)
  private boolean calculateTaxDuringImport;

  @Column(name = "ADD_TAX_TO_INVOICE_AMOUNT", nullable = false, length = 1)
  private boolean addTaxToInvoiceAmount;

  @Column(name = "INVOICE_GROUP", nullable = false)
  private BigDecimal invoiceGroup;
  
  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "invoiceApTxnHdr", fetch = FetchType.LAZY)
  private List<APInvoiceTransactionRecDetail> lines;

}
