package com.serviceco.coex.payment.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Preconditions;
import com.serviceco.coex.crp.model.CRPClaimHeader;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.mrf.model.MRFClaimHdr;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.calculation.CRPAtypicalPaymentSupportImpl;
import com.serviceco.coex.payment.calculation.CRPPaymentSupport;
import com.serviceco.coex.payment.calculation.CalculationParameter;
import com.serviceco.coex.payment.calculation.ExporterPaymentSupport;
import com.serviceco.coex.payment.calculation.MRFPaymentSupport;
import com.serviceco.coex.payment.calculation.ManufacturerPaymentSupport;
import com.serviceco.coex.payment.calculation.ProcessorPaymentSupport;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentMetadata;
import com.serviceco.coex.payment.model.calculation.VExporterPaymentTxn;
import com.serviceco.coex.payment.model.calculation.VUnprocessedVolume;
import com.serviceco.coex.processor.model.ProcessorClaimHeader;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.NoArgsConstructor;

/**
 * Generates payment transactions for non-auction payments.
 * 
 * <p>The super class (ComputationTemplate) handles creating and updating a PaymentBatch record for keeping track
 * of the processing. It calls the run method in this class to perform the actual processing. </p>
 * 
 * <p>See {@link #run}</p>
 *
 */
@Service
@Transactional
@Qualifier("default")
@NoArgsConstructor
public class DefaultComputationTemplateImpl extends ComputationTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultComputationTemplateImpl.class);

  @Autowired
  private ManufacturerPaymentSupport manufacturerPaymentSupport;

  @Autowired
  private ExporterPaymentSupport exporterPaymentSupport;

  @Autowired
  private ProcessorPaymentSupport processorPaymentSupport;

  @Autowired
  private MRFPaymentSupport mrfPaymentSupport;

  @Autowired
  private CRPAtypicalPaymentSupportImpl crpAtypicalPaymentSupport;

//  @Autowired
//  private ConsumerAtypicalPaymentSupportImpl consumerAtypicalPaymentSupport;

  @Autowired
  private CRPPaymentSupport crpPaymentSupport;

  private static final String AUCTION_METADATA = "AUCTION";
  private static final String CRP_HANDLING_FEES = "HANDLING_FEES";

  /**
   * <p>
   * Generates payment transactions for non-auction payments based on volume data which is available for the current period (or forecasted volume data for manufacturers).
   * </p>
   *  
   * <p>
   * This uses the first payment meta data record found which matches the scheme participant type (in the request), excluding meta data related to AUCTION transaction types and excluding meta data related to CRP participant types where the transaction type is not CPP_HANLDING_FEES.
   * </p>
   * 
   * 
   * The Scheme record is looked up based on a hard coded "QLD" string.<br>
   * The payment period is obtained from the payment metadata based on today's date.<br>
   * The scheme participants are obtained based on the include flag in the request. <br>
   * The volume data is obtained differently for different scheme participant types. The payment records are also calculated differently based on the scheme participant type. To perform these operations, it defers to one of the following classes:<br>
   * <br>
   * <ul> 
   * <li>{@link com.serviceco.coex.payment.calculation.ManufacturerPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.ExporterPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.ProcessorPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.AuctionPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.MRFPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.CRPPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.CRPAtypicalPaymentSupportImpl}</li>
   * <li>{@link com.serviceco.coex.payment.calculation.ConsumerAtypicalPaymentSupportImpl}</li>
   * </ul>
   * 
   * @param paymentBatch The record which identifies the current batch processing and keeps track of the result. See {@link com.serviceco.coex.payment.service.ComputationTemplate}. 
   * @param request The data which was passed in to the {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction} web service. It should include:
   * @param request.schemeParticipants The participants to create payment transactions for, or the participants to exclude (see request.include)
   * @param request.schemeParticipantType  The scheme participant type (used to filter the allSalesVolumes)
   * @param request.include  If true, payment transactions are generated for the scheme participants passed in. If false, payment transactions are generated for all of the scheme participants associated with the scheme participant type EXCLUDING the scheme participants passed in.
   * 
   */
  @Override
  public List<PaymentTransactionRec> run(PaymentBatch paymentBatch, PaymentCalculationRequest request) {
    final List<PaymentTransactionRec> allRecords = new ArrayList<>();
    List<PaymentTransactionRec> paymentRecordForNonDeclaringParticipants = new ArrayList<>();
    List<PaymentTransactionRec> paymentRecordForDeclaringParticipants = new ArrayList<>();

    LOGGER.info("Computation service: starts.");
    LOGGER.info("Computation service: input argument: schemeParticipantType {}, schemeParticipants {}, include {}, scheme {}", request.getSchemeParticipantType(),
        request.getSchemeParticipantIds(), request.isInclude(), paymentBatch.getScheme().getId());

    Scheme scheme = paymentBatch.getScheme();
    
    /**
     * set payment metadata
     */
    final QPaymentMetadata qPaymentMetadata = QPaymentMetadata.paymentMetadata;
    final List<PaymentMetadata> paymentMetadata = getQueryFactory().select(qPaymentMetadata).from(qPaymentMetadata)
        .where(qPaymentMetadata.schemeParticipantType.eq(request.getSchemeParticipantType())).fetch();
    final PaymentMetadata firstPaymentMetadata = paymentMetadata.stream().filter(pm -> shouldConsiderMetadata(pm)).findFirst().get();
    request.setPaymentMetadata(firstPaymentMetadata);
    final Period paymentPeriodForSchemeParticipant = assertPaymentPeriod(firstPaymentMetadata, scheme);
    final Period paymentPeriodForSchemParticipantEnriched = periodSupport.periodFactory(paymentPeriodForSchemeParticipant.getValue(), paymentPeriodForSchemeParticipant.getType());

    final List<MdtParticipantSite> declaringSchemeParticipants = super.partitionByDeclaration(request, scheme);

    switch (request.getSchemeParticipantType()) {
    case LRG_MANUFACTURER:
      //@formatter:off
      List<VUnprocessedVolume> sourceData = getSalesVolumeForManufacturer(request.getSchemeParticipantIds(), scheme);
      final CalculationParameter<VUnprocessedVolume> paramLargeManufacturer = new CalculationParameter<VUnprocessedVolume>(
                                                                  scheme
                                                                , request.getSchemeParticipantType()          
                                                                , declaringSchemeParticipants
                                                                , sourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = manufacturerPaymentSupport.calculateViaActual(paramLargeManufacturer);
      paymentRecordForNonDeclaringParticipants = manufacturerPaymentSupport.calculateViaForecast(paramLargeManufacturer);
      //@formatter:on  
      break;
    case SML_MANUFACTURER:
      //@formatter:off
      List<VUnprocessedVolume> manufacturerSourceData = getSalesVolumeForManufacturer(request.getSchemeParticipantIds(), scheme);
      final CalculationParameter<VUnprocessedVolume> paramSmallManufacturer = new CalculationParameter<VUnprocessedVolume>(scheme
                                                                , request.getSchemeParticipantType()
                                                                , declaringSchemeParticipants
                                                                , manufacturerSourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = manufacturerPaymentSupport.calculateViaActual(paramSmallManufacturer);
      paymentRecordForNonDeclaringParticipants = manufacturerPaymentSupport.calculateViaForecast(paramSmallManufacturer);
      //@formatter:on
      break;
    case EXPORTER:
      List<VExporterPaymentTxn> exporterTransactionSourceData = exporterPaymentSupport.getExporterPaymentUnprocessedVolumes(request.getSchemeParticipantIds(), scheme);
      //@formatter:off
      final CalculationParameter<VExporterPaymentTxn> paramExporter = new CalculationParameter<VExporterPaymentTxn>(scheme
                                                                , request.getSchemeParticipantType()
                                                                , declaringSchemeParticipants
                                                                , exporterTransactionSourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = exporterPaymentSupport.calculateViaActual(paramExporter);      
      //@formatter:on
      break;
    case PROCESSOR:

      List<ProcessorClaimHeader> processorTxnSourceData = getProcessorClaimVolumesForProcessor(request.getSchemeParticipantIds(), scheme);
      //@formatter:off
      final CalculationParameter<ProcessorClaimHeader> paramProcessor = new CalculationParameter<ProcessorClaimHeader>(scheme
                                                                , request.getSchemeParticipantType()
                                                                , declaringSchemeParticipants
                                                                , processorTxnSourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = processorPaymentSupport.calculateViaActual(paramProcessor);

      //@formatter:on

      break;
    case MRF:

      List<MRFClaimHdr> mrfClaimSourceData = getClaimVolumesForMRF(request.getSchemeParticipantIds(), scheme);
      //@formatter:off
      final CalculationParameter<MRFClaimHdr> paramMrf = new CalculationParameter<MRFClaimHdr>(scheme
                                                                , request.getSchemeParticipantType()
                                                                , declaringSchemeParticipants
                                                                , mrfClaimSourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = mrfPaymentSupport.calculateViaActual(paramMrf);      
      //@formatter:on

      break;
    case CRP:
      List<CRPClaimHeader> crpClaimSourceData = getClaimVolumesForCRP(request.getSchemeParticipantIds(), scheme);
      //@formatter:off
      final CalculationParameter<CRPClaimHeader> paramCrp = new CalculationParameter<CRPClaimHeader>(scheme
                                                                , request.getSchemeParticipantType()
                                                                , declaringSchemeParticipants
                                                                , crpClaimSourceData
                                                                , periodSupport.getToday(scheme)
                                                                , paymentBatch
                                                                , paymentPeriodForSchemParticipantEnriched
                                                                , firstPaymentMetadata
                                                                , null);
      paymentRecordForDeclaringParticipants = crpPaymentSupport.calculateViaActual(paramCrp);
    
      if (!paymentRecordForDeclaringParticipants.isEmpty()) {
        // @formatter:off
        Map<String,List<PaymentTransactionRec>> paymentRecsPerSchemeParticipantType=paymentRecordForDeclaringParticipants
                                                                                    .stream()
                                                                                    .collect(
                                                                                        Collectors.groupingBy(PaymentTransactionRec::getSchemeParticipantId)
                                                                                        );
        // @formatter:on
        Map<String, List<Period>> handlingFeePeriodPerSchemeParticiantType = new HashMap<>();
        for (Map.Entry<String, List<PaymentTransactionRec>> entry : paymentRecsPerSchemeParticipantType.entrySet()) {
          // find unique payment periods
          final List<Period> handlingFeePeriod = entry.getValue().stream()
              // @Holger suggest
              .filter(distinctByKey(pr -> Arrays.asList(pr.getPeriodType(), pr.getPeriod()))).map(new Function<PaymentTransactionRec, Period>() {

                @Override
                public Period apply(PaymentTransactionRec t) {
                  return periodSupport.periodFactory(t.getPeriod(), PeriodType.valueOf(t.getPeriodType()));
                }
              }).collect(Collectors.toList());
          handlingFeePeriodPerSchemeParticiantType.put(entry.getKey(), handlingFeePeriod);
        }
        final List<PaymentTransactionRec> atypicalTransactions = crpAtypicalPaymentSupport.calculateOnTransactionalData(paymentBatch, declaringSchemeParticipants,
            request.isInclude(), paymentPeriodForSchemParticipantEnriched, handlingFeePeriodPerSchemeParticiantType, scheme);
        paymentRecordForDeclaringParticipants.addAll(atypicalTransactions);
      }
      break;
    //@formatter:on  
    case CONSUMER:
      throw new RuntimeException("Consumer payment transactions now occur in real time. The payment calculation and invoices are generated through the database.");
//      paymentRecordForDeclaringParticipants = consumerAtypicalPaymentSupport.calculateOnTransactionalData(scheme, paymentBatch, declaringSchemeParticipants, request.isInclude(),
//          paymentPeriodForSchemParticipantEnriched);
//      break;
    default:
      break;
    }

    allRecords.addAll(paymentRecordForDeclaringParticipants);
    allRecords.addAll(paymentRecordForNonDeclaringParticipants);

    return allRecords;
  }

  private boolean shouldConsiderMetadata(PaymentMetadata metadata) {
    if (metadata.getTransactionType().contains(AUCTION_METADATA)) {
      return false;
    }
    if (metadata.getSchemeParticipantType().equals(SchemeParticipantType.CRP) && !StringUtils.equals(metadata.getTransactionType(), CRP_HANDLING_FEES)) {
      return false;
    }
    return true;
  }

  @Override
  protected void validate(PaymentCalculationRequest request) {
    Preconditions.checkArgument((null != request.getSchemeParticipantType()), "scheme participant type is a mandatory for running payment computation process");
  }

  protected List<VUnprocessedVolume> getSalesVolumeForManufacturer(List<String> schemeParticipantIds, Scheme scheme) {
    return manufacturerPaymentSupport.getUnprocessedVolume(schemeParticipantIds, scheme);
  }

  protected List<ProcessorClaimHeader> getProcessorClaimVolumesForProcessor(List<String> schemeParticipantIds, Scheme scheme) {
    return processorPaymentSupport.getUnprocessedVolume(schemeParticipantIds, scheme);
  }

  protected List<String> getLotItem(String auctionLotIdentifier) {
    return Collections.singletonList(auctionLotIdentifier);
  }

  protected List<MRFClaimHdr> getClaimVolumesForMRF(List<String> schemeParticipantIds, Scheme scheme) {
    return mrfPaymentSupport.getUnprocessedVolume(schemeParticipantIds, scheme);
  }

  protected List<CRPClaimHeader> getClaimVolumesForCRP(List<String> schemeParticipantIds, Scheme scheme) {
    return crpPaymentSupport.getUnprocessedVolume(schemeParticipantIds, scheme);
  }

  @Override
  protected Period assertPaymentPeriod(PaymentMetadata paymentMetadata, Scheme scheme) {
    PeriodType periodType = null;
    if (paymentMetadata != null) {
      periodType = PeriodType.valueOf(paymentMetadata.getFrequency());
    } else {
      throw new AssertionError("payment metadata for requested scheme participant type, is unavailable ");
    }

    return periodSupport.findPaymentPeriod(periodSupport.getToday(scheme), periodType);
  }

  private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    final Map<Object, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }

}
