package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAP;

public interface VAuctionPaymentTransactionRecAPRepository extends JpaRepository<VAuctionPaymentTransactionRecAP, String> {

  List<VAuctionPaymentTransactionRecAP> findByLotItem(String lotItemId);
  
}
