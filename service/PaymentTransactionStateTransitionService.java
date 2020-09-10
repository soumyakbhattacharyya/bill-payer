package com.serviceco.coex.payment.service;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionSummary;
import com.serviceco.coex.persistence.PersistenceService;

/**
 * An interface for a service which transitions payment transaction records from their current state to a new state according to the
 * input data
 * @see com.serviceco.coex.payment.service.DefaultStateTransitionService
 *
 */
public interface PaymentTransactionStateTransitionService extends PersistenceService {

  StateTransitionSummary transition(StateTransitionRequest request);

}
