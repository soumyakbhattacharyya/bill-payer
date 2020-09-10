package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.mrf.model.MRFClaimHdr;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.RecoveryFeeReference;

public interface MRFPaymentSupport extends CalculationSupport<MRFClaimHdr> {

  /**
   * API to retrieve recovery fee
   * 
   * @param materialTypeId
   * @param period
   */
  RecoveryFeeReference fetchRecoveryFeeReference(Scheme scheme, String schemeParticipantId, String materialTypeId, Period period);

  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<MRFClaimHdr> param);

  List<MRFClaimHdr> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme);

}
