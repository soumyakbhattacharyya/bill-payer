package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.persistence.OldForiegnKeyHelper;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "RECOVERY_FEE_REFERENCE")
public class RecoveryFeeReference extends EntityBase {

  private static final long serialVersionUID = 1L;
  
  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;

  @ManyToOne
  @JoinColumn(name = "MSC_PARTICIPANT_SITE_ID")
  private MdtParticipantSite mrf;

  @ManyToOne
  @JoinColumn(name = "MATERIAL_TYPE_ID", referencedColumnName = "ID")
  private MaterialType materialType;

  @Column(name = "RECOVERY_FEE", nullable = false)
  private BigDecimal recoveryFee;

  @Column(name = "EFFECTIVE_FROM", nullable = false)
  private Date effectiveFrom;

  @Column(name = "EFFECTIVE_TO")
  private Date effectiveTo;

  @Column(name = "SCHEME_ID")
  @Setter(AccessLevel.NONE)
  private String schemeId;
  
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

  public static RecoveryFeeReference ZERO_VALUE() {
    final RecoveryFeeReference ref = new RecoveryFeeReference();
    ref.setRecoveryFee(BigDecimal.ZERO);
    return ref;
  }
}
