package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;

/**
 * A base interface for a component which is able to generate payment transaction records based on a type of volume data (and other reference data).
 * @param <T> The entity class which contains the source volume / claim data which will be used to generate payment transactions.
 */
public interface CalculationSupport<T> {

  /**
   * Calculates payment transactions using actual volume data (not forecasts).
   * @param param The volume data and other input parameters.
   * @return Returns a list of the payment transactions created
   */
  List<PaymentTransactionRec> calculateViaActual(CalculationParameter<T> param);

}