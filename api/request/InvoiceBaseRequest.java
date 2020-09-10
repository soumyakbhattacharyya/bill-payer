package com.serviceco.coex.payment.api.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.constant.AuctionType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * Represents a request payload, passed by the invoker of payment calculation &amp; invoice generation APIs
 */
public class InvoiceBaseRequest {

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

  private String callbackUrl;

}
