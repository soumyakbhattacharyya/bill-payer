package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

import com.serviceco.coex.exception.InvoiceAttributeNotFoundException;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentMetadata;
import com.serviceco.coex.payment.model.calculation.QVGenericPaymentRecordAR;
import com.serviceco.coex.payment.model.calculation.QVProcessablePaymentRecord;
import com.serviceco.coex.payment.model.calculation.VGenericPaymentRecordAR;
import com.serviceco.coex.payment.model.calculation.VProcessablePaymentRecord;
import com.serviceco.coex.payment.model.calculation.View;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ar.ARInvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.payment.support.Filter;
import com.serviceco.coex.payment.support.FilteringDecoratorByPaymentType;
import com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantId;
import com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantType;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.model.QMdtParticipantSite;

import lombok.NoArgsConstructor;

@Service
@Transactional
@NoArgsConstructor
public class ARInvoiceGenerationService extends GenericService implements InvoiceGenerationService {

  private static final String INVOICE_TYPE_AR = "AR";

  private static final Logger LOG = LoggerFactory.getLogger(ARInvoiceGenerationService.class);

  @Autowired
  private ARTransactionIsolator transactionIsolator;

  /**
   * Generates AR (Accounts Receivable) invoices for a particular type of scheme participants.
   * 
   * <p>If the scheme participant type is a small or large manufacturer, the processable payment records are found by looking at {@link com.serviceco.coex.payment.model.calculation.QVProcessablePaymentRecord} records with a status of AWAITING_INVOICING.</p>  
   * 
   * <p>For all other scheme participant types, the processable payment records are found by looking at {@link com.serviceco.coex.payment.model.calculation.QVGenericPaymentRecordAR} records which have a {@code schemeParticipantType} field which matches the request.schemeParticipantType.supplierType.</p>
   * 
   * The processable transaction records are then filtered by:
   * <ul>
   * <li>the scheme participant type (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantType})</li> 
   * <li>the scheme participant IDs passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantId})</li>
   * <li>the scheme payment types passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorByPaymentType})</li>
   * </ul> 
   * 
   * <p>The filtered records are grouped by the scheme participant IDs. Invoices are then generated for each scheme participant ID by calling
   *  {@link com.serviceco.coex.payment.service.ARInvoiceGenerationService.ARTransactionIsolator#isolateTransactionAndProcess} </p>
   *  
   *  @param request The request body which was sent to to the ARInvoicingOfPaymentTransaction web service. It should contain:
   *  @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   *  @param request.schemeParticipantIds A list of scheme participant IDs to generate invoices for, or "ALL" to generate invoices for all participants.
   *  @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   *  @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
   *  @param scheme Invoices will only be generated for this scheme
   * 
   *  @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices generated, a list of error messages (if there were any errors) and the invoice batch ID.
   */ 
  @Override
  public InvoiceTransactionWrapper generateInvoices(InvoicingRequest request, Scheme scheme) {

    // Generate Invoice batch id
    String invoiceBatchId = UUID.randomUUID().toString();
    
    LOG.info("Generating AR invoices for type " + request.getSchemeParticipantType() + " and scheme " + scheme.getId());

    InvoiceAttributeCache attributeCache = new InvoiceAttributeCache(scheme);
    
    final List<InvoiceARTransactionRec> invoices = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    final SchemeParticipantType schemeParticipantType = request.getSchemeParticipantType();

    List<View> viableRecords0 = null;

    final QPaymentMetadata qPaymentMetadata = QPaymentMetadata.paymentMetadata;
    final List<PaymentMetadata> metadata = getQueryFactory().select(qPaymentMetadata).from(qPaymentMetadata)
        .where(qPaymentMetadata.schemeParticipantType.eq(request.getSchemeParticipantType()).and(qPaymentMetadata.invoiceType.eq(INVOICE_TYPE_AR))).fetch();

    if (request.getSchemeParticipantType().equals(SchemeParticipantType.LRG_MANUFACTURER) || request.getSchemeParticipantType().equals(SchemeParticipantType.SML_MANUFACTURER)) {

      // find all payment records which can be processed
      final QVProcessablePaymentRecord qvProcessablePaymentRecord = QVProcessablePaymentRecord.vProcessablePaymentRecord;
      final List<VProcessablePaymentRecord> paymentIds = getQueryFactory().select(qvProcessablePaymentRecord).from(qvProcessablePaymentRecord).where(
          qvProcessablePaymentRecord.status.eq(PaymentTransactionRec.PaymentStatus.AWAITING_INVOICING.name())
              .and(qvProcessablePaymentRecord.schemeParticipantType.eq(schemeParticipantType.getSupplierType()))
              .and(qvProcessablePaymentRecord.multiSchemeId.eq(scheme.getMultiSchemeId()))
          ).fetch();

      viableRecords0 = paymentIds.stream().map(new VProcessablePaymentToViewFunction()).collect(Collectors.toList());
    } else {

      // find all payment records which can be processed
      final QVGenericPaymentRecordAR qvProcessablePaymentRecord = QVGenericPaymentRecordAR.vGenericPaymentRecordAR;
      final List<VGenericPaymentRecordAR> paymentIds = getQueryFactory().select(qvProcessablePaymentRecord).from(qvProcessablePaymentRecord)
          .where(qvProcessablePaymentRecord.schemeParticipantType.eq(schemeParticipantType.getSupplierType())
              .and(qvProcessablePaymentRecord.multiSchemeId.eq(scheme.getMultiSchemeId())))
          .fetch();

      viableRecords0 = paymentIds.stream().map(new VGenericPaymentARToView()).collect(Collectors.toList());
    }

    final Filter filter = new FilteringDecoratorByPaymentType(metadata, new FilteringDecoratorBySchemeParticipantId(new FilteringDecoratorBySchemeParticipantType()));
    final List<View> filteredPayments = filter.doFilter(request, viableRecords0);

    final List<PaymentTransactionRec> paymentsLevel1 = new ArrayList<>();

    List<String> paymentTransactionIds = filteredPayments.stream().map(t -> t.getPaymentTransactionId()).collect(Collectors.toList());
    Session session = em.unwrap(Session.class);
    MultiIdentifierLoadAccess<PaymentTransactionRec> multiLoadAccess = session.byMultipleIds(PaymentTransactionRec.class);
    List<PaymentTransactionRec> recs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);
    paymentsLevel1.addAll(recs);

    final Map<String, List<PaymentTransactionRec>> paymentsGroupedBySchemeParticipants = paymentsLevel1.stream()
        .collect(Collectors.groupingBy(PaymentTransactionRec::getSchemeParticipantId));
    for (final Map.Entry<String, List<PaymentTransactionRec>> entry1 : paymentsGroupedBySchemeParticipants.entrySet()) {
      try {
        List<InvoiceARTransactionRec> generatedInvoices = transactionIsolator.isolateTransactionAndProcess(request, entry1.getKey(), entry1.getValue(), invoiceBatchId, attributeCache, scheme);
        invoices.addAll(generatedInvoices);
      } catch (Exception e) {
        LOG.error("Could not process payment transaction for scheme participant " + entry1.getKey(), e);
        String errorMessage = e.getMessage();
        if (e instanceof InvoiceAttributeNotFoundException) {
          InvoiceAttributeNotFoundException ex = (InvoiceAttributeNotFoundException) e;
          if (ex.getErrorMessage() != null) {
            if (CollectionUtils.isNotEmpty(ex.getErrorMessage().getErrors())) {
              errorMessage = ex.getErrorMessage().getErrors().get(0).getAdditionalInfo();
            }
          }
        }
        errors.add(String.format("Scheme participant id: %s, error-message: %s", entry1.getKey(), errorMessage));
      }
    }
    InvoiceTransactionWrapper wrapper = new InvoiceTransactionWrapper(asResponse(invoices), errors, invoiceBatchId, scheme.getId());

    //Sorting the invoice based on priority order(R>F>FO>A) for manufacturer scheme participant
    if (request.getSchemeParticipantType().equals(SchemeParticipantType.LRG_MANUFACTURER) || request.getSchemeParticipantType().equals(SchemeParticipantType.SML_MANUFACTURER)) {
      if (CollectionUtils.isNotEmpty(wrapper.getInvoices())) {
        sortInvoices(wrapper.getInvoices());
      }
    }
    
    LOG.info(invoices.size() + " AR Invoices generated for scheme " + scheme.getId());
    return wrapper;
  }

  public void sortInvoices(List<InvoiceTransaction> invoiceTxn) {

    Comparator<InvoiceTransaction> comparator = (txn1, txn2) -> {
      return txn1.getPriorityOrder() - txn2.getPriorityOrder();
    };
    invoiceTxn.sort(comparator);
  }

  public List<InvoiceTransaction> asResponse(List<InvoiceARTransactionRec> source) {

    final List<InvoiceTransaction> generatedInvoices = new ArrayList<>();
    for (final InvoiceARTransactionRec row : source) {
      final ARInvoiceTransaction response = new ARInvoiceTransaction(row);

      generatedInvoices.add(response);
    }
    return generatedInvoices;
  }

  private boolean hasInvoicablePaymentTransactions(final List<PaymentTransactionRec> payments) {

    return (null != payments) && !payments.isEmpty();
  }

  private final class VGenericPaymentARToView implements Function<VGenericPaymentRecordAR, View> {
    @Override
    public View apply(VGenericPaymentRecordAR t) {

      return new View() {

        private static final long serialVersionUID = 1L;

        @Override
        public String getStatus() {

          return t.getStatus();
        }

        @Override
        public String getSchemeParticipantType() {

          return t.getSchemeParticipantType();
        }

        @Override
        public String getSchemeParticipantId() {

          return t.getSchemeParticipantId();
        }

        @Override
        public String getPaymentType() {

          return t.getPaymentType();
        }

        @Override
        public String getPaymentTransactionId() {

          return t.getPaymentTransactionId();
        }

        @Override
        public String getId() {

          return t.getId();
        }

      };
    }
  }

  private final class VProcessablePaymentToViewFunction implements Function<VProcessablePaymentRecord, View> {
    @Override
    public View apply(VProcessablePaymentRecord t) {

      return new View() {

        private static final long serialVersionUID = 1L;

        @Override
        public String getStatus() {

          return t.getStatus();
        }

        @Override
        public String getSchemeParticipantType() {

          return t.getSchemeParticipantType();
        }

        @Override
        public String getSchemeParticipantId() {

          return t.getSchemeParticipantId();
        }

        @Override
        public String getPaymentType() {

          return t.getPaymentType();
        }

        @Override
        public String getPaymentTransactionId() {

          return t.getPaymentTransactionId();
        }

        @Override
        public String getId() {

          return t.getId();
        }

      };
    }
  }

  @Service
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  class ARTransactionIsolator {

    @Autowired
    private DateTimeSupport dateTimeSupport;

    @Autowired
    private InvoiceNumberGenerator invoiceNumberGenerator;

    @Autowired
    private ARInvoiceGenerationPersistenceService arInvoiceGenerationPersistenceService;

    /**
     * Generates invoices for a particular scheme participant and list of payment transaction records. 
     * 
     * <p>This method wraps the transaction so if an exception is thrown it will rolled back.</p>
     * 
     * <p>The payment transaction records are grouped by the payment type, then an invoice is generated for each payment type.</p>
     * 
     * <p>Invoice lines are generated for each payment transaction record associated with the payment type.</p>
     * 
     * <p>The actual invoice lines are built and persisted through {@link com.serviceco.coex.payment.service.ARInvoiceGenerationPersistenceService#buildInvoice}. </p>
     * 
     * <p>After each invoice is generated, there is an additional step to set/update statuses associated with the invoice. See {@link com.serviceco.coex.payment.service.ARInvoiceGenerationPersistenceService#postStep}.</p> 
     * 
     * @param request The input parameters which were passed into the invoice generation web service.
     * @param schemeParticipantId Not used
     * @param paymentsLevel2 The transactions which are being converted into invoice lines
     * @param invoiceBatchId ID which identifies the current execution of the invoice generation web service
     * @param scheme The scheme associated with the transaction/invoice
     * @return Returns the invoice records created
     */
    public List<InvoiceARTransactionRec> isolateTransactionAndProcess(InvoicingRequest request, String schemeParticipantId, List<PaymentTransactionRec> paymentsLevel2,
        String invoiceBatchId, InvoiceAttributeCache attributeCache, Scheme scheme) {

      List<InvoiceARTransactionRec> invoices = new ArrayList<>();
      // final String schemeParticipantId = entry1.getKey();
      // final List<PaymentTransactionRec> paymentsLevel2 = entry1.getValue();
      final Map<String, List<PaymentTransactionRec>> paymentsGroupedByPaymentType = paymentsLevel2.stream().collect(Collectors.groupingBy(PaymentTransactionRec::getPaymentType));
      for (final Map.Entry<String, List<PaymentTransactionRec>> entry : paymentsGroupedByPaymentType.entrySet()) {
        final List<PaymentTransactionRec> paymentsLevel3 = entry.getValue();
        BigDecimal netAmount = BigDecimal.ZERO;
        for (PaymentTransactionRec rec : paymentsLevel3) {
          netAmount = netAmount.add(rec.getGrossAmount());
        }

        final String transactionNumber = String.valueOf(invoiceNumberGenerator.createOrFindTransactionNumber());

        if (hasInvoicablePaymentTransactions(paymentsLevel3)) {

          final String invoiceLineNumberPrefix = "Line";
          int invoiceLineNumber = 0;
          for (final PaymentTransactionRec payment : paymentsLevel3) {
            invoiceLineNumber++;

            final Period period = dateTimeSupport.periodFactory(payment.getPeriod(), PeriodType.valueOf(payment.getPeriodType()));

            final QMdtParticipantSite qSchemeParticipant = QMdtParticipantSite.mdtParticipantSite;
            final MdtParticipantSite participant = getQueryFactory().select(qSchemeParticipant).from(qSchemeParticipant)
                .where(qSchemeParticipant.siteNumber.eq(payment.getSchemeParticipantId())).fetchOne();

            // build invoice
            final InvoiceARTransactionRec invoice = arInvoiceGenerationPersistenceService
                .buildInvoice(participant, period, transactionNumber, invoiceLineNumberPrefix + invoiceLineNumber, payment, null, invoiceBatchId, netAmount, 
                    attributeCache, scheme);

            arInvoiceGenerationPersistenceService.postStep(request.getSchemeParticipantType(), payment, invoice);
            invoices.add(invoice);

          }
        }
      }
      return invoices;
    }

  }

}
