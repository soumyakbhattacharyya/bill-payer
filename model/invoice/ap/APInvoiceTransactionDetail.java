
package com.serviceco.coex.payment.model.invoice.ap;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class APInvoiceTransactionDetail {

  private Double lineNumber;
  private String lineType;
  private Double amount;
  private Double quantity;
  private Double unitPrice;
  private String unitOfMeasure;
  private String description;
  private String itemDescription;
  private Boolean finalMatch;
  private String distributionCombination;
  private String taxClassificationCode;
  private Boolean prorateAcrossAllLineItems;
  private Double lineGroupNumber;
  private Boolean trackAsAsset;
  private Boolean priceCorrectionLine;
  private String paymentPeriodStartDate;
  private String paymentPeriodEndDate;
  private String auctionDate;

}
