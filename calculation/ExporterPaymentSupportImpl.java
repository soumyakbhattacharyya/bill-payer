package com.serviceco.coex.payment.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.BooleanOperation;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exporter.model.QExportVolumeDetail;
import com.serviceco.coex.exporter.model.QExportVolumeHeader;
import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.model.QSchemePriceReference;
import com.serviceco.coex.masterdata.model.SchemePriceReference;
import com.serviceco.coex.masterdata.repository.MaterialTypeRepository;
import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentBatchGenericHdrRel;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QVExporterPaymentTxn;
import com.serviceco.coex.payment.model.calculation.VExporterPaymentTxn;
import com.serviceco.coex.payment.repository.PaymentBatchGenericHdrRelRepository;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.service.DefaultComputationTemplateImpl;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.util.DateUtility;

/**
 * <p>Generates payment transactions for exporters based on exporter volume data.</p>
 * 
 * {@link #getUnprocessedVolume} obtains the available exporter volume data.<br>
 * 
 * {@link #calculateViaActual} generates the payment transactions based on the volume data.
 *
 */
@Component
@Transactional
public class ExporterPaymentSupportImpl implements ExporterPaymentSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultComputationTemplateImpl.class);

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRepository;

  @Autowired
  private MaterialTypeRepository materialTypeRepository;

  @Autowired
  private PaymentBatchGenericHdrRelRepository paymentBatchGenericHdrRepo;

  /**
   * <p>Generates payment transaction records based on exporter volume data.</p>
   * 
   * The code loops through all exporter volume records passed in and creates a {@link PaymentTransactionRec} record for each one provided that:
   * <ul>
   * <li>The volume is not zero</li>
   * <li>The calculated gross amount is not zero</li>
   * </ul>
   * 
   * <p>Any existing payment transaction records with a matching scheme participant ID, material type, period type, period, entry type, batch ID and a status of AWAITING_REVIEW will have its status updated to STALE.</p>
   * The new PaymentTransactionRec data created includes:
   * <ul>
   * 	<li>Line type = "ITEM"</li>
   * 	<li>Uom = "Ea"</li>
   * 	<li>Scheme participant Id = exporter ID</li>
   * 	<li>Scheme participant name = exporter name</li>
   * 	<li>status = "AWAITING_REVIEW"</li>
   * 	<li>Arrear = "N" if the volume period is equal to the current payment period, otherwise "Y"
   * <br>(note: it looks like there is a mistake in the code here - I think it should also be "N" if the current period is before the volume period start date)
   * 	<li>...</li>
   * </ul>
   * 
   */
  @Override
  public List<PaymentTransactionRec> calculateViaActual(CalculationParameter<VExporterPaymentTxn> param) {

    Map<String, Map<LocalDate, Map<String, SchemePriceReference>>> schemePriceReferencePerSP = new HashMap<>();
    Map<LocalDate, Map<String, SchemePriceReference>> schemePriceReferencePerS = new HashMap<>();
    Map<String, Period> periodCache = new HashMap<>();
    Map<String, DateDimension> dateDimensionCache = new HashMap<>();
    Set<String> exportVolumeHeaders = new HashSet<>();
    List<PaymentBatchGenericHdrRel> paymentBatchExporterHeaders = new ArrayList<>();
    
    Scheme scheme = param.getScheme();

    preparePriceReference(schemePriceReferencePerSP, schemePriceReferencePerS, scheme);

    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();

    for (final VExporterPaymentTxn header : param.getAllSalesVolumes()) {

      if (!periodCache.containsKey(header.getPeriod())) {
        final Period tempPeriod = dateTimeSupport.periodFactory(header.getPeriod(), header.getPeriodType());
        DateDimension dateDimension = dateTimeSupport.correspondingDateDimension(tempPeriod.getStart());
        periodCache.put(header.getPeriod(), tempPeriod);
        dateDimensionCache.put(header.getPeriod(), dateDimension);
      }
      final Period period = periodCache.get(header.getPeriod());
      final boolean isCurrent = param.currentPeriod.getStart().isEqual(period.getStart()) || period.getStart().isBefore(period.getStart());
      final String arrear = isCurrent ? "N" : "Y";
      final MaterialType materialType = materialTypeRepository.findById(header.getMaterialTypeId()).get();
      final String materialTypeId = header.getMaterialTypeId();
      final BigDecimal volume = header.getVolume();

      if (volume.compareTo(BigDecimal.ZERO) == 0) {
        continue;
      }
      SchemePriceReference schemePrice = fetchPriceReference(materialTypeId, dateDimensionCache.get(header.getPeriod()), scheme, header.getExporterId(), schemePriceReferencePerSP,
          schemePriceReferencePerS, scheme);
      if (null == schemePrice) {
        LOGGER.error("No scheme price reference found for " + materialTypeId + " and " + header.getPeriod() + " (" + header.getExporterId() + ")");
        schemePrice = SchemePriceReference.ZERO_VALUE();
      }
      final BigDecimal grossAmount = volume.multiply(schemePrice.getSchemePrice());
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
                                                                                 .where(qPaymentTransactionRec.schemeParticipantId.eq(header.getExporterId())
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
      paymentTransactionRec.setSchemeParticipantId(header.getExporterId());
      paymentTransactionRec.setSchemeParticipantName(header.getExporterName());
      paymentTransactionRec.setSchemeParticipantType(param.getSchemeParticipantType().name());
      paymentTransactionRec.setMaterialType(materialType);
      paymentTransactionRec.setPaymentPeriod(param.getCurrentPeriod().toString());
      paymentTransactionRec.setPeriodType(header.getPeriodType().name());
      paymentTransactionRec.setPeriod(header.getPeriod());
      paymentTransactionRec.setEntryType(header.getEntryType().name());
      paymentTransactionRec.setUnitSellingPrice(schemePrice.getSchemePrice());
      paymentTransactionRec.setArrear(arrear);
      paymentTransactionRec.setGrossAmount(grossAmount);
      paymentTransactionRec.setTaxableAmount(taxableAmount);
      paymentTransactionRec.setGstAmount(gstAmount);
      paymentTransactionRec.setLineType("ITEM");
      paymentTransactionRec.setVolume(header.getVolume());
      paymentTransactionRec.setUom("Ea");
      paymentTransactionRec.setPaymentTimestamp(param.paymentBatch.getStartTimeStamp());
      paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW);
      paymentTransactionRec.setScheme(scheme);

      paymentTransactionRepository.save(paymentTransactionRec);
      paymentTransactionRecords.add(paymentTransactionRec);

      List<String> headers = getUnprocessedHeaders(materialTypeId, header.getExporterId(), header.getPeriodType(), header.getPeriod(), header.getEntryType(), scheme);
      LOGGER.info("headers>>"+headers+">>"+materialTypeId+">>"+header.getExporterId()+">>"+header.getPeriodType()+">>"+header.getPeriod()+">>"+header.getEntryType());
      // add the unique transaction header records to the set so that we can know which exact ones to be invoiced
      for (String exportHeaderId : headers) {
        if (!exportVolumeHeaders.contains(exportHeaderId)) {
          PaymentBatchGenericHdrRel rel = new PaymentBatchGenericHdrRel();
          rel.setId(UUID.randomUUID().toString());
          rel.setPaymentBatchId(param.paymentBatch.getId());
          rel.setSchemeParticipantId(header.getExporterId());
          rel.setTxnHeaderId(exportHeaderId);
          rel.setScheme(scheme);
          // adding all headers
          paymentBatchExporterHeaders.add(rel);
          // adding to set so that duplicates are not processed
          exportVolumeHeaders.add(exportHeaderId);
        }
      }

      paymentBatchGenericHdrRepo.saveAll(paymentBatchExporterHeaders);

    }

    return paymentTransactionRecords;
  }

  public void preparePriceReference(Map<String, Map<LocalDate, Map<String, SchemePriceReference>>> schemePriceReferencePerSP,
      Map<LocalDate, Map<String, SchemePriceReference>> schemePriceReferencePerS, Scheme scheme) {
    final QSchemePriceReference priceReference = QSchemePriceReference.schemePriceReference;
    List<SchemePriceReference> schemePriceReferences = getQueryFactory().select(priceReference).from(priceReference)
        .where(priceReference.scheme.eq(scheme)).fetch();
    for (SchemePriceReference reference : schemePriceReferences) {
      Date effectiveFromDate = reference.getEffectiveFrom();
      LocalDate effectiveFrom = DateUtility.convertToLocalDate(effectiveFromDate);
      if (reference.getSchemeParticipant() != null) {
        String schemeParticipantSiteNumber = reference.getSchemeParticipant().getSiteNumber();
        if (!schemePriceReferencePerSP.containsKey(schemeParticipantSiteNumber)) {
          schemePriceReferencePerSP.put(schemeParticipantSiteNumber, new HashMap<>());
          schemePriceReferencePerSP.get(schemeParticipantSiteNumber).put(effectiveFrom, new HashMap<>());
        } else if (!schemePriceReferencePerSP.get(schemeParticipantSiteNumber).containsKey(effectiveFrom)) {
          schemePriceReferencePerSP.get(schemeParticipantSiteNumber).put(effectiveFrom, new HashMap<>());
        }
        schemePriceReferencePerSP.get(schemeParticipantSiteNumber).get(effectiveFrom).put(reference.getMaterialType().getId(), reference);
      } else {
        if (!schemePriceReferencePerS.containsKey(effectiveFrom)) {
          schemePriceReferencePerS.put(effectiveFrom, new HashMap<>());
        }
        schemePriceReferencePerS.get(effectiveFrom).put(reference.getMaterialType().getId(), reference);
      }
    }
  }

  public SchemePriceReference fetchPriceReference(final String materialTypeId, final DateDimension dateDimesion, final Scheme sp, final String schemeParticipantId,
      Map<String, Map<LocalDate, Map<String, SchemePriceReference>>> schemePriceReferencePerSP, Map<LocalDate, Map<String, SchemePriceReference>> schemePriceReferencePerS,
      Scheme scheme) {
    
    LocalDate effectiveFrom = dateDimesion.getLocalDate();
    if (schemePriceReferencePerSP.containsKey(schemeParticipantId)) {
      if (schemePriceReferencePerSP.get(schemeParticipantId).containsKey(effectiveFrom)) {
        if (schemePriceReferencePerSP.get(schemeParticipantId).get(effectiveFrom).containsKey(materialTypeId)) {
          return schemePriceReferencePerSP.get(schemeParticipantId).get(effectiveFrom).get(materialTypeId);
        }
      }
    }

    if (schemePriceReferencePerS.containsKey(effectiveFrom)) {
      if (schemePriceReferencePerS.get(effectiveFrom).containsKey(materialTypeId)) {
        return schemePriceReferencePerS.get(effectiveFrom).get(materialTypeId);
      }
    }

    return null;

  }

  private JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * <p>The exporter volume data is fetched from the {@code vExporterPaymentTxn} table.</p> 
   * 
   * @param schemeParticipantIds A list of scheme participant IDs you want to obtain volume data for.
   * 
   * If the {@code schemeParticipantIds} is empty or contains "ALL", all of the records are returned. <br>
   * Otherwise, only the records which have a exporterId matching one of the schemeParticipantIds are returned.
   * 
   * @return Returns a list of {@link com.serviceco.coex.payment.model.calculation.VExporterPaymentTxn} objects containing the exporter volume data.
   */
  @Override
  public List<VExporterPaymentTxn> getExporterPaymentUnprocessedVolumes(List<String> schemeParticipantIds, Scheme scheme) {
    QVExporterPaymentTxn exporterPayment = QVExporterPaymentTxn.vExporterPaymentTxn;

    BooleanExpression whereClause = exporterPayment.multiSchemeId.eq(scheme.getMultiSchemeId());
    if (CollectionUtils.isNotEmpty(schemeParticipantIds) && !schemeParticipantIds.contains("ALL")) {
      whereClause = whereClause.and(exporterPayment.exporterId.in(schemeParticipantIds));
    }
    return getQueryFactory().select(exporterPayment).from(exporterPayment).where(whereClause).fetch();
  }

  private List<String> getUnprocessedHeaders(String materialTypeId, String exporterId, PeriodType periodType, String period, EntryType entryType, Scheme scheme) {
    final QExportVolumeHeader qExportVolumeHeader = QExportVolumeHeader.exportVolumeHeader;
    final QExportVolumeDetail qExportVolumeDetail = QExportVolumeDetail.exportVolumeDetail;

    //@formatter:off
    return getQueryFactory().select(qExportVolumeHeader.id).from(qExportVolumeDetail,qExportVolumeHeader)
                                  .where(qExportVolumeHeader.id.eq(qExportVolumeDetail.header.id)
                                    .and(qExportVolumeDetail.materialType.id.eq(materialTypeId))
                                    .and(qExportVolumeHeader.period.eq(period))
                                    .and(qExportVolumeHeader.exporter.siteNumber.eq(exporterId))
                                    .and(qExportVolumeHeader.entryType.eq(entryType))
                                    .and(qExportVolumeHeader.periodType.eq(periodType))
                                    .and(qExportVolumeHeader.scheme.eq(scheme))
                                    ).distinct().fetch(); 
    //@formatter:on

  }

}
