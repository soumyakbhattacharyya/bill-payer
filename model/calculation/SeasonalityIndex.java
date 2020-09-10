
package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "SEASONALITY_INDEX_REFERENCE")
@Getter
@Setter
public class SeasonalityIndex extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "EFFECTIVE_FROM", nullable = false)
  private Date effectiveFrom;

  @Column(name = "EFFECTIVE_TO")
  private Date effectiveTo;

  @Column(name = "VALUE", nullable = false)
  private BigDecimal value;

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
  
  @Getter
  @Setter
  public static class SeasonalityIndexDTO {
    private String materialTypeId;
    private String effectiveFrom;
    private double value;
  }

  public static SeasonalityIndex ZERO_VALUE() {
    final SeasonalityIndex index = new SeasonalityIndex();
    index.setValue(BigDecimal.ZERO);
    return index;
  }

}
