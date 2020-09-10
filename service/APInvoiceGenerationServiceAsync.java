package com.serviceco.coex.payment.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionDetail;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionDtlMapper;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionHdrMapper;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionHeader;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.rest.support.ObjectMapperFactory;
import com.serviceco.coex.scheme.participant.service.SchemeService;

/**
 * Generates AP (Accounts Payable) invoices asynchronously.
 * 
 * <p>This is similar to {@code APInvoiceGenerationService} except it is designed to be executed by a background thread and therefore posts the result of the processing to a callbackUrl.</p>
 * 
 * <p>See {@link #generateInvoices} and {@link #findAll}
 *
 */
@Service
public class APInvoiceGenerationServiceAsync implements GenericServiceAsync {

  private static final Logger LOG = LoggerFactory.getLogger(APInvoiceGenerationServiceAsync.class);

  @Autowired
  private APInvoiceGenerationService apInvoiceGenerationService;
  
  @Autowired
  private AuctionAPInvoiceGenerationService auctionApInvoiceGenerationService;

  @Value("${oic.client-user-name}")
  private String oicClientUsername;

  @Value("${oic.client-user-password}")
  private String oicClientUserPassword;

  @PersistenceContext
  private EntityManager em;

  @Autowired
  private JdbcTemplate localJdbcTemplate;
  
  @Autowired
  private SchemeService schemeService;

  protected JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  /**
   * Generate AP invoices based on processable payment records. It is expected that this is called by a background thread. The result of the processing
   * is posted to a callback URL specified within the request.
   * 
   * <p>The posted message contains a basic authentication header using the OIC client username and password properties defined within the Spring configuration.</p>
   * 
   * <p>The "processable" records ({@link com.serviceco.coex.payment.model.calculation.QVGenericPaymentRecord}) are fetched from the {@code vGenericPaymentRecord} table
   * where the {@code schemeParticipantType} field matches the {@code request.schemeParticipantType.supplierType} passed in.
   * </p>
   * 
   * <p>The payment records are filtered by:</p>
   * <ul>
   * <li>the scheme participant type (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantType})</li> 
   * <li>the scheme participant IDs passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantId})</li>
   * <li>the scheme payment types passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorByPaymentType})</li>
   * </ul>
   * 
   * <p>The filtered records are grouped by the scheme participant IDs. Invoices are then generated for each scheme participant ID by calling
   *  {@link com.serviceco.coex.payment.service.APInvoiceGenerationService.APTransactionIsolator#isolateTransactionAndProcess} </p>
   *  
   *  
   *  @param request The request body which was sent to to the APInvoicingOfPaymentTransaction web service. It should contain:
   *  @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   *  @param request.schemeParticipantIds A list of scheme participant IDs to generate invoices for, or "ALL" to generate invoices for all participants.
   *  @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   *  @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
   *  @param request.callbackUrl The URL to post the result to. The body of the posted message will contain a {@link com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest} converted to JSON.
   *  @param scheme The scheme to generate invoices for
   */
  @Override
  @Transactional  
  public void generateInvoices(InvoicingRequest request, Scheme scheme) {
    LOG.info("AP Generating invoices for scheme participant type {}", request.getSchemeParticipantType());
    LOG.info("AP Initial Invoicing Request Callback request is {}", request.getCallbackUrl());

    ObjectMapper mapper = ObjectMapperFactory.getMapperInstance();
    InvoiceTransactionWrapper invoiceTxn = null;
    if ((null != request.getAuctionType()) && StringUtils.isNotBlank(request.getAuctionLotIdentifier())) {
      invoiceTxn = auctionApInvoiceGenerationService.generateInvoices(request, scheme);
    }
    
    invoiceTxn = apInvoiceGenerationService.generateInvoices(request, scheme);
    LOG.info("AP Invoice batch id {}", invoiceTxn.getInvoiceBatchId());
    LOG.info("AP Invoice generation finished at {} with failure for {} scheme participants", DateTimeSupport.now(), invoiceTxn.getErrors().size());

    if ((invoiceTxn.getInvoices() != null && invoiceTxn.getInvoices().size() > 0) || (invoiceTxn.getErrors() != null && invoiceTxn.getErrors().size() > 0)){ 
      AsyncCallbackRequest callbackRequest = new AsyncCallbackRequest(invoiceTxn.getInvoiceBatchId(), invoiceTxn.getErrors(), request, scheme.getId());

      LOG.info("AP Invoice batch id passed in callback request {}", callbackRequest.getInvoicesBatchId());
      LOG.info("AP Requesting callback.");
      try {
        Preconditions.checkArgument(StringUtils.isNotEmpty(request.getCallbackUrl()), "Callback url must not be null or empty.");

        RestTemplate template = new RestTemplate();
        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);

        String authHeader = "Basic " + Base64.getEncoder().encodeToString((oicClientUsername + ":" + oicClientUserPassword).getBytes());
        header.set("Authorization", authHeader);

        HttpEntity<AsyncCallbackRequest> entity = new HttpEntity<AsyncCallbackRequest>(callbackRequest, header);
        LOG.info("AP Callback request payload : " + mapper.writeValueAsString(callbackRequest));
        template.exchange(callbackRequest.getComputeParameters().getCallbackUrl(), HttpMethod.POST, entity, String.class);
        LOG.info("AP Posted callback request");
      } catch (Exception e) {
        LOG.error("AP Callback failed for invoice batch id {}", callbackRequest.getInvoicesBatchId());
        e.printStackTrace();
      }
    }
  }

  /**
   * Find invoices which where generated during a particular invoice batch.
   * 
   * @param invoiceBatchId The ID which identifies the invoice generation batch.
   * @param pageNumber If there are multiple pages of results, the page number will indicate which page should be returned (starting from 0). If this is null, the first page will be returned.
   * @param pageSize The maximum number of results to return in the requested page.
   * @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices found.
   * 
   */
  @Override
  public InvoiceTransactionWrapper findAll(String invoiceBatchId, Integer pageNumber, Integer pageSize) {

    LocalDateTime start = LocalDateTime.now();

    final List<InvoiceTransaction> generatedInvoices = new ArrayList<>();
    List<APInvoiceTransactionHeader> invoiceTransactions = new ArrayList<>();

    List<APInvoiceTransactionRecHeader> invoiceHeaders = localJdbcTemplate.query("select * from INVOICE_AP_TXN_HDR where INVOICE_BATCH_ID = ? and nvl(SYNCED,0) = 0 and ROWNUM <= ? order by INVOICE_NUMBER asc"
        , new Object[] { invoiceBatchId,pageSize }, new APInvoiceTransactionHdrMapper(schemeService));

    if (invoiceHeaders.isEmpty()) {
      return new InvoiceTransactionWrapper(generatedInvoices, null);
    }
    Scheme scheme = invoiceHeaders.get(0).getScheme();

    // transformation from page to list of InvoiceTransaction
    Iterator<APInvoiceTransactionRecHeader> iterator = invoiceHeaders.iterator();
    long diff = 0L;
    
    while (iterator.hasNext() && diff<4) {
      
      APInvoiceTransactionRecHeader apInvoiceTransactionRecHeader = iterator.next();
      
      //cutting corners to get better performance
      List<APInvoiceTransactionDetail> invoiceLines = localJdbcTemplate.query("select AMOUNT,DESCRIPTION,DISTRIBUTION_COMBINATION,FINAL_MATCH,ITEM_DESCRIPTION,LINE_GROUP_NUMBER"
          + ",LINE_NUMBER,LINE_TYPE,PAYMENT_PERIOD_START_DATE,PAYMENT_PERIOD_END_DATE,AUCTION_DATE,PRICE_CORRECTION_LINE,PRORATE_ACROSS_ALL_LINE_ITEMS"
          + ",QUANTITY,TAX_CLASSIFICATION_CODE,TRACK_AS_ASSET,UNIT_OF_MEASURE,UNIT_PRICE from INVOICE_AP_TXN_DTL where INVOICE_HDR_REF = ?"
          , new Object[] { apInvoiceTransactionRecHeader.getId()}, new APInvoiceTransactionDtlMapper());
      
      final InvoiceTransaction response = new APInvoiceTransactionHeader(apInvoiceTransactionRecHeader,invoiceLines);
      
      invoiceTransactions.add((APInvoiceTransactionHeader)response);
      generatedInvoices.add(response);
      
      LocalDateTime timeCheck = LocalDateTime.now();
      Duration duration = Duration.between(start, timeCheck);
      diff = Math.abs(duration.toMinutes());
    }
    
    String sqlIN = invoiceTransactions.stream()
        .map(invoiceTransactionRec -> "'"+invoiceTransactionRec.getId()+"'")
        .collect(Collectors.joining(",", "(", ")"));

    String updateSQL = "update INVOICE_AP_TXN_HDR set SYNCED = 1 where ID in (?)".replace("(?)", sqlIN);
    
    localJdbcTemplate.update(updateSQL);
    
    return new InvoiceTransactionWrapper(generatedInvoices, scheme == null ? null : scheme.getId());

  }

}
