package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentBatch.RUN_STATUS;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.PaymentBatchExecutionSummary;
import com.serviceco.coex.payment.support.PartitionSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.BigDecimalUtility;

/**
 * <p>An abstract class for payment computation operations. This handles creating a PaymentBatch record to identify and
 * keep track of the processing.</p> 
 * 
 * <p>The process is kicked off by calling {@link #compute}.</p>
 * 
 * <p>The actual processing to be done within the batch is handled by an implementation of the {@link #run} method within a super class.</p>   
 *
 */
@Service
@Qualifier("template")
public abstract class ComputationTemplate extends GenericService implements PaymentTransactionComputationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ComputationTemplate.class);

  // utility service
  /**
   * Handles an exception which was caught during the exception of the run method. Unless overridden,
   * this method logs an error message to a SLF4j logger.
   * @param e
   */
  protected void handle(Exception e) {
    LOGGER.error(" getMessage : " + ExceptionUtils.getMessage(e));
    LOGGER.error(" getRootCauseMessage : " + ExceptionUtils.getRootCauseMessage(e));
    LOGGER.error(" getThrowableCount : " + ExceptionUtils.getThrowableCount(e));
    LOGGER.error(" getThrowables : ");

    for (final Throwable element : ExceptionUtils.getThrowables(e)) {
      LOGGER.error(element.getMessage());
    }

    LOGGER.error(" indexOfThrowable(e,RuntimeException.class) : " + ExceptionUtils.indexOfThrowable(e, RuntimeException.class));
    LOGGER.error(" indexOfThrowable(e,Throwable.class) : " + ExceptionUtils.indexOfThrowable(e, Throwable.class));
    LOGGER.error(" indexOfType(e,RuntimeException.class) : " + ExceptionUtils.indexOfType(e, RuntimeException.class));
    LOGGER.error(" indexOfType(e,Throwable.class) : " + ExceptionUtils.indexOfType(e, Throwable.class));
    LOGGER.error(" getRootCause : " + ExceptionUtils.getRootCause(e));
    LOGGER.error(" getRootCauseStackTrace : ");

    for (final String element : ExceptionUtils.getRootCauseStackTrace(e)) {
      LOGGER.error(element);
    }

    throw new RuntimeException(e);
  }

  /**
   * Generates a summary containing details about the payment batch, the payment transactions generated and the number of scheme participants associated
   * with the payments.
   * @param request 	 The input data passed into the {@code ComputationOfPaymentTransaction} web service for the payment computation.	
   * @param paymentBatch	The {@code PaymentBatch} record which identifies the current batch and tracks the status of processing. This was created by {@link #compute}.
   * @param paymentTransactionRecords The payment transaction records which were generated
   * @return Returns the summary in a {@link PaymentTransactionRec.PaymentBatchExecutionSummary}
   */
  protected PaymentTransactionRec.PaymentBatchExecutionSummary summarize(PaymentCalculationRequest request, PaymentBatch paymentBatch, List<PaymentTransactionRec> paymentTransactionRecords) {

    Scheme scheme = paymentBatch.getScheme();
    
    if ((paymentTransactionRecords != null) && !paymentTransactionRecords.isEmpty()) {
      final long numberOfPaymentTransactions = paymentTransactionRecords.stream().count();
      BigDecimal totalPaymentAmount = BigDecimal.ZERO;
      for (PaymentTransactionRec rec : paymentTransactionRecords) {
        totalPaymentAmount = totalPaymentAmount.add(rec.getGrossAmount());
      }
      
      //@formatter:off
      final int numberOfSchemeParticipants = paymentTransactionRecords.stream()
                                                                .filter(PaymentTransactionRec.distinctByKey(PaymentTransactionRec::getSchemeParticipantId))
                                                                .collect(Collectors.toList()).size();
      
      return new PaymentTransactionRec.PaymentBatchExecutionSummary(paymentBatch.getId(), 
                                                                    paymentBatch.getStatus(), 
                                                                    PaymentBatchExecutionSummary.dateFormatter(paymentBatch.getStartTimeStamp()), 
                                                                    PaymentBatchExecutionSummary.dateFormatter(paymentBatch.getEndTimeStamp()), 
                                                                    numberOfPaymentTransactions, 
                                                                    numberOfSchemeParticipants,
                                                                    BigDecimalUtility.asDouble(totalPaymentAmount),
                                                                    assertPaymentPeriod(request.getPaymentMetadata(), scheme).toString(),
                                                                    paymentBatch.getScheme().getId());
    } else {
      return new PaymentTransactionRec.PaymentBatchExecutionSummary(paymentBatch.getId(), 
                                                                    paymentBatch.getStatus(), 
                                                                    PaymentBatchExecutionSummary.dateFormatter(paymentBatch.getStartTimeStamp()), 
                                                                    PaymentBatchExecutionSummary.dateFormatter(paymentBatch.getEndTimeStamp()), 
                                                                    new Long(0), 
                                                                    new Integer(0),
                                                                    new Double(0),
                                                                    assertPaymentPeriod(request.getPaymentMetadata(), scheme).toString(),
                                                                    paymentBatch.getScheme().getId());
      //@formatter:on
    }
  }

  // unmodifiable computation algorithm
  /**
   * <p>Begins the processing of a payment computation request.</p> 
   * <p>A new {@link com.serviceco.coex.payment.model.calculation.PaymentBatch} record is initially created with the status {@code STARTED}.</p>
   * <p>The {@link #run} method is then execute to perform the actual processing of the request.</p>
   * <p>If there are any exceptions thrown from the run method, the {@code PaymentBatch} record is updated with the status {@code ERROR}. The {@link #handle} method is also called to handle the exception.</p>
   * <p>If there are no exceptions caught during the processing of the run method, the {@code PaymentBatch} record is updated with the status {@code SUCCESS}. </p>
   */
  @Override
  @Transactional
  public PaymentBatchExecutionSummary compute(PaymentCalculationRequest request, Scheme scheme) {
    validate(request);
    final PaymentBatch instance = mark(PaymentBatch._new(scheme), PaymentBatch.RUN_STATUS.STARTED);
    boolean error = false;
    List<PaymentTransactionRec> records = new ArrayList<>();
    try {
      records = run(instance, request);
    } catch (final Exception ex) {
      error = true;
      handle(ex);
    } finally {
      if (error) {
        mark(instance, PaymentBatch.RUN_STATUS.ERROR);
      } else {
        mark(instance, PaymentBatch.RUN_STATUS.SUCCESS);
      }
    }
    return summarize(request, instance, records);
  }

  private PaymentBatch mark(PaymentBatch instance, PaymentBatch.RUN_STATUS status) {
    if (null == instance.getStartTimeStamp()) {
      instance.setStartTimeStamp(Date.from(Instant.now()));
    }
    if ((null == instance.getEndTimeStamp()) && ((status == RUN_STATUS.SUCCESS) || (status == RUN_STATUS.ERROR) || (status == RUN_STATUS.ABORT))) {
      instance.setEndTimeStamp(Date.from(Instant.now()));
    }
    instance.setStatus(status);
    return super.paymentBatchRepository.save(instance);
  }

  public abstract List<PaymentTransactionRec> run(PaymentBatch paymentBatch, PaymentCalculationRequest request);

  protected abstract void validate(PaymentCalculationRequest request);

  /**
   * Determines which scheme participants should be looked at during processing.
   * The list of scheme participants is either: 
   * (a) All scheme participants in the schemeParticipants list (if request.include = true or  null)
   * (b) All scheme participants in the allSchemeParticipants list excluding those in request.schemeParticipantIds (if request.include = false)
   * (c) All scheme participants in allSchemeParticipants (if request.schemeParticipantIds is null or empty)
   * @param request
   * @param period
   * @param scheme
   * @return
   */
  public List<MdtParticipantSite> partitionByDeclaration(PaymentCalculationRequest request, Scheme scheme) {
    final List<MdtParticipantSite> allSchemeParticipants = fetchSchemeParticipants(request.getSchemeParticipantType(), scheme);
    final List<MdtParticipantSite> userSuppliedSchemeParticipant = fetchSchemeParticipants(allSchemeParticipants, request.getSchemeParticipantIds());

    // partition
    //@formatter:off
    final List<MdtParticipantSite> schemeParticipantsToLookAt = PartitionSupport.builder()
                                                                                                     .allSchemeParticipants(allSchemeParticipants)
                                                                                                     .schemeParticipants(userSuppliedSchemeParticipant)
                                                                                                     .include(Optional.ofNullable(request.isInclude()))
                                                                                                     .build()
                                                                                                     .partition();
    //@formatter:on
    return schemeParticipantsToLookAt;
  }

  protected abstract Period assertPaymentPeriod(PaymentMetadata paymentMetadata, Scheme scheme);

}
