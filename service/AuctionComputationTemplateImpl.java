package com.serviceco.coex.payment.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Preconditions;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.AuctionType;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.PaymentCalculationRequest;
import com.serviceco.coex.payment.calculation.AuctionPaymentSupport;
import com.serviceco.coex.payment.calculation.CalculationParameter;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentMetadata;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.NoArgsConstructor;

/**
 * 
 * Generates payment transaction records for auctions.
 * 
 * The super class (ComputationTemplate) handles creating and updating a PaymentBatch record for keeping track
 * of the processing. It calls the run method in this class to perform the actual processing. 
 * 
 * See {@link #run}.
 *
 */
@Service
@Transactional
@Qualifier("auction")
@NoArgsConstructor
public class AuctionComputationTemplateImpl extends ComputationTemplate {

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionComputationTemplateImpl.class);

  @Autowired
  private AuctionPaymentSupport auctionPaymentSupport;

  /**
   * <p>Generates {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} records based on a particular auction lot manifest.</p>
   * 
   * <p>Payment transaction records are only generated for auctions where the scheme participant type is either PROCESSOR or MRF. 
   * In both cases, {@link com.serviceco.coex.payment.calculation.AuctionPaymentSupportImpl} is used to obtain the volume data and calculate the payment transactions.</p>
   * 
   * <p>The payment metadata is obtained from the paymentMetadata table based on the scheme participant type (in the request) and the transaction type. The transaction type is either "POSITIVE_AUCTION" or "NEGATIVE_AUCTION" based on the actionType in the request.</p>
   * 
   * <p>The Scheme record is looked up based on a hard coded "QLD" string.</p>
   * 
   * @param paymentBatch The record which identifies the current batch processing and keeps track of the result. See {@link com.serviceco.coex.payment.service.ComputationTemplate}
   * @param request The data which was passed in to the {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction} web service. It should include:
   * @param request.schemeParticipants The participants to create payment transactions for, or the participants to exclude (see request.include).
   * @param request.schemeParticipantType  The scheme participant type (used to filter the allSalesVolumes).
   * @param request.include  If true, payment transactions are generated for the scheme participants passed in. If false, payment transactions are generated for all of the scheme participants associated with the scheme participant type EXCLUDING the scheme participants passed in.
   * @param request.auctionLotIdentifier The ID of the auction lot item ({@link com.serviceco.coex.auction.model.LotItem})).
   * @param request.auctionLotItemManifestId The ID of the auction lot manifest ({@link com.serviceco.coex.auction.model.LotItemManifest}) which should be used to generate payment records.
   * @param request.scheme Ignored. The scheme will be determined from the lot item record.
   * 
   * 
   * @return Returns a list of {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec records} which have been generated and persisted.
   */
  @Override
  public List<PaymentTransactionRec> run(PaymentBatch paymentBatch, PaymentCalculationRequest request) {
    final List<PaymentTransactionRec> allRecords = new ArrayList<>();
    List<PaymentTransactionRec> paymentRecordForNonDeclaringParticipants = new ArrayList<>();
    List<PaymentTransactionRec> paymentRecordForDeclaringParticipants = new ArrayList<>();

    LOGGER.info("Computation service: starts.");
    LOGGER.info("Computation service: input argument: schemeParticipantType {}, schemeParticipants {}, include {}, action lot item ID {}", request.getSchemeParticipantType(),
        request.getSchemeParticipantIds(), request.isInclude(), request.getAuctionLotIdentifier());

    String paymentTransactionType = request.getAuctionType() == AuctionType.POSITIVE ? "POSITIVE_AUCTION" : "NEGATIVE_AUCTION";
    
    Scheme scheme = paymentBatch.getScheme();
    
    /**
     * set payment metadata
     */
    final QPaymentMetadata qPaymentMetadata = QPaymentMetadata.paymentMetadata;
    final List<PaymentMetadata> paymentMetadata = getQueryFactory().select(qPaymentMetadata).from(qPaymentMetadata)
        .where(qPaymentMetadata.schemeParticipantType.eq(request.getSchemeParticipantType()), qPaymentMetadata.transactionType.eq(paymentTransactionType)).fetch();
    final PaymentMetadata firstElement = paymentMetadata.stream().findFirst().get();
    request.setPaymentMetadata(firstElement);
    final Period paymentPeriodForSchemeParticipant = assertPaymentPeriod(firstElement, scheme);
    final Period paymentPeriodForSchemParticipantEnriched = periodSupport.periodFactory(paymentPeriodForSchemeParticipant.getValue(), paymentPeriodForSchemeParticipant.getType());

    final List<MdtParticipantSite> declaringSchemeParticipants = super.partitionByDeclaration(request, scheme);

    switch (request.getSchemeParticipantType()) {

    case PROCESSOR:
      //@formatter:off
       final CalculationParameter<String> paramProcessor = new CalculationParameter<String>(scheme
                                                                  , request.getSchemeParticipantType()
                                                                  , declaringSchemeParticipants
                                                                  , getLotItem(request.getAuctionLotIdentifier())
                                                                  , periodSupport.getToday(scheme)
                                                                  , paymentBatch
                                                                  , paymentPeriodForSchemParticipantEnriched
                                                                  , firstElement
                                                                  , request.getAuctionLotItemManifestIdentifier());
       paymentRecordForDeclaringParticipants = auctionPaymentSupport.calculateViaActual(paramProcessor);

       //@formatter:on
      break;
    case MRF:
      //@formatter:off
       final CalculationParameter<String> paramMrf = new CalculationParameter<String>(scheme
                                                                  , request.getSchemeParticipantType()
                                                                  , declaringSchemeParticipants
                                                                  , getLotItem(request.getAuctionLotIdentifier())
                                                                  , periodSupport.getToday(scheme)
                                                                  , paymentBatch
                                                                  , paymentPeriodForSchemParticipantEnriched
                                                                  , firstElement
                                                                  , request.getAuctionLotItemManifestIdentifier());
       paymentRecordForDeclaringParticipants = auctionPaymentSupport.calculateViaActual(paramMrf);
       //@formatter:on

      break;

    default:
      break;
    }

    allRecords.addAll(paymentRecordForDeclaringParticipants);
    allRecords.addAll(paymentRecordForNonDeclaringParticipants);

    return allRecords;
  }

  @Override
  protected void validate(PaymentCalculationRequest request) {
    Preconditions.checkArgument((null != request.getSchemeParticipantType()), "scheme participant type is a mandatory for running payment computation process");
  }


  protected List<String> getLotItem(String auctionLotIdentifier) {
    return Collections.singletonList(auctionLotIdentifier);
  }

  @Override
  protected Period assertPaymentPeriod(PaymentMetadata paymentMetadata, Scheme scheme) {
    PeriodType periodType = null;
    if (paymentMetadata != null) {
      periodType = PeriodType.valueOf(paymentMetadata.getFrequency());
    } else {
      throw new AssertionError("payment metadata for requested scheme participant type, is unavailable ");
    }

    return periodSupport.periodFactory(periodSupport.getToday(scheme).getDateInYYYYMMDDFormat(), periodType);
  }


}
