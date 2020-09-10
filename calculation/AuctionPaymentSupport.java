package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;

/**
 * An interface for a component which generates auction payment records based on a particular auction Lot Item Manifest.
 * 
 * See {@link com.serviceco.coex.payment.calculation.AuctionPaymentSupportImpl}
 * 
 *
 */
public interface AuctionPaymentSupport extends CalculationSupport<String> {

  /**
   * <p>Generates {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} records based on auction lot items and a particular
   * lot item manifest which are provided in the parameter.</p>
   * 
   * @param param The data which was passed in to the {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction} web service. It should include:
   * @param param.allSalesVolumes A list of auction lot item identifiers ({@link com.serviceco.coex.auction.model.LotItem}).
   * @param param.auctionLotItemManifestId The ID of the auction lot manifest ({@link com.serviceco.coex.auction.model.LotItemManifest}) which should be used to generate payment records.
   * @param param.currentPeriod The current payment period
   * 
   */
  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<String> param);

}
