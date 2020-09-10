package com.serviceco.coex.payment.model.calculation;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

/**
 * <p>A record which identifies and keeps track of an instance of computation executed by
 * the ComputationOfPaymentTransaction (or ComputationOfPaymentTransactionAsync) web service.</p> 
 * 
 * <p>This maps to the PAYMENT_BATCH database table.</p>
 *
 */
@Entity
@Table(name = "PAYMENT_BATCH")
@Getter
@Setter
public class PaymentBatch extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "START_TIMESTAMP")
  private Date startTimeStamp;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "END_TIMESTAMP")
  private Date endTimeStamp;

  @Column(name = "STATUS", length = 50)
  @Enumerated(EnumType.STRING)
  private RUN_STATUS status;

  @OneToMany(mappedBy = "paymentBatch", targetEntity = PaymentTransactionRec.class)
  private List<PaymentTransactionRec> listOfPaymentTransactionRec;
  
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
   * The different states possible for a PaymentBatch
   */
  public static enum RUN_STATUS {
    STARTED, IN_PROGRESS, ERROR, ABORT, SUCCESS;
  }

  /**
   * Creates a new PaymentBatch object with a random ID and a start time of now.
   * The object is not persisted by this method.
   * @return
   */
  public static final PaymentBatch _new(Scheme scheme) {
    PaymentBatch paymentBatch = new PaymentBatch();
    paymentBatch.setId(UUID.randomUUID().toString());
    paymentBatch.setStartTimeStamp(Date.from(Instant.now()));
    paymentBatch.setScheme(scheme);
    return paymentBatch;
  }

}
