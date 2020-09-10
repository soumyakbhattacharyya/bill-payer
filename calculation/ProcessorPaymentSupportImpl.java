package com.serviceco.coex.payment.calculation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.PersistenceContext;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exporter.model.dto.EntryStatus;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.model.ProcessingFeeReference;
import com.serviceco.coex.masterdata.model.QProcessingFeeReference;
import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.processor.model.ProcessorClaimDetail;
import com.serviceco.coex.processor.model.ProcessorClaimHeader;
import com.serviceco.coex.processor.model.QProcessorClaimHeader;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

/**
 * <p>Generates payment transaction records for processors based on their volume data for the current period.</p>
 * 
 * {@link #getUnprocessedVolume} obtains the volume data.
 * {@link #calculateViaActual} generates the payment transactions based on the volume data.
 *
 */
@Component
@Transactional
public class ProcessorPaymentSupportImpl implements ProcessorPaymentSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorPaymentSupportImpl.class);

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRepository;

  /**
   * <p>Generates payment transaction records based on processor volume data.</p>
   * 
   * <p>The method loops through all processor claim details (ProcessorClaimDetail) linked to the processor volume header data passed in and generates a {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} record for each one, provided that the gross amount is not zero.</p>
   * <p>The gross amount is calculated as the volume multiplied by a processing fee. The fee is obtained from the processingFeeReference table based on either:</p>
   * <ul>
   * <li>the processor ID, material type ID and period start date OR:</li>
   * <li>the scheme ID, material type ID and the period start date</li>
   * </ul>
   * <p>Any existing PaymentTransactionRec records matching the scheme participant ID, material type, period type, period, entryType, paymentBatch ID and a status of AWAITING_REVIEW will be updated to the STALE status.</p>
   * <p>The new PaymentTransactionRec records include:</p>
   * <ul>
   * <li>Scheme participant Id = processor ID</li>
   * <li>Scheme participant name = processor name</li>
   * <li>Line type = "ITEM"</li>
   * <li>Uom = "KILOGRAM"</li>
   * <li>Status = "AWAITING_REVIEW"</li>
   * <li>Arrear = "N" if the volume period is equal to the current payment period otherwise "Y"
   * <br>(note: it looks like there is a mistake in the code here - I think it should also be "N" if the current period is before the volume period start date)</li>
   * <li>...</li>
   * </ul>
   * 
   * @param param The input calculation data including:
   * @param param.scheme The scheme which is associated with the scheme participants
   * @param param.paymentBatch The object which identifies the current batch
   * @param param.currentPeriod The current payment period associated with the volume data and the transactions which are being created
   * @param param.allSalesVolumes The processor volume data
   * 
   */
  @Override
  public List<PaymentTransactionRec> calculateViaActual(CalculationParameter<ProcessorClaimHeader> param) {

    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();
    final Scheme scheme = param.getScheme();
    for (final ProcessorClaimHeader header : param.getAllSalesVolumes()) {
      final Period period = dateTimeSupport.periodFactory(header.getPeriod(), header.getPeriodType());
      final List<ProcessorClaimDetail> details = header.getLines();
      final boolean isCurrent = param.currentPeriod.getStart().isEqual(period.getStart()) || period.getStart().isBefore(period.getStart());
      final String arrear = isCurrent ? "N" : "Y";

      for (final ProcessorClaimDetail detail : details) {
        final MdtParticipantSite processor = header.getProcessor();
        final MaterialType materialType = detail.getMaterialType();
        final BigDecimal volume = detail.getVolume();
        final ProcessingFeeReference feeReference = fetchProcessingFeeReference(scheme, processor, materialType.getId(), period);
        final BigDecimal fee = (null != feeReference) ? feeReference.getFee() : BigDecimal.ZERO;
        final BigDecimal grossAmount = volume.multiply(fee);
        if (grossAmount.compareTo(BigDecimal.ZERO) == 0) {
          // skip transaction creation for zero amounts;
          continue;
        }
        final BigDecimal taxableAmount = BigDecimal.ZERO;
        final BigDecimal gstAmount = BigDecimal.ZERO;

        /*
         * create payment record
         */
        final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;

        //@formatter:off
        final List<PaymentTransactionRec> oldPaymentTransactionRecs = getQueryFactory().select(qPaymentTransactionRec)
                                                                                   .from(qPaymentTransactionRec)
                                                                                 .where(qPaymentTransactionRec.schemeParticipantId.eq(header.getProcessor().getSiteNumber())
                                                                                  .and(qPaymentTransactionRec.materialType.eq(materialType))
                                                                                  .and(qPaymentTransactionRec.periodType.eq(header.getPeriodType().name()))
                                                                                  .and(qPaymentTransactionRec.period.eq(header.getPeriod()))
                                                                                  .and(qPaymentTransactionRec.entryType.eq(header.getEntryType().name()))
                                                                                  .and(qPaymentTransactionRec.paymentBatch.id.ne(param.paymentBatch.getId()))
                                                                                  .and(qPaymentTransactionRec.status.eq(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW))
                                                                                  .and(qPaymentTransactionRec.scheme.eq(scheme))
                                                                                  ).fetch();
        
        oldPaymentTransactionRecs.stream().forEach(x -> {
          x.setStatus(PaymentTransactionRec.PaymentStatus.STALE);
          paymentTransactionRepository.save(x);
        });
        //@formatter:on

        final PaymentTransactionRec paymentTransactionRec = new PaymentTransactionRec();
        paymentTransactionRec.setPaymentType(param.getPaymentMetadata().getTransactionType());
        paymentTransactionRec.setId(UUID.randomUUID().toString());
        paymentTransactionRec.setPaymentBatch(param.paymentBatch);
        paymentTransactionRec.setSchemeParticipantId(header.getProcessor().getSiteNumber());
        paymentTransactionRec.setSchemeParticipantName(header.getProcessor().getParticipant().getParticipantName());
        paymentTransactionRec.setSchemeParticipantType(param.getSchemeParticipantType().name());
        paymentTransactionRec.setMaterialType(materialType);
        paymentTransactionRec.setPaymentPeriod(param.getCurrentPeriod().toString());
        paymentTransactionRec.setPeriodType(header.getPeriodType().name());
        paymentTransactionRec.setPeriod(header.getPeriod());
        paymentTransactionRec.setEntryType(header.getEntryType().name());
        paymentTransactionRec.setUnitSellingPrice(fee);
        paymentTransactionRec.setArrear(arrear);
        paymentTransactionRec.setGrossAmount(grossAmount);
        paymentTransactionRec.setTaxableAmount(taxableAmount);
        paymentTransactionRec.setGstAmount(gstAmount);
        paymentTransactionRec.setLineType("ITEM");
        paymentTransactionRec.setVolume(volume);
        paymentTransactionRec.setVolumeHeaderId(header.getId());
        paymentTransactionRec.setUom("KILOGRAM");
        paymentTransactionRec.setPaymentTimestamp(param.paymentBatch.getStartTimeStamp());
        paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW);
        paymentTransactionRec.setScheme(scheme);

        paymentTransactionRepository.save(paymentTransactionRec);
        paymentTransactionRecords.add(paymentTransactionRec);

      }
    }

    return paymentTransactionRecords;
  }

  /**
   * Fetches the processing fee reference data based on either:
   * <ul>
   * 	<li>processor ID, material type ID and period start date (preferred)</li>
   * 	<li>OR scheme ID, material type ID and period start date</li>
   * </ul>
   * 
   * @param scheme The scheme associated with the scheme participants and processing fee reference data
   * @param processor The specific scheme participant you want to obtain processing fee data for.  
   * @param materialTypeId The type of material which was processed
   * @param period The payment period
   */
  @Override
  public ProcessingFeeReference fetchProcessingFeeReference(final Scheme scheme, final MdtParticipantSite processor, final String materialTypeId, final Period period) {
    try {
      LOGGER.info("fetching processing fee for material type {}, period {}, scheme {}, processor {}", materialTypeId, period, scheme.getName(), processor.getSiteNumber());
      final QProcessingFeeReference feeReference = QProcessingFeeReference.processingFeeReference;
      final DateDimension effectiveFromDate = dateTimeSupport.correspondingDateDimension(period.getStart());
      final Date periodStartDate = effectiveFromDate.getStartOfDay(scheme);
      ProcessingFeeReference pfr = getQueryFactory().select(feeReference)
      //@formatter:off
                                                          .from(feeReference)
                                                          .where(feeReference.processor.siteNumber.eq(processor.getSiteNumber())
                                                              .and(feeReference.materialType.id.eq(materialTypeId))
                                                           .and(feeReference.effectiveFrom.loe(periodStartDate))
                                                           .and(feeReference.effectiveTo.isNull().or(feeReference.effectiveTo.goe(periodStartDate))))
                                                          .fetchOne();
    if (pfr == null) {
                          pfr =  getQueryFactory().select(feeReference)
                                                        //@formatter:off
                                                        .from(feeReference)
                                                        .where(feeReference.scheme.eq(scheme)
                                                            .and(feeReference.materialType.id.eq(materialTypeId))
                                                         .and(feeReference.effectiveFrom.loe(periodStartDate))
                                                         .and(feeReference.effectiveTo.isNull().or(feeReference.effectiveTo.goe(periodStartDate))))
                                                        .fetchFirst();
    }
    //@formatter:on
      return pfr;
    } catch (final NonUniqueResultException exception) {
      throw new RuntimeException(
          exception.getMessage() + " multiple reference data found for period : " + period + " scheme participant " + processor + " material type " + materialTypeId);
    }

  }

  private JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * Fetches the unprocessed volume data for processors.
   * 
   * @param schemeParticipantIds A list of the scheme participant IDs you want to fetch volume data for, or an empty list/"ALL" to fetch all.
   * 
   * <p>The processor volume data is obtained from the {@code processorClaimHeader} table where the {@code entryStatus} is FINAL.</p> 
   * <p>If {@code schemaParticipantIds} is not empty and does not contain "ALL", then the records must also have a processor id which matches one of the {@code schemaParticipantIds}.</p>
   * 
   * @return Returns a list of volume data in {@link com.serviceco.coex.processor.model.ProcessorClaimHeader} objects.
   */
  @Override
  public List<ProcessorClaimHeader> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme) {

    final QProcessorClaimHeader qProcessorClaimHeader = QProcessorClaimHeader.processorClaimHeader;

    final JPAQuery<ProcessorClaimHeader> processorClaimVolumesQuery = getQueryFactory().select(qProcessorClaimHeader).from(qProcessorClaimHeader);
    BooleanExpression whereClause = qProcessorClaimHeader.entryStatus.eq(EntryStatus.FINAL).and(qProcessorClaimHeader.scheme.eq(scheme));
    if (CollectionUtils.isNotEmpty(schemeParticipantIds) && !schemeParticipantIds.contains("ALL")) {
      whereClause = whereClause.and(qProcessorClaimHeader.processor.siteNumber.in(schemeParticipantIds));
    }
    final List<ProcessorClaimHeader> processorClaimVolumes = processorClaimVolumesQuery.where(whereClause).fetch();
    return processorClaimVolumes;
  }
}
