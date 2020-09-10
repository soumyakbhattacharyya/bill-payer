package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.MultiIdentifierLoadAccess;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.serviceco.coex.exception.InvoiceAttributeNotFoundException;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.calculation.PaymentTxnType;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.calculation.QPaymentMetadata;
import com.serviceco.coex.payment.model.calculation.QVGenericPaymentRecord;
import com.serviceco.coex.payment.model.calculation.VGenericPaymentRecord;
import com.serviceco.coex.payment.model.calculation.View;
import com.serviceco.coex.payment.model.invoice.InvoiceTransaction;
import com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionHeader;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader;
import com.serviceco.coex.payment.support.Filter;
import com.serviceco.coex.payment.support.FilteringDecoratorByPaymentType;
import com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantId;
import com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantType;
import com.serviceco.coex.scheme.participant.model.MdtParticipant;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.model.QSchemeParticipantRelationshipHeader;
import com.serviceco.coex.scheme.participant.model.SchemeParticipantRelationshipDetail;
import com.serviceco.coex.scheme.participant.model.SchemeParticipantRelationshipHeader;
import com.serviceco.coex.scheme.participant.repository.MdtParticipantSiteRepository;
import com.serviceco.coex.util.DateUtility;
import com.serviceco.coex.util.model.SchemeRefCodes;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Generates AP (Accounts Payable) invoices based on "processable" payment records.
 * 
 * <p>See {@link #generateInvoices(InvoicingRequest)}</p>
 *
 */
@Service
@Transactional
@NoArgsConstructor
public class APInvoiceGenerationService extends GenericService implements InvoiceGenerationService {

  private static final Logger LOG = LoggerFactory.getLogger(APInvoiceGenerationService.class);

  @Autowired
  private APTransactionIsolator transactionIsolator;

  public List<InvoiceTransaction> map(List<APInvoiceTransactionRecHeader> source) {
    final List<InvoiceTransaction> generatedInvoices = new ArrayList<>();
    for (final APInvoiceTransactionRecHeader row : source) {
      final InvoiceTransaction response = new APInvoiceTransactionHeader(row);
      generatedInvoices.add(response);
    }
    return generatedInvoices;
  }

  /**
   * Generates AP (Accounts Payable) invoices based on "processable" payment records.
   * 
   * <p>The "processable" records ({@link com.serviceco.coex.payment.model.calculation.QVGenericPaymentRecord}) are fetched from the {@code vGenericPaymentRecord} view
   * where the {@code schemeParticipantType} field matches the {@code request.schemeParticipantType.supplierType} passed in.
   * </p>
   * 
   * <p>The payment records are filtered by:</p>
   * <ul>
   * <li>the scheme participant type (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantType})</li> 
   * <li>the scheme participant IDs passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorBySchemeParticipantId})</li>
   * <li>the scheme payment types passed in (see {@link com.serviceco.coex.payment.support.FilteringDecoratorByPaymentType})</li>
   * <li>the scheme</li>
   * </ul>
   * 
   * <p>The filtered records are grouped by the scheme participant IDs. Invoices are then generated for each scheme participant ID by calling
   *  {@link com.serviceco.coex.payment.service.APInvoiceGenerationService.APTransactionIsolator#isolateTransactionAndProcess} </p>
   *  
   *  @param request The request body which was sent to to the APInvoicingOfPaymentTransaction web service. It should contain:
   *  @param request.schemeParticipantType The type of scheme participant this should generate invoices for. The participants (if specified) should be of this type.  
   *  @param request.schemeParticipantIds A list of scheme participant IDs to generate invoices for, or "ALL" to generate invoices for all participants.
   *  @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   *  @param request.include If true, the payment transaction types and scheme participant IDs passed in will be allowed and everything else excluded. If false, the payment transaction types and scheme participant IDs passed in will be excluded.
   *  @param scheme The scheme to generate invoices for.
   * 
   *  @return Returns an {@link com.serviceco.coex.payment.model.invoice.InvoiceTransactionWrapper} containing the invoices generated, a list of error messages (if there were errors generated) and the invoice batch ID.  
   */
  @Override
  public InvoiceTransactionWrapper generateInvoices(InvoicingRequest request, Scheme scheme) {

    LOG.info("request received for generating invoice {}", request);

    InvoiceAttributeCache attributesCache = new InvoiceAttributeCache(scheme);
    
    final SchemeParticipantType schemeParticipantType = request.getSchemeParticipantType();

    final QPaymentMetadata qPaymentMetadata = QPaymentMetadata.paymentMetadata;
    final List<PaymentMetadata> metadata = getQueryFactory().select(qPaymentMetadata).from(qPaymentMetadata)
        .where(qPaymentMetadata.schemeParticipantType.eq(request.getSchemeParticipantType())
            .and(qPaymentMetadata.invoiceType.eq(InvoiceConstants.AP.INVOICE_TYPE)))
        .fetch();

    // find all payment records which can be processed
    final QVGenericPaymentRecord qvProcessablePaymentRecord = QVGenericPaymentRecord.vGenericPaymentRecord;
    final List<VGenericPaymentRecord> paymentIds = getQueryFactory().select(qvProcessablePaymentRecord)
        .from(qvProcessablePaymentRecord)
        .where(qvProcessablePaymentRecord.schemeParticipantType.eq(schemeParticipantType.getSupplierType())
            .and(qvProcessablePaymentRecord.multiSchemeId.eq(scheme.getMultiSchemeId())))
        .fetch();
    
    final List<View> viableRecords = paymentIds.stream().map(new VGenericPaymentToViewFunction()).collect(Collectors.toList());

    // filters by scheme participant type (viableRecords[x].
    final Filter filter = new FilteringDecoratorByPaymentType(metadata, new FilteringDecoratorBySchemeParticipantId(new FilteringDecoratorBySchemeParticipantType()));

    // filter payment records by payment type, scheme participant id and scheme participant type
    final List<View> filteredPayments0 = filter.doFilter(request, viableRecords);

    // get all payment records which should be processed
    final List<PaymentTransactionRec> allPayments = new ArrayList<>();

    List<String> paymentTransactionIds = filteredPayments0.stream().map(t -> t.getPaymentTransactionId()).collect(Collectors.toList());

    Session session = em.unwrap(Session.class);
    MultiIdentifierLoadAccess<PaymentTransactionRec> multiLoadAccess = session.byMultipleIds(PaymentTransactionRec.class);
    List<PaymentTransactionRec> recs = multiLoadAccess.withBatchSize(paymentTransactionIds.size() % 999).multiLoad(paymentTransactionIds);
    allPayments.addAll(recs);

    final List<APInvoiceTransactionRecHeader> from = new ArrayList<>();
    final List<String> errors = new ArrayList<>();

    // QCRS-1213 generating all invoices of one run under one batch id.
    String invoiceBatchId = UUID.randomUUID().toString();

    //@formatter:on

    // group by scheme participant ids
    final Map<String, List<PaymentTransactionRec>> paymentsGroupedBySchemeParticipants = allPayments.stream()
        .collect(Collectors.groupingBy(PaymentTransactionRec::getSchemeParticipantId));

    Map<String, Period> cachedPeriod = new HashMap<>();
    for (final Map.Entry<String, List<PaymentTransactionRec>> entry1 : paymentsGroupedBySchemeParticipants.entrySet()) {
      try {
        LOG.info("Processing invoices for scheme participant : " + entry1.getKey());
        List<APInvoiceTransactionRecHeader> invoices = transactionIsolator.isolateTransactionAndProcess(request, invoiceBatchId, entry1.getKey(), entry1.getValue(), 
            cachedPeriod, attributesCache, scheme, errors);
        from.addAll(invoices);
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
    return new InvoiceTransactionWrapper(map(from), errors, invoiceBatchId, scheme.getId());
  }

  private final class VGenericPaymentToViewFunction implements Function<VGenericPaymentRecord, View> {
    @Override
    public View apply(VGenericPaymentRecord t) {
      return new View() {

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

  class EntityWisePaymentTransaction {
    String entity;
    List<PaymentTransactionRec> payments;

    public EntityWisePaymentTransaction(String entity, List<PaymentTransactionRec> payments) {
      super();
      this.entity = entity;
      this.payments = payments;
    }

  }

  @AllArgsConstructor
  @Getter
  @EqualsAndHashCode
  class LegalEntityTuple {
    String legalEntityId;
    String legalEntityName;
    String schemeIdForLegalEntity;
    String supplierNumber;
  }

  @Service
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  class APTransactionIsolator {

    @Autowired
    private TransactionHeaderRepositoryFacade headerRepoFacade;

    @Autowired
    private MdtParticipantSiteRepository schemeParticipantRepository;

    @Autowired
    private APInvoiceGenerationPersistenceService apInvoiceGenerationPersistenceService;

    /**
     * <p>Generates invoices for a particular scheme participant ID and associated payment transaction records. This method is wrapped in a database transaction so if an exception is thrown, the database will be rolled back.</p>
     * 
     * <p>The payment transactions are first grouped by the transaction type.</p>
     * 
     * <p>If transaction type is a refund or collection payment type and the scheme entity type is a CRP, the mapping between the source participant and target
     * processors is looked at, including the material types the target processors accept from the source entity. A separate invoice is generated
     * for each target processor and each payment type. The relationships between source participant and processors (plus the materials accepted) is queried
     * from the {@link com.serviceco.coex.scheme.participant.model.QSchemeParticipantRelationshipHeader} records.</p>
     * 
     * <p>If the transaction type is a refund and the scheme entity type is a Consumer, the payment transaction records are grouped by the processor
     * and an invoice is generated for each one. In this scenario, the processor is defined by the {@link com.serviceco.coex.payment.model.calculation.PaymentTransactionRec} records.</p>
     *  
     * <p>For any other type of transaction, a single invoice is generated for all {@code PaymentTransactionRec} records with transactions of that type.
     * There is also a SQL procedure ({@code PROC_EXPORTER_REFUND_TXN_HD}) invoked to "update exporter refunds transaction headers" in this scenario. This indicates perhaps
     * the 'other' transactions are for exporters.
     * </p>
     * 
     * <p>Each individual invoice is generated by calling {@link com.serviceco.coex.payment.service.APInvoiceGenerationPersistenceService#process}.</p>
     *  
     * 
     * @param request The invoicing request which was passed to the  APInvoicingOfPaymentTransaction web service. The data should include:
     * @param request.schemeParticipantType	The scheme participant type to generate invoices for.
     * @param invoiceBatchId An ID which can be used to identify the current batch of invoice generation.
     * @param schemeParticipantId  The ID of the scheme participant this should generate invoices for.
     * @param allPaymentsForSpecificSchemeParticipant The payment transaction records to include in the invoices.
     * @param cachedPeriod Used to temporarily cache payment periods. This can be an empty Map initially.
     * @param scheme The scheme associated with the payments
     * @param errors An existing list where errors can be appended to (for reporting in the results)
     * @return Returns a list of the generated AP invoice transaction headers.
     */
    public List<APInvoiceTransactionRecHeader> isolateTransactionAndProcess(InvoicingRequest request, String invoiceBatchId, String schemeParticipantId,
        List<PaymentTransactionRec> allPaymentsForSpecificSchemeParticipant, Map<String, Period> cachedPeriod, InvoiceAttributeCache attributesCache, Scheme scheme, List<String> errors) {

      List<APInvoiceTransactionRecHeader> from = new ArrayList<>();
      final BigDecimal invoiceGroupNumber = new BigDecimal(1);

      final MdtParticipantSite schemeParticipant = schemeParticipantRepository.findBySiteNumber(schemeParticipantId).get();
      final Map<String, List<PaymentTransactionRec>> paymentsGroupedByPaymentType = allPaymentsForSpecificSchemeParticipant.stream()
          .collect(Collectors.groupingBy(new Function<PaymentTransactionRec, String>() {

            @Override
            public String apply(PaymentTransactionRec t) {
              final String paymentType = t.getPaymentType();
              return paymentType;
            }
          }));

      for (final Entry<String, List<PaymentTransactionRec>> paymentPerTransactionType : paymentsGroupedByPaymentType.entrySet()) {
        final String paymentTransactionType = paymentPerTransactionType.getKey().split("#")[0];

        final boolean isRefundOrCollectionPaymentTransactionType = (StringUtils.equals(paymentTransactionType, PaymentTxnType.REFUND_AMOUNT.name())
            || StringUtils.equals(paymentTransactionType, PaymentTxnType.COLLECTION_FEES.name())) && request.getSchemeParticipantType().name().equals("CRP") ? true : false;

        // final String paymentMethod = paymentPerTransactionType.getKey().split("#")[1];
        final List<PaymentTransactionRec> allPaymentsForSpecificPaymentTransactionType = paymentPerTransactionType.getValue();

        // A refund or collection fees where the scheme participant type is CRP
        if (isRefundOrCollectionPaymentTransactionType) {

          handleCrpRefundOrCollectionFees(invoiceBatchId, cachedPeriod, from, invoiceGroupNumber, schemeParticipant,
              paymentTransactionType, allPaymentsForSpecificPaymentTransactionType, attributesCache, scheme, errors);

        } else {
          apInvoiceGenerationPersistenceService.process(from, invoiceBatchId, invoiceGroupNumber, schemeParticipant, paymentTransactionType, StringUtils.EMPTY,
              allPaymentsForSpecificPaymentTransactionType, null, cachedPeriod, attributesCache, scheme);
          
          List<String> paymentBatchIds=allPaymentsForSpecificPaymentTransactionType.stream().map(p->p.getPaymentBatch().getId()).collect(Collectors.toList());
          headerRepoFacade.updateExporterRefundTxnHeader(paymentBatchIds, schemeParticipant.getSiteNumber());
        }
      }
      return from;
    }

    private void handleCrpRefundOrCollectionFees(String invoiceBatchId, Map<String, Period> cachedPeriod,
        List<APInvoiceTransactionRecHeader> from, final BigDecimal invoiceGroupNumber,
        final MdtParticipantSite schemeParticipant, final String paymentTransactionType,
        final List<PaymentTransactionRec> allPaymentsForSpecificPaymentTransactionType,
        InvoiceAttributeCache attributesCache,
        Scheme scheme, List<String> errors) {
      final QSchemeParticipantRelationshipHeader qSchemeParticipantRelationshipHeader = QSchemeParticipantRelationshipHeader.schemeParticipantRelationshipHeader;
      // @formatter:off
      // Gets all processors which have a relationship with the scheme participant & the material types each processor accepts
      Date now = new Date();
      final List<SchemeParticipantRelationshipHeader> relationships = getQueryFactory().select(qSchemeParticipantRelationshipHeader)
                                                                      .from(qSchemeParticipantRelationshipHeader)
                                                                      .where(qSchemeParticipantRelationshipHeader.sourceSchemeParticipant.eq(schemeParticipant)
                                                                      .and(qSchemeParticipantRelationshipHeader.targetSchemeParticipant.siteTypeId.eq(SchemeRefCodes.ParticipantSiteType.fetchId(SchemeRefCodes.ParticipantSiteType.PROCESSOR)))
                                                                      .and(qSchemeParticipantRelationshipHeader.effectiveFrom.loe(now))
                                                                      .and(qSchemeParticipantRelationshipHeader.effectiveTo.isNull().or(qSchemeParticipantRelationshipHeader.effectiveTo.gt(now)))
                                                                      )
                                                                      .fetch();
      // @formatter:on
      // at this point we have got all valid material types and legal entity combination for the scheme participant

      final Map<LegalEntityTuple, List<MaterialType>> entityMaterialMap = new HashMap<>();

      for (final SchemeParticipantRelationshipHeader relationship : relationships) {
        final List<SchemeParticipantRelationshipDetail> details = relationship.getMaterialTypes();
        for (final SchemeParticipantRelationshipDetail detail : details) {
          if (!DateUtility.isActiveNow(now, detail)) {
            continue;
          }
          final MaterialType materialType = detail.getMaterialType();
          MdtParticipantSite targetSchemeParticipantSite = detail.getHeader().getTargetSchemeParticipant();
          MdtParticipant participant = targetSchemeParticipantSite.getParticipant();
          final String legalEntityId = targetSchemeParticipantSite.getErpLegalEntityId();
          final String legalEntityName = targetSchemeParticipantSite.getErpLegalEntityName();
          final String schemeIdForLegalEntity = targetSchemeParticipantSite.getSiteNumber();
          final String supplierNumber = ""+participant.getParticipantNumber();
          LegalEntityTuple legalEntityTuple = new LegalEntityTuple(legalEntityId, legalEntityName, schemeIdForLegalEntity, supplierNumber);
          if (entityMaterialMap.containsKey(legalEntityTuple)) {
            entityMaterialMap.get(legalEntityTuple).add(materialType);
          } else {
            entityMaterialMap.put(legalEntityTuple, new ArrayList<>());
            entityMaterialMap.get(legalEntityTuple).add(materialType);
          }
        }
      }

      final Map<MaterialType, List<PaymentTransactionRec>> paymentsGroupedByMaterialType = allPaymentsForSpecificPaymentTransactionType.stream()
          .collect(Collectors.groupingBy(PaymentTransactionRec::getMaterialType));

      final Map<LegalEntityTuple, List<PaymentTransactionRec>> entityPaymentTransactionMap = new HashMap<>();

      Set<MaterialType> unMatchedMterialTypesInPayments = new HashSet<>(paymentsGroupedByMaterialType.keySet());
      
      for (final Entry<LegalEntityTuple, List<MaterialType>> legalEntityMaterialType : entityMaterialMap.entrySet()) {
        final LegalEntityTuple legalEntity = legalEntityMaterialType.getKey();
        final List<MaterialType> materialTypes = legalEntityMaterialType.getValue();
        for (final MaterialType materialType : materialTypes) {
        	// Since this is getting the payments grouped by material type, there must only be a single processor for each material type. Otherwise
        	// the same payments would be duplicated between processors.
          final List<PaymentTransactionRec> payments = paymentsGroupedByMaterialType.get(materialType);
          if ((null != payments) && !payments.isEmpty()) {
            if (entityPaymentTransactionMap.containsKey(legalEntity)) {
              final List<PaymentTransactionRec> existingPaymentTransactions = entityPaymentTransactionMap.get(legalEntity);
              existingPaymentTransactions.addAll(payments);

            } else {
              entityPaymentTransactionMap.put(legalEntity, payments);
            }
            unMatchedMterialTypesInPayments.remove(materialType);
          }
        }
      }

      if (unMatchedMterialTypesInPayments.size() > 0) { 
        for (MaterialType unMatchedMaterialType : unMatchedMterialTypesInPayments) {
          errors.add("There is no relationship with a target processor for material type " + unMatchedMaterialType.getId() 
            + " for source entity " + schemeParticipant.getSiteNumber());
        }
      }

      // At this point, entityPaymentTransactionMap contains a mapping of the processors and their payment transaction records (based on
      // the material types specified in the relationship between the source scheme participant and the target processor).
      for (final Entry<LegalEntityTuple, List<PaymentTransactionRec>> paymentPerEntityType : entityPaymentTransactionMap.entrySet()) {

        final LegalEntityTuple entity = paymentPerEntityType.getKey();
        final List<PaymentTransactionRec> allPaymentsForLegalEntity = paymentPerEntityType.getValue();
        final Map<String, List<PaymentTransactionRec>> paymentsGroupedByPaymentMethod = allPaymentsForLegalEntity.stream()
            .collect(Collectors.groupingBy(new Function<PaymentTransactionRec, String>() {

              @Override
              public String apply(PaymentTransactionRec t) {
                final String paymentMethod = StringUtils.isEmpty(t.getPaymentMethod()) ? "NA" : t.getPaymentMethod();
                return paymentMethod;
              }
            }));

        for (final Entry<String, List<PaymentTransactionRec>> entryPerPaymentMethod : paymentsGroupedByPaymentMethod.entrySet()) {
          final List<PaymentTransactionRec> allPaymentsPerPaymentMethod = entryPerPaymentMethod.getValue();
          final String paymentMethod = entryPerPaymentMethod.getKey();
          apInvoiceGenerationPersistenceService.process(from, invoiceBatchId, invoiceGroupNumber, schemeParticipant, paymentTransactionType, paymentMethod,
              allPaymentsPerPaymentMethod, entity, cachedPeriod, attributesCache, scheme);
        }

      }
    }

  }

}
