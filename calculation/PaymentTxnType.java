package com.serviceco.coex.payment.calculation;

/**
 * Types of payment transactions (PaymentTransactionRec records). These are also associated with generating invoices.
 *
 */
public enum PaymentTxnType {
  
  REFUND_AMOUNT, 
  COLLECTION_FEES, 
  GST_RECOVERY_AMOUNT, 
  HANDLING_FEES,
  /** used prior to implementation of direct payments **/
  CONSUMER, 
  PROCESSING_FEES,
  EXPORT_REBATE,
  RECOVERY_AMOUNT_CLAIM,
  HANDLING_FEE,
  // Note: For positive & negative auctions, the type stored in the db is POSITIVE_AUCTION, or NEGATIVE_AUCTION. The scheme participant type is prepended
  // at runtime during invoice generation.
  PROCESSOR_POSITIVE_AUCTION,
  PROCESSOR_NEGATIVE_AUCTION;
}
