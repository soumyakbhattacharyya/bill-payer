package com.serviceco.coex.payment.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.serviceco.coex.model.EntityBaseNoId;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.persistence.OldForiegnKeyHelper;
import com.serviceco.coex.scheme.participant.model.EntityWithActiveDates;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * A database entity which stores the payment profiles (bank, Paypal, etc.) for a particular scheme participant.
 *
 */
@Entity
@Table(name = "PYMT_PAY_SCHEME_PROFILES")
@Getter
@Setter
public class PymtPaySchemeProfile extends EntityBaseNoId implements EntityWithActiveDates {
  
  private static final long serialVersionUID = 1491396531814619625L;

  @Id
  @SequenceGenerator(name = "PAY_SCHEME_PROFILES_SEQ", sequenceName = "PAY_SCHEME_PROFILES_SEQ", allocationSize = 1)
  @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="PAY_SCHEME_PROFILES_SEQ")
  @Column(name = "PAY_SCHEME_PROFILE_ID")
  private Long paySchemeProfileId;
 
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
  
  @Column(name = "PAYMENT_METHOD")
  private String paymentMethod;
  
  /**
   * This is a foreign key to the bank account record in the secure schema. As its in a different schema, there is no direct relationship mapped.
   */
  @Column(name = "BANK_ACCOUNT_ID")
  private long bankAccountId;
  
  @Column(name = "DEFAULT_FLAG")
  private boolean defaultFlag;
  
  @Column(name = "START_DATE_ACTIVE")
  private Date startDateActive;
  
  @Column(name = "END_DATE_ACTIVE")
  private Date endDateActive;
  
  @Column(name = "ACTIVE_FLAG")
  private boolean activeFlag;
  
  @Column(name = "ATTRIBUTE_CATEGORY")
  private String attributeCategroy;
  
  @Column(name = "PAYMENT_LEGAL_ENTITY")
  private String paymentLegalEntity;
  
  @Column(name = "ATTRIBUTE1")
  private String attribute1;
  @Column(name = "ATTRIBUTE2")
  private String attribute2;
  @Column(name = "ATTRIBUTE3")
  private String attribute3;
  @Column(name = "ATTRIBUTE4")
  private String attribute4;
  @Column(name = "ATTRIBUTE5")
  private String attribute5;
  @Column(name = "ATTRIBUTE6")
  private String attribute6;
  @Column(name = "ATTRIBUTE7")
  private String attribute7;
  @Column(name = "ATTRIBUTE8")
  private String attribute8;
  @Column(name = "ATTRIBUTE9")
  private String attribute9;
  @Column(name = "ATTRIBUTE10")
  private String attribute10;
  @Column(name = "ATTRIBUTE11")
  private String attribute11;
  @Column(name = "ATTRIBUTE12")
  private String attribute12;
  @Column(name = "ATTRIBUTE13")
  private String attribute13;
  @Column(name = "ATTRIBUTE14")
  private String attribute14;
  @Column(name = "ATTRIBUTE15")
  private String attribute15;
  
  @Column(name = "SCHEME_ID")
  @Setter(AccessLevel.NONE)
  private String schemeId;
  
  @Column(name = "ERP_PAYMENT_METHOD_CDE")
  private String erpPaymentMethodCode;
 
  /**
   * Called before inserting
   */
  @PrePersist
  public void onPrePersist() {
    doAutoUpdates();
  }
  
  /**
   * Called before updating
   */
  @PreUpdate
  public void onPreUpdate() {
    doAutoUpdates();
  }
 
  /**
   * Sets fields automatically based on other entities prior to inserting or updating
   */
  private void doAutoUpdates() {
    schemeId = OldForiegnKeyHelper.getSchemeId(scheme);
  }
  
  @Override
  public Long getPrimaryId() {
    return paySchemeProfileId;
  }
  

}
