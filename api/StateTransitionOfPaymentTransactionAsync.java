package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.service.DefaultStateTransitionServiceAsync;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service which changes the status of payment transactions according to the input provided, asynchronously.
 * 
 * <p>This is similar to {@link com.serviceco.coex.payment.api.StateTransitionOfPaymentTransaction} except this version executes the processing
 * within a background thread and posts the result back using a callbackUrl which is defined in the request.</p>
 * 
 * See {@link #create}
 * 
 *
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("ASYNC.PAYMENT.WORKFLOW")
public class StateTransitionOfPaymentTransactionAsync {

  private static final Logger LOG = LoggerFactory.getLogger(StateTransitionOfPaymentTransactionAsync.class);

  @Autowired
  DefaultStateTransitionServiceAsync service;

  @Autowired
  private TaskExecutor taskExecutor;

  /**
   * <p>Transitions payment records by changing the status of the records to the one which is specified in the request, asynchronously.</p>
   * 
   * <p>This method starts a new background task which will invoke {@link com.serviceco.coex.payment.service.DefaultStateTransitionServiceAsync#transition}.
   * The result will be posted to the callback URL specified in the request.</p>
   * 
   * @param request The input data posted to the StateTransitionOfPaymentTransactionAsync web service. Important fields include:
   * @param request.schemeParticipantType The participant type associated with each of the scheme participants which are having their payment transactions updated				
   * @param request.schemeParticipantToStateMappers	Each of the scheme participant IDs which should have their payment transactions updated along with a new payment transaction status for each scheme participant.
   * @param request.callbackUrl The URL to post the result to after the processing completes in the background.
   */
  @POST
  @Path("async/workflow")
  @ActionType("TRANSITION_STATE")
  public void create(PaymentTransactionRec.StateTransitionRequest request) {
    LOG.info("State Transaction workflow asyncronous processing.");
    taskExecutor.execute(new IntegrationTask(request));
  }

  /**
   * A runnable class which changes the status of payment transactions in the background.
   *
   */
  public class IntegrationTask implements Runnable {
    PaymentTransactionRec.StateTransitionRequest request;

    public IntegrationTask(PaymentTransactionRec.StateTransitionRequest request) {
      this.request = request;
    }

    /**
     * Changes the status of payment transactions through @{link com.serviceco.coex.payment.service.DefaultStateTransitionServiceAsync#transition}
     */
    @Override
    public void run() {
      service.transition(request);
    }

  }
}
