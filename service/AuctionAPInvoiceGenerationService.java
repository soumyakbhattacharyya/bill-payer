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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAP;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader;
import com.serviceco.coex.payment.repository.VAuctionPaymentTransactionRecAPRepository;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

/**
 * Generates AP (Accounts Payable) invoices for auction payments.  
 * 
 * <p>See {@link #generateInvoices(InvoicingRequest)}</p>
 */
@Service
@Transactional
public class AuctionAPInvoiceGenerationService extends GenericService implements InvoiceGenerationService {

  private static final String TRANS_TYPE_NEGATIVE_AUCTION = "NEGATIVE_AUCTION";
  private static final String TRANS_TYPE_POSITIVE_AUCTION = "POSITIVE_AUCTION";

  private static final Logger LOGGER = LoggerFactory.getLogger(AuctionAPInvoiceGenerationService.class);

  @Autowired
  private APAuctionTransactionIsolator transactionIsolator;

  @Autowired
  private LotItemRepository lotItemRepository;

  @Autowired
  private APInvoiceGenerationService aPInvoiceGenerationService;

  @Autowired
  private VAuctionPaymentTransactionRecAPRepository vPaymentTransactionRecAPRepository;

  /**
   * Generates AP (Accounts Payable) invoices based on an auction lot identifier specified in the request. 
   * 
   * <p>A {@code LotItem} is looked up using the auction lot identifier in the request. The {@code LotItem} 
   * is then used to fetch a list of {@link com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAP} records associated with the auction.</p>
   * 
   * <p>Payment transaction records ({@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec}) are fetched based on the {@link com.serviceco.coex.payment.model.calculation.VAuctionPaymentTransactionRecAP}'s and then at least one invoice is generated for
   * each individual {@code PaymentTransactionRec}. </p>
   * 
   * <p>The actual creation of the invoices is done through {@link com.serviceco.coex.payment.service.AuctionAPInvoiceGenerationService.APAuctionTransactionIsolator#isolateTransactionAndProcess}</p>
   * 
   * <p>If an exception is caught during the creation of an invoice, an error message will be generated and added to the list of errors in the object returned.</p>
   * 
   * @param request The request body which was sent to to the APInvoicingOfPaymentTransaction web service. It should contain:
   * @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   * @param request.auctionLotIdentifier The ID of an auction {@link com.serviceco.coex.auction.model.LotItem} you want to generate invoices for. 
   * @param request.auctionType The action type (see {@link com.serviceco.coex.model.constant.AuctionType}). Either {@code AuctionType.POSITIVE}, or {@code AuctionType.NEGATIVE}.
   * @param request.schemeId Ignored. The scheme ID will be determined from the auction lot identifier.
   * @param scheme Scheme associated with the auction lot item
   * @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices generated, a list of errors (if there were any) and the invoice batch ID.
   */
  @Override
  public InvoiceTransactionWrapper generateInvoices(InvoicingRequest request, Scheme scheme) {
    final List<String> errors = new ArrayList<>();
    final List<APInvoiceTransactionRecHeader> from = new ArrayList<>();
    MdtParticipantSite seller = null;
    String invoiceBatchId = UUID.randomUUID().toString();
    try {
      final LotItem lotItem = lotItemRepository.findById(request.getAuctionLotIdentifier()).get();
      seller = lotItem.getSeller();
      final MdtParticipantSite buyer = lotItem.getBuyer();

      List<VAuctionPaymentTransactionRecAP> paymentTransctionRecords = vPaymentTransactionRecAPRepository.findByLotItem(lotItem.getId());

      List<String> paymentTransactionIds = paymentTransctionRecords.stream().map(t -> t.getPaymentTransctionRecId()).collect(Collectors.toList());
      Session session = em.unwrap(Session.class);
      MultiIdentifierLoadAccess<PaymentTransactionRec> multiLoadAccess = session.byMultipleIds(PaymentTransactionRec.class);
      List<PaymentTransactionRec> recs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);

      String paymentTransactionType = TRANS_TYPE_POSITIVE_AUCTION;
      if (request.getAuctionType() == AuctionType.NEGATIVE) {
        paymentTransactionType = TRANS_TYPE_NEGATIVE_AUCTION;
      }
      
      Map<String, Period> cachedPeriod = new HashMap<>();
      InvoiceAttributeCache attributesCache = new InvoiceAttributeCache(scheme);
      for (PaymentTransactionRec paymentTransactionRec : recs) {
        final List<PaymentTransactionRec> paymentRecords = Collections.singletonList(paymentTransactionRec);
        List<APInvoiceTransactionRecHeader> generatedInvoice = transactionIsolator.isolateTransactionAndProcess(request, invoiceBatchId, seller, buyer, paymentTransactionType,
            paymentRecords, cachedPeriod, attributesCache, scheme);
        from.addAll(generatedInvoice);
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
    return new InvoiceTransactionWrapper(aPInvoiceGenerationService.map(from), errors, invoiceBatchId, scheme.getId());

  }


  @Service
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  class APAuctionTransactionIsolator {
    @Autowired
    private LotItemDeliveryRepository lotItemDeliveryRepo;

    @Autowired
    private APInvoiceGenerationPersistenceService aPInvoiceGenerationPersistenceService;

    /**
     * <p>This method is wrapped in a database transaction. If an exception is thrown, the database will be rolled back.</p>
     * 
     * <p>It prepares some data for invoice generation, such as the invoice amount, unit selling price and tax classification, then
     * invokes {@link com.serviceco.coex.payment.service.APInvoiceGenerationPersistenceService#processAuction} to generate the invoice.</p>
     * 
     * <p>If the scheme participant type is PROCESSOR and the auction type is {@code POSITIVE}, a single invoice is generated for the seller (processor) containing the gross payment transaction amount.</p>
     * <p>If the scheme participant type is PROCESSOR and the auction type is {@code NEGATIVE}, there are two invoices generated - one for the buyer (recycler) and one for the seller (processor). The invoice for the seller (processor) contains a zero invoice amount and zero unit selling price. The invoice for the buyer (recycler) contains the gross payment transaction amount as a positive number. The invoice amount comes from the gross amount of the first payment transaction record passed in.</p>
     * <p>If the scheme participant type is MRF and the auction type is {@code POSITIVE}, there is a single invoice created for the seller (MRF) with a zero amount.</p>
     * <p>If the scheme participant type is MRF and the auction type is {@code NEGATIVE}, there is a single invoice created for the buyer (recycler) with the gross payment transaction amount shown as a positive number.</p>
     * 
     * <p>No invoices are created for any other scheme participant type.</p>
     * 
     * <p>If the auction buyer is overseas, the tax classification reference is set to {@code GST_FREE_AP}.</p>
     *  
     * @param request The request which was passed in to the {@code APInvoicingOfPaymentTransaction} web service.
     * @param invoiceBatchId The ID which identifies the current invoice generation batch. 
     * @param seller The scheme participant who has sold the auction lot item
     * @param buyer The scheme participant who has purchased the auction lot item
     * @param paymentTransactionType The payment transaction type. I believe this should be "POSITIVE_AUCTION" or "NEGATIVE_AUCTION".
     * @param paymentRecords The payment transaction item records which should be invoiced.
     * @param cachedPeriod Used to temporarily cache payment periods. This can be an empty Map initially.
     * @param scheme The scheme associated with the auction lot item sold
     * @return Returns a list of {@link com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader} records, one for each invoice created.
     */
    public List<APInvoiceTransactionRecHeader> isolateTransactionAndProcess(InvoicingRequest request, String invoiceBatchId, MdtParticipantSite seller, MdtParticipantSite buyer,
        String paymentTransactionType, List<PaymentTransactionRec> paymentRecords, Map<String, Period> cachedPeriod, InvoiceAttributeCache attributesCache, Scheme scheme) {
      List<APInvoiceTransactionRecHeader> from = new ArrayList<>();
      Map<String, String> additionalInfo = new HashMap<>();

      additionalInfo.put(InvoiceConstants.AdditionInfo.LOT_ITEM_ID, request.getAuctionLotIdentifier());
      additionalInfo.put(InvoiceConstants.AdditionInfo.LOT_ITEM_FINAL_MANIFEST_ID,paymentRecords.get(0).getLotItemManifestId());

      final BigDecimal invoiceGroupNumber = new BigDecimal(1);
      if (request.getSchemeParticipantType().equals(SchemeParticipantType.PROCESSOR)) {
        if (request.getAuctionType() == AuctionType.POSITIVE) {

          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().toString());
          aPInvoiceGenerationPersistenceService.processAuction(from, invoiceBatchId, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(),
              paymentRecords, additionalInfo, cachedPeriod, attributesCache, scheme);
        } else if (request.getAuctionType() == AuctionType.NEGATIVE) {
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, BigDecimal.ZERO.toString());
          additionalInfo.put(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE, BigDecimal.ZERO.toString());
          aPInvoiceGenerationPersistenceService.processAuction(from, invoiceBatchId, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(),
              paymentRecords, additionalInfo, cachedPeriod, attributesCache, scheme);

          additionalInfo.remove(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE);
          if (isBuyerOverseas(request.getAuctionLotIdentifier())) {
            additionalInfo.put(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF, InvoiceConstants.GST_FREE_AP);
          }
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().abs().toString());
          aPInvoiceGenerationPersistenceService.processAuction(from, invoiceBatchId, invoiceGroupNumber, buyer, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords,
              additionalInfo, cachedPeriod, attributesCache, scheme);
        }
      } else if (request.getSchemeParticipantType().equals(SchemeParticipantType.MRF)) {
        if (request.getAuctionType() == AuctionType.POSITIVE) {

          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().toString());
          aPInvoiceGenerationPersistenceService.processAuction(from, invoiceBatchId, invoiceGroupNumber, seller, paymentTransactionType, PaymentMethod.SCHEME.name(),
              paymentRecords, additionalInfo, cachedPeriod, attributesCache, scheme);
        } else if (request.getAuctionType() == AuctionType.NEGATIVE) {
          if (isBuyerOverseas(request.getAuctionLotIdentifier())) {
            additionalInfo.put(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF, InvoiceConstants.GST_FREE_AP);
          }
          additionalInfo.put(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT, paymentRecords.get(0).getGrossAmount().abs().toString());
          aPInvoiceGenerationPersistenceService.processAuction(from, invoiceBatchId, invoiceGroupNumber, buyer, paymentTransactionType, PaymentMethod.SCHEME.name(), paymentRecords,
              additionalInfo, cachedPeriod, attributesCache, scheme);
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
