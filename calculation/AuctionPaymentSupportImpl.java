package com.serviceco.coex.payment.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.auction.model.LotItem;
import com.serviceco.coex.auction.model.LotItemManifest;
import com.serviceco.coex.auction.model.QLotItemManifest;
import com.serviceco.coex.auction.repository.LotItemRepository;
import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.mrf.model.MRFMaterialType;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipant;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.DateUtility;

/**
 * Generates auction payment records based on a particular auction lot item manifest.
 * 
 * See {@link #calculateViaActual}
 * 
 *
 */
@Component
@Transactional
public class AuctionPaymentSupportImpl implements AuctionPaymentSupport {

  private static final String TRANS_TYPE_NEGATIVE_AUCTION = "NEGATIVE_AUCTION";
  private static final String TRANS_TYPE_POSITIVE_AUCTION = "POSITIVE_AUCTION";

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRepository;

  @Autowired
  private LotItemRepository lotItemRepository;
  
  /**
   * <p>Generates {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} records based on auction lot items and a particular
   * lot item manifest which are provided in the parameter.</p>
   * 
   * <p>This method loops through every auction lot item ID which was passed in ({@code param.allSalesVolumes}) and creates a PaymentTransactionRec record for each one.</p>
   * 
   * <p>Any existing PaymentTransactionRec records matching the scheme participant ID, lot item manifesct ID, period type, period, entryType, paymentBatch ID and a status of AWAITING_REVIEW will be updated to the STALE status.</p>
   * 
   * <p>The payment transaction record created includes the following:</p>
   * <ul>
   * 	<li>Scheme participant ID = seller ID</li>
   * 	<li>Scheme participant name = seller name</li>
   * 	<li>Period type = D</li>
   * 	<li>Entry type = R (regular)</li>
   * 	<li>Status = AWAITING_INVOICING</li>
   * 	<li>Arrear = "N" if either the auction was sold during the current payment period, or the current payment period started before the auction sold date. Otherwise "Y".</li>
   * 	<li>...</li>
   * </ul>
   * 
   * @param param The data which was passed in to the {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction} web service. It should include:
   * @param param.allSalesVolumes A list of auction lot item identifiers ({@link com.serviceco.coex.auction.model.LotItem}).
   * @param param.auctionLotItemManifestId The ID of the auction lot manifest ({@link com.serviceco.coex.auction.model.LotItemManifest}) which should be used to generate payment records.
   * @param param.currentPeriod The current payment period
   * @param param.scheme Ignored. The scheme will be determined from the lot item record.
   * 
   */
  @Override
  public List<PaymentTransactionRec> calculateViaActual(CalculationParameter<String> param) {

    final List<PaymentTransactionRec> paymentTransactionRecords = new ArrayList<>();
    for (final String auctionLotIdentifier : param.getAllSalesVolumes()) {
      final LotItem lotItem = lotItemRepository.findById(auctionLotIdentifier).get();
      Scheme scheme = lotItem.getScheme();
      param.getPaymentBatch().setScheme(scheme);

      final MdtParticipantSite seller = lotItem.getSeller();
      final MRFMaterialType mrfMaterialType = lotItem.getMrfMaterialType();
      final MaterialType materialType = lotItem.getSchemeMaterialType();
      final LotItemManifest lotItemManifest = fetchLotItemManifest(lotItem.getId(), param.getAuctionLotItemManifestId());
      Date soldOn = lotItem.getSoldOn() == null ? lotItemManifest.getManifestDate(): lotItem.getSoldOn();
      final String headerPeriod = DateUtility.formatDate(soldOn, "yyyy-MM-dd", scheme);
      final Period period = dateTimeSupport.periodFactory(headerPeriod, PeriodType.D);
      final boolean isCurrent = param.currentPeriod.getStart().isEqual(period.getStart()) || param.currentPeriod.getStart().isBefore(period.getStart());
      final String arrear = isCurrent ? "N" : "Y";

      final BigDecimal salePriceInTonnes = lotItem.getSalePrice();
      final BigDecimal volume = fetchActualVolume(lotItem.getId(),lotItemManifest.getId());
      final BigDecimal unitSellingPrice = salePriceInTonnes.divide(new BigDecimal(1000));
      final BigDecimal grossAmount = volume.multiply(unitSellingPrice);
      final BigDecimal taxableAmount = BigDecimal.ZERO;
      final BigDecimal gstAmount = BigDecimal.ZERO;

      String transactionType = TRANS_TYPE_POSITIVE_AUCTION;
      if (BigDecimal.ZERO.compareTo(grossAmount) > 0) {
        transactionType = TRANS_TYPE_NEGATIVE_AUCTION;
      }

      /*
       * create payment record
       */
      final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;

      Predicate materialTypePredicate = null;
      if (null != materialType) {
        materialTypePredicate = qPaymentTransactionRec.materialType.eq(materialType);
      }
      if (null != mrfMaterialType) {
        materialTypePredicate = qPaymentTransactionRec.mrfMaterialType.eq(mrfMaterialType);
      }

      //@formatter:off
        final List<PaymentTransactionRec> oldPaymentTransactionRecs = getQueryFactory().select(qPaymentTransactionRec)
                                                                                   .from(qPaymentTransactionRec)
                                                                                 .where(qPaymentTransactionRec.schemeParticipantId.eq(seller.getSiteNumber())
                                                                                  .and(materialTypePredicate)
                                                                                  .and(qPaymentTransactionRec.lotItemManifestId.eq(param.getAuctionLotItemManifestId()))
                                                                                  .and(qPaymentTransactionRec.periodType.eq(PeriodType.D.name())
                                                                                  .and(qPaymentTransactionRec.period.eq(headerPeriod)
                                                                                  .and(qPaymentTransactionRec.entryType.eq(EntryType.R.name())
                                                                                  .and(qPaymentTransactionRec.paymentBatch.id.ne(param.paymentBatch.getId()))
                                                                                  .and(qPaymentTransactionRec.status.eq(PaymentTransactionRec.PaymentStatus.AWAITING_INVOICING)))))).fetch();
              
        oldPaymentTransactionRecs.stream().forEach(x -> {
          x.setStatus(PaymentTransactionRec.PaymentStatus.STALE);
          paymentTransactionRepository.save(x);
        });
        //@formatter:on

      final PaymentTransactionRec paymentTransactionRec = new PaymentTransactionRec();
      
      paymentTransactionRec.setPaymentType(transactionType);
      paymentTransactionRec.setId(UUID.randomUUID().toString());
      paymentTransactionRec.setPaymentBatch(param.paymentBatch);
      paymentTransactionRec.setSchemeParticipantId(seller.getSiteNumber());
      MdtParticipant sellerParticipant = seller.getParticipant();
      paymentTransactionRec.setSchemeParticipantName(sellerParticipant.getParticipantName());
      paymentTransactionRec.setSchemeParticipantType(seller.getSiteType());
      paymentTransactionRec.setMrfMaterialType(mrfMaterialType);
      paymentTransactionRec.setMaterialType(materialType);
      paymentTransactionRec.setPaymentPeriod(param.getCurrentPeriod().toString());
      paymentTransactionRec.setPeriodType(PeriodType.D.name());
      paymentTransactionRec.setPeriod(period.getValue());
      paymentTransactionRec.setEntryType(EntryType.R.name());
      paymentTransactionRec.setUnitSellingPrice(unitSellingPrice.setScale(4, RoundingMode.HALF_UP));
      paymentTransactionRec.setArrear(arrear);
      paymentTransactionRec.setGrossAmount(grossAmount);
      paymentTransactionRec.setTaxableAmount(taxableAmount);
      paymentTransactionRec.setGstAmount(gstAmount);
      paymentTransactionRec.setLineType("ITEM");
      paymentTransactionRec.setVolume(volume);
      paymentTransactionRec.setUom("KILOGRAM");
      paymentTransactionRec.setPaymentTimestamp(param.paymentBatch.getStartTimeStamp());
      paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_INVOICING);
      paymentTransactionRec.setLotItem(lotItem);
      paymentTransactionRec.setFinalManifest(lotItemManifest);
      paymentTransactionRec.setLotItemManifestId(param.getAuctionLotItemManifestId());
      paymentTransactionRec.setScheme(scheme);

      paymentTransactionRepository.save(paymentTransactionRec);
      paymentTransactionRecords.add(paymentTransactionRec);

    }

    return paymentTransactionRecords;
  }

  /**
   * Fetches a Lot Item Manifest matching a particular Lot Item and manifest ID
   * @param id ID of a {@link com.serviceco.coex.auction.model.LotItem}
   * @param manifestId ID of a {@link LotItemManifest}
   * @return Returns the matching entity, or null if its not found
   */
  private LotItemManifest fetchLotItemManifest(String id, String manifestId) {
    final QLotItemManifest qLotItemManifest = QLotItemManifest.lotItemManifest;
    //@formatter:off

    return getQueryFactory().select(qLotItemManifest).from(qLotItemManifest)
        .where(qLotItemManifest.lotItem.id.eq(id).and(qLotItemManifest.id.eq(manifestId))).fetchOne();
    //@formatter:on

  }

  /**
   * Fetches the actual weight from a lot item manifest which matches a particular ID and lot item.
   * @param id	ID of a {@link LotItem}
   * @param lotItemManifest ID of a {@link LotItemManifest}
   * @return Returns the actual weight value from the matching record, or null if no matching record is found
   */
  private BigDecimal fetchActualVolume(String id,String lotItemManifest) {
    final QLotItemManifest qLotItemManifest = QLotItemManifest.lotItemManifest;
    //@formatter:off
    final BigDecimal actualVolume = getQueryFactory().select(qLotItemManifest.actualWeight.sum())
        .from(qLotItemManifest).where(qLotItemManifest.lotItem.id.eq(id).and(qLotItemManifest.id.eq(lotItemManifest))).fetchOne();
    //@formatter:on

    return actualVolume;
  }

  /**
   * Constructs a new JPA query factory using the entity manager.
   * @return Returns the factory created.
   */
  private JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

}
