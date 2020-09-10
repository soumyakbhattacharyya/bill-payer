package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentAggregateView;
import com.serviceco.coex.payment.service.PaymentTransactionAggregationService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.NoRbac;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service implementation for fetching payment transactions linked to a particular payment batch and returning the data in an aggregated form. 
 * See {@link #findAll}
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@NoRbac
@ResourceType("PAYMENT.TRANSACTIONS")
public class PaymentTransactionResource {

  @Autowired
  PaymentTransactionAggregationService aggregationService;

  /**
   * Fetches payment transaction records linked to a particular payment batch and aggregates the data by the scheme participants and payment types.
   * 
   * This calls an implementation of {@code PaymentTransactionAggregationService}. See {@link com.serviceco.coex.payment.service.DefaultAggregationService#aggregate}.
   * 
   * @param schemeParticipantType The scheme participant type associated with the payment records. This is not used for querying the data, but is passed through to the PaymentAggregateView.
   * @param batchId The ID of the payment batch. This is used to find the payment transaction records to report on.
   * @return Returns a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView} containing the generated details including the list of {@code SchemeParticipantPayment}'s and the overall total.
   */
  @GET
  @ActionType("VIEW")
  @Path("/scheme-participants/{schemeParticipantType}/batches/{batchId}")
  public PaymentAggregateView findAll(@PathParam("schemeParticipantType") String schemeParticipantType, @PathParam("batchId") String batchId) {
    return aggregationService.aggregate(SchemeParticipantType.valueOf(schemeParticipantType), batchId);
  }
}
