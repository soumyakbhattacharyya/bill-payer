package com.serviceco.coex.payment.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;

import com.serviceco.coex.model.EntityBaseNoId;
import com.serviceco.coex.model.Scheme;

import lombok.Getter;
import lombok.Setter;

/**
 * A database entity presenting a single batch of payments which is sent to a payment processor.
 *
 */
@Entity(name = "PYMT_BATCHES_ALL")
@Getter
@Setter
public class PymtBatch extends EntityBaseNoId {

  @Id
  @SequenceGenerator(name = "PAY_BATCHES_SEQ", sequenceName = "PAY_BATCHES_SEQ", allocationSize = 1)
  @GeneratedValue(strategy=GenerationType.SEQUENCE, generator="PAY_BATCHES_SEQ")
  @Column(name = "BATCH_ID")
  private Long batchId;
  
  
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
  
  @Column(name = "BATCH_NAME")
  private String batchName;
  
  @Column(name = "BATCH_STATUS")
  private String batchStatus;
  
  @Column(name = "PAYMENT_METHOD")
  private String paymentMethod;
  
  @Column(name = "BATCH_DATE")
  private Date batchDate;
  
  @Column(name = "BATCH_COUNT")
  private long batchCount;
  
  @Column(name = "BATCH_AMOUNT")
  private long batchAmount;
  
  
  @Override
  public Long getPrimaryId() {
    return batchId;
  }

}
