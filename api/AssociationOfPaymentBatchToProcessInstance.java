package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceco.coex.payment.api.request.AssociationRequest;
import com.serviceco.coex.payment.service.PaymentTransactionHelperService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.NoRbac;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service which only contains a single deprecated 'associate' method which has no effect. 
 *
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_BATCHES)
@Produces("application/json")
@Consumes("application/json")
@NoRbac
@ResourceType("PAYMENT.BATCH")
public class AssociationOfPaymentBatchToProcessInstance {

  @Autowired
  PaymentTransactionHelperService paymentHelper;

  /**
   * Deprecated as this contains commented out code and has no net effect on anything.
   * @param associationRequest The input data
   */
  @POST
  @ActionType("CREATE")
  @Path("/associate")
  @Deprecated
  public void associate(AssociationRequest associationRequest) {
    paymentHelper.associate(associationRequest);
  }
}
