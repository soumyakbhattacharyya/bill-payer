package com.serviceco.coex.payment.service;

import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gdata.util.common.base.Preconditions;
import com.serviceco.coex.exception.CoexRuntimeException;
import com.serviceco.coex.exception.model.ExceptionConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.rest.support.ObjectMapperFactory;
import com.serviceco.coex.scheme.participant.service.SchemeService;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * A service which generates payment transactions based on available volume data.
 * This is similar to the {@link PaymentTransactionComputationService}, except it is designed to execute
 * within a background thread. The result of the processing will be posted back to a callback URL.
 * 
 * See {@link #compute}.
 *
 */
@Service
@Slf4j
public class PaymentTransactionAsyncComputationService {

  @Autowired
  @Qualifier("default")
  PaymentTransactionComputationService computationService;

  @Autowired
  @Qualifier("auction")
  PaymentTransactionComputationService auctionComputationService;
  
  @Autowired
  SchemeService schemeService;

  @Value("${oic.client-user-name}")
  private String oicClientUsername;

  @Value("${oic.client-user-password}")
  private String oicClientUserPassword;

  /**
   *
   * Generates payment transactions based on available volume data. This is expected to be called by a background thread. The result of the processing
   * is posted back to a callback URL which is provided in the request.
   * 
   * <p>The actual processing work is done by an instance of {@code PaymentTransactionComputationService}.<br>
   * For auction payments, see {@link AuctionComputationTemplateImpl#compute}.<br>
   * For non-auction payments, see {@link DefaultComputationTemplateImpl#compute}.
   * </p>
   *  
   * <p>The posted message contains a basic authentication header using the OIC client username and password properties defined within the Spring configuration.</p>
   * 
   * <p>The body of the posted message will contain a {@link com.serviceco.coex.payment.service.PaymentTransactionAsyncComputationService.PaymentAsyncComputationResult} converted to JSON.</p>
   * 
   * @param request The JSON request body converted into a GenericRequest object. This includes the following data:
   * @param request.schemeParticipantIds The participants to create payment transactions for, or the participants to exclude (see request.include).
   * @param request.schemeParticipantType The scheme participant type associated with the participants.
   * @param request.auctionLotIdentifier For auction payments only. This specifies the LotItem identifier.
   * @param request.auctionLotItemManifestId For auction payments only. This specifies the LotItemManifest identifier.
   * @param request.include If true, payment transactions are generated for the scheme participants passed in. If false, payment transactions are generated for all of the scheme participants associated with the scheme participant type EXCLUDING the scheme participants passed in.
   * @param request.scheme Required for non-auction payments. If provided, only payments for the specified scheme will be processed. If not provided, payments for all schemes will be processed, but in separate batches. 
   * @param request.callbackUrl The URL to post the result back to.
   */
  public void compute(PaymentCalculationRequest request) {

    
    String requestedSchemeId = request.getSchemeId();
    if (request.getAuctionLotIdentifier() != null) {
      executeForScheme(request, null);
    }
    else if (requestedSchemeId != null) {
      Scheme scheme = schemeService.getById(requestedSchemeId).orElseThrow(() -> new CoexRuntimeException(ExceptionConstants.ERROR_CODES.VALIDATION, null, "Scheme ID is not valid"));
      executeForScheme(request, scheme);
    } else {
      for (Scheme scheme : schemeService.getAll()) {
        executeForScheme(request, scheme);
      }
    }
  }

  private void executeForScheme(PaymentCalculationRequest request, Scheme scheme) {
    if (scheme != null) {
      log.info("Initiating payment computation for scheme " + scheme.getName());
    } else {
      log.info("Initiating payment computation for auction lot item " + request.getAuctionLotIdentifier());
    }
    PaymentAsyncComputationResult result = new PaymentAsyncComputationResult();
    try {
      PaymentTransactionRec.PaymentBatchExecutionSummary executionSummary = null;
      if (StringUtils.isNotEmpty(request.getAuctionLotIdentifier())) {
        executionSummary = auctionComputationService.compute(request, scheme);
      } else {
        executionSummary = computationService.compute(request, scheme);
      }
      result.setExecutionSummary(executionSummary);
      log.info("Payment batch id : " + executionSummary.getPaymentBatchId());
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      result.setError(e.toString());
    }
    result.setComputeParameters(request);
    if (scheme != null) {
      log.info("Payment computation for scheme " + scheme.getName() + " finished. Approaching to invoke callback URL");
    } else {
      log.info("Payment computation for for auction lot item " + request.getAuctionLotIdentifier() + " finished. Approaching to invoke callback URL");
    }
    
    sendResult(request, result);
  }

  private void sendResult(PaymentCalculationRequest request, PaymentAsyncComputationResult result) {
    try {
      ObjectMapper mapper = ObjectMapperFactory.getMapperInstance();
      Preconditions.checkArgument(StringUtils.isNotEmpty(request.getCallbackUrl()), "Callback url must not be null or empty");
      log.info("Payment callback request, callback url is {}", request.getCallbackUrl());

      RestTemplate template = new RestTemplate();
      HttpHeaders header = new HttpHeaders();

      String authHeader = "Basic " + Base64.getEncoder().encodeToString((oicClientUsername + ":" + oicClientUserPassword).getBytes());
      header.set("Authorization", authHeader);
      header.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<PaymentAsyncComputationResult> entity = new HttpEntity<PaymentAsyncComputationResult>(result, header);
      log.info("Payment callback request payload : " + mapper.writeValueAsString(result));

      template.exchange(request.getCallbackUrl(), HttpMethod.POST, entity, String.class);
      
      // Note: In SIT (gen 1), the callback URL OIC provided was: https://sitoic01ic-coexservices01.aucom-east-1.oraclecloud.com:443/ic/api/integration/v1/flows/rest/PAYMENT_PROCESSOR_ASYNC_CB/1.0/payment-processor/async/callback
      // It expects JSON.

      log.info("Payment computation posted callback request");
    } catch (Exception e) {
      log.error("Payment computation callback failed for payment batch id {}", result.getExecutionSummary().getPaymentBatchId());
      e.printStackTrace();
    }
  }

  /**
   * Contains the result of a payment computation operation which occurred asynchronously. This is posted back to 
   * a callback URL.
   * 
   * <p>It contains a summary (@{link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.PaymentBatchExecutionSummary}) of the payment computation batch, an error (if there was one) and the original request which was processed.</p>
   */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  private static class PaymentAsyncComputationResult {

    private PaymentTransactionRec.PaymentBatchExecutionSummary executionSummary;

    private String error;

    private PaymentCalculationRequest computeParameters;

  }

}

