package com.serviceco.coex.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;

/**
 * Repository class for accessing {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} entities.
 *
 */
public interface PaymentTransactionRecRepository extends JpaRepository<PaymentTransactionRec, String> {

	/**
	 * Finds a {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} entity which links to a particular
	 * {@link com.serviceco.coex.auction.model.LotItem}
	 * @param lotItemId
	 * @return
	 */
  PaymentTransactionRec findByLotItemId(String lotItemId);

}
