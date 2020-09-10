package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "V_AUCTION_PMT_TXN_REC_AR")
public class VAuctionPaymentTransactionRecAR {

  @Id
  private String id;

  @Column(name = "PAYMENT_TRANSCTION_REC_ID")
  private String paymentTransctionRecId;

  @Column(name = "LOT_ITEM_ID")
  private String lotItem;
}
