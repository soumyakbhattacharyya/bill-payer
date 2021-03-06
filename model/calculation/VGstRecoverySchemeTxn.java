package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import com.serviceco.coex.model.Model;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "V_GST_RECOVERY_SCHEME_TXN")
@Getter
@Setter
public class VGstRecoverySchemeTxn implements Model {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  
  @Id
  private String id;

  @Column(name = "CRP_ID", length = 200)
  private String crpId;

  @Column(name = "TRANSACTION_WEEK", length = 8)
  private String transactionWeek;

  @Column(name = "MATERIAL_TYPE_ID", nullable = false, length = 50)
  private String materialTypeId;

  @Column(name = "GST_RECOVERY_SUM")
  private BigDecimal gstRecoverySum;
  
  @Column(name = "VOLUME")
  private BigDecimal volume;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

}
