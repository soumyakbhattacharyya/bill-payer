package com.serviceco.coex.payment.model.invoice.ap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.util.BigDecimalUtility;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A data transfer object containing details of an Accounts Payable invoice.
 * 
 * @see com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader
 *
 */
@Getter
@Setter
public class APInvoiceTransactionHeader implements InvoiceTransaction {

  /**
   * 
   */
  private static final long serialVersionUID = 1191856899532457882L;


  //CHECK this method
    public APInvoiceTransactionHeader(APInvoiceTransactionRecHeader row) {

        invoiceNumber = row.getInvoiceNumber();
        businessUnit = row.getBusinessUnit();
        invoiceSource = row.getInvoiceSource();
        invoiceAmount = BigDecimalUtility.asDouble(row.getInvoiceAmount());
        invoiceDate = row.getInvoiceDate();
        supplierNumber = row.getSupplierNumber();
        supplierSite = row.getSupplierSite();
        invoiceCurrency = row.getInvoiceCurrency();
        paymentCurrency = row.getPaymentCurrency();
        description = row.getDescription();
        invoiceType = row.getInvoiceType();
        legalEntity = row.getLegalEntity();
        paymentTerms = row.getPaymentTerms();
        payGroup = row.getPayGroup();
        calculateTaxDuringImport = row.isCalculateTaxDuringImport();
        addTaxToInvoiceAmount = row.isAddTaxToInvoiceAmount();
        invoiceGroup = String.valueOf(row.getInvoiceGroup().intValue());
        scheme = row.getScheme();

        final List<APInvoiceTransactionRecDetail> lines = row.getLines();
        for (final APInvoiceTransactionRecDetail dtl : lines) {
          final APInvoiceTransactionDetail detail = new APInvoiceTransactionDetail();
          detail.setAmount(BigDecimalUtility.asDouble(dtl.getAmount()));
          detail.setDescription(dtl.getDescription());
          detail.setDistributionCombination(dtl.getDistributionCombination());
          detail.setFinalMatch(dtl.isFinalMatch());
          detail.setItemDescription(dtl.getItemDescription());
          detail.setLineGroupNumber(dtl.getLineGroupNumber().doubleValue());
          detail.setLineNumber(BigDecimalUtility.asDouble(dtl.getLineNumber()));
          detail.setLineType(dtl.getLineType());
          detail.setPaymentPeriodStartDate(dtl.getPaymentPeriodStartDate());
          detail.setPaymentPeriodEndDate(dtl.getPaymentPeriodEndDate());
          detail.setAuctionDate(dtl.getAuctionDate());
          detail.setPriceCorrectionLine(dtl.isPriceCorrectionLine());
          detail.setProrateAcrossAllLineItems(dtl.isProrateAcrossAllLineItems());
          detail.setQuantity(BigDecimalUtility.asDouble(dtl.getQuantity(), 4));
          detail.setTaxClassificationCode(dtl.getTaxClassificationCode());
          detail.setTrackAsAsset(dtl.isTrackAsAsset());
          detail.setUnitOfMeasure(dtl.getUnitOfMeasure());
          detail.setUnitPrice(BigDecimalUtility.asDouble(dtl.getUnitPrice(), 6));

          invoiceLines.add(detail);
        }
      }  
  
  public APInvoiceTransactionHeader(APInvoiceTransactionRecHeader row,List<APInvoiceTransactionDetail> invoiceDtlLines) {

    id = row.getId();
    invoiceNumber = row.getInvoiceNumber();
    businessUnit = row.getBusinessUnit();
    invoiceSource = row.getInvoiceSource();
    invoiceAmount = BigDecimalUtility.asDouble(row.getInvoiceAmount());
    invoiceDate = row.getInvoiceDate();
    supplierNumber = row.getSupplierNumber();
    supplierSite = row.getSupplierSite();
    invoiceCurrency = row.getInvoiceCurrency();
    paymentCurrency = row.getPaymentCurrency();
    description = row.getDescription();
    invoiceType = row.getInvoiceType();
    legalEntity = row.getLegalEntity();
    paymentTerms = row.getPaymentTerms();
    payGroup = row.getPayGroup();
    calculateTaxDuringImport = row.isCalculateTaxDuringImport();
    addTaxToInvoiceAmount = row.isAddTaxToInvoiceAmount();
    invoiceGroup = String.valueOf(row.getInvoiceGroup().intValue());

    invoiceLines = invoiceDtlLines;

  }

  private String invoiceNumber;

  private String businessUnit;

  private String invoiceSource;

  private Double invoiceAmount;

  private String invoiceDate;

  private String supplierNumber;

  private String supplierSite;

  private String invoiceCurrency;

  private String paymentCurrency;

  private String description;

  private String invoiceType;

  private String legalEntity;

  private String paymentTerms;

  private String payGroup;

  private Boolean calculateTaxDuringImport;

  private Boolean addTaxToInvoiceAmount;

  private String invoiceGroup;

  private List<APInvoiceTransactionDetail> invoiceLines = new ArrayList<>();

  private String id;
  
  @JsonIgnore
  private Scheme scheme;
  
  @Override
  public int getPriorityOrder() {

    return 0;
  }

}
