package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.exporter.model.ExportVolumeHeader;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.VExporterPaymentTxn;

public interface ExporterPaymentSupport extends CalculationSupport<VExporterPaymentTxn> {

  @Override
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<VExporterPaymentTxn> paramExporter);

  List<VExporterPaymentTxn> getExporterPaymentUnprocessedVolumes(List<String> schemeParticipantIds, Scheme scheme);

}
