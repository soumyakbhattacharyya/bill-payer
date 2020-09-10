package com.serviceco.coex.payment.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.serviceco.coex.exception.CoexRuntimeException;
import com.serviceco.coex.exception.model.ExceptionConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.PaymentBatchExecutionSummary;
import com.serviceco.coex.payment.service.PaymentTransactionComputationService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;
import com.serviceco.coex.scheme.participant.service.SchemeService;

/**
 * A REST web service for generating payment transaction records based on volume data which is ready for processing.
 * 
 * See {@link #create}.
 *
 */
@Component
@Path(ResourceConstants.URLS.PAYMENT_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("PAYMENT.COMPUTATION")
public class ComputationOfPaymentTransaction {

  @Autowired
  @Qualifier("default")
  PaymentTransactionComputationService computationService;

  @Autowired
  @Qualifier("auction")
  PaymentTransactionComputationService auctionComputationService;
  
  @Autowired
  SchemeService schemeService;

  /**
   * <p>Creates payment transaction records for a specific scheme participant type (and optionally particular scheme participants) based on available or forecasted volume/claim data.</p>
   * <p>
   * This forwards the request on to instances of PaymentTransactionComputationService.
   * If the request contains an auction lot identifier, the request is forwarded to {@link com.serviceco.coex.payment.service.AuctionComputationTemplateImpl}.
   * For everything else, the request goes to {@link com.serviceco.coex.payment.service.DefaultComputationTemplateImpl}.
   * </p>
   * @param request JSON request converted into a GenericRequest object. This includes the following data:
   * @param request.schemeParticipantIds The participants site numbers to create payment transactions for, or the participants to exclude (see request.include).
   * @param request.schemeParticipantType The scheme participant type you want to generate payment transaction records for.
   * @param request.auctionLotIdentifier For auction payments only. This specifies the LotItem identifier.
   * @param request.auctionLotItemManifestId For auction payments only. This specifies the LotItemManifest identifier.
   * @param request.include If true, payment transactions are generated for the scheme participants passed in. If false, payment transactions are generated for all of the scheme participants associated with the scheme participant type EXCLUDING the scheme participants passed in.
   * @param request.scheme Not used. A hard coded string "QLD" is used to lookup the scheme instead.
   * 
   * @return Returns a PaymentBatchExecutionSummary containing the number of transactions, number of participants and the total payment amount and
   * 			details about the batch execution.
   * 
   * @see com.serviceco.coex.payment.service.AuctionComputationTemplateImpl
   * @see com.serviceco.coex.payment.service.DefaultComputationTemplateImpl
   * 
   */
  @POST
  @Path("/compute")
  @ActionType("CREATE")
  public List<PaymentBatchExecutionSummary> create(PaymentCalculationRequest request) {
    String requestedSchemeId = request.getSchemeId();
    // For auctions, the scheme is determined based on the lot item identifier passed in. The scheme ID in the request is ignored.
    if (request.getAuctionLotIdentifier() != null) {
      PaymentBatchExecutionSummary summary = executeForScheme(request, null);
      return Collections.singletonList(summary); 
    }
    else if (requestedSchemeId != null) {
      Scheme scheme = schemeService.getById(requestedSchemeId).orElseThrow(() -> new CoexRuntimeException(ExceptionConstants.ERROR_CODES.VALIDATION, null, "Scheme ID is not valid"));
      PaymentBatchExecutionSummary summary = executeForScheme(request, scheme);
      return Collections.singletonList(summary);
    }
    List<PaymentBatchExecutionSummary> summaries = new ArrayList<PaymentBatchExecutionSummary>();
    for (Scheme scheme : schemeService.getAll()) {
      PaymentBatchExecutionSummary summary = executeForScheme(request, scheme);
      summaries.add(summary);
    }
    return summaries;
  }

  private PaymentBatchExecutionSummary executeForScheme(PaymentCalculationRequest request, Scheme scheme) {
    if (StringUtils.isNotEmpty(request.getAuctionLotIdentifier())) {
      return auctionComputationService.compute(request, scheme);
    }
    return computationService.compute(request, scheme);
  }

}
