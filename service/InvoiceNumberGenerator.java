package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionNumberSeq;
import com.serviceco.coex.payment.model.invoice.QInvoiceTransactionNumberSeq;
import com.serviceco.coex.payment.repository.InvoiceTransactionNumberSeqRepository;

@Component
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class InvoiceNumberGenerator {

  @Autowired
  private InvoiceTransactionNumberSeqRepository invoiceTransactionNumberSeqRepository;

  @PersistenceContext
  private EntityManager em;

  private JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  public Long createOrFindTransactionNumber() {

    InvoiceTransactionNumberSeq invoiceTransaction = getInvoiceTransactionSequence();
    if (null != invoiceTransaction) {
      invoiceTransaction.setInvoiceTransactionNumber(invoiceTransaction.getInvoiceTransactionNumber().add(new BigDecimal(1)));
      invoiceTransaction = invoiceTransactionNumberSeqRepository.save(invoiceTransaction);
    } else {
      invoiceTransaction = createInvoiceTransactionNumber();
    }
    return invoiceTransaction.getInvoiceTransactionNumber().longValue();
  }

  private InvoiceTransactionNumberSeq getInvoiceTransactionSequence() {

    final QInvoiceTransactionNumberSeq qInvoiceTransactionNumberSeq = QInvoiceTransactionNumberSeq.invoiceTransactionNumberSeq;
    final InvoiceTransactionNumberSeq currentSequenceNumber = getQueryFactory()
    //@formatter:off    
                                                                        .select(qInvoiceTransactionNumberSeq)
                                                                        .from(qInvoiceTransactionNumberSeq)
                                                                        .fetchOne();
    //@formatter:on

    return currentSequenceNumber;

  }

  protected InvoiceTransactionNumberSeq createInvoiceTransactionNumber() {

    final InvoiceTransactionNumberSeq invoiceTransaction = new InvoiceTransactionNumberSeq();
    invoiceTransaction.setId(UUID.randomUUID().toString());
    invoiceTransaction.setInvoiceTransactionNumber(BigDecimal.valueOf(1000000000));

    return invoiceTransactionNumberSeqRepository.save(invoiceTransaction);
  }
}
