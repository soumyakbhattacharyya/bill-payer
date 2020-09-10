
package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.persistence.OldForiegnKeyHelper;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "FORECASTED_SALES_VOLUME")
@Getter
@Setter
public class ForecastedSalesVolume extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "ROLLING_MONTHLY_AVERAGE", nullable = false)
  private BigDecimal rollingMonthlyAverage;

//  @ManyToOne
//  @JoinColumn(name = "MSC_PARTICIPANT_SITE_ID")
//  private MdtParticipantSite schemeParticipant;

  @ManyToOne
  @JoinColumn(name = "MATERIAL_TYPE_ID", referencedColumnName = "ID")
  private MaterialType materialType;

  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
  
  @Transient
  public ForecastedSalesVolumeDTO resource;
  
  @Column(name = "SCHEME_PARTICIPANT_ID")
  //@Setter(AccessLevel.NONE)
  private String schemeParticipantId;
  
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
    //schemeParticipantId = OldForiegnKeyHelper.getSiteNumber(schemeParticipant);
  }

  @Getter
  @Setter
  public static class ForecastedSalesVolumeDTO {

    private String id;

    private String materialTypeId;
    private String schemeParticipantId;
    private String rollingMonthlyAverage;

  }

  public static ForecastedSalesVolume ZERO_VOLUME() {
    final ForecastedSalesVolume forecastedSalesVolume = new ForecastedSalesVolume();
    forecastedSalesVolume.setRollingMonthlyAverage(BigDecimal.ZERO);
    return forecastedSalesVolume;
  }

}
