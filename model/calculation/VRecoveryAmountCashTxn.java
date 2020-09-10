package com.serviceco.coex.payment.model.calculation;

import com.serviceco.coex.model.Model;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "V_RECOVERY_AMOUNT_CASH_TXN")
@Getter
@Setter
public class VRecoveryAmountCashTxn implements Model {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @Column(name = "CRP_ID", length = 200)
  private String crpId;

  @Column(name = "TRANSACTION_WEEK", length = 8)
  private String transactionWeek;

  @Column(name = "MATERIAL_TYPE_ID", nullable = false, length = 50)
  private String materialTypeId;

  @Column(name = "VOLUME")
  private BigDecimal volume;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

  @Transient
  private BigDecimal grossAmountSum;

}
