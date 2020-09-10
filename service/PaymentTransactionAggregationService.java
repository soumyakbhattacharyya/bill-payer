package com.serviceco.coex.payment.service;

import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentAggregateView;

/**
 * An interface for a service which aggregates payment transaction records and returns
 * the result.
 * 
 * @see com.serviceco.coex.payment.service.DefaultAggregationService
 * @see com.serviceco.coex.payment.api.PaymentTransactionResource
 *
 */
public interface PaymentTransactionAggregationService {

  PaymentAggregateView aggregate(SchemeParticipantType schemeParticipantType, String batchId);

//  PaymentAggregateView aggregate(SchemeParticipantType schemeParticipantType, Predicate<? super PaymentTransactionRec> predicate);
//
//  PaymentAggregateView aggregate(SchemeParticipantType schemeParticipantType, Period period);
//
//  PaymentAggregateView aggregate(SchemeParticipantType schemeParticipantType, Period period, Status status);

}
