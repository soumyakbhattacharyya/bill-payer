package com.serviceco.coex.payment.calculation;

import java.util.List;

import com.serviceco.coex.model.DateDimension;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.AuctionType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Contains input data for the payment calculation support classes.
 *
 * @param <T> The entity class which contains the source volume / claim data which will be used to generate payment transactions.
 */
@Getter
@Setter
@AllArgsConstructor
public class CalculationParameter<T> {

	/**
	 * Constructs the calculation parameter with the required input data
	 * @param scheme	The scheme the participant is apart of &amp; the payment transaction is related to
	 * @param schemeParticipantType The type of scheme participant. Payment transactions are only generated for participants of a particular type at a time.
	 * @param schemeParticipants The scheme participants to generate the payment transactions for. These should all be of the of the scheme participant type above.	
	 * @param allSalesVolumes The source volume / claim records which will be converted into payment transaction records. 
	 * @param today	The current date (including day, month, year, quarter, etc.)
	 * @param paymentBatch The record which identifies the current payment calculation batch. See @{link com.serviceco.coex.payment.service.ComputationTemplate}.
	 * @param currentPeriod
	 * @param paymentMetadata
	 * @param auctionLotItemManifestId
	 */
  public CalculationParameter(Scheme scheme, SchemeParticipantType schemeParticipantType, List<MdtParticipantSite> schemeParticipants, List<T> allSalesVolumes, DateDimension today,
      PaymentBatch paymentBatch, Period currentPeriod, PaymentMetadata paymentMetadata, String auctionLotItemManifestId) {
    this.scheme = scheme;
    this.schemeParticipantType = schemeParticipantType;
    this.schemeParticipants = schemeParticipants;
    this.allSalesVolumes = allSalesVolumes;
    this.today = today;
    this.paymentBatch = paymentBatch;
    this.currentPeriod = currentPeriod;
    this.paymentMetadata = paymentMetadata;
    this.auctionLotItemManifestId = auctionLotItemManifestId;
  }

  public Scheme scheme;
  public SchemeParticipantType schemeParticipantType;
  public List<MdtParticipantSite> schemeParticipants;
  public List<T> allSalesVolumes;
  public DateDimension today;
  public PaymentBatch paymentBatch;
  public Period currentPeriod;
  private PaymentMetadata paymentMetadata;
  private String auctionLotIdentifier;
  private AuctionType auctionType;
  private String auctionLotItemManifestId;

}