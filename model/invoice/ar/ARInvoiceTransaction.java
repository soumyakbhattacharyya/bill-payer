package com.serviceco.coex.payment.model.invoice.ar;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.payment.model.invoice.InvoiceSortOrder;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.util.BigDecimalUtility;
import lombok.Getter;
import lombok.Setter;

/**
 * A data transfer object containing the details of a single Accounts Receiveable invoice line.
 * 
 * @see com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec
 *
 */
@Getter
@Setter
public class ARInvoiceTransaction implements InvoiceTransaction {

  public ARInvoiceTransaction(InvoiceARTransactionRec source) {

    this.source = source;
    businessUnitName = source.getBusinessUnitName();
    batchSource = source.getBatchSource();
    transactionType = source.getTransactionType();
    transactionTypeId = source.getTransactionTypeId();
    paymentTerms = source.getPaymentTerms();
    transactionDate = source.getTransactionDate();
    baseDueDateOnTransactionDate = source.isBaseDueDateOnTransactionDate();
    transactionNumber = source.getTransactionNumber();
    billToCustomerAccountNumber = source.getBillToCustomerAccountNumber();
    billToCustomerSiteNumber = source.getBillToCustomerSiteNumber();
    soldToCustomerAccountNumber = source.getSoldToCustomerAccountNumber();
    transactionLineType = source.getTransactionLineType();
    transactionLineDescription = source.getTransactionLineDescription();
    currencyCode = source.getCurrencyCode();
    currencyConversionType = source.getCurrencyConversionType();
    transactionLineAmount = BigDecimalUtility.asDouble(source.getTransactionLineAmount(), 2);
    transactionLineQuantity = BigDecimalUtility.asDouble(source.getTransactionLineQuantity(), 4);
    unitSellingPrice = BigDecimalUtility.asDouble(source.getUnitSellingPrice(), 6);
    invoiceSource = source.getInvoiceSource();
    invoiceLineNumber = source.getInvoiceLineNumber();
    invoiceLineType = source.getInvoiceLineType();
    taxClassificationCode = source.getTaxClassificationCode();
    legalEntityIdentifier = source.getLegalEntityIdentifier();
    unitOfMeasureCode = source.getUnitOfMeasureCode();
    defaultTaxationCountry = source.getDefaultTaxationCountry();
    lineAmountIncludesTax = source.isLineAmountIncludesTax();
    inventoryItemNumber = source.getInventoryItemNumber();
    paymentPeriodStartDate = source.getPaymentPeriodStartDate();
    paymentPeriodEndDate = source.getPaymentPeriodEndDate();
    auctionDate = source.getAuctionDate();
    accountClass = source.getAccountClass();
    amountPercentage = source.getAmountPercentage().doubleValue();
    revenue = new Revenue(source.getEntity(), source.getMaterialType(), source.getCostCentre(), source.getNaturalAcc(), source.getInterCoAcc(), source.getSpare());
  }

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  private String businessUnitName;

  private String batchSource;

  private String transactionType;

  private String transactionTypeId;

  private String paymentTerms;

  private String transactionDate;

  private boolean baseDueDateOnTransactionDate;

  private String transactionNumber;

  private String billToCustomerAccountNumber;

  private String billToCustomerSiteNumber;

  private String soldToCustomerAccountNumber;

  private String transactionLineType;

  private String transactionLineDescription;

  private String currencyCode;

  private String currencyConversionType;

  private double transactionLineAmount;

  private double transactionLineQuantity;

  private double unitSellingPrice;

  private String invoiceSource;

  private String invoiceLineNumber;

  private String invoiceLineType;

  private String taxClassificationCode;

  private String legalEntityIdentifier;

  private String unitOfMeasureCode;

  private String defaultTaxationCountry;

  private boolean lineAmountIncludesTax;

  private String inventoryItemNumber;

  private String paymentPeriodStartDate;

  private String paymentPeriodEndDate;

  private String auctionDate;

  private String accountClass;

  private double amountPercentage;

  private Revenue revenue;

  @JsonIgnore
  private final InvoiceARTransactionRec source;

  @JsonIgnore
  @Override
  public int getPriorityOrder() {

    InvoiceSortOrder[] sortOrders = InvoiceSortOrder.values();
    for (InvoiceSortOrder order : sortOrders) {
      String orderName = order.name().replace('_', ' ');
      if (this.getTransactionLineDescription().startsWith(orderName)) {
        return order.getOrder();
      }
    }
    return 0;
  }

}
