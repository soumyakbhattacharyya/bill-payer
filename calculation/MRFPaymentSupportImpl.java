package com.serviceco.coex.payment.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityManager;
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
import com.serviceco.coex.crp.model.dto.ReferenceDataDto;
import com.serviceco.coex.crp.service.ReferenceDataService;
import com.serviceco.coex.exporter.model.dto.EntryStatus;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.model.dto.MaterialTypeReferenceData;
import com.serviceco.coex.masterdata.model.dto.UOMConversion;
import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.mrf.model.MRFClaimDtl;
import com.serviceco.coex.mrf.model.MRFClaimHdr;
import com.serviceco.coex.mrf.model.QMRFClaimHdr;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QRecoveryFeeReference;
import com.serviceco.coex.payment.model.calculation.RecoveryFeeReference;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;

/**
 * <p>Generates payment transactions for MRFs (Material Recovery Facilities, council recycling).</p>
 *
 * {@link #getUnprocessedVolume} obtains the available volume data (claims) for MRFs.
 * {@link #calculateViaActual} generates the payment transactions based on the volume data. 
 *
 */
@Component
@Transactional
public class MRFPaymentSupportImpl implements MRFPaymentSupport {

  private static final String ALL_SCHEME_PART_IDS = "ALL";

  private static final String UNIT_UNITS = "UNIT";

  private static final String KILOGRAM_UNITS = "KILOGRAM";

  private static final String INELIGIBLE_MATERIAL_TYPE = MaterialType.INELIGIBLE;

  private static final Logger LOGGER = LoggerFactory.getLogger(MRFPaymentSupportImpl.class);

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRepository;

  @Autowired
  private ReferenceDataService referenceDataService;
  
  /**
   * <p>Generates payment transaction records based on the MRF claims associated with the current payment period.</p>
   * 
   * <p>The code loops through every {@link com.serviceco.coex.mrf.model.MRFClaimDtl} which is linked to the MRF claim header records passed in.</p> 
   * 
   * <p>A {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} is created for each {@code MRFClaimDtl}, provided the gross amount is not zero.</p>
   * 
   * <p>The gross amount is calculated by multiplying the volume (in units) by a fee. The fee is obtained from the {@code recoveryFeeReference} table based on the material type ID, the scheme participant ID and the period start date.</p>
   * 
   * <p>Any existing {@code PaymentTransactionRec} records matching the scheme participant ID, material type, period type, period, entryType, paymentBatch ID and a status of {@code AWAITING_REVIEW} will be updated to the {@code STALE} status.</p>
   *
   * <p>The PaymentTransactionRec records created include the following data:</p>
   * <ul>
   * <li>Scheme participant ID = MFR ID</li>
   * <li>Scheme participant name = MFR name</li>
   * <li>Line type = "ITEM"</li>
   * <li>Uom = "UNIT"</li>
   * <li>Status = "AWAITING_REVIEW"</li>
   * <li>Arrear = "N" if the claim period is equal to the current payment period or "Y"
   * <br>(note: it looks like there is a mistake in the code here - I think it should also be "N" if the current period is before the claim period start date)</li>
   * <li>...</li>
   * </ul>
   * 
   * @param param The input calculation data including:
   * @param param.paymentBatch The object which identifies the current batch
   * @param param.currentPeriod The current payment period associated with the volume data and the transactions which are being created
   * @param param.allSalesVolumes The volume (claim) header records
   * @param param.scheme Transactions will only be processed for the specified scheme 
   * 
   * @return Returns a list of newly created and persisted PaymentTransactionRec records
   * 
   */
  @Override
  public List<PaymentTransactionRec> calculateViaActual(CalculationParameter<MRFClaimHdr> param) {

    Scheme scheme = param.getScheme();
    
    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();
    for (final MRFClaimHdr header : param.getAllSalesVolumes()) {
      final Period period = dateTimeSupport.periodFactory(header.getPeriod(), header.getPeriodType());
      final List<MRFClaimDtl> details = header.getLines();
      final boolean isCurrent = param.currentPeriod.getStart().isEqual(period.getStart()) || param.currentPeriod.getStart().isBefore(period.getStart());
      final String arrear = isCurrent ? "N" : "Y";

      Map<String, Map<String, Float>> uomConversionRates = fetchUomConversionRates(header.getMrf().getSiteNumber(), param.currentPeriod.getStart(), scheme);
      for (final MRFClaimDtl detail : details) {
        final MaterialType materialType = detail.getMaterialType();

        BigDecimal volumeInUnits = detail.getUnits();
        if (null == volumeInUnits || volumeInUnits.compareTo(BigDecimal.ZERO) == 0) {
          final BigDecimal volumeInKilos = detail.getKilos();
          volumeInUnits = convertVolumeFromKgsToUnits(header.getMrf().getSiteNumber(), materialType.getId(), volumeInKilos, uomConversionRates);
        }

        final RecoveryFeeReference feeReference = fetchRecoveryFeeReference(scheme, header.getMrf().getSiteNumber(), materialType.getId(), period);
        BigDecimal fee = (null != feeReference) ? feeReference.getRecoveryFee() : BigDecimal.ZERO;
        final BigDecimal grossAmount = volumeInUnits.multiply(fee);
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
                                                                                 .where(qPaymentTransactionRec.schemeParticipantId.eq(header.getMrf().getSiteNumber())
                                                                                  .and(qPaymentTransactionRec.materialType.eq(materialType))
                                                                                  .and(qPaymentTransactionRec.periodType.eq(header.getPeriodType().name()))
                                                                                  .and(qPaymentTransactionRec.period.eq(header.getPeriod()))
                                                                                  .and(qPaymentTransactionRec.entryType.eq(header.getEntryType().name()))
                                                                                  .and(qPaymentTransactionRec.paymentBatch.id.ne(param.paymentBatch.getId()))
                                                                                  .and(qPaymentTransactionRec.status.eq(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW))
                                                                                  .and(qPaymentTransactionRec.scheme.eq(scheme))
                                                                                 )
                                                                                 .fetch();
        
        oldPaymentTransactionRecs.stream().forEach(x -> {
          x.setStatus(PaymentTransactionRec.PaymentStatus.STALE);
          paymentTransactionRepository.save(x);
        });
        //@formatter:on

        final PaymentTransactionRec paymentTransactionRec = new PaymentTransactionRec();
        paymentTransactionRec.setPaymentType(param.getPaymentMetadata().getTransactionType());
        paymentTransactionRec.setId(UUID.randomUUID().toString());
        paymentTransactionRec.setPaymentBatch(param.paymentBatch);
        paymentTransactionRec.setSchemeParticipantId(header.getMrf().getSiteNumber());
        paymentTransactionRec.setSchemeParticipantName(header.getMrf().getParticipant().getParticipantName());
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
        paymentTransactionRec.setVolume(volumeInUnits);
        paymentTransactionRec.setVolumeHeaderId(header.getId());
        paymentTransactionRec.setUom(UNIT_UNITS);
        paymentTransactionRec.setPaymentTimestamp(param.paymentBatch.getStartTimeStamp());
        paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW);
        paymentTransactionRec.setScheme(scheme);

        paymentTransactionRepository.save(paymentTransactionRec);
        paymentTransactionRecords.add(paymentTransactionRec);

      }
    }

    return paymentTransactionRecords;
  }

  @Override
  public RecoveryFeeReference fetchRecoveryFeeReference(final Scheme scheme, final String schemeParticipantId, final String materialTypeId, final Period period) {
    LOGGER.info("fetching processing fee for material type {}, period {}", materialTypeId, period);
    final QRecoveryFeeReference feeReference = QRecoveryFeeReference.recoveryFeeReference;
    final DateDimension effectiveFromDate = dateTimeSupport.correspondingDateDimension(period.getStart());
    final Date periodStartDate = effectiveFromDate.getStartOfDay(scheme);
    //@formatter:off
    RecoveryFeeReference refData= getQueryFactory().select(feeReference)
                                  .from(feeReference)
                                  .where(feeReference.materialType.id.eq(materialTypeId)
                                      .and(feeReference.mrf.siteNumber.eq(schemeParticipantId))
                                      .and(feeReference.scheme.eq(scheme))
                                      .and(feeReference.effectiveFrom.loe(periodStartDate))
                                      .and(feeReference.effectiveTo.isNull().or(feeReference.effectiveTo.goe(periodStartDate))))
                                  .fetchOne();
    //@formatter:on 
    if (refData == null) {
      //@formatter:off
      refData= getQueryFactory().select(feeReference)
              .from(feeReference)
              .where(feeReference.materialType.id.eq(materialTypeId)
                  .and(feeReference.scheme.eq(scheme))
                  .and(feeReference.effectiveFrom.loe(periodStartDate))
                  .and(feeReference.effectiveTo.isNull().or(feeReference.effectiveTo.goe(periodStartDate))))
              .fetchOne();
      //@formatter:on
    }
    return refData;
  }

  private JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * <p>Fetches the available volume data (claims) for MRFs.</p>
   * 
   * <p>The MRF volume data is obtained from the {@code mRFClaimHdr} table where the {@code entryStatus} is FINAL. </p>
   * <p>If the {@code schemeParticipantIds} passed in is not empty and does not contain "ALL", then the records are filtered to those which have a MRF ID that matches a scheme participant ID in {@code schemeParticipantIds}.</p>
   * 
   * @param schemeParticipantIds A list of scheme participant IDs for the participants you want to fetch volume data for. Or, pass an empty list/"ALL" to retrieve volume data for all participants.
   * @return Returns {@link com.serviceco.coex.mrf.model.MRFClaimHdr} objects which link to the claim details.  
   * 
   */
  @Override
  public List<MRFClaimHdr> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme) {

    final QMRFClaimHdr qMRFClaimHdr = QMRFClaimHdr.mRFClaimHdr;
    JPAQuery<MRFClaimHdr> mrfClaimVolumesQuery = getQueryFactory().select(qMRFClaimHdr).from(qMRFClaimHdr);
    BooleanExpression whereClause = qMRFClaimHdr.entryStatus.eq(EntryStatus.FINAL).and(qMRFClaimHdr.scheme.eq(scheme));
    if (CollectionUtils.isNotEmpty(schemeParticipantIds) && !schemeParticipantIds.contains(ALL_SCHEME_PART_IDS)) {
      whereClause.and(qMRFClaimHdr.mrf.siteNumber.in(schemeParticipantIds));
    }
    final List<MRFClaimHdr> mrfClaimVolumes = mrfClaimVolumesQuery.where(whereClause).fetch();
    return mrfClaimVolumes;
  }

  private Map<String, Map<String, Float>> fetchUomConversionRates(String processorId, LocalDate date, Scheme scheme) {
    String dateInYYYYMMDDFormat = "D" + date.toString();
    ReferenceDataDto referenceData = referenceDataService.getReferenceData(processorId, dateInYYYYMMDDFormat, scheme);
    Map<String, Map<String, Float>> materialTypeUomKiloConversions = new HashMap<>();
    List<MaterialTypeReferenceData> materialTypes = referenceData.getMaterialTypes();
    for (MaterialTypeReferenceData materialType : materialTypes) {
      List<UOMConversion> uomConversionsList = materialType.getUomConversions();
      if (uomConversionsList == null) {
        continue;
      }
      Map<String, Float> uomConversionsMap = new HashMap<>();
      for (UOMConversion uomConversion : uomConversionsList) {
        if (uomConversion.getToUnit().equals(UNIT_UNITS)) {
          uomConversionsMap.put(uomConversion.getFromUnit(), uomConversion.getConversionFactor());
        }
      }
      materialTypeUomKiloConversions.put(materialType.getMaterialTypeId(), uomConversionsMap);
    }
    return materialTypeUomKiloConversions;
  }

  private BigDecimal convertVolumeFromKgsToUnits(String siteNumber, String materialTypeId, BigDecimal volume, Map<String, Map<String, Float>> conversionRatesMap) {
    if (materialTypeId != null && materialTypeId.endsWith(INELIGIBLE_MATERIAL_TYPE)) {
      return BigDecimal.ZERO;
    }
    Map<String, Float> conversionsForMaterial = conversionRatesMap.get(materialTypeId);
    if (conversionsForMaterial == null) {
      throw new RuntimeException("There are no UMO conversion mappings for material " + materialTypeId + " and site " + siteNumber);
    }
    Float conversionRate = conversionsForMaterial.get(KILOGRAM_UNITS);
    if (conversionRate == null) {
      throw new RuntimeException("There is no UMO conversion mapping from " + KILOGRAM_UNITS + " for material " + materialTypeId + " and site " + siteNumber);
    }
    return volume.multiply(new BigDecimal(conversionRate));
  }

}
