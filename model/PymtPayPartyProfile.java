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
import javax.persistence.Transient;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import com.serviceco.ces.secure.model.BankAccount;
import com.serviceco.coex.model.EntityBaseNoId;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.scheme.participant.model.EntityWithActiveDates;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.DateUtility;

import lombok.Getter;
import lombok.Setter;

/**
 * A database entity which stores the payment profiles (bank, Paypal, etc.) for a particular participant site (eg. for each consumer).
 *
 */
@Entity(name = "PYMT_PAY_PARTY_PROFILES")
@Getter
@Setter
@Immutable
public class PymtPayPartyProfile extends EntityBaseNoId implements EntityWithActiveDates {

  private static final long serialVersionUID = -2079631190212197010L;

  @Id
  @SequenceGenerator(name = "PYMT_PAY_PARTY_PROFILES_S", sequenceName = "PYMT_PAY_PARTY_PROFILES_S", allocationSize = 1)
  @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="PYMT_PAY_PARTY_PROFILES_S")
  @Column(name = "PAY_PARTY_PROFILE_ID")
  private Long payPartyProfileId;

  @Column(name = "SCHEME_PARTICIPANT_ID")
  private String schemeParticipantId;
  
  @JoinColumn(name = "PARTICIPANT_SITE_ID")
  @ManyToOne
  private MdtParticipantSite site;
 
  @Column(name = "PAYMENT_METHOD")
  private String paymentMethod;
  
  /**
   * This is a foreign key to the bank account record in the secure schema. As its in a different schema, there is no direct relationship mapped.
   */
  @Column(name = "BANK_ACCOUNT_ID")
  private Long bankAccountId;
  
  @Transient
  private BankAccount bankAccount;
  
  @Column(name = "DEFAULT_FLAG")
  @Type(type="yes_no")  // Required to store Y/N rather than 1/0
  private boolean defaultFlag;
  
  @Column(name = "START_DATE_ACTIVE")
  private Date startDateActive;
  
  @Column(name = "END_DATE_ACTIVE")
  private Date endDateActive;
  
  @Column(name = "ATTRIBUTE_CATEGORY")
  private String attributeCategroy;
  
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
  
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

  @Override
  public Long getPrimaryId() {
    return payPartyProfileId;
  }
  
  @PrePersist
  public void onPrePersist() {
    throw new RuntimeException("This PymtPayPartyProfile entity can not be saved directly as its controlled by a DB package API");
  }
  
  @PreUpdate
  public void onPreUpdate() {
    throw new RuntimeException("This PymtPayPartyProfile entity can not be saved directly as its controlled by a DB package API");
  }
  
  public boolean isActive(Date onDate) {
    return DateUtility.isRecordActive(this, onDate);
  }

}
