/**
 * 
 */
package com.serviceco.coex.payment.service;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentAggregateView;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentTransactionRec;
import com.serviceco.coex.payment.support.DateTimeSupport;

/**
 * <p>An implementation of a {@code PaymentTransactionAggregationService} which aggregates payment transaction
 * records for a particular payment batch.</p>
 * 
 * <p>The transactions are aggregated by the scheme participants and the payment methods.</p>
 *
 * See {@link #aggregate}
 *
 */
@Service
@Transactional
public class DefaultAggregationService implements PaymentTransactionAggregationService {

  private static final Logger logger = LoggerFactory.getLogger(DefaultAggregationService.class);

  @Autowired
  DateTimeSupport periodSupport;

  @PersistenceContext
  EntityManager em;

  /**
   * <p>Fetches all payment transaction records ({@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec}) for a given payment batch ID 
   * and passes them on to a new {@code PaymentAggregateView}.</p>
   * 
   * <p>For details of the aggregation process see {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView#of}.</p>
   * 
   * @param schemeParticipantType The scheme participant type associated with the payment records. This is not used for querying the data, but is passed through to the PaymentAggregateView.
   * @param batchId The ID of the payment batch. This is used to find the payment transaction records to report on. 
   * 
   * @return Returns a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView} containing the generated details including the list of {@code SchemeParticipantPayment}'s and the overall total. 
   */
  @Override
  public PaymentAggregateView aggregate(SchemeParticipantType schemeParticipantType, String batchId) {
    logger.info("querying payment records for {}", schemeParticipantType);
    final QPaymentTransactionRec qPaymentTransactionRec = QPaymentTransactionRec.paymentTransactionRec;
    final List<PaymentTransactionRec> records = getQueryFactory().select(qPaymentTransactionRec).from(qPaymentTransactionRec)
        .where(qPaymentTransactionRec.paymentBatch.id.eq(batchId)).fetch();
    
    return PaymentAggregateView.of(schemeParticipantType, records);
  }

  public JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

}
