package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.crp.model.CRPClaimHeader;
import com.serviceco.coex.masterdata.model.HandlingFeeReference;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

public interface CRPPaymentSupport extends CalculationSupport<CRPClaimHeader> {

  HandlingFeeReference fetchHandlingFeeReference(Scheme sp, MdtParticipantSite schemeParticipant, String materialTypeId, Period period);

  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<CRPClaimHeader> param);

  List<CRPClaimHeader> getUnprocessedVolume(List<String> schemeParticipantSiteNumbers, Scheme scheme);

}
