package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.serviceco.coex.abac.strategy.SecurityAccessContext;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.service.PaymentTransactionAsyncComputationService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.MessageContextAccessor;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service for generating payment transaction records asynchronously.
 * 
 * See {@link #create}.
 * 
 * @see com.serviceco.coex.payment.api.ComputationOfPaymentTransaction
 *
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("ASYNC.PAYMENT.COMPUTATION")
public class ComputationOfPaymentTransactionAsync {

  @Autowired
  private PaymentTransactionAsyncComputationService asyncComputationService;

  @Autowired
  private TaskExecutor taskExecutor;

  /**
   * <p>Creates payment transaction records for a specific scheme participant type (and optionally particular scheme participants) based on available or forecasted volume/claim data.</p>
   * 
   * <p>This works in a similar way to {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction#create}, except it starts 
   * the processing within a background thread and returns immediately without waiting for the result. The 
   * result will end up being posted back using a callbackUrl which is defined in the request.</p>
   *  
   * <p>
   * The actual processing done within the background thread is defined in {@link com.serviceco.coex.payment.service.PaymentTransactionAsyncComputationService#compute}.
   * </p>
   * @param request The input data provided within the body of the web service message. This includes the following data:
   * @param request.schemeParticipantIds The participant site numbers to create payment transactions for, or the participants to exclude (see request.include).
   * @param request.schemeParticipantType The scheme participant type associated with the participants.
   * @param request.auctionLotIdentifier For auction payments only. This specifies the LotItem identifier.
   * @param request.auctionLotItemManifestId For auction payments only. This specifies the LotItemManifest identifier.
   * @param request.include If true, payment transactions are generated for the scheme participants passed in. If false, payment transactions are generated for all of the scheme participants associated with the scheme participant type EXCLUDING the scheme participants passed in.
   * @param request.scheme Not used. A hard coded string "QLD" is used to lookup the scheme instead.
   * @param request.callbackUrl The URL to post the result to. 
   * 
   * @see com.serviceco.coex.payment.service.PaymentTransactionAsyncComputationService
   * @see com.serviceco.coex.payment.api.ComputationOfPaymentTransaction
   */
  @POST
  @Path("async/compute")
  @ActionType("CREATE")
  public void create(PaymentCalculationRequest request) {

    taskExecutor.execute(new IntegrationTask(MessageContextAccessor.current(), request));
  }

  /**
   * A runnable task for generating payment transactions in the background.
   *
   */
  private class IntegrationTask implements Runnable {

    private PaymentCalculationRequest request;

    private SecurityAccessContext context;

    public IntegrationTask(SecurityAccessContext context, PaymentCalculationRequest request) {

      this.context = context;
      this.request = request;
    }

    @Override

    /**
     * Generates payment transactions through {@link com.serviceco.coex.payment.service.PaymentTransactionAsyncComputationService#compute}
     */
    public void run() {

      MessageContextAccessor.ThreadLocalAccessor.set(this.context);
      asyncComputationService.compute(request);
    }

  }

}
