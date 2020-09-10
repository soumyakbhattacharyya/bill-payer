package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.serviceco.coex.auction.model.LotItem;
import com.serviceco.coex.auction.model.LotItemDelivery;
import com.serviceco.coex.auction.repository.LotItemDeliveryRepository;
import com.serviceco.coex.auction.repository.LotItemRepository;
import com.serviceco.coex.crp.model.dto.PaymentMethod;
import com.serviceco.coex.exception.InvoiceAttributeNotFoundException;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.AuctionType;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAR;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec;
import com.serviceco.coex.payment.repository.VAuctionPaymentTransactionRecARRepository;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.model.SchemeRefCodes;
import com.serviceco.coex.util.service.SchemeRefCodeService;

/**
 * Generates AR (Accounts Receivable) invoices for auction payments.  
 * 
 * <p>See {@link #generateInvoices(InvoicingRequest)}</p>
 */
@Service
@Transactional
public class AuctionARInvoiceGenerationService extends GenericService implements InvoiceGenerationService {

  private static final String TRANS_TYPE_NEGATIVE_AUCTION = "NEGATIVE_AUCTION";
  private static final String TRANS_TYPE_POSITIVE_AUCTION = "POSITIVE_AUCTION";

  @Autowired
  private LotItemRepository lotItemRepository;

  @Autowired
  private ARInvoiceGenerationService arInvoiceGenerationService;

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @Autowired
  private ARAuctionTransactionIsolator transactionIsolator;

  @Autowired
  private VAuctionPaymentTransactionRecARRepository vPaymentTransactionRecARRepository;

  /**
   * Generates AR (Accounts Receivable) invoices based on an auction lot identifier specified in the request. 
   * 
   * <p>A {@code LotItem} is looked up using the auction lot identifier in the request. The {@code LotItem} 
   * is then used to fetch a list of {@link com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAR} records associated with the auction.</p>
   * 
   * <p>Payment transaction records (@link PaymentTransactionRec) are fetched based on the {@code VAuctionPaymentTransactionRecAR}'s and then an invoice is generated for
   * each individual {@code PaymentTransactionRec}. </p>
   * 
   * <p>The actual creation of each invoice is done through {@link com.serviceco.coex.payment.service.AuctionARInvoiceGenerationService.ARAuctionTransactionIsolator#isolateTransactionAndProcess}</p>
   * 
   * <p>If an exception is caught during the creation of an invoice, an error message will be generated and added to the list of errors in the object returned.</p>
   * 
   * @param request The request body which was sent to to the ARInvoicingOfPaymentTransaction web service. It should contain:
   * @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   * @param request.auctionLotIdentifier The ID of an auction {@link com.serviceco.coex.auction.model.LotItem} you want to generate invoices for. 
   * @param request.auctionType The action type (see {@link com.serviceco.coex.model.constant.AuctionType}). Either {@code AuctionType.POSITIVE}, or {@code AuctionType.NEGATIVE}.
   * @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices generated, a list of errors (if there were any) and the invoice batch ID.
   */
  @Override
  public InvoiceTransactionWrapper generateInvoices(InvoicingRequest request, Scheme scheme) {

    // Generate Invoice batch Id
    String invoiceBatchId = UUID.randomUUID().toString();

    InvoiceAttributeCache attributesCache = new InvoiceAttributeCache(scheme);
    
    List<InvoiceARTransactionRec> from = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    MdtParticipantSite seller = null;
    try {
      final LotItem lotItem = lotItemRepository.findById(request.getAuctionLotIdentifier()).get();
      seller = lotItem.getSeller();
      final MdtParticipantSite buyer = lotItem.getBuyer();

      List<VAuctionPaymentTransactionRecAR> paymentTransctionRecords = vPaymentTransactionRecARRepository.findByLotItem(lotItem.getId());

      // query
      List<String> paymentTransactionIds = paymentTransctionRecords.stream().map(t -> t.getPaymentTransctionRecId()).collect(Collectors.toList());
      Session session = em.unwrap(Session.class);
      MultiIdentifierLoadAccess<PaymentTransactionRec> multiLoadAccess = session.byMultipleIds(PaymentTransactionRec.class);
      List<PaymentTransactionRec> recs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);

      String paymentTransactionType = TRANS_TYPE_POSITIVE_AUCTION;
      if (request.getAuctionType() == AuctionType.NEGATIVE) {
        paymentTransactionType = TRANS_TYPE_NEGATIVE_AUCTION;
      }

      for (PaymentTransactionRec paymentTransactionRec : recs) {
        final Period period = dateTimeSupport.periodFactory(paymentTransactionRec.getPeriod(), PeriodType.valueOf(paymentTransactionRec.getPeriodType()));
        List<PaymentTransactionRec> paymentRecords = Collections.singletonList(paymentTransactionRec);
        List<InvoiceARTransactionRec> generatedInvoices = transactionIsolator.isolateTransactionAndProcess(request, seller, buyer, paymentTransactionType, paymentRecords, period,
            invoiceBatchId, attributesCache, scheme);
        from.addAll(generatedInvoices);
      }
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      if (e instanceof InvoiceAttributeNotFoundException) {
        InvoiceAttributeNotFoundException ex = (InvoiceAttributeNotFoundException) e;
        if (ex.getErrorMessage() != null) {
          if (CollectionUtils.isNotEmpty(ex.getErrorMessage().getErrors())) {
            errorMessage = ex.getErrorMessage().getErrors().get(0).getAdditionalInfo();
          }
        }
      }
      errors.add(String.format("Scheme participant id: %s, error-message: %s", seller.getSiteNumber(), errorMessage));
    }
    return new InvoiceTransactionWrapper(arInvoiceGenerationService.asResponse(from), errors, invoiceBatchId, scheme.getId());

  }

  @Service
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  class ARAuctionTransactionIsolator {

    @Autowired
    private ARInvoiceGenerationPersistenceService arInvoiceGenerationPersistenceService;

    @Autowired
    private LotItemDeliveryRepository lotItemDeliveryRepo;
    
    @Autowired
    private SchemeRefCodeService schemeRefCodeService;

    /**
     * <p>This method is wrapped in a transaction.</p>
     * 
     * <p>It prepares some data for invoice generation, such as the invoice amount, unit selling price and tax classification, then
     * invokes {@link com.serviceco.coex.payment.service.ARInvoiceGenerationPersistenceService#processAuction} to generate the invoice.</p>
     *
     * <p>If the scheme participant type is PROCESSOR and the auction type is {@code POSITIVE}, there are two invoices generated - one for the buyer (recycler) and one for the seller (processor). The buyer (recycler) invoice amount is $0. The seller (processor) invoice amount is 80% of the gross payment transaction amount. The 80% is hard coded.</p>
     * <p>If the scheme participant type is PROCESSOR and the auction type is {@code NEGATIVE}, there are no invoices generated.</p>
     * 
     * <p>If the scheme participant type is MRF and the auction type is {@code POSITIVE}, there are two invoices generated - one for the buyer (recycler) and one for the seller (MRF). The buyer (recycler) invoice contains the gross payment transaction amount. The seller (MRF) invoice contains 2.6% of the gross payment transaction amount, up to a maximum of the PLATFORM_FEE (19.86). The 2.6% and PLATFORM_FEE are hard coded.
     * <p>If the scheme participant type is MRF and the auction type is {@code NEGATIVE}, there are also twice invoices generated, but they are both for the seller (MRF). One invoice contains the gross payment transaction amount shown as a positive number. The other invoice contains the PLATFORM_FEE (19.86). 
     * 
     * <p>No invoices are generated for any other scheme participant type.</p>
     * 
     * <p>If the auction buyer is overseas, the tax classification reference is set to {@code GST_FREE_AP}.</p>
     *  
     * @param request The request which was passed in to the {@code APInvoicingOfPaymentTransaction} web service.
     * @param seller The scheme participant who has sold the auction lot item
     * @param buyer The scheme participant who has purchased the auction lot item
     * @param paymentTransactionType The payment transaction type. For auctions, I believe this should be "POSITIVE_AUCTION" or "NEGATIVE_AUCTION".
     * @param paymentRecords The payment transaction item records which should be invoiced.
     * @param period The payment period the transaction falls in
     * @param invoiceBatchId The ID which identifies the current invoice generation batch.
     * @param scheme Scheme associated with the invoice being generated 

     * @return Returns a list of {@link com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader} records, one for each invoice created.
     */
    public List<InvoiceARTransactionRec> isolateTransactionAndProcess(InvoicingRequest request, MdtParticipantSite seller, MdtParticipantSite buyer, String paymentTransactionType,
        List<PaymentTransactionRec> paymentRecords, Period period, String invoiceBatchId, InvoiceAttributeCache attributesCache, Scheme scheme) {
      BigDecimal invoiceGroupNumber = new BigDecimal(1);
      List<InvoiceARTransactionRec> from = new ArrayList<>();
      Map<String, String> additionalInfo = new HashMap<>();

      // Scheme specific percentages and platform fee
      BigDecimal resaleAdjustPercentage = schemeRefCodeService.getValueAsBigDecimalOrThrow(SchemeRefCodes.FeesAndPrices.CATEGORY, SchemeRefCodes.FeesAndPrices.AUCTION_RESALE_ADJUST_PERCENTAGE, scheme);
      BigDecimal commissionPercentage = schemeRefCodeService.getValueAsBigDecimalOrThrow(SchemeRefCodes.FeesAndPrices.CATEGORY, SchemeRefCodes.FeesAndPrices.AUCTION_COMMISSION_PERCENTAGE, scheme);
      BigDecimal platformFee = schemeRefCodeService.getValueAsBigDecimalOrThrow(SchemeRefCodes.FeesAndPrices.CATEGORY, SchemeRefCodes.FeesAndPrices.AUCTION_PLATFORM_FEE, scheme);
      BigDecimal percentageDivisor      = new BigDecimal (100);

      additionalInfo.put(InvoiceConstants.AdditionInfo.LOT_ITEM_ID, request.getAuctionLotIdentifier());
      additionalInfo.put(InvoiceConstants.AdditionInfo.LOT_ITEM_FINAL_MANIFEST_ID, paymentRecords.get(0).getLotItemManifestId());

      if (request.getSchemeParticipantType().equals(SchemeParticipantType.PROCESSOR)) {
        if (request.getAuctionType() == AuctionType.POSITIVE) {

          if (isBuyerOverseas(request.getAuctionLotIdentifier())) {
            additionalInfo.put(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF, InvoiceConstants.GST_FREE_AR);
          }
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().toString());
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, buyer, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);

          additionalInfo.remove(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF);
          final BigDecimal sellerInvoiceAmount = paymentRecords.get(0).getGrossAmount().multiply(resaleAdjustPercentage).divide(percentageDivisor);
          final BigDecimal unitSellingPrice = paymentRecords.get(0).getUnitSellingPrice().multiply(resaleAdjustPercentage).divide(percentageDivisor);
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, sellerInvoiceAmount.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE, unitSellingPrice.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.IS_ADJUSTMENT, "true");
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);
        } else if (request.getAuctionType() == AuctionType.NEGATIVE) {
          // No AR Invoices
        }
      } else if (request.getSchemeParticipantType().equals(SchemeParticipantType.MRF)) {
        if (request.getAuctionType() == AuctionType.POSITIVE) {
          if (isBuyerOverseas(request.getAuctionLotIdentifier())) {
            additionalInfo.put(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF, InvoiceConstants.GST_FREE_AR);
          }
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().toString());
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, buyer, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);

          additionalInfo.remove(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF);
          final BigDecimal grossAmountPer = paymentRecords.get(0).getGrossAmount().multiply(commissionPercentage).divide(percentageDivisor);
          final BigDecimal sellerInvoiceAmount = grossAmountPer.max(platformFee);
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, sellerInvoiceAmount.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.VOLUME, sellerInvoiceAmount.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE, "1");
          additionalInfo.put(InvoiceConstants.AdditionInfo.IS_PLATFORM_FEE, "true");
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);
        } else if (request.getAuctionType() == AuctionType.NEGATIVE) {
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().abs().toString());
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);

          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, platformFee.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.VOLUME, platformFee.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE, "1");
          additionalInfo.put(InvoiceConstants.AdditionInfo.IS_PLATFORM_FEE, "true");
          arInvoiceGenerationPersistenceService.processAuction(from, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords, period,
              additionalInfo, invoiceBatchId, attributesCache, scheme);
        }
      }
      return from;
    }


    private boolean isBuyerOverseas(String lotId) {
      LotItemDelivery destination = lotItemDeliveryRepo.findByLotItemId(lotId);
      return (destination.getFinalState().trim().toUpperCase().equals(InvoiceConstants.EXPORTS));
    }

  }

}
