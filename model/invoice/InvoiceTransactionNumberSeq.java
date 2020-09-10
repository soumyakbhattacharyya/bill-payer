package com.serviceco.coex.payment.model.invoice;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "INV_TRANSACTION_NUMBER_SEQ", uniqueConstraints = @UniqueConstraint(columnNames = { "INVOICE_TXN_NUMBER" }))
public class InvoiceTransactionNumberSeq extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "INVOICE_TXN_NUMBER")
  private BigDecimal invoiceTransactionNumber;

}
