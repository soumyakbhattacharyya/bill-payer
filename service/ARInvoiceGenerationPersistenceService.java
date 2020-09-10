package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.serviceco.coex.exporter.model.dto.EntryStatus;
import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.manufacturer.model.SalesVolumeHdr;
import com.serviceco.coex.manufacturer.repository.SalesVolumeHeaderRepository;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoiceStatusAssociation;
import com.serviceco.coex.payment.calculation.PaymentTxnType;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.InvoiceStatus;
import com.serviceco.coex.payment.model.invoice.PaymentInvoiceStatus;
import com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec;
import com.serviceco.coex.payment.model.invoice.reference.InvDistributionCodeLov;
import com.serviceco.coex.payment.repository.ARInvoiceTransactionRecRepository;
import com.serviceco.coex.payment.repository.PaymentInvoiceStatusRepository;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.scheme.participant.model.QSchemeParticipantRelationshipHeader;
import com.serviceco.coex.scheme.participant.model.SchemeParticipantRelationshipDetail;
import com.serviceco.coex.scheme.participant.model.SchemeParticipantRelationshipHeader;
import com.serviceco.coex.util.DateUtility;
import com.serviceco.coex.util.model.SchemeRefCodes;
import com.serviceco.coex.util.service.SchemeRefCodeService;

import lombok.NoArgsConstructor;

/**
 * Generates and persists individual AR (Accounts Receivable) invoices.
 *	
 * <p>See {@link #buildInvoice} and {@link #processAuction}</p>
 *
 */
@Service
@Transactional
@NoArgsConstructor
public class ARInvoiceGenerationPersistenceService extends GenericService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ARInvoiceGenerationPersistenceService.class);

  private static final String AUDITED_REC_DESCRIPTION = "AUDIT_ADJUSTMENT";

  private static final String INELIGIBLE_MATERIAL_TYPE = MaterialType.INELIGIBLE;

  private static final String BM_CREDIT = "BM Credit";

  private static final String BM_INVOICE = "BM Invoice";

  @Autowired
  private ARInvoiceTransactionRecRepository invoiceRepo;

  @Autowired
  private InvoiceNumberGenerator invoiceNumberGenerator;

  @Autowired
  private SalesVolumeHeaderRepository salesVolumeRepo;

  @Autowired
  private PaymentInvoiceStatusRepository paymentInvoiceStatusRepo;

  @Autowired
  private InvoiceAtrributeFinder attributeFinder;

  @Autowired
  private ARInvoiceTransactionRecRepository arInvoiceTransactionRecRepo;
  
  @Autowired
  private SchemeRefCodeService schemeRefCodeService;

  public InvoiceARTransactionRec postStep(final SchemeParticipantType schemeParticipantType, final PaymentTransactionRec payment, final InvoiceARTransactionRec invoice) {

    final PaymentInvoiceStatus paymentInvoiceStatusPOJO = new PaymentInvoiceStatus();
    paymentInvoiceStatusPOJO.setId(UUID.randomUUID().toString());
    paymentInvoiceStatusPOJO.setPayment(payment);
    paymentInvoiceStatusPOJO.setStatus(InvoiceStatus.GENERATED);
    paymentInvoiceStatusPOJO.setInvoiceId(invoice.getId());
    paymentInvoiceStatusPOJO.setScheme(payment.getScheme());
    final PaymentInvoiceStatus paymentInvoiceStatusEntity = paymentInvoiceStatusRepo.save(paymentInvoiceStatusPOJO);

    // payment.setStatus(PaymentStatus.INVOICED);

    if ((schemeParticipantType == SchemeParticipantType.LRG_MANUFACTURER) || (schemeParticipantType == SchemeParticipantType.SML_MANUFACTURER)) {
      final String volumeHeaderId = payment.getVolumeHeaderId();
      if (volumeHeaderId != null) {
        final Optional<SalesVolumeHdr> header = salesVolumeRepo.findById(volumeHeaderId);
        if (header.isPresent()) {
          header.get().setEntryStatus(EntryStatus.INVOICED);
        }
      } else {
        LOGGER.warn("Payment " + payment.getId() + " does not have a SalesVolumeHdr linked. Can not update entry status to INVOICED.");
      }
    }

    invoice.setPaymentInvoiceStatus(paymentInvoiceStatusEntity);
    return invoiceRepo.save(invoice);
    // paymentRepo.save(payment);
  }

  //@formatter:on
  /**
   * Builds a particular invoice line for a particular scheme participant and payment transaction record. 
   * 
   * <p>If the payment transaction type is COLLECTION_FEES, the legal identity is obtained by looking for a QSchemeParticipantRelationshipHeader
   * record where the source participant is equal to the schemeParticipant passed in, the target participant ID starts with "QP" and the material type
   * matches the payment.materialType passed in. The billToCustomerAccount is set to a hard coded "10001", the billToCustomerSiteNumber is set to a hard coded "Q10001001"
   * and soldToCustomerAccountNumber is set to a hard coded "10001".</p> 
   * 
   * <p>For other payment transaction types, the legal entity is looked up using invoice meta data ({@link com.serviceco.coex.payment.model.invoice.reference.QInvLegalIdentifierLov}) based on the invoice type, scheme participant type and payment transaction type. 
   * The billToCustomerAccountNumber, billToCustomerSiteNumber and soldToCustomerAccountNumber fields come from the scheme participant ({@link com.serviceco.coex.scheme.participant.model.MdtParticipantSite}).
   * </p>
   * 
   * <p>The invoice transaction type is set to one of the following:</p>
   * <ul>
   * 	<li>"AuctionProcessor Inv" if its an auction payment and the scheme participant type is {@code PROCESSOR}</li>
   * 	<li>"AuctionPlatfomMRFInv" if its an auction payment, the scheme participant type is MRF and a IS_PLATFORM_FEE key exists in the additionalInfo.</li>
   * 	<li>"Auction Neg MRF Inv" if its an auction payment, the scheme participant type is MRF and a IS_PLATFORM_FEE key does not exists in the additionalInfo.</li>
   * 	<li>"AuctionPrcRecyclrInv" if its an auction payment and the payment transaction type is "PaymentTxnType.PROCESSOR_NEGATIVE_AUCTION.name()" or "PaymentTxnType.PROCESSOR_POSITIVE_AUCTION"</li>
   * 	<li>"AuctionMRFRecyclrInv" if its an auction payment and the above don't apply.</li>
   * 	<li>"BM Credit" if its not an auction payment and the gross amount is negative.</li>
   * 	<li>"BM Invoice" if its not an auction payment and the gross amount is positive.</li>
   * </ul>
   * 
   * <p>Unless the {@code INVOICE_AMOUNT} is passed in through the {@code additionalInfo}, the invoice amount is calculated as the unit selling price multiplied by the volume. The unit selling price and volume come from the payment transaction record if they are not passed in through the {@code additionalInfo}.</p>
   * 
   * 
   * @param schemeParticipant	The participant being invoiced.
   * @param period				The payment period being invoiced.
   * @param invoiceTransactionNumber A number which uniquely identifies the transaction on the invoice. 
   * @param invoiceLineNumber	A number identifying the invoice line within the invoice.
   * @param payment				The payment transaction record which is being invoiced.
   * @param additionalInfo		Key-value pairs containing details to include. The keys can include:  UNIT_SELLING_PRICE, INVOICE_AMOUNT, VOLUME, TAX_CLASSIFICATION_REF, IS_PLATFORM_FEE. If a key for the unit selling price, amount or tax classification is present, the value will be used in preference to obtaining the value in other ways. See {@link com.serviceco.coex.payment.service.InvoiceConstants.AdditionInfo}.
   * @param invoiceBatchId		The ID which identifies the current invoicing batch.
   * @param attributeCache    A cache of pre-loaded invoice attributes. This can be empty to begin with but must only contain data related to the correct scheme.  
   * @return Returns a new {@link com.serviceco.coex.payment.model.invoice.ar.InvoiceARTransactionRec} object containing the invoice details. It is not persisted by this method.
   */
  public InvoiceARTransactionRec buildInvoice(final MdtParticipantSite schemeParticipant, final Period period, final String invoiceTransactionNumber, final String invoiceLineNumber,
      final PaymentTransactionRec payment, Map<String, String> additionalInfo, String invoiceBatchId, BigDecimal netAmount, InvoiceAttributeCache attributeCache, Scheme scheme) {

    final InvoiceARTransactionRec invoice = new InvoiceARTransactionRec();
    if (additionalInfo == null) {
      additionalInfo = new HashMap<>();
    }

    /**
     * decide value for variables
     */

    String transactionTypeId = StringUtils.EMPTY;
    String legalEntityIdentifier = StringUtils.EMPTY;
    String entity = StringUtils.EMPTY;
    String transactionType = StringUtils.EMPTY;

    final String currencyCode = InvoiceConstants.CURRENCY_CODE;
    final String currencyConversionType = InvoiceConstants.CURRENCY_CONVERSION_TYPE;

    final String transactionNumber = "P" + invoiceTransactionNumber;
    final String paymentTransactionType = schemeParticipant.getSiteType().equals("RECYCLER") ?
        payment.getSchemeParticipantType() + "_" + payment.getPaymentType() :
        payment.getPaymentType();
    final String businessUnitName = attributeFinder.findBusinessUnitName(InvoiceConstants.AR.INVOICE_TYPE, schemeParticipant.getSiteType(), paymentTransactionType, attributeCache, scheme);
    final String batchSource = attributeFinder.findBatchSource(InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType);
    // final String transactionType = attributeFinder.findTransactionTypeName(InvoiceConstants.AR.INVOICE_TYPE, payment.getPaymentType());
    final String paymentTerms = attributeFinder.findPaymentTerms(InvoiceConstants.AR.INVOICE_TYPE, schemeParticipant.getSiteType(), paymentTransactionType);
    final String transactionDate = attributeFinder.findTransactionDate();

    // Commenting due to QCRS-1084 where all invoices should have this field set to true
    // final boolean baseDueDateOnTransactionDate = attributeFinder.isBaseDueDateOnTransactionDate(payment.getSchemeParticipantType());
    final boolean baseDueDateOnTransactionDate = true;

    String billToCustomerAccountNumber = StringUtils.EMPTY;
    String billToCustomerSiteNumber = StringUtils.EMPTY;
    String soldToCustomerAccountNumber = StringUtils.EMPTY;
    final String transactionLineDescription = constructDescription(payment, schemeParticipant, paymentTransactionType, additionalInfo, period);

    BigDecimal unitSellingPrice = additionalInfo.get(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE) != null ?
        new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE)) :
        payment.getUnitSellingPrice();
    unitSellingPrice = paymentTransactionType.contains("AUCTION") ? unitSellingPrice.abs() : unitSellingPrice;

    BigDecimal transactionLineQuantity =
        additionalInfo.get(InvoiceConstants.AdditionInfo.VOLUME) != null ? new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.VOLUME)) : payment.getVolume();
    transactionLineQuantity = assertQantity(payment, schemeParticipant, transactionLineQuantity);

    BigDecimal transactionLineAmount = additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT) == null ?
        transactionLineQuantity.multiply(unitSellingPrice) :
        new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT));

    final String invoiceSource = InvoiceConstants.INVOICE_SOURCE;
    final String lineNumber = invoiceLineNumber;
    final String invoiceLineType = attributeFinder.findTaxInvoiceLineType(InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType, "1");
    final String taxClassificationCode = additionalInfo.get(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF) != null ?
        additionalInfo.get(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF) :
        attributeFinder.findTaxClassificationCode(schemeParticipant, InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType);

    LOGGER.info("processing for collection fees");
    if (paymentTransactionType.equals(PaymentTxnType.COLLECTION_FEES.name())) {

      final QSchemeParticipantRelationshipHeader qSchemeParticipantRelationshipHeader = QSchemeParticipantRelationshipHeader.schemeParticipantRelationshipHeader;
      final Date now = new Date();
      final List<SchemeParticipantRelationshipHeader> relationships = getQueryFactory().select(qSchemeParticipantRelationshipHeader).from(qSchemeParticipantRelationshipHeader)
          .where(QSchemeParticipantRelationshipHeader.schemeParticipantRelationshipHeader.sourceSchemeParticipant.eq(schemeParticipant)
              .and(qSchemeParticipantRelationshipHeader.targetSchemeParticipant.siteTypeId.eq(SchemeRefCodes.ParticipantSiteType.fetchId(SchemeRefCodes.ParticipantSiteType.PROCESSOR)))
              .and(qSchemeParticipantRelationshipHeader.effectiveFrom.before(now))
              .and(qSchemeParticipantRelationshipHeader.effectiveTo.isNull().or(qSchemeParticipantRelationshipHeader.effectiveTo.after(now))
                  )).fetch();

      if (relationships.size() > 0) {
        LOGGER.info("Size of Scheme Participant relationship header {}", relationships.size());
      } else {
        LOGGER.info("There are no relationship data for source scheme participant {} and target processor", schemeParticipant.getSiteNumber());
      }

      boolean foundRelationshipMatch = false;
      for (final SchemeParticipantRelationshipHeader header : relationships) {
        final Optional<SchemeParticipantRelationshipDetail> detail = header.getMaterialTypes().stream().filter(new Predicate<SchemeParticipantRelationshipDetail>() {

          @Override
          public boolean test(SchemeParticipantRelationshipDetail t) {

            if (!DateUtility.isActiveNow(now, t)) {
              return false;
            }
            
            return t.getMaterialType().equals(payment.getMaterialType());
          }
        }).findFirst();

        if (detail.isPresent()) {

          LOGGER.info("detail about the relationship {}", detail.get());

          legalEntityIdentifier = detail.get().getHeader().getTargetSchemeParticipant().getErpLegalEntityId();
          LOGGER.info("Legal entity identifier in detail block {}", legalEntityIdentifier);
          entity = legalEntityIdentifier;
          transactionType = "CollectionFeeInv" + legalEntityIdentifier;
          LOGGER.info("Transaction type in detail block {}", transactionType);
          transactionTypeId = attributeFinder.findTransactionTypeId(InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType, transactionType);
          LOGGER.info("Transaction type id in detail block {}", transactionTypeId);
          foundRelationshipMatch = true;
          break;
        }
      }
      if (!foundRelationshipMatch) {
        throw new RuntimeException("Unable to find relationship " + schemeParticipant.getSiteNumber() + " with target type PROCESSOR and material type " + payment.getMaterialType().getId());
      }

      // find scheme participant with legal entity "1420"
      String schemeId = scheme.getId();
      billToCustomerAccountNumber = schemeRefCodeService.getValue(SchemeRefCodes.InvoiceRefData.CATEGORY, SchemeRefCodes.InvoiceRefData.COLLECTION_FEE_BILL_TO_ACC, schemeId, "");
      billToCustomerSiteNumber = schemeRefCodeService.getValue(SchemeRefCodes.InvoiceRefData.CATEGORY, SchemeRefCodes.InvoiceRefData.COLLECTION_FEE_BILL_TO_SITE, schemeId, "");
      soldToCustomerAccountNumber = schemeRefCodeService.getValue(SchemeRefCodes.InvoiceRefData.CATEGORY, SchemeRefCodes.InvoiceRefData.COLLECTION_FEE_SOLD_TO_ACC, schemeId, "");
      unitSellingPrice = payment.getMaterialType().getId().endsWith(INELIGIBLE_MATERIAL_TYPE) ? BigDecimal.ZERO : new BigDecimal(0.10);
      transactionLineAmount = payment.getVolume().multiply(unitSellingPrice);
      LOGGER.info("payment transction type {} is completed ", paymentTransactionType);
      LOGGER.info("processing completes for collection fees");
    } else {
      String name;
      if (paymentTransactionType.contains("AUCTION")) {
        if (schemeParticipant.getSiteType().equals(SchemeParticipantType.PROCESSOR.toString())) {
          name = "AuctionProcessor Inv";
        } else if (schemeParticipant.getSiteType().equals(SchemeParticipantType.MRF.toString())) {
          if (additionalInfo.get(InvoiceConstants.AdditionInfo.IS_PLATFORM_FEE) != null) {
            name = "AuctionPlatfomMRFInv";
          } else {
            name = "Auction Neg MRF Inv";
          }
        } else {
          if (paymentTransactionType.equals(PaymentTxnType.PROCESSOR_NEGATIVE_AUCTION.name()) || paymentTransactionType.equals(PaymentTxnType.PROCESSOR_POSITIVE_AUCTION.name())) {
            name = "AuctionPrcRecyclrInv";
          } else {
            name = "AuctionMRFRecyclrInv";
          }
        }
      } else {
        if (netAmount.signum() == -1) {
          name = BM_CREDIT;
        } else {
          name = BM_INVOICE;
        }
      }

      legalEntityIdentifier = attributeFinder.findLegalEntityIdentifier(InvoiceConstants.AR.INVOICE_TYPE, schemeParticipant.getSiteType(), paymentTransactionType, attributeCache, scheme);
      transactionType = name;
      transactionTypeId = attributeFinder.findTransactionTypeId(InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType, name);
      billToCustomerAccountNumber =  ""+schemeParticipant.getParticipant().getParticipantNumber(); // billToProfile.getCustAccountNumber();
      billToCustomerSiteNumber =  schemeParticipant.getSiteNumber(); // billToProfile.getCustSiteNumber();
      soldToCustomerAccountNumber = billToCustomerAccountNumber; // billToProfile.getSoldToCustAccNumber();
    }

    final String unitOfMeasureCode = payment.getUom().equals("KILOGRAM") ? "Kg" : "Ea";
    final String transactionLineType = "LINE";
    final String defaultTaxationCountry = InvoiceConstants.DEFAULT_TAXATION_COUNTRY;
    final boolean lineAmountIncludesTax = attributeFinder.isLineAmountIncludesTax(InvoiceConstants.AR.INVOICE_TYPE, paymentTransactionType);
    final String inventoryItemNumber = getMaterialTypeName(payment);
    final String paymentPeriodStartDate = period.getStart().format(DateTimeFormatter.ofPattern(InvoiceConstants.YYYY_MM_DD));
    final String paymentPeriodEndDate = period.getEnd().format(DateTimeFormatter.ofPattern(InvoiceConstants.YYYY_MM_DD));
    final InvDistributionCodeLov distributionLineMetadata = attributeFinder
        .findDistributionLine(InvoiceConstants.AR.INVOICE_TYPE, schemeParticipant.getSiteType(), payment, additionalInfo, attributeCache, scheme);
    if (distributionLineMetadata == null) {
      throw new RuntimeException("There is no matching distribution line metadata for " + InvoiceConstants.AR.INVOICE_TYPE + ", " + schemeParticipant.getSiteType() + ", " + payment.getPaymentType() + "," + payment.getSchemeParticipantType() + ", " + scheme.getId() + ", " + payment.getMaterialType().getId());
    }    
    final String accountClass = distributionLineMetadata.getAccountClass();
    final BigDecimal amountPercentage = new BigDecimal(100);
    entity = entity.equals(StringUtils.EMPTY) ? distributionLineMetadata.getEntity() : entity;
    final String costCentre = distributionLineMetadata.getCostCentre();
    final String naturalAcc = distributionLineMetadata.getNaturalAcc();
    final String interCoAcc = distributionLineMetadata.getInterCoAcc();
    final String spare = distributionLineMetadata.getSpare();
    final String accountingMaterialTypeId = distributionLineMetadata.getMaterialTypeId();

    /**
     * populate variables
     *
     */
    if (invoiceBatchId != null) {
      invoice.setInvoiceBatchId(invoiceBatchId);
    }

    invoice.setBusinessUnitName(businessUnitName);

    invoice.setBatchSource(batchSource);

    LOGGER.info("Transaction type in outside block {}", transactionType);
    invoice.setTransactionType(transactionType);

    LOGGER.info("Transaction type id in outside block {}", transactionTypeId);
    invoice.setTransactionTypeId(transactionTypeId);

    if (!transactionType.equals(BM_CREDIT)) {
      invoice.setPaymentTerms(paymentTerms);
    }

    invoice.setTransactionDate(transactionDate);

    invoice.setBaseDueDateOnTransactionDate(baseDueDateOnTransactionDate);

    invoice.setTransactionNumber(transactionNumber);

    invoice.setBillToCustomerAccountNumber(billToCustomerAccountNumber);

    invoice.setBillToCustomerSiteNumber(billToCustomerSiteNumber);

    invoice.setSoldToCustomerAccountNumber(soldToCustomerAccountNumber);

    invoice.setTransactionLineType(transactionLineType);

    invoice.setTransactionLineDescription(transactionLineDescription);

    invoice.setCurrencyCode(currencyCode);

    invoice.setCurrencyConversionType(currencyConversionType);

    invoice.setTransactionLineQuantity(transactionLineQuantity);

    invoice.setUnitSellingPrice(unitSellingPrice);

    invoice.setTransactionLineAmount(transactionLineAmount);

    invoice.setInvoiceSource(invoiceSource);

    invoice.setInvoiceLineNumber(lineNumber);

    invoice.setInvoiceLineType(invoiceLineType);

    invoice.setTaxClassificationCode(taxClassificationCode);

    invoice.setLegalEntityIdentifier(legalEntityIdentifier);

    invoice.setUnitOfMeasureCode(unitOfMeasureCode);

    invoice.setDefaultTaxationCountry(defaultTaxationCountry);

    invoice.setLineAmountIncludesTax(lineAmountIncludesTax);

    invoice.setInventoryItemNumber(inventoryItemNumber);
    if (!paymentTransactionType.contains("AUCTION")) {
      invoice.setPaymentPeriodStartDate(paymentPeriodStartDate);
      invoice.setPaymentPeriodEndDate(paymentPeriodEndDate);
    } else {
      invoice.setAuctionDate(paymentPeriodStartDate);
    }

    invoice.setAccountClass(accountClass);

    invoice.setAmountPercentage(amountPercentage);

    invoice.setEntity(entity);

    invoice.setMaterialType(accountingMaterialTypeId);

    invoice.setCostCentre(costCentre);

    invoice.setNaturalAcc(naturalAcc);

    invoice.setInterCoAcc(interCoAcc);

    invoice.setSpare(spare);

    invoice.setPaymentTransactionRec(payment);

    invoice.setId(UUID.randomUUID().toString());
    invoice.setScheme(scheme);

    return invoice;
  }

  /**
   * Updates the payment invoice status (PaymentInvoiceStatus) on one or more invoices. 
   *
   * @param requests A list of invoices to update along with a new status for each one
   */
  public void updateStatus(List<InvoiceStatusAssociation> requests) {

    for (final InvoiceStatusAssociation invoiceStatusAssociation : requests) {
      final List<InvoiceARTransactionRec> invoices = invoiceRepo.findByTransactionNumber(invoiceStatusAssociation.getInvoiceNumber());
      for (final InvoiceARTransactionRec invoice : invoices) {
        if (invoice != null) {
          final InvoiceARTransactionRec row = invoice;
          final PaymentInvoiceStatus paymentInvoiceStatus = row.getPaymentInvoiceStatus();
          paymentInvoiceStatus.setStatus(invoiceStatusAssociation.getStatus());
          paymentInvoiceStatusRepo.save(paymentInvoiceStatus);
        }
      }
    }

  }

  private String getMaterialTypeName(PaymentTransactionRec payment) {

    if (payment.getMrfMaterialType() != null) {
      String paasName = payment.getMrfMaterialType().getName();
      if (InvoiceConstants.MATERIAL_TYPE_NAME_ERP_MAPPING.get(paasName) != null) {
        return InvoiceConstants.MATERIAL_TYPE_NAME_ERP_MAPPING.get(paasName);
      }
      return paasName;
    }
    return payment.getMaterialType().getName();
  }

  private String constructDescription(PaymentTransactionRec payment, MdtParticipantSite schemeParticipant, String paymentType, Map<String, String> additionalInfo, Period period) {

    String descriptionPrefix = null;
    if (paymentType.contains("AUCTION")) {
      String schemeParticipantType = schemeParticipant.getSiteType();
      if (schemeParticipantType.equals(SchemeParticipantType.PROCESSOR.name()) || schemeParticipantType.equals(SchemeParticipantType.MRF.name())) {
        if (additionalInfo.get(InvoiceConstants.AdditionInfo.IS_ADJUSTMENT) != null) {
          descriptionPrefix = "RESALE ADJUSTMENT";
        } else if (additionalInfo.get(InvoiceConstants.AdditionInfo.IS_PLATFORM_FEE) != null) {
          descriptionPrefix = "Auction Platform Commission Fees";
        } else {
          descriptionPrefix = "RESALE PAYMENT";
        }
      } else if (schemeParticipantType.equals(SchemeParticipantType.RECYCLER.name())) {
        descriptionPrefix = "PROCESSED MATERIALS";
      }
      return String.format("%s, Auction Lot ID: %s, Manifest ID: %s", descriptionPrefix, additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_ID),
          additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_FINAL_MANIFEST_ID));
    }

    if (schemeParticipant.getSiteType().equals("MANUFACTURER") && payment.getVolumeHdrEntryType().equals(EntryType.F.name())) {

      descriptionPrefix = "SCHEME_CONTRIBUTION_ESTIMATE";
    } else if (schemeParticipant.getSiteType().equals("MANUFACTURER") && payment.getVolumeHdrEntryType().equals(EntryType.FO.name())) {

      descriptionPrefix = "ADJUSTED_SCHEME_CONTRIBUTION";
    } else {

      descriptionPrefix = StringUtils.equals(payment.getEntryType(), EntryType.A.name()) ? AUDITED_REC_DESCRIPTION : paymentType;
    }
    return attributeFinder.buildTransactionLineDescription(descriptionPrefix, period);
  }

  public BigDecimal assertQantity(PaymentTransactionRec payment, MdtParticipantSite schemeParticipant, BigDecimal quantity) {

    if (schemeParticipant.getSiteType().equals("MANUFACTURER")) {
      if (payment.getVolumeHdrEntryType().equals(EntryType.F.name()) || payment.getVolumeHdrEntryType().equals(EntryType.FO.name())) {
        return quantity.setScale(0, RoundingMode.HALF_UP);

      }
    }
    return quantity;
  }

  //@formatter:off
  public void processAuction(final List<InvoiceARTransactionRec> from
                    , final BigDecimal invoiceGroupNumber
                    , final MdtParticipantSite schemeParticipant
                    , final String paymentTransactionType
                    , final String paymentMethod
                    , final List<PaymentTransactionRec> payments
                    , final Period period
                    , final Map<String,String> additionalInfo
                    , String invoiceBatchId
                    , InvoiceAttributeCache attributeCache
                    , Scheme scheme) {
    //final APInvoiceTransactionRecHeader header = buildHeader(invoiceGroupNumber, schemeParticipant, paymentTransactionType, payments, paymentMethod, null);
    
    final InvoiceARTransactionRec invoice=buildInvoice(schemeParticipant, period, String.valueOf(invoiceNumberGenerator.createOrFindTransactionNumber()), "1", payments.get(0),additionalInfo,invoiceBatchId,null,attributeCache,scheme);
    final InvoiceARTransactionRec persistedInvoice = postStep(SchemeParticipantType.valueOf(schemeParticipant.getSiteType()), payments.get(0), invoice);
    from.add(persistedInvoice);
  }
  //@formatter:on
}
