/**
 * 
 */
package com.serviceco.coex.payment.service;

import java.util.Base64;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gdata.util.common.base.Preconditions;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec.StateTransitionSummary;
import com.serviceco.coex.rest.support.ObjectMapperFactory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * <p>A service which transitions payment transaction records from their current state to a new one, asynchronously.
 * This is very similar to {@link com.serviceco.coex.payment.service.DefaultStateTransitionService} except it is expected this version
 * posts a result back to a callback URL which is defined in the request.</p>
 * 
 * See {@link #transition}
 *
 */
@Service
@Transactional
public class DefaultStateTransitionServiceAsync {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultStateTransitionServiceAsync.class);

  @Autowired
  PaymentTransactionStateTransitionService service;

  @Value("${oic.client-user-name}")
  private String oicClientUsername;

  @Value("${oic.client-user-password}")
  private String oicClientUserPassword;

  /**
   * 
   * <p>Transitions payment transactions using {@link PaymentTransactionStateTransitionService#transition}.</p>
   * 
   * <p>The result of the processing is posted to the callback URL defined in the request. The body of the posted message
   * contains a {@link com.serviceco.coex.payment.service.DefaultStateTransitionServiceAsync.SummaryAsyncTransactionResult} converted to JSON.
   * </p>
   * 
   * <p>The posted message contains a basic authentication header using the OIC client username and password properties defined within the Spring configuration.</p>
   * 
   * @param request The input data posted to the StateTransitionOfPaymentTransactionAsync web service. Important fields include:
   * @param request.schemeParticipantType The participant type associated with each of the scheme participants which are having their payment transactions updated				
   * @param request.schemeParticipantToStateMappers	Each of the scheme participant IDs which should have their payment transactions updated along with a new payment transaction status for each scheme participant.
   * @param request.callbackUrl The URL to post the result to after the processing completes in the background.
   */
  public void transition(StateTransitionRequest request) {
    
    LOG.info("Initiating state transaction workflow.");
    SummaryAsyncTransactionResult result = new SummaryAsyncTransactionResult();
    
    try {
      StateTransitionSummary transactionSummary = service.transition(request);
      result.setExecutionSummary(transactionSummary);
      LOG.info("State transaction workflow async payment transactions : " + transactionSummary.getNumberOfPaymentTransactions());
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      result.setError(e.toString());
    }
    
    result.setComputeParameters(request);
    LOG.info("State transaction workflow finished. Approaching to invoke callback URL");

    try {
      ObjectMapper mapper = ObjectMapperFactory.getMapperInstance();
      Preconditions.checkArgument(StringUtils.isNotEmpty(request.getCallbackUrl()), "Callback url must not be null or empty");
      LOG.info("State transaction workflow async callback request, callback url is {}", request.getCallbackUrl());

      RestTemplate template = new RestTemplate();
      HttpHeaders header = new HttpHeaders();

      String authHeader = "Basic " + Base64.getEncoder().encodeToString((oicClientUsername + ":" + oicClientUserPassword).getBytes());
      header.set("Authorization", authHeader);

      HttpEntity<SummaryAsyncTransactionResult> entity = new HttpEntity<SummaryAsyncTransactionResult>(result, header);
      LOG.info("State transaction workflow async callback request payload : " + mapper.writeValueAsString(result));
      template.exchange(request.getCallbackUrl(), HttpMethod.POST, entity, String.class);

      LOG.info("State transaction workflow async posted callback request");
    } catch (Exception e) {
      LOG.error("State transaction workflow async callback failed ", result.getExecutionSummary());
      e.printStackTrace();
    }

  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  private static class SummaryAsyncTransactionResult {

    private PaymentTransactionRec.StateTransitionRequest computeParameters;

    private PaymentTransactionRec.StateTransitionSummary executionSummary;

    private String error;
  }

}