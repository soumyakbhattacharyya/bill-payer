package com.serviceco.coex.payment.api;

import java.util.Optional;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import com.serviceco.coex.auction.service.LotItemService;
import com.serviceco.coex.exception.CoexRuntimeException;
import com.serviceco.coex.exception.model.ExceptionConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.service.APInvoiceGenerationServiceAsync;
import com.serviceco.coex.payment.service.AuctionAPInvoiceGenerationService;
import com.serviceco.coex.rest.annotation.ActionType;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;
import com.serviceco.coex.scheme.participant.service.SchemeService;

/**
 * A REST web service for generating AP (Accounts Payable) invoices asynchronously and for fetching invoices which have been generated.
 *
 * <p>See {@link #create} and {@link #findAll}</p>
 *
 */
@Component
@Path(ResourceConstants.URLS.AP_INVOICE_TRANSACTIONS)
@Produces("application/json")
@Consumes("application/json")
@ResourceType("ASYNC.PAYMENT.AP.INVOICING")
public class APInvoicingOfPaymentTransactionAsync {

  private static final Logger LOG = LoggerFactory.getLogger(APInvoicingOfPaymentTransactionAsync.class);

  @Autowired
  private APInvoiceGenerationServiceAsync apInvoiceGenerationServiceAsync;

  @Autowired
  private TaskExecutor taskExecutor;
  
  @Autowired
  private AuctionAPInvoiceGenerationService auctionApInvoiceService;
  
  @Autowired
  private SchemeService schemeService;
  
  @Autowired
  private LotItemService lotItemService;

  /**
   * Generates AP (Accounts Payable) invoices for a particular scheme participant type (and optionally particular scheme participants and particular payment types) asynchronously.
   * 
   * <p>This triggers the process and returns immediately. The result ({@link com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest}) will be posted back to the callback URL which is defined in the request.</p>
   * 
   * <p>The actual processing work is done by {@link com.serviceco.coex.payment.service.APInvoiceGenerationServiceAsync#generateInvoices}.  
   *  
   *  @param request The body of the HTTP request message, parsed from JSON. It should contain:
   *  @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   *  @param request.schemeParticipantIds A list of scheme participant site numbers to generate invoices for, or "ALL" to generate invoices for all participants.
   *  @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   *  @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
   *  @param request.calbackUrl The URL to post the result to. The body of the posted message will contain a {@link com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest} converted to JSON.
   *  @param request.scheme Optional. If provided (for non-auction invoices), invoices will only be generated for the particular scheme. If not provided, invoices will be generated for all schemes,
   *        but in separate batches, one after the other. This is ignored for auction invoices as the scheme is determined based on the auction lot identifier.
   *  
   *  @throws InterruptedException Should not be thrown
   */
  @POST
  @Path("async/compute")
  @ActionType("CREATE")
  public void create(InvoicingRequest request) throws InterruptedException {
    LOG.info("AP Recieved asynchronous invoice generation request.");
    taskExecutor.execute(new IntegrationTask(request));
  }

  /**
   * Finds existing invoices that were generated during a particular invoice generating batch.
   * @param batchId The ID of the invoice batch
   * @param pageNumber The page to return (starting from 0). If this is null, the first page will be returned.
   * @param pageSize The maximum number of results to return in each page.
   * @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices found.
   */
  @GET
  @Path("batch/{batchId}")
  @ActionType("VIEW")
  public InvoiceTransactionWrapper findAll(@PathParam("batchId") String batchId, @QueryParam("pageNumber") Integer pageNumber, @QueryParam("pageSize") Integer pageSize) {
    return apInvoiceGenerationServiceAsync.findAll(batchId, pageNumber, pageSize);
  }

  /**
   * A runnable task which generates invoices in the background.
   *
   */
  public class IntegrationTask implements Runnable {
    InvoicingRequest request;

    public IntegrationTask(InvoicingRequest request) {
      this.request = request;
    }

    @Override
    public void run() {
      
      if (request.getAuctionLotIdentifier() != null) {
        Scheme scheme = lotItemService.getScheme(request.getAuctionLotIdentifier());
        apInvoiceGenerationServiceAsync.generateInvoices(request, scheme);
      } else if (request.getSchemeId() != null) {
        Optional<Scheme> schemeOp = schemeService.getById(request.getSchemeId());
        if (!schemeOp.isPresent()) {
          throw new CoexRuntimeException(ExceptionConstants.ERROR_CODES.SCHEME_NOT_VALID, null, "Can not find scheme " + request.getSchemeId());
        }
        Scheme scheme = schemeOp.get();
        apInvoiceGenerationServiceAsync.generateInvoices(request, scheme);
      } else {
        for (Scheme scheme : schemeService.getAll()) {
          apInvoiceGenerationServiceAsync.generateInvoices(request, scheme);
        }
      }
    }

  }

}
