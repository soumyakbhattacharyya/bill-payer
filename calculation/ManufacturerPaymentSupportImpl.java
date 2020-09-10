package com.serviceco.coex.payment.calculation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.manufacturer.dto.SalesVolumeDto;
import com.serviceco.coex.manufacturer.service.SalesVolumeService;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.model.QSchemePriceReference;
import com.serviceco.coex.masterdata.model.SchemePriceReference;
import com.serviceco.coex.masterdata.repository.MaterialTypeRepository;
import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PaymentTransactionType;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.ContainerVolumeLine;
import com.serviceco.coex.model.dto.ContainerVolumeLine.Containers;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.ForecastedSalesVolume;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QSeasonalityIndex;
import com.serviceco.coex.payment.model.calculation.QVHistoricVolumeForForcast;
import com.serviceco.coex.payment.model.calculation.QVUnprocessedVolume;
import com.serviceco.coex.payment.model.calculation.SeasonalityIndex;
import com.serviceco.coex.payment.model.calculation.VHistoricVolumeForForcast;
import com.serviceco.coex.payment.model.calculation.VUnprocessedVolume;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.service.DefaultComputationTemplateImpl;
import com.serviceco.coex.payment.service.PaymentTransactionService;
import com.serviceco.coex.payment.service.volume.GenericVolumeFinder;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.processor.model.UnitOfMeasure;
import com.serviceco.coex.rest.support.ObjectMapperFactory;
import com.serviceco.coex.scheme.participant.model.MdtParticipant;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.DateUtility;

// TODO : there is significant amount of code duplication, we are driven here by the principle, that, first get it working, then get it optimized
/**
 * <p>Generates payment transactions for (both small and large) manufacturers.</p>  
 * 
 * <p>For manufactures there are two different payment record calculations that are done. 
 * One is for "declaring" participants (participants who have declared volume data for the current period) and the other is for "non-declaring" participants (participants who have not declared volume data for the current period). In the case of non-declaring participants, the volume data is forecasted based on a 12-month rolling average.
 * </p>
 * 
 * <p>
 * {@link #getUnprocessedVolume} obtains the actual declared volume data.<br>
 * {@link #calculateViaActual} generates payment transactions for actual declared volumes.<br>
 * {@link #calculateViaForecast} generates payment transactions based on forecasted data in the case where there is no declared volumes for the current period.<br>
 * </p> 
 * 
 *
 */
@Component
@Transactional
public class ManufacturerPaymentSupportImpl implements ManufacturerPaymentSupport {

  private static final int MAX_NUMBER_OF_PREVIOUS_MONTHS_TO_CONSIDER = 12;

  private static final Logger logger = LoggerFactory.getLogger(DefaultComputationTemplateImpl.class);

  @PersistenceContext
  EntityManager em;

  @Autowired
  private PaymentTransactionRecRepository paymentRepository;

  @Autowired
  private PaymentTransactionService paymentTransactionService;

  @Autowired
  private MaterialTypeRepository materialTypeRepo;

  @Autowired
  private SalesVolumeService volumeService;

  @Autowired
  private DateTimeSupport periodSupport;

  /**
   * <p>Calculates payment transactions based on actual volume data.</p>
   * 
   * @param param Required input data including:
   *  <ul>
   *  <li>schemeParticipants - The participants to create payment transactions for</li>
   *  <li>allSalesVolumes - the actual volume data which has been declared</li>
   *  <li>schemeParticipantType  - the scheme participant type (used to filter the allSalesVolumes)</li>
   *  <li>paymentBatch - the record which has been created to track the status of the payment processing</li>
   *  <li>currentPeriod - the period of time to look at</li>
   *  </ul>
   * <p>The sales volumes are filtered by the scheme participant type.</p>
   * <p>Existing PaymentTransactionRec records matching one of the scheme participants (up to a maximum of 99), the batch ID, a period type of monthly, an entry type of R or A and a status of AWAITING_REVIEW, will have their status set to STALE.</p>
   * <p>Scheme price reference data is loaded from the {@code SCHEME_PRICE_REFERENCE} table and mapped by the material type and the effective start date.</p>
   * <p>The method loops through each of the filtered sales volume data ({@code VUnprocessedVolume}) and creates a payment transaction record ({@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec}) based on it.</p>
   * <p> The transaction entry type is set to:
   * <ul>
   * 	<li>R if the volume entry type is F</li>
   * 	<li>A if the volume entry type is FO</li>
   * 	<li>The same as the volume entry type (for all other volume entry types)</li>
   * </ul>
   * <p>The arrear flag is set to Y if the volume period is not current (where current means the volume period is either equal to the current payment period, or the current payment period started before the volume period start date).</p>
   * <p>The scheme price is obtained from the scheme price reference data based on the volume's material type and the date. The computed price is then equal to the sales volume multiplied by the reference scheme price.</p>
   * <p>The newly created PaymentTransactionRec object data includes:
   * <ul>
   * 	<li>paymentType = SCHEME_PRICE</li>
   * 	<li>paymentBatch = the ID of the payment batch passed in</li>
   * 	<li>lineType = "ITEM"</li>
   * 	<li>paymentTimestamp = the start timestamp for the current batch</li>
   * 	<li>a status of AWAITING_REVIEW.</li>
   * 	<li>...</li>
   * </ul>
   */
  @Override
  public List<PaymentTransactionRec> calculateViaActual(CalculationParameter<VUnprocessedVolume> param) {

    Map<String, MdtParticipantSite> mapOfSchemeParticipants = param.schemeParticipants.stream().collect(Collectors.toMap(sp -> sp.getSiteNumber(), sp -> sp));
    Map<String, MaterialType> mapOfMaterialTypes = materialTypeRepo.findAll().stream().collect(Collectors.toMap(m -> m.getId(), m -> m));
    Map<String, Period> mapOfVolumePeriods = new HashMap<>();
    Map<String, DateDimension> mapOfDateDimensions = new HashMap<>();
    
    Scheme scheme = param.getScheme();

    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();
    logger.info("filtering sales volume based on business size of the manufacturer");
    final List<VUnprocessedVolume> filteredBySchemeParticipantType = param.allSalesVolumes.stream()
        .filter(volume -> StringUtils.equals(volume.getSchemeParticipantType(), param.getSchemeParticipantType().name())).collect(Collectors.toList());

    List<String> schemeParticipantIds = filteredBySchemeParticipantType.stream().map(v -> v.getSchemeParticipantId()).collect(Collectors.toList());
    staleRecords(param.paymentBatch.getId(), schemeParticipantIds, scheme);

    Map<String, Map<LocalDate, SchemePriceReference>> schemePriceReferences = fetchSchemePriceReference(param.getScheme());
    for (final VUnprocessedVolume volume : filteredBySchemeParticipantType) {

      logger.info("processing volume : {}", volume);

      //Payment transaction records are created with entry type R for forecasted volumes, entry type A for forecast overridden volumes
      String entryType = null;
      if (EntryType.F.name().equals(volume.getEntryType())) {
        entryType = EntryType.R.name();
      } else if (EntryType.FO.name().equals(volume.getEntryType())) {
        entryType = EntryType.A.name();
      } else {
        entryType = volume.getEntryType();
      }
      Period volumePeriod = null;
      DateDimension dateDimension = null;
      final Period currentPeriod = param.getCurrentPeriod();

      if (mapOfVolumePeriods.containsKey(volume.getPeriod())) {
        volumePeriod = mapOfVolumePeriods.get(volume.getPeriod());
        dateDimension = mapOfDateDimensions.get(volume.getPeriod());
      } else {
        volumePeriod = periodSupport.periodFactory(volume.getPeriod(), PeriodType.valueOf(volume.getPeriodType()));
        mapOfVolumePeriods.put(volume.getPeriod(), volumePeriod);
        dateDimension = periodSupport.correspondingDateDimension(volumePeriod.getStart());
        mapOfDateDimensions.put(volume.getPeriod(), dateDimension);
      }

      final boolean isCurrent = currentPeriod.getStart().isEqual(volumePeriod.getStart()) || currentPeriod.getStart().isBefore(volumePeriod.getStart());
      final String arrear = isCurrent ? "N" : "Y";

      final MdtParticipantSite schemeParticipant = mapOfSchemeParticipants.get(volume.getSchemeParticipantId());
      final MaterialType materialType = mapOfMaterialTypes.get(volume.getMaterialTypeId());

      if (schemeParticipant != null) {
        if (!scheme.equals(schemeParticipant.getScheme())) {
          logger.error("scheme mismatch found for the associated scheme participant, won't process the volume");
          continue;
        }
      } else {
        logger.error("scheme not found for the associated scheme participant, won't process the volume");
        continue;
      }

      // if (regular && isCurrent) {

      MdtParticipant participant = schemeParticipant.getParticipant();
      logger.info("processing regular declaration for scheme participant {}", participant.getParticipantName());

      // if (periodCategory.isCurrent()) {
      // process current
      logger.info("processing for current period");

      // for this period, find the scheme price
      final SchemePriceReference referenceSchemePrice =
          schemePriceReferences.get(volume.getMaterialTypeId()) != null ? schemePriceReferences.get(volume.getMaterialTypeId()).get(dateDimension.getLocalDate()) : null;
      if (null == referenceSchemePrice) {
        throw new RuntimeException("scheme price is unavailable for following period " + volumePeriod + " and material type " + volume.getMaterialTypeId());
      }

      logger.info("fetched scheme price {} for the current period {}, material type {} and scheme {}", referenceSchemePrice.getSchemePrice(), volumePeriod,
          volume.getMaterialTypeId(), scheme.getName());

      final BigDecimal salesVol = new BigDecimal(volume.getSalesVolume());
      logger.info("sales volume is {}", salesVol.intValue());
      final BigDecimal price = referenceSchemePrice.getSchemePrice().multiply(salesVol);
      logger.info("computed price is {}", price.doubleValue());

      final BigDecimal grossAmount = price;
      final BigDecimal taxableAmount = BigDecimal.ZERO;
      final BigDecimal gstAmount = BigDecimal.ZERO;

      final String volumeUOM = StringUtils.equals(volume.getUom(), UnitOfMeasure.UNIT.name()) ? "Ea" : "KG";

      final PaymentTransactionRec paymentTransactionRec = new PaymentTransactionRec();
      paymentTransactionRec.setPaymentType(PaymentTransactionType.SCHEME_PRICE.getDescription());
      paymentTransactionRec.setId(UUID.randomUUID().toString());
      paymentTransactionRec.setPaymentBatch(param.paymentBatch);
      paymentTransactionRec.setSchemeParticipantId(schemeParticipant.getSiteNumber());
      paymentTransactionRec.setSchemeParticipantName(participant.getParticipantName());
      paymentTransactionRec.setSchemeParticipantType(schemeParticipant.getSiteType());
      paymentTransactionRec.setMaterialType(materialType);
      paymentTransactionRec.setPaymentPeriod(param.getCurrentPeriod().toString());
      paymentTransactionRec.setPeriodType(volume.getPeriodType());
      paymentTransactionRec.setPeriod(volume.getPeriod());
      paymentTransactionRec.setEntryType(entryType);
      paymentTransactionRec.setUnitSellingPrice(referenceSchemePrice.getSchemePrice());
      paymentTransactionRec.setArrear(arrear);
      paymentTransactionRec.setGrossAmount(grossAmount);
      paymentTransactionRec.setTaxableAmount(taxableAmount);
      paymentTransactionRec.setGstAmount(gstAmount);
      paymentTransactionRec.setVolume(salesVol);
      paymentTransactionRec.setLineType("ITEM");
      paymentTransactionRec.setVolumeHeaderId(volume.getVolumeHdrId());
      paymentTransactionRec.setUom(volumeUOM);
      paymentTransactionRec.setPaymentTimestamp(param.paymentBatch.getStartTimeStamp());
      paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW);
      paymentTransactionRec.setVolumeHdrEntryType(volume.getEntryType());
      paymentTransactionRec.setScheme(scheme);

      paymentRepository.save(paymentTransactionRec);
      paymentTransactionRecords.add(paymentTransactionRec);

      // }
    }

    return paymentTransactionRecords;
  }

  private void staleRecords(String paymentBatchId, List<String> schemeParticipantIds, Scheme scheme) {

    final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;

    List<PaymentTransactionRec> oldPaymentTransactionRecs = new ArrayList<>();
    List<String> schemeParticipantIdCopy = new ArrayList<>();
    schemeParticipantIdCopy.addAll(schemeParticipantIds);
    while (schemeParticipantIdCopy.size() > 0) {
      List<String> schemeParticipantIds_99recs = schemeParticipantIdCopy.stream().limit(99).collect(Collectors.toList());
      // @formatter:off
     List<PaymentTransactionRec> oldPaymentTransactionRecsTemp = getQueryFactory()
                                                                  .select(qPaymentTransactionRec)
                                                                  .from(qPaymentTransactionRec)
                                                                  .where(qPaymentTransactionRec.schemeParticipantId.in(schemeParticipantIds_99recs)
                                                                  .and(qPaymentTransactionRec.periodType.eq(PeriodType.M.name()))
                                                                  .and(qPaymentTransactionRec.entryType.in(EntryType.R.name(),EntryType.A.name()))
                                                                  .and(qPaymentTransactionRec.paymentBatch.id.ne(paymentBatchId))
                                                                  .and(qPaymentTransactionRec.status.eq(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW))
                                                                  .and(qPaymentTransactionRec.scheme.multiSchemeId.eq(scheme.getMultiSchemeId()))
                                                                  ).fetch();
    //@formatter:on
      oldPaymentTransactionRecs.addAll(oldPaymentTransactionRecsTemp);
      schemeParticipantIdCopy.removeAll(schemeParticipantIds_99recs);
    }

    if (oldPaymentTransactionRecs.size() > 0) {
      oldPaymentTransactionRecs.stream().forEach(x -> {
        x.setStatus(PaymentTransactionRec.PaymentStatus.STALE);
        paymentRepository.save(x);
      });
    }
  }

  /**
   * Calculates payment transaction records based on forecasted data. 
   * 
   * <p>This method loops through all scheme participants (passed in) where no volume has been registered for the current period and calculates a forecasted sale volume for each material type in the database ({@code MATERIAL_TYPE}).</p>
   * <p>The forecasted sale volume is calculated based on historical data in the {@code VHistoricVolumeForForcast} table, where the period was no more than 12 months ago. It is a monthly average multiplied by a seasonality index. The seasonality index varies based on the material type and the period start date.</p> 
   * <p>Once the volume has been calculated, it is multiplied by a reference scheme price. The reference scheme price also varies based on the material type and the period start date.</p>
   * 
   * <p>A {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} record is created with the gross amount calculated. Sales volume data is also persisted through the {@link com.serviceco.coex.manufacturer.service.SalesVolumeService}.</p>
   * <ul>
   * <li>A sales volume header does not already exist with the same period, manufacture ID and an entry type of R or FO</li>
   * <li>If the forecasted volume already exists with an entry type of F, the original forecasted volume header entry type status is changed to FO and a new sales volume header is created with entry type L (late submitted)</li>
   * <li>If the forecasted volume does not already exist, it is persisted with a header entry type of R.</li>
   * </ul>
   * 
   * @param argument Input data passed into the service which includes:
   * @param argument.schemeParticipants	The scheme participants you wish to generate payment transactions for
   * @param argument.currentPeriod The period of time to generate payment transactions for
   * @param argument.allSalesVolumes The actual volume data which has been declared by the scheme participants
   * @param argumment.scheme The scheme the transactions & volume data apply to
   */
  @Override
  public List<PaymentTransactionRec> calculateViaForecast(CalculationParameter<VUnprocessedVolume> argument) {

    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();

    List<MdtParticipantSite> schemeParticipants = argument.schemeParticipants;
    List<String> listOfSchemeParticipants = schemeParticipants.stream().map(p -> p.getSiteNumber()).collect(Collectors.toList());
    List<VHistoricVolumeForForcast> allHistoricVolumesForForcast = getHistoricVolumesForForcast(listOfSchemeParticipants);
    logger.info("list of scheme participants {}", listOfSchemeParticipants);
    logger.info("size of historic sales volumes for forcasting {}", allHistoricVolumesForForcast.size());

    switch (argument.schemeParticipantType) {
    //@formatter:off
    case LRG_MANUFACTURER:
      createPaymentTransactionViaForecast(argument.getScheme()
                                        , argument.schemeParticipantType
                                        , schemeParticipants
                                        , argument.paymentBatch
                                        , argument.allSalesVolumes
                                        , paymentTransactionRecords
                                        , argument.getCurrentPeriod()
                                        , argument.getCurrentPeriod()
                                        , allHistoricVolumesForForcast);
      break;
    case SML_MANUFACTURER:
      
      final List<Period> monthsForPaymentPeriod = argument.getCurrentPeriod().getPeriods();
      for (final Period monthForPaymentPeriod : monthsForPaymentPeriod) {
        createPaymentTransactionViaForecast(argument.getScheme()
                                          , argument.schemeParticipantType
                                          , schemeParticipants
                                          , argument.paymentBatch
                                          , argument.allSalesVolumes
                                          , paymentTransactionRecords
                                          , argument.getCurrentPeriod()
                                          , monthForPaymentPeriod
                                          ,allHistoricVolumesForForcast);
      }
      break;
    default:
      break;
    }
    //@formatter:on
    return paymentTransactionRecords;
  }

  private void createPaymentTransactionViaForecast(Scheme scheme, final SchemeParticipantType schemeParticipantType, final List<MdtParticipantSite> schemeParticipants,
      final PaymentBatch paymentBatch, List<VUnprocessedVolume> allSalesVolumes, final List<PaymentTransactionRec> paymentTransactionRecords, final Period paymentPeriod,
      final Period period, List<VHistoricVolumeForForcast> allHistoricVolumesForForcast) {

    final String periodValue = period.getValue();
    logger.info("creating payment transaction for the payment period {}", period);

    final DateDimension effectiveFromDate = periodSupport.correspondingDateDimension(period.getStart());

    Map<String, MaterialType> materialTypes = materialTypeRepo.findAllByScheme(scheme).stream().collect(Collectors.toMap(m -> m.getId(), m -> m));
    Map<String, Period> periodMap = new HashMap<>();
    Map<String, Map<Date, SeasonalityIndex>> seasonalityIndexes = findSeasonalityIndex(scheme);
    Map<String, Map<LocalDate, SchemePriceReference>> schemePriceReferences = fetchSchemePriceReference(scheme);

    List<String> schemeParticipantsHavingRegisteredVolume = volumeService.getSchemeParticipantHavingRegisteredVolume(period.getValue(), scheme);
    ObjectMapper mapper = ObjectMapperFactory.getMapperInstance();
    try {
      logger.info("Scheme Participants who has Registered volumes for period {} : {}", period.getValue(), mapper.writeValueAsString(schemeParticipantsHavingRegisteredVolume));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    for (final MdtParticipantSite schemeParticipant : schemeParticipants) {
      if (schemeParticipant != null) {
        logger.info("proceeding to forecast for scheme participant - {}", schemeParticipant.getParticipant().getParticipantName());

        // first find if there exists actual volume for this period
        final boolean hasVolumeForThePeriod = schemeParticipantsHavingRegisteredVolume.contains(schemeParticipant.getSiteNumber());

        if (hasVolumeForThePeriod) {
          continue; // do nothing for this scheme participant if there is an actual volume that has been registered already
        } else {

          final SalesVolumeDto salesVolume = SalesVolumeDto.factory(EntryType.F);
          final List<PaymentTransactionRec> persistedListOfPaymentRecords = new ArrayList<>();
          boolean paymentRecordsCreated = false;

          //Cashing Period
          for (final MaterialType materialType : materialTypes.values()) {

            logger.info("proceeding to forecast for material type - {}", materialType.getName());
            final ForecastedSalesVolume esv = forecastVolume(schemeParticipant.getSiteNumber(), materialType.getId(), allHistoricVolumesForForcast, period, periodMap);
            SeasonalityIndex seasonalityIndex = null;
            if ((esv != null) && (esv.getRollingMonthlyAverage() != BigDecimal.ZERO)) {
              seasonalityIndex = seasonalityIndexes.get(materialType.getId()) != null ? seasonalityIndexes.get(materialType.getId()).get(effectiveFromDate.getStartOfDayUTC()) : null;
              if (seasonalityIndex == null) {
                seasonalityIndex = SeasonalityIndex.ZERO_VALUE();
              }
              // compute
              final BigDecimal totalVolume = esv.getRollingMonthlyAverage().multiply(seasonalityIndex.getValue());
              SchemePriceReference referenceSchemePrice =
                  schemePriceReferences.get(materialType.getId()) != null ? schemePriceReferences.get(materialType.getId()).get(effectiveFromDate.getLocalDate()) : null;
              if (null == referenceSchemePrice) {
                referenceSchemePrice = SchemePriceReference.ZERO_VALUE();
              }

              final BigDecimal grossAmount = referenceSchemePrice.getSchemePrice().multiply(totalVolume);
              final BigDecimal taxableAmount = BigDecimal.ZERO;
              final BigDecimal gstAmount = BigDecimal.ZERO;

              final PaymentTransactionRec record = paymentTransactionService
                  .createPaymentTransaction(paymentBatch, new Period(periodValue, PeriodType.M), paymentPeriod, schemeParticipant, materialType, totalVolume, referenceSchemePrice,
                      grossAmount, taxableAmount, gstAmount, scheme);
              paymentRecordsCreated = true;
              paymentTransactionRecords.add(record);

              //@formatter:on
              final ContainerVolumeLine containerVolumeLine = new ContainerVolumeLine();
              final Containers containers = new Containers();
              containers.setUnits(totalVolume);
              containerVolumeLine.setMaterialTypeId(materialType.getId());
              containerVolumeLine.setContainers(containers);
              salesVolume.getCurrentVolume().getLines().add(containerVolumeLine);
              final PaymentTransactionRec persistedPaymentRecord = paymentRepository.save(record);
              persistedListOfPaymentRecords.add(persistedPaymentRecord);
            }
          }
          if (paymentRecordsCreated) {
            // persist the forecasted volume for the period
            final SalesVolumeDto volumeHeader = volumeService.saveSalesVolumes(schemeParticipant.getSiteNumber(), period.toString(), salesVolume, scheme);
            // associate payment row with the header value
            if (null != volumeHeader) {
              for (final PaymentTransactionRec payment : persistedListOfPaymentRecords) {
                payment.setVolumeHeaderId(volumeHeader.getSalesVolumeHdr().getId());
                paymentRepository.save(payment);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Fetches all scheme price reference data for a particular Scheme. 
   * @param sp The scheme to obtain the reference data for
   * @return Returns the price reference data mapped by the material type ID on the outside. The value of the outer map is another map
   * containing price reference data mapped by the reference data's effective from date.
   */
  public Map<String, Map<LocalDate, SchemePriceReference>> fetchSchemePriceReference(final Scheme sp) {

    logger.info("fetching scheme price for  scheme {}", sp.getName());
    final QSchemePriceReference priceReference = QSchemePriceReference.schemePriceReference;

    // @formatter:off
    List<SchemePriceReference> schemePriceReferences= getQueryFactory()
                                                      .select(priceReference)
                                                      .from(priceReference)
                                                      .where(priceReference.scheme.multiSchemeId.eq(sp.getMultiSchemeId()))
                                                      .fetch();

    // @formatter:on
    Map<String, Map<LocalDate, SchemePriceReference>> schemePriceReferenceMap = new HashMap<>();
    for (SchemePriceReference reference : schemePriceReferences) {
      String materialTypeId_ = reference.getMaterialType().getId();
      if (!schemePriceReferenceMap.containsKey(materialTypeId_)) {
        schemePriceReferenceMap.put(materialTypeId_, new HashMap<>());
      }
      Date effectiveFromDate = reference.getEffectiveFrom();
      LocalDate effectiveFrom = DateUtility.convertToLocalDate(effectiveFromDate);
      schemePriceReferenceMap.get(materialTypeId_).put(effectiveFrom, reference);
    }
    return schemePriceReferenceMap;
  }

  private Map<String, Map<Date, SeasonalityIndex>> findSeasonalityIndex(Scheme scheme) {

    final QSeasonalityIndex qSeasonalityIndex = QSeasonalityIndex.seasonalityIndex;

    //@formatter:off
    final List<SeasonalityIndex> seasonalityIndexes = getQueryFactory().select(qSeasonalityIndex)
                                                              .from(qSeasonalityIndex)
                                                              .where(qSeasonalityIndex.scheme.multiSchemeId.eq(scheme.getMultiSchemeId()))
                                                               .fetch();
    //@formatter:on

    Map<String, Map<Date, SeasonalityIndex>> seasonalityIndexMap = new HashMap<>();
    for (SeasonalityIndex index : seasonalityIndexes) {
      String materialTypeId_ = index.getMaterialType().getId();
      if (!seasonalityIndexMap.containsKey(materialTypeId_)) {
        seasonalityIndexMap.put(materialTypeId_, new HashMap<>());
      }
      seasonalityIndexMap.get(materialTypeId_).put(index.getEffectiveFrom(), index);
    }
    return seasonalityIndexMap;
  }

  /**
   * forecast sales volume based on previous sales
   *
   * @return forecasted sales volume
   */
  private ForecastedSalesVolume forecastVolume(String schemeParticipantId, String materialTypeId, List<VHistoricVolumeForForcast> volumes, final Period period,
      Map<String, Period> periodMap) {

    // compress volumes by adding

    // step 1: filter
    final List<VHistoricVolumeForForcast> filtered = volumes.stream().filter(new Predicate<VHistoricVolumeForForcast>() {
      @Override
      public boolean test(VHistoricVolumeForForcast t) {

        return StringUtils.equals(t.getSchemeParticipantId(), schemeParticipantId) && StringUtils.equals(t.getMaterialTypeId(), materialTypeId) && (
            StringUtils.equals(t.getEntryType(), "R") || StringUtils.equals(t.getEntryType(), "L"));
      }
    }).collect(Collectors.toList());

    // step 2: mapped as period - volume

    final Map<Period, VHistoricVolumeForForcast> mappedToPeriod = filtered.stream().collect(Collectors.toMap(new Function<VHistoricVolumeForForcast, Period>() {

      @Override
      public Period apply(VHistoricVolumeForForcast volume) {

        // important: at this point the start date of a given period is updated
        if (periodMap.containsKey(volume.getPeriod())) {
          return periodMap.get(volume.getPeriod());
        } else {
          final Period period = new Period(volume.getPeriod(), PeriodType.valueOf(volume.getPeriodType()));
          periodSupport.setTerminalDates(period);
          periodMap.put(volume.getPeriod(), period);
          return period;
        }
      }
    }, t -> t));

    // step 3: ordered over period start date

    final LinkedHashMap<Period, VHistoricVolumeForForcast> elements = mappedToPeriod.entrySet().stream().sorted(new Comparator<Entry<Period, VHistoricVolumeForForcast>>() {

      @Override
      public int compare(Entry<Period, VHistoricVolumeForForcast> o1, Entry<Period, VHistoricVolumeForForcast> o2) {
        // sort the collection
        return o2.getKey().getStart().compareTo(o1.getKey().getStart());
      }
    }).filter(e -> {
      final Period periodForHistoricalSalesVolume = e.getKey();
      //I dont think we need this.terminal date would already be there
      // periodSupport.setTerminalDates(periodForHistoricalSalesVolume);
      final LocalDate historicalStartDate = periodForHistoricalSalesVolume.getStart();
      final LocalDate currentPeriodStartDate = period.getStart();

      final java.time.Period diff = java.time.Period.between(historicalStartDate, currentPeriodStartDate);
      return diff.toTotalMonths() <= MAX_NUMBER_OF_PREVIOUS_MONTHS_TO_CONSIDER;
    }).limit(MAX_NUMBER_OF_PREVIOUS_MONTHS_TO_CONSIDER).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

    if (elements.isEmpty() || (elements.size() < 3)) {
      return ForecastedSalesVolume.ZERO_VOLUME();

    } else {
      final DoubleSummaryStatistics summaryStatistics = elements.values().stream().mapToDouble(x -> Double.parseDouble(x.getSalesVolume())).summaryStatistics();
      final ForecastedSalesVolume forecasted = new ForecastedSalesVolume();
      forecasted.setRollingMonthlyAverage(BigDecimal.valueOf(summaryStatistics.getAverage()));

      return forecasted;
    }
  }

  private JPAQueryFactory getQueryFactory() {

    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * Gets the unprocessed volume data for particular scheme participants.
   * 
   * If the {@code schemeParticipantIds} is empty or contains "ALL", all records in the {@code vUnprocessedVolume} table will be fetched. 
   * Otherwise, the only the records matching the IDs in {@code schemeParticipantIds} will be fetched from the {@code vUnprocessedVolume} table
   * 
   *  @param schemeParticipantIds A list containing the IDs of the scheme participants you want to obtain volume data from.
   *  @return Returns a list of unprocessed volume data ({@link com.serviceco.coex.payment.model.calculation.VUnprocessedVolume})
   */
  @Override
  public List<VUnprocessedVolume> getUnprocessedVolume(List<String> schemeParticipantIds, Scheme scheme) {

    GenericVolumeFinder<VUnprocessedVolume> factory = GenericVolumeFinder.<VUnprocessedVolume>factory(getQueryFactory());
    List<VUnprocessedVolume> unprocessedVolumes = null;
    if (CollectionUtils.isNotEmpty(schemeParticipantIds) && !schemeParticipantIds.contains("ALL")) {
      unprocessedVolumes = factory.find(QVUnprocessedVolume.vUnprocessedVolume, QVUnprocessedVolume.vUnprocessedVolume.schemeParticipantId.in(schemeParticipantIds).
          and(QVUnprocessedVolume.vUnprocessedVolume.multiSchemeId.eq(scheme.getMultiSchemeId())));
    } else {
      unprocessedVolumes = factory.find(QVUnprocessedVolume.vUnprocessedVolume, QVUnprocessedVolume.vUnprocessedVolume.multiSchemeId.eq(scheme.getMultiSchemeId()));
    }
    return unprocessedVolumes;
  }

  /**
   * Get historic volume data to be used for forecasting future volumes. This data is obtained from the {@code vHistoricVolumeForForcast} table 
   * where the scheme participant ID matches one of those passed in.
   * 
   * @param schemeParticipantIds Identifiers for scheme participants which require volume forecasting.
   * @return Returns a list of historic volume data for forecasting.
   */
  public List<VHistoricVolumeForForcast> getHistoricVolumesForForcast(List<String> schemeParticipantIds) {

    GenericVolumeFinder<VHistoricVolumeForForcast> factory = GenericVolumeFinder.<VHistoricVolumeForForcast>factory(getQueryFactory());
    List<VHistoricVolumeForForcast> processingVolumes = factory
        .find(QVHistoricVolumeForForcast.vHistoricVolumeForForcast, QVHistoricVolumeForForcast.vHistoricVolumeForForcast.schemeParticipantId.in(schemeParticipantIds));
    return processingVolumes;
  }


}
