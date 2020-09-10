package com.serviceco.coex.payment.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.stereotype.Service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exporter.model.dto.ExportVolumeConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.repository.PaymentBatchRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.model.QMdtParticipantSite;
import com.serviceco.coex.scheme.participant.repository.MdtParticipantSiteRepository;
import com.serviceco.coex.util.model.SchemeRefCodes;

import lombok.NoArgsConstructor;

@Service
@Configurable(dependencyCheck = true)
@NoArgsConstructor
public class GenericService {

  @PersistenceContext
  protected EntityManager em;

  @Autowired
  protected DateTimeSupport periodSupport;

  @Autowired
  protected MdtParticipantSiteRepository participantRepository;

  @Autowired(required = true)
  protected PaymentBatchRepository paymentBatchRepository;

  protected JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * Fetches all scheme participants of a particular type and a particular scheme
   * @param schemeParticipantType
   * @param scheme
   * @return
   */
  protected List<MdtParticipantSite> fetchSchemeParticipants(SchemeParticipantType schemeParticipantType, Scheme scheme) {
    final QMdtParticipantSite participantSite = QMdtParticipantSite.mdtParticipantSite;
    BooleanBuilder participantTypeSpecificClause = null;
    
    BooleanBuilder currentPayToProfileConditions = new BooleanBuilder();
    
    JPAQuery<MdtParticipantSite> from = getQueryFactory().select(participantSite).from(participantSite);
    
    participantTypeSpecificClause = currentPayToProfileConditions;
    
    //@formatter:off
    switch (schemeParticipantType) {
    case LRG_MANUFACTURER:
      participantTypeSpecificClause = participantTypeSpecificClause.and(participantSite.siteTypeId.eq(SchemeRefCodes.ParticipantSiteType.fetchId(SchemeRefCodes.ParticipantSiteType.MANUFACTURER)))
              .and(participantSite.taxonomySubType.eq(SchemeParticipantType.TaxonomySubType.MANUFACTURER_LARGE)).and(participantSite.status.eq(ExportVolumeConstants.CONSTANTS.Active));
      break;
    case SML_MANUFACTURER:
      participantTypeSpecificClause = participantTypeSpecificClause.and(participantSite.siteTypeId.eq(SchemeRefCodes.ParticipantSiteType.fetchId(SchemeRefCodes.ParticipantSiteType.MANUFACTURER)))
        .and(participantSite.taxonomySubType.eq(SchemeParticipantType.TaxonomySubType.MANUFACTURER_SMALL)).and(participantSite.status.eq(ExportVolumeConstants.CONSTANTS.Active));

      break;
    //@formatter:on5
    default:
      participantTypeSpecificClause = new BooleanBuilder().and(participantSite.siteTypeId.eq(SchemeRefCodes.ParticipantSiteType.fetchId(schemeParticipantType.getSupplierType())));
      from = getQueryFactory().select(participantSite).from(participantSite);
      break;
    }
    BooleanBuilder whereClause = new BooleanBuilder().and(participantTypeSpecificClause);
    if (scheme != null) {
      whereClause = whereClause.and(participantSite.scheme.eq(scheme));
    }
    return from.where(whereClause).fetch();
  }

  public List<MdtParticipantSite> fetchSchemeParticipants(List<MdtParticipantSite> allSchemeParticipants, List<String> schemeParticipantIds) {
    if ((schemeParticipantIds != null) && !schemeParticipantIds.isEmpty() && !schemeParticipantIds.get(0).equals("ALL")) {
      return allSchemeParticipants.stream().filter(new Predicate<MdtParticipantSite>() {
        @Override
        public boolean test(MdtParticipantSite t) {
          return schemeParticipantIds.contains(t.getSiteNumber());
        }
      }).collect(Collectors.toList());
    } else {
      return new ArrayList<>();
    }
  }

  // protected List<PaymentTransactionRec> filter(SchemeParticipantType participantType, Status status, List<String> paymentTypes) {
  // String participantTypeStr = "";
  // if ((participantType == SchemeParticipantType.LRG_MANUFACTURER) || (participantType == SchemeParticipantType.SML_MANUFACTURER)) {
  // participantTypeStr = "MANUFACTURER";
  // }else {
  // participantTypeStr = participantType.name();
  // }
  //
  // final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;
  // return getQueryFactory()
  // .select(qPaymentTransactionRec)
  // .from(qPaymentTransactionRec)
  // .where(qPaymentTransactionRec.status.eq(status)
  // .and(qPaymentTransactionRec.paymentType.in(paymentTypes))
  // .and(qPaymentTransactionRec.schemeParticipantType.eq(participantTypeStr)))
  // .fetch();
  //
  // }

  protected <T> T fetchInvoiceMetaData(EntityPath<T> table, BooleanExpression criteria) {
    return getQueryFactory().select(table).from(table).where(criteria).fetchFirst();
  }

}