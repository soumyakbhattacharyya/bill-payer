package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.masterdata.model.ProcessingFeeReference;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.processor.model.ProcessorClaimHeader;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

public interface ProcessorPaymentSupport extends CalculationSupport<ProcessorClaimHeader> {

  /**
   * API to retrieve processing fee
   * 
   * @param scheme 
   * @param schemeParticipant
   * @param materialTypeId
   * @param period
   */
  ProcessingFeeReference fetchProcessingFeeReference(Scheme scheme, MdtParticipantSite schemeParticipant, String materialTypeId, Period period);

  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<ProcessorClaimHeader> param);

  List<ProcessorClaimHeader> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme);

}
