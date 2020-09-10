package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAR;

public interface VAuctionPaymentTransactionRecARRepository extends JpaRepository<VAuctionPaymentTransactionRecAR, String> {

  List<VAuctionPaymentTransactionRecAR> findByLotItem(String lotItem);

}
