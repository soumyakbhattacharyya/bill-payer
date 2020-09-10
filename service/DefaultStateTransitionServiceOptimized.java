/**
 *
 */
package com.serviceco.coex.payment.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.masterdata.repository.MaterialTypeRepository;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.*;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.PaymentStatus;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.SchemeParticipantToStateMapper;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionSummary;
import com.serviceco.coex.payment.repository.ARInvoiceTransactionRecRepository;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.repository.MdtParticipantSiteRepository;
import com.serviceco.coex.util.BigDecimalUtility;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Transactional
public class DefaultStateTransitionServiceOptimized {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStateTransitionService.class);

  @Autowired
  DateTimeSupport periodSupport;

  @PersistenceContext
  EntityManager em;

  @Autowired
  PaymentTransactionRecRepository repo;

  @Autowired
  MaterialTypeRepository materialTypeRepo;

  @Autowired
  ARInvoiceTransactionRecRepository arInvoiceRepo;

  @Autowired
  MdtParticipantSiteRepository mdtParticipantSiteRepo;
  
  public StateTransitionSummary transition(StateTransitionRequest request) {

    // find all payment transaction records
    // filter by scheme participant type
    // filter by scheme participant id
    // for each record, set status that has been supplied by the user

    LOGGER.info("transitioning state of payment transactions");
    final Map<String, BigDecimal> internal = new HashMap<>();
    final String NUMBER_OF_PAYMENT_TXN = "NUMBER_OF_PAYMENT_TXN";
    final String NUMBER_OF_SCHEME_PARTICIPANT = "NUMBER_OF_SCHEME_PARTICIPANT";
    final String TOTAL_PAYMENT = "TOTAL_PAYMENT";
    final Set<SchemeParticipantToStateMapper> outgoing = new HashSet<PaymentTransactionRec.SchemeParticipantToStateMapper>();
    Session session = em.unwrap(Session.class);
    MultiIdentifierLoadAccess<PaymentTransactionRec> multiLoadAccess = session.byMultipleIds(PaymentTransactionRec.class);

    final boolean processingForManufacturer =
        request.getSchemeParticipantType().equals(SchemeParticipantType.LRG_MANUFACTURER) || request.getSchemeParticipantType().equals(SchemeParticipantType.SML_MANUFACTURER);

    Scheme scheme = checkAndExtractScheme(request);
    
    // TODO : remove code duplication
    if (processingForManufacturer) {
      final QVProcessablePaymentStatusRecord qvProcessablePaymentRecord = QVProcessablePaymentStatusRecord.vProcessablePaymentStatusRecord;
      final List<VProcessablePaymentStatusRecord> paymentRecords = getQueryFactory().select(qvProcessablePaymentRecord).from(qvProcessablePaymentRecord).fetch();
      if ((null != paymentRecords) && !paymentRecords.isEmpty()) {
        LOGGER.info("fetched processable payment records");
        final List<VProcessablePaymentStatusRecord> recordsFilteredBySchemeParticipant = paymentRecords.stream()
            .filter(record -> StringUtils.equals(request.getSchemeParticipantType().getSupplierType(), record.getSchemeParticipantType())).collect(Collectors.toList());
        final List<SchemeParticipantToStateMapper> incoming = request.getSchemeParticipantToStateMappers();
        internal.put(NUMBER_OF_PAYMENT_TXN, BigDecimal.valueOf(recordsFilteredBySchemeParticipant.size()));
        internal.put(NUMBER_OF_SCHEME_PARTICIPANT, BigDecimal.valueOf(incoming.size()));
        internal.put(TOTAL_PAYMENT, BigDecimal.ZERO);
        for (final SchemeParticipantToStateMapper mapper : incoming) {
          final List<String> paymentTransactionIds = new ArrayList<>();
          final String schemeParticipantId = mapper.getSchemeParticipantId();
          final PaymentStatus status = mapper.getStatus();

          LOGGER.info("setting all payment record status to {}, for scheme participant {}", status, schemeParticipantId);

          final List<VProcessablePaymentStatusRecord> recordsFilteredBySchemeParticipantId = recordsFilteredBySchemeParticipant.stream()
              .filter(record -> StringUtils.equals(record.getSchemeParticipantId(), schemeParticipantId)).collect(Collectors.toList());

          recordsFilteredBySchemeParticipantId.forEach(new Consumer<VProcessablePaymentStatusRecord>() {
            @Override
            public void accept(VProcessablePaymentStatusRecord paymentTransactionLine) {

              final BigDecimal existingVal = internal.get(TOTAL_PAYMENT);
              internal.put(TOTAL_PAYMENT, existingVal.add(paymentTransactionLine.getGrossAmount()));
              paymentTransactionIds.add(paymentTransactionLine.getPaymentTransactionId());
              outgoing.add(new SchemeParticipantToStateMapper(mapper.getSchemeParticipantId(), status));

            }
          });

          List<PaymentTransactionRec> allRecs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);
          allRecs.stream().forEach(r -> r.setStatus(status));
          repo.saveAll(allRecs);
        }

      }
    } else {
      // for AP invoices
      final QVGenericPaymentStatusRecord qvGenericPaymentRecord = QVGenericPaymentStatusRecord.vGenericPaymentStatusRecord;
      final List<VGenericPaymentStatusRecord> paymentRecords = getQueryFactory().select(qvGenericPaymentRecord).from(qvGenericPaymentRecord).fetch();
      if ((null != paymentRecords) && !paymentRecords.isEmpty()) {
        LOGGER.info("fetched processable payment records");
        final List<VGenericPaymentStatusRecord> recordsFilteredBySchemeParticipant = paymentRecords.stream()
            .filter(record -> StringUtils.equals(request.getSchemeParticipantType().getSupplierType(), record.getSchemeParticipantType())).collect(Collectors.toList());
        final List<SchemeParticipantToStateMapper> incoming = request.getSchemeParticipantToStateMappers();

        internal.put(NUMBER_OF_PAYMENT_TXN, BigDecimal.valueOf(recordsFilteredBySchemeParticipant.size()));
        internal.put(NUMBER_OF_SCHEME_PARTICIPANT, BigDecimal.valueOf(incoming.size()));
        internal.put(TOTAL_PAYMENT, BigDecimal.ZERO);
        for (final SchemeParticipantToStateMapper mapper : incoming) {
          final List<String> paymentTransactionIds = new ArrayList<>();
          final String schemeParticipantId = mapper.getSchemeParticipantId();
          final PaymentStatus status = mapper.getStatus();

          LOGGER.info("setting all payment record status to {}, for scheme participant {}", status, schemeParticipantId);

          final List<VGenericPaymentStatusRecord> recordsFilteredBySchemeParticipantId = recordsFilteredBySchemeParticipant.stream()
              .filter(record -> StringUtils.equals(record.getSchemeParticipantId(), schemeParticipantId)).collect(Collectors.toList());

          recordsFilteredBySchemeParticipantId.forEach(new Consumer<VGenericPaymentStatusRecord>() {
            @Override
            public void accept(VGenericPaymentStatusRecord paymentTransactionLine) {

              final BigDecimal existingVal = internal.get(TOTAL_PAYMENT);
              internal.put(TOTAL_PAYMENT, existingVal.add(paymentTransactionLine.getGrossAmount()));
              paymentTransactionIds.add(paymentTransactionLine.getPaymentTransactionId());
              outgoing.add(new SchemeParticipantToStateMapper(mapper.getSchemeParticipantId(), status));
            }
          });
          List<PaymentTransactionRec> allRecs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);
          allRecs.stream().forEach(r -> r.setStatus(status));
          repo.saveAll(allRecs);
        }
      }

      // for AR invoices
      final QVGenericPaymentStatusRecordAR qvGenericPaymentRecordAR = QVGenericPaymentStatusRecordAR.vGenericPaymentStatusRecordAR;
      final List<VGenericPaymentStatusRecordAR> paymentRecordsAR = getQueryFactory().select(qvGenericPaymentRecordAR).from(qvGenericPaymentRecordAR).fetch();
      if ((null != paymentRecordsAR) && !paymentRecordsAR.isEmpty()) {
        LOGGER.info("fetched processable payment records");
        final List<VGenericPaymentStatusRecordAR> recordsFilteredBySchemeParticipant = paymentRecordsAR.stream()
            .filter(record -> StringUtils.equals(request.getSchemeParticipantType().getSupplierType(), record.getSchemeParticipantType())).collect(Collectors.toList());
        final List<SchemeParticipantToStateMapper> incoming = request.getSchemeParticipantToStateMappers();

        internal.put(NUMBER_OF_PAYMENT_TXN, BigDecimal.valueOf(recordsFilteredBySchemeParticipant.size()));
        internal.put(NUMBER_OF_SCHEME_PARTICIPANT, BigDecimal.valueOf(incoming.size()));
        internal.put(TOTAL_PAYMENT, BigDecimal.ZERO);
        for (final SchemeParticipantToStateMapper mapper : incoming) {
          final List<String> paymentTransactionIds = new ArrayList<>();

          final String schemeParticipantId = mapper.getSchemeParticipantId();
          final PaymentStatus status = mapper.getStatus();

          LOGGER.info("setting all payment record status to {}, for scheme participant {}", status, schemeParticipantId);

          final List<VGenericPaymentStatusRecordAR> recordsFilteredBySchemeParticipantId = recordsFilteredBySchemeParticipant.stream()
              .filter(record -> StringUtils.equals(record.getSchemeParticipantId(), schemeParticipantId)).collect(Collectors.toList());

          recordsFilteredBySchemeParticipantId.forEach(new Consumer<VGenericPaymentStatusRecordAR>() {
            @Override
            public void accept(VGenericPaymentStatusRecordAR paymentTransactionLine) {

              final BigDecimal existingVal = internal.get(TOTAL_PAYMENT);
              internal.put(TOTAL_PAYMENT, existingVal.add(paymentTransactionLine.getGrossAmount()));
              paymentTransactionIds.add(paymentTransactionLine.getPaymentTransactionId());
              outgoing.add(new SchemeParticipantToStateMapper(mapper.getSchemeParticipantId(), status));
            }
          });
          List<PaymentTransactionRec> allRecs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);
          allRecs.stream().forEach(r -> r.setStatus(status));
          repo.saveAll(allRecs);
        }
      }
    }

    final double totalPaymentAmount = internal.get(TOTAL_PAYMENT) != null ? BigDecimalUtility.asDouble(internal.get(TOTAL_PAYMENT)) : 0;
    final int numberOfSchemeParticipants = internal.get(NUMBER_OF_SCHEME_PARTICIPANT) != null ? internal.get(NUMBER_OF_SCHEME_PARTICIPANT).intValue() : 0;
    final long numberOfPaymentTransactions = internal.get(NUMBER_OF_PAYMENT_TXN) != null ? internal.get(NUMBER_OF_PAYMENT_TXN).longValue() : 0;

    //@formatter:on
    return new PaymentTransactionRec.StateTransitionSummary(request.getSchemeParticipantType(), numberOfPaymentTransactions, numberOfSchemeParticipants, totalPaymentAmount,
        outgoing, scheme.getId());
  }

  private Scheme checkAndExtractScheme(StateTransitionRequest request) {
    Scheme scheme = null;
    if (request.getSchemeParticipantToStateMappers() != null) {
      for (SchemeParticipantToStateMapper schemeParticipantAndStatus : request.getSchemeParticipantToStateMappers()) {
        Optional<MdtParticipantSite> participantOp = mdtParticipantSiteRepo.findBySiteNumber(schemeParticipantAndStatus.getSchemeParticipantId());
        if (participantOp.isPresent()) {
          MdtParticipantSite participant = participantOp.get();
          if (scheme == null) {
            scheme = participant.getScheme();            
          } else {
            if (!scheme.getId().equals(participant.getScheme().getId())) {
              throw new RuntimeException("The request contains participants across schemes.");
            }
          }
        }
      }
    }
    return scheme;
  }
  
  public JPAQueryFactory getQueryFactory() {

    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

}
