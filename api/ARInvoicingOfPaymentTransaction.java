package com.serviceco.coex.payment.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceco.coex.auction.service.LotItemService;
import com.serviceco.coex.exception.CoexRuntimeException;
import com.serviceco.coex.exception.model.ExceptionConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoiceStatusAssociation;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.service.ARInvoiceGenerationPersistenceService;
import com.serviceco.coex.payment.service.ARInvoiceGenerationService;
import com.serviceco.coex.payment.service.AuctionARInvoiceGenerationService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;
import com.serviceco.coex.scheme.participant.service.SchemeService;


/**
 * A REST web service for generating AR (Accounts Receivable) invoices and updating their status.
 * <p>See {@link #create} and {@link #update}</p>
 */
@Component
@Path(ResourceConstants.URLS.AR_INVOICE_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("PAYMENT.AR.INVOICING")
public class ARInvoicingOfPaymentTransaction {

  @Autowired
  private ARInvoiceGenerationService invoiceGenerationService;

  @Autowired
  private ARInvoiceGenerationPersistenceService invoiceGenerationPersistenceService;

  @Autowired
  private AuctionARInvoiceGenerationService auctionARInvoiceGenerationService;
  
  @Autowired
  private LotItemService lotItemService;
  
  @Autowired
  private SchemeService schemeService;

  /**
   * Generates AR (Accounts Receivable) invoices for a particular scheme participant type (and optionally particular scheme participants and particular payment types). 
   *  
   * <p>This calls implementations of {@code AuctionARInvoiceGenerationService}. </p>
   * <p>Requests which contain an action type and auction lot identifier go through {@link com.serviceco.coex.payment.service.AuctionARInvoiceGenerationService#generateInvoices(InvoicingRequest)}.</p>
   * <p>All other requests will go through {@link com.serviceco.coex.payment.service.ARInvoiceGenerationService#generateInvoices(InvoicingRequest) }.</p>  
   *  
   *  @param request The body of the HTTP request message, parsed from JSON. It should contain:
   *  @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   *  @param request.schemeParticipantIds A list of scheme participant site numbers to generate invoices for, or "ALL" to generate invoices for all participants.
   *  @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   *  @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
   *  @param request.scheme Optional. If provided (for non-auction invoices), invoices will only be generated for the particular scheme. If not provided, invoices will be generated for all schemes,
   *        but in separate batches, one after the other. This is ignored for auction invoices as the scheme is determined based on the auction lot identifier.
   * 
   *  @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices generated and errors (if there were any). This will be formatted as JSON in the response.
   * @param request
   * @return
   */
  @POST
  @Path("/compute")
  @ActionType("CREATE")
  public List<InvoiceTransactionWrapper> create(InvoicingRequest request) {
    
    if (StringUtils.isNotBlank(request.getAuctionLotIdentifier())) {
      // For auctions, the scheme Id is determined based on the LotItem
      Scheme scheme = lotItemService.getScheme(request.getAuctionLotIdentifier());
      InvoiceTransactionWrapper result = auctionARInvoiceGenerationService.generateInvoices(request, scheme);
      return Collections.singletonList(result);
    }
    String schemeId = request.getSchemeId();
    if (schemeId != null) {
      Optional<Scheme> schemeOp = schemeService.getById(request.getSchemeId());
      if (!schemeOp.isPresent()) {
        throw new CoexRuntimeException(ExceptionConstants.ERROR_CODES.SCHEME_NOT_VALID, null, "Can not find scheme " + request.getSchemeId());
      }
      InvoiceTransactionWrapper result = invoiceGenerationService.generateInvoices(request, schemeOp.get());
      return Collections.singletonList(result);
    } else {
      // if the scheme ID is not provided, generate invoices for every scheme, one after the other
      List<InvoiceTransactionWrapper> results = new ArrayList<>();
      for (Scheme scheme : schemeService.getAll()) {
        InvoiceTransactionWrapper result = invoiceGenerationService.generateInvoices(request, scheme);  
        results.add(result);
      }
      return results;
    }
    
  }

  /**
   * Updates the {@code PaymentInvoiceStatus} records linked to AR invoice transaction detail lines ({@code InvoiceARTransactionRec}) according to the details
   * in the request.
   * 
   * <p>This calls to {@link com.serviceco.coex.payment.service.ARInvoiceGenerationPersistenceService#updateStatus}</p>
   * 
   * @param request A list of InvoiceStatusAssociation objects containing an invoice number and a new status.
   */
  @PATCH
  @Path("/status")
  @ActionType("UPDATE")
  public void update(List<InvoiceStatusAssociation> request) {
    invoiceGenerationPersistenceService.updateStatus(request);
  }

}
