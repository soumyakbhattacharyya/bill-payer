package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionSummary;
import com.serviceco.coex.payment.service.PaymentTransactionStateTransitionService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service which changes the status of payment transactions according to the input provided.
 * 
 * See {@link #create}
 *
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("PAYMENT.WORKFLOW")
public class StateTransitionOfPaymentTransaction {

  @Autowired
  PaymentTransactionStateTransitionService service;

  /**
   * Transitions payment records by changing the status of the records to the one which is specified in the request.
   * 
   * The actual work is delegated to {@link com.serviceco.coex.payment.service.DefaultStateTransitionService}
   * 
   * @param request
   * @return
   */
  @POST
  @Path("/workflow")
  @ActionType("TRANSITION_STATE")
  public StateTransitionSummary create(PaymentTransactionRec.StateTransitionRequest request) {
    return service.transition(request);
  } 

}
