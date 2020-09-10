package com.serviceco.coex.payment.api.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.constant.AuctionType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.Getter;
import lombok.Setter;

/**
 * A payment calculation API request (either through the synchronous or asynchronous API)
 *
 */
@Getter
@Setter
public class PaymentCalculationRequest {
  
  /**
   * mandatory argument, represents a type of scheme participants
   */
  private SchemeParticipantType schemeParticipantType;

  /**
   * optional filter, by default the value SHOULD be new String["ALL"]; or else should consists of scheme participant ids on which the calculation and invoicing engine should act
   */
  private List<String> schemeParticipantIds;

  /**
   * optional filter, by default the value should be true; indicating inclusion for {@link MdtParticipantSite ids}; false meaning opposite
   */
  private boolean include;

  @JsonIgnore
  private PaymentMetadata paymentMetadata;
  
  /**
   * Required for auction payment processing only
   */
  private String auctionLotIdentifier;

  /**
   * Required for auction payment processing only
   */
  private AuctionType auctionType;
  
  /**
   * Required for auction payment processing only
   */
  private String auctionLotItemManifestIdentifier;

  /**
   * Required for the asynchronous web service call only
   */
  private String callbackUrl;
  
  /**
   * Optional. If provided, the only payments for the specified scheme will be processed. If not provided, payments for all schemes will be processed, but in separate batches.
   */
  private String schemeId;

}
