package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.repository.MaterialTypeRepository;
import com.serviceco.coex.masterdata.repository.SchemePriceReferenceRepository;
import com.serviceco.coex.payment.api.request.AssociationRequest;
import com.serviceco.coex.payment.model.calculation.ForecastedSalesVolume;
import com.serviceco.coex.payment.model.calculation.ForecastedSalesVolume.ForecastedSalesVolumeDTO;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.repository.ExpectedSalesVolumeRepository;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.repository.SeasonalityIndexRepository;
import com.serviceco.coex.repository.SchemeRepository;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.repository.MdtParticipantSiteRepository;

/**
 * A service containing helper methods related to payment transactions. 
 * It doesn't appear as though these are actually used / have an effect.  
 *
 */
@Service
@Transactional
public class PaymentTransactionHelperService {

  @PersistenceContext
  protected EntityManager em;

  @Autowired
  private ExpectedSalesVolumeRepository expectedSalesVolumeRepository;

  @Autowired
  private MaterialTypeRepository materialTypeRepository;

  @Autowired
  private MdtParticipantSiteRepository schemeParticipantRepository;

  @Autowired
  private SchemeRepository schemeRepo;

  @Autowired
  private SeasonalityIndexRepository siRepo;

  @Autowired
  private SchemePriceReferenceRepository sprRepo;

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRecRepository;

  /**
   * There doesn't appear to be anything calling this method.
   * @param dtos
   * @return
   */
  public List<ForecastedSalesVolumeDTO> save(List<ForecastedSalesVolumeDTO> dtos) {

    final List<ForecastedSalesVolumeDTO> returnedVal = new ArrayList<>();

    Preconditions.checkArgument((dtos != null) && !dtos.isEmpty(), "expected argument to have a body, while received, null");

    dtos.stream().forEach(dto -> {
      final Optional<MaterialType> materialType = materialTypeRepository.findById(dto.getMaterialTypeId());
      final Optional<MdtParticipantSite> schemeParticipant = schemeParticipantRepository.findBySiteNumber(dto.getSchemeParticipantId());

      final ForecastedSalesVolume expectedSalesVolume = new ForecastedSalesVolume();
      expectedSalesVolume.setId(UUID.randomUUID().toString());
      expectedSalesVolume.setMaterialType(materialType.get());
      expectedSalesVolume.setSchemeParticipantId(schemeParticipant.get().getSiteNumber());
      expectedSalesVolume.setRollingMonthlyAverage(BigDecimal.valueOf(Integer.parseInt(dto.getRollingMonthlyAverage())));
      final ForecastedSalesVolume persistedInstance = expectedSalesVolumeRepository.save(expectedSalesVolume);
      dto.setId(persistedInstance.getId());
      returnedVal.add(dto);
    });

    return returnedVal;

  }

  /*public List<SeasonalityIndexDTO> populateRandomSeasonalityIndecies(List<SeasonalityIndexDTO> indices) {
    final List<SeasonalityIndexDTO> returnedCollection = new ArrayList<>();

    for (final SeasonalityIndexDTO period : indices) {
      final SeasonalityIndex index = new SeasonalityIndex();
      index.setId(UUID.randomUUID().toString());
      index.setEffectiveFrom(period.getEffectiveFrom());
      index.setValue(BigDecimal.valueOf(period.getValue()));
      index.setMaterialType(materialTypeRepository.findById(period.getMaterialTypeId()).get());
      siRepo.save(index);
      returnedCollection.add(period);
    }

    return returnedCollection;
  }*/

//  public List<SchemePriceReference> populateRandomSchemePrice(List<PaymentTransactionRec.SchemePricePopulationRequest> requests) {
//
//    final List<SchemePriceReference> indices = new ArrayList<>();
//    final Scheme scheme = schemeRepo.findById("QLD").get();
//    for (final SchemePricePopulationRequest request : requests) {
//      for (final SchemePriceRecord dto : request.getSchemePriceRecords()) {
//        final SchemePriceReference priceReference = new SchemePriceReference();
//        priceReference.setId(UUID.randomUUID().toString());
//        priceReference.setEffectiveFrom(dto.getPeriod());
//        priceReference.setMaterialType(materialTypeRepository.findById(request.getMaterialTypeId()).get());
//        priceReference.setSchemePrice(BigDecimal.valueOf(dto.getPrice()));
//        priceReference.setScheme(scheme);
//        final SchemePriceReference obj = sprRepo.save(priceReference);
//        indices.add(obj);
//      }
//    }
//
//    return indices;
//
//  }

  /**
   * This contains commented out code. It is simply fetching records and then saving them without making any changes. So the net effect
   * is no changes are made.
   * @param associationRequest
   */
  public void associate(AssociationRequest associationRequest) {
    final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;
    final List<PaymentTransactionRec> paymentRecords = getQueryFactory().select(qPaymentTransactionRec).from(qPaymentTransactionRec)
        .where(qPaymentTransactionRec.paymentBatch.id.eq(associationRequest.getPaymentBatchId())).fetch();
    for (final PaymentTransactionRec payment : paymentRecords) {
      // payment.setProcessInstanceId(associationRequest.getProcessInstanceId());
      // TODO do this is batch
      paymentTransactionRecRepository.save(payment);
    }
  }

  protected JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

}
