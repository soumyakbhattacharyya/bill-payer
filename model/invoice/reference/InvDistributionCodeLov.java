
package com.serviceco.coex.payment.model.invoice.reference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBase;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INV_DISTRIBUTION_CODE_LOV")
@Getter
@Setter
public class InvDistributionCodeLov extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "SCHEME_PARTICIPANT_TYPE", nullable = false, length = 50)
  private String schemeParticipantType;

  @Column(name = "INVOICE_TYPE", nullable = false, length = 10)
  private String invoiceType;

  @Column(name = "PAYMENT_GROUP", nullable = false, length = 50)
  private String paymentGroup;

  @Column(name = "MATERIAL_TYPE_NAME", nullable = false, length = 50)
  private String materialTypeName;

  // This is the GL code in ERP
  @Column(name = "MATERIAL_TYPE_ID", nullable = false, length = 50)
  private String materialTypeId;

  @Column(name = "ACCOUNT_CLASS", nullable = false, length = 100)
  private String accountClass;

  @Column(name = "ENTITY", nullable = false, length = 100)
  private String entity;

  @Column(name = "COST_CENTRE", nullable = false, length = 100)
  private String costCentre;

  @Column(name = "NATURAL_ACC", nullable = false, length = 100)
  private String naturalAcc;

  @Column(name = "INTER_CO_ACC", nullable = false, length = 100)
  private String interCoAcc;

  @Column(name = "SPARE", nullable = false, length = 100)
  private String spare;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;
}
