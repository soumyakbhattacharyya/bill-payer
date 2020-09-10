package com.serviceco.coex.payment.model.invoice.reference;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.persistence.OldForiegnKeyHelper;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "INV_TAX_CLASSIFICATION_REF")
@Getter
@Setter
public class InvTaxClassificationRef extends EntityBase {

  /**
   * 
   */
  private static final long serialVersionUID = -1154750348294447579L;

  @ManyToOne
  @JoinColumn(name = "MSC_PARTICIPANT_SITE_ID")
  private MdtParticipantSite schemeParticipant;

  @Column(name = "SCHEME_PARTICIPANT_ID")
  @Setter(AccessLevel.NONE)
  private String schemeParticipantId;
  
  @Column(name = "INVOICE_TYPE", nullable = false, length = 50)
  private String invoiceType;

  @Column(name = "PAYMENT_TYPE", nullable = false, length = 100)
  private String paymentType;

  @Column(name = "VALUE", nullable = false, length = 100)
  private String value;
  
  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

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
    schemeParticipantId = OldForiegnKeyHelper.getSiteNumber(schemeParticipant);
  }
}
