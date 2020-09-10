package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.serviceco.coex.model.Model;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "V_PAYMENT_APPROVAL_TXN")
@Getter
@Setter
public class VPaymentApprovalTxn implements Model {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @Column(name = "CONSUMER_ID", length = 200)
  private String consumerId;

  @Column(name = "TRANSACTION_DAY")
  private String transactionDay;

}
