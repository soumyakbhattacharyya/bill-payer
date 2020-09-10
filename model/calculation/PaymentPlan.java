
package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "PAYMENT_PLAN")
@Getter
@Setter
public class PaymentPlan extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "PERIOD", length = 50)
  private String period;

  @Column(name = "BILL_CYCLE_DAY", length = 50)
  private String billCycleDay;

  @Column(name = "CURRENCY", length = 50)
  private String currency;

  @Column(name = "UNIT", length = 50)
  private String unit;

  @ManyToOne
  @JoinColumn(name = "SCHEME_PARTICIPANT_ID", referencedColumnName = "SITE_NUMBER")
  private MdtParticipantSite schemeParticipant;

  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
}
