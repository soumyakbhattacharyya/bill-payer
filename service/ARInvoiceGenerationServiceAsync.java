package com.serviceco.coex.payment.service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gdata.util.common.base.Preconditions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ar.ARInvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionMapper;
import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.rest.support.ObjectMapperFactory;
import com.serviceco.coex.scheme.participant.service.SchemeService;

/**
 * Generates AR (Accounts Receivable) invoices asynchronously.
 * 
 * <p>This is similar to the {@link com.serviceco.coex.payment.service.ARInvoiceGenerationService} except it is designed to be executed by a background thread and therefore posts the result of the processing to a callbackUrl.</p>
 * 
 * <p>See {@link #generateInvoices} and {@link #findAll}
 *
 */
@Service
public class ARInvoiceGenerationServiceAsync implements GenericServiceAsync {

	private static final Logger LOG = LoggerFactory.getLogger(ARInvoiceGenerationServiceAsync.class);

	@Autowired
	private ARInvoiceGenerationService arInvoiceGenerationService;

	@Autowired
	private AuctionARInvoiceGenerationService auctionARInvoiceGenerationService;

	@Value("${oic.client-user-name}")
	private String oicClientUsername;

	@Value("${oic.client-user-password}")
	private String oicClientUserPassword;

	@Autowired
	private DateTimeSupport dateTimeSupport;

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
	 * Generate AR invoices based on processable payment records. It is expected that this is called by a background thread. The result of the processing
	 * is posted to a callback URL specified within the request.
	 * 
	 * <p>The posted message sent to the callback URL contains a basic authentication header using the OIC client username and password properties defined within the Spring configuration.</p>
	 * 
	 * <p>If the request contains an auction lot identifier, the invoice is generated through {@link com.serviceco.coex.payment.service.AuctionARInvoiceGenerationService#generateInvoices}</p>
	 * <p>For all other requests, the invoice is generated through {@link com.serviceco.coex.payment.service.ARInvoiceGenerationService#generateInvoices}</p>
	 * 
	 * @param request The request body which was sent to to the ARInvoicingOfPaymentTransaction web service. It should contain:
	 * @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
	 * @param request.schemeParticipantIds A list of scheme participant IDs to generate invoices for, or "ALL" to generate invoices for all participants.
	 * @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
	 * @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
	 * @param request.callbackUrl The URL to post the result to. The body of the posted message will contain an {@link com.serviceco.coex.payment.model.invoice.AsyncCallbackRequest} converted to JSON.
	 * @param scheme 
	 */
	@Override
	@Transactional
	public void generateInvoices(InvoicingRequest request, Scheme scheme) {

		LOG.info("AR Generating invoices for scheme participant type {}", request.getSchemeParticipantType());
		LOG.info("AR Initial Invoicing Request Callback request is {}", request.getCallbackUrl());

		ObjectMapper mapper = ObjectMapperFactory.getMapperInstance();
		InvoiceTransactionWrapper invoiceTxn = null;

		if (StringUtils.isNotEmpty(request.getAuctionLotIdentifier())) {
			invoiceTxn = auctionARInvoiceGenerationService.generateInvoices(request, scheme);
		}
		invoiceTxn = arInvoiceGenerationService.generateInvoices(request, scheme);

		LOG.info("AR Invoice batch id {}", invoiceTxn.getInvoiceBatchId());
		LOG.info("AR Invoice generation finished at {} with failure for {} scheme participants", dateTimeSupport.now(), invoiceTxn.getErrors().size());

		if ((invoiceTxn.getInvoices() != null && invoiceTxn.getInvoices().size() > 0) || (invoiceTxn.getErrors() != null && invoiceTxn.getErrors().size() > 0)) {
			AsyncCallbackRequest callbackRequest = new AsyncCallbackRequest(invoiceTxn.getInvoiceBatchId(), invoiceTxn.getErrors(), request, scheme.getId());
			try {
				LOG.info("AR Invoice batch id , after completed the invoice generation {}", callbackRequest.getInvoicesBatchId());
				LOG.info("AR Call back request from payload , Call back url is {}", request.getCallbackUrl());
				LOG.info("AR Call back request after completed the invoice generation {}",callbackRequest.getComputeParameters().getCallbackUrl());

				Preconditions.checkArgument(StringUtils.isNotEmpty(request.getCallbackUrl()), "Callback url must not be null or empty");

				LOG.info("AR Requesting callback.");
				RestTemplate template = new RestTemplate();
				HttpHeaders header = new HttpHeaders();

				String authHeader = "Basic " + Base64.getEncoder().encodeToString((oicClientUsername + ":" + oicClientUserPassword).getBytes());
				header.set("Authorization", authHeader);
				header.setContentType(MediaType.APPLICATION_JSON);

				HttpEntity<AsyncCallbackRequest> entity = new HttpEntity<AsyncCallbackRequest>(callbackRequest, header);
				LOG.info("AR Callback request payload : " + mapper.writeValueAsString(callbackRequest));

				template.exchange(callbackRequest.getComputeParameters().getCallbackUrl(), HttpMethod.POST, entity, String.class);

				LOG.info("AR Posted callback request");
			} catch (Exception e) {
				LOG.error("AR Callback failed for invoice batch id {}", callbackRequest.getInvoicesBatchId());
				e.printStackTrace();
			}
		}
	}

	/**
	 * Finds existing invoices by querying the {@link com.serviceco.coex.payment.model.invoice.ar.QInvoiceARTransactionRec} records.
	 * 
	 * @param invoiceBatchId The ID of the invoice batch which contains the invoices
	 * @param pageNumber If multiple pages are available, this specifies the page (starting from 0). If this is null, the 1st page will be returned.
	 * @param pageSize The maximum number of records to return for the given page.
	 * @return Returns a {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the list of invoices found
	 */
	public InvoiceTransactionWrapper findAll(String invoiceBatchId, Integer pageNumber, Integer pageSize) {

		List<InvoiceARTransactionRec> invoiceHeaders = localJdbcTemplate.query("select * from INVOICE_AR_TRANSACTION_REC where INVOICE_BATCH_ID = ? and nvl(SYNCED,0) = 0 order by TXN_NUMBER asc"
				, new Object[] { invoiceBatchId }, new InvoiceARTransactionMapper(schemeService));

		final List<InvoiceTransaction> generatedInvoices = new ArrayList<>();
		if (invoiceHeaders.isEmpty()) {
			return new InvoiceTransactionWrapper(generatedInvoices, null);
		}

		Iterator<InvoiceARTransactionRec> iterator = invoiceHeaders.iterator();
		int recordCount = 0 , batchCount = 0;
		HashMap<String,Integer> hmRecords= new HashMap<>();

		List<InvoiceARTransactionRec> finalInvoiceHeaders = new ArrayList<>();
		List<InvoiceARTransactionRec> tempInvoiceHeaders = new ArrayList<>();

		Scheme scheme = null;
		if (iterator.hasNext()) {
		  scheme = invoiceHeaders.get(0).getScheme();
		}
		
		while (iterator.hasNext()) {
			recordCount ++;
			InvoiceARTransactionRec invcARTransactionRec = (InvoiceARTransactionRec)iterator.next();
			if(batchCount == 0)
				hmRecords.put(invcARTransactionRec.getTransactionNumber(), batchCount);
			if(hmRecords.containsKey(invcARTransactionRec.getTransactionNumber())) {
				finalInvoiceHeaders.add(invcARTransactionRec);	
				tempInvoiceHeaders.add(invcARTransactionRec);
				if(recordCount <= pageSize) {
					batchCount ++;
					hmRecords.put(invcARTransactionRec.getTransactionNumber(), batchCount);
				}else {
					finalInvoiceHeaders.removeAll(tempInvoiceHeaders);
					hmRecords.remove(invcARTransactionRec.getTransactionNumber());
					recordCount = recordCount - tempInvoiceHeaders.size();
					break;
				}
			}
			else if(!hmRecords.containsKey(invcARTransactionRec.getTransactionNumber())){
				if(recordCount <= pageSize) {
					batchCount = 1;
					hmRecords.put(invcARTransactionRec.getTransactionNumber(),batchCount);
					tempInvoiceHeaders.clear();
					finalInvoiceHeaders.add(invcARTransactionRec);	
					tempInvoiceHeaders.add(invcARTransactionRec);					
				}
			}
		}    

		String sqlIN = finalInvoiceHeaders.stream()
				.map(invoiceARTransactionRec-> "'"+invoiceARTransactionRec.getId()+"'")
				.collect(Collectors.joining(",", "(", ")"));

		String updateSQL = "update INVOICE_AR_TRANSACTION_REC set SYNCED = 1 where ID in (?)".replace("(?)", sqlIN);
		Iterator<InvoiceARTransactionRec> itr = finalInvoiceHeaders.iterator();
		int result = localJdbcTemplate.update(updateSQL);

		//		LOG.info("The number of records sent {} for a particular txn ID. {} , result of update is {}", recordCount, hmRecords,result);

		while (itr.hasNext()) {
			final InvoiceTransaction response = new ARInvoiceTransaction(itr.next());
			generatedInvoices.add(response);
		}

		return new InvoiceTransactionWrapper(generatedInvoices, scheme == null ? null : scheme.getId());
	}

}
