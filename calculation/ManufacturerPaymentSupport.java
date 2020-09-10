package com.serviceco.coex.payment.calculation;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.VUnprocessedVolume;

import java.util.List;

public interface ManufacturerPaymentSupport extends CalculationSupport<VUnprocessedVolume> {

  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<VUnprocessedVolume> param);

  List<PaymentTransactionRec> calculateViaForecast(CalculationParameter<VUnprocessedVolume> param);

  List<VUnprocessedVolume> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme);

}
