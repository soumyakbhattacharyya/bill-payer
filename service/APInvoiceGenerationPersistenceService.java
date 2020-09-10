package com.serviceco.coex.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Splitter;
import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.InvoiceLineDescription;
import com.serviceco.coex.model.constant.PeriodType;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.api.request.InvoiceStatusAssociation;
import com.serviceco.coex.payment.calculation.PaymentTxnType;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.InvoiceStatus;
import com.serviceco.coex.payment.model.invoice.PaymentInvoiceStatus;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecDetail;
import com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader;
import com.serviceco.coex.payment.model.invoice.reference.InvDistributionCodeLov;
import com.serviceco.coex.payment.repository.APInvoiceTransactionRecDetailRepository;
import com.serviceco.coex.payment.repository.APInvoiceTransactionRecRepository;
import com.serviceco.coex.payment.repository.PaymentInvoiceStatusRepository;
import com.serviceco.coex.payment.service.APInvoiceGenerationService.LegalEntityTuple;
import com.serviceco.coex.payment.service.TransactionHeaderRepositoryFacade.PaymentType;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipant;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.NoArgsConstructor;

/**
 * Generates and persists individual AP (Accounts Payable) invoices.
 *
 * <p>See {@link #process} and {@link #processAuction}</p>
 *
 */
@Service
@Transactional
@NoArgsConstructor
public class APInvoiceGenerationPersistenceService extends GenericService {

  private static final Logger LOG = LoggerFactory.getLogger(APInvoiceGenerationPersistenceService.class);

  private static final String YYYY_MM_DD = "yyyy/MM/dd";
  private static final String AUDITED_REC_DESCRIPTION = "AUDIT_ADJUSTMENT";

  @Autowired
  private TransactionHeaderRepositoryFacade headerRepoFacade;

  @Autowired
  private DateTimeSupport dateTimeSupport;

  @Autowired
  private APInvoiceTransactionRecRepository apInvoiceTransactionRecrepo;

  @Autowired
  private APInvoiceTransactionRecDetailRepository detailRepo;

  @Autowired
  private PaymentInvoiceStatusRepository paymentInvoiceStatusRepo;

  @Autowired
  private InvoiceAtrributeFinder attributeFinder;

  @Autowired
  private InvoiceNumberGenerator invoiceNumberGenerator;

  /**
   * Constructs an invoice header ({@code APInvoiceTransactionRecHeader}) and associated invoice detail lines ({@code APInvoiceTransactionRecDetail}) 
   * based on a list of payment transaction records, a particular scheme participant, transaction type, etc.
   * 
   * <p>This method is not used for auction payments. For auction payments, see {@link #processAuction}.</p>
   * 
   * <p>The header is constructed by {@link #buildHeader}.</p>
   * 
   * <p>An invoice detail line is constructed by {@link #buildDetail} for each payment transaction record passed in.</p>
   * 
   * <p>There is also a {@link PaymentInvoiceStatus} record created and persisted for each invoice detail line. The status given to the {@code PaymentInvoiceStatus}
   * record is {@code GENERATED}.</p>
   * 
   * <p>The status of the volume/claim records associated with the payment transaction records are updated through {@link com.serviceco.coex.payment.service.TransactionHeaderRepositoryFacade#updateHeader}</p>
   * 
   * <p>The invoice header and associated detail records are persisted and then appended to the {@code from} list.</p>
   *  
   * @param from	A valid list this method can append the newly created invoice transaction headers to.
   * @param invoiceBatchId A number which identifies the current invoice processing batch. This will be set in the invoice header record.
   * @param invoiceGroupNumber	A number which identifies which group the invoice belongs to.
   * @param schemeParticipant The scheme participant the invoice is being generated for.
   * @param paymentTransactionType The payment transaction type from the {@code PaymentTransactionRec} records.
   * @param paymentMethod If the transactions have been grouped by the payment method, this is the payment method associated with the transactions. Otherwise set to an empty string.
   * @param paymentsLevel3 The payment transactions to invoice
   * @param entity The legal entity the invoice targets
   * @param scheme The scheme associated with the invoice \ payments
   * @param cachedPeriod Used to temporarily cache payment periods. This can be an empty Map initially.
   */
  public void process(final List<APInvoiceTransactionRecHeader> from, final String invoiceBatchId, final BigDecimal invoiceGroupNumber, final MdtParticipantSite schemeParticipant,
      final String paymentTransactionType, final String paymentMethod, final List<PaymentTransactionRec> paymentsLevel3, LegalEntityTuple entity,
      Map<String, Period> cachedPeriod, InvoiceAttributeCache attributeCache, Scheme scheme) {
    final APInvoiceTransactionRecHeader header = buildHeader(invoiceGroupNumber, schemeParticipant, paymentTransactionType, paymentsLevel3, paymentMethod, entity, null, attributeCache, scheme);
    header.setInvoiceBatchId(invoiceBatchId);

    BigDecimal detailLineNum = BigDecimal.ZERO;
    for (final PaymentTransactionRec payment : paymentsLevel3) {
      boolean isTax = false;
      final BigDecimal invoiceLineNumber = detailLineNum.add(new BigDecimal(1));
      APInvoiceTransactionRecDetail invoiceItemLine = null;
      if (payment.getPaymentType().equals(PaymentTxnType.REFUND_AMOUNT.name())) {
        invoiceItemLine = buildDetail(schemeParticipant, invoiceGroupNumber, invoiceLineNumber, header, paymentTransactionType, payment, entity, isTax, 
            null, cachedPeriod, attributeCache, scheme);
      } else {
        invoiceItemLine = buildDetail(schemeParticipant, invoiceGroupNumber, invoiceLineNumber, header, paymentTransactionType, payment, null, isTax, 
            null, cachedPeriod, attributeCache, scheme);
      }

      // create status record
      final PaymentInvoiceStatus paymentInvoiceStatus = new PaymentInvoiceStatus();
      paymentInvoiceStatus.setId(UUID.randomUUID().toString());
      paymentInvoiceStatus.setPayment(payment);
      paymentInvoiceStatus.setStatus(InvoiceStatus.GENERATED);
      paymentInvoiceStatus.setInvoiceId(invoiceItemLine.getId());
      paymentInvoiceStatus.setScheme(scheme);
      final PaymentInvoiceStatus persistedPaymentInvoiceStatus = paymentInvoiceStatusRepo.save(paymentInvoiceStatus);

      invoiceItemLine.setStatus(persistedPaymentInvoiceStatus);

      header.getLines().add(invoiceItemLine);
      detailLineNum = invoiceLineNumber;

      headerRepoFacade.updateHeader(PaymentType.valueOf(payment.getPaymentType()), null, payment.getVolumeHeaderId());
    }
    apInvoiceTransactionRecrepo.save(header);
    from.add(header);

  }

  /**
   * Constructs an invoice header ({@code APInvoiceTransactionRecHeader}) and associated invoice detail lines ({@code APInvoiceTransactionRecDetail})
   * based on a list of payment transaction records, a particular scheme participant, transaction type, etc..
   * 
   * <p>This is similar to {@link #process}, but has some minor
   * differences for auctions.
   * 
   * <p>If the scheme participant's type is "RECYCLER", the scheme participant type associated with the payment records is prepended to the payment transaction type.</p>
   * 
   * <p>The header is constructed by {@link #buildHeader}.</p>
   * 
   * <p>An invoice detail line is constructed by {@link #buildDetail} for each payment transaction record passed in.</p>
   * 
   * <p>There is also a {@link PaymentInvoiceStatus} record created and persisted for each invoice detail line. The status given to the {@code PaymentInvoiceStatus}
   * record is {@code GENERATED}.</p>
   * 
   * <p>The invoice header and associated detail records are persisted and then appended to the {@code from} list.</p>
   * 
   * 
   * @param from	A valid list this method can append the newly created invoice transaction headers to.
   * @param invoiceBatchId A number which identifies the current invoice processing batch. This will be set in the invoice header record.
   * @param invoiceGroupNumber	A number which identifies which group the invoice belongs to.
   * @param schemeParticipant The scheme participant the invoice is being generated for.
   * @param paymentTransactionType The payment transaction type from the {@code PaymentTransactionRec} records.
   * @param paymentMethod If the transactions have been grouped by the payment method, this is the payment method associated with the transactions. Otherwise set to an empty string.
   * @param payments The payment transactions to invoice
   * @param additionalInfo Key/value pairs which override particular details. This can include the following keys: {@code InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE}, {@code InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF}, and {@code InvoiceConstants.AdditionInfo.INVOICE_AMOUNT}. If these keys are present, their values are used. If the keys are not present, the values are looked up from the other data provided.
   * @param cachedPeriod Used to temporarily cache payment periods. This can be an empty Map initially.
   * @param scheme Scheme associated with the payments
   */
  //@formatter:off
  public void processAuction(final List<APInvoiceTransactionRecHeader> from
                    , final String invoiceBatchId
                    , final BigDecimal invoiceGroupNumber
                    , final MdtParticipantSite schemeParticipant
                    , String paymentTransactionType
                    , final String paymentMethod
                    , final List<PaymentTransactionRec> payments
                    , Map<String,String> additionalInfo
                    , Map<String, Period> cachedPeriod
                    , InvoiceAttributeCache attributeCache
                    , Scheme scheme) {
    
    
    String participantTypeCode = schemeParticipant.getSiteType();
    if(participantTypeCode.equals("RECYCLER")) {
      paymentTransactionType=payments.get(0).getSchemeParticipantType()+"_"+paymentTransactionType;
    }
    final APInvoiceTransactionRecHeader header = buildHeader(invoiceGroupNumber, schemeParticipant, paymentTransactionType, payments, 
        paymentMethod, null, additionalInfo, attributeCache, scheme);
    header.setInvoiceBatchId(invoiceBatchId);
    
    BigDecimal detailLineNum = BigDecimal.ZERO;
    for (final PaymentTransactionRec payment : payments) {
      boolean isTax = false;
      final BigDecimal invoiceLineNumber = detailLineNum.add(new BigDecimal(1));
      APInvoiceTransactionRecDetail invoiceItemLine = null;
      invoiceItemLine = buildDetail(schemeParticipant, invoiceGroupNumber, invoiceLineNumber, header, paymentTransactionType, payment, 
          null, isTax, additionalInfo, cachedPeriod, attributeCache, scheme);
      header.getLines().add(invoiceItemLine);

      detailLineNum = invoiceLineNumber;
    }
    final APInvoiceTransactionRecHeader persistedHeader = apInvoiceTransactionRecrepo.save(header);
    from.add(header);

    // update status
    final List<APInvoiceTransactionRecDetail> persistedLines = persistedHeader.getLines();
    for (final APInvoiceTransactionRecDetail line : persistedLines) {
      // create status record
      final PaymentTransactionRec payment = line.getPaymentTransactionRec();
      final PaymentInvoiceStatus paymentInvoiceStatus = new PaymentInvoiceStatus();
      paymentInvoiceStatus.setId(UUID.randomUUID().toString());
      paymentInvoiceStatus.setPayment(payment);
      paymentInvoiceStatus.setStatus(InvoiceStatus.GENERATED);
      paymentInvoiceStatus.setInvoiceId(line.getId());
      paymentInvoiceStatus.setScheme(scheme);
      final PaymentInvoiceStatus persistedPaymentInvoiceStatus = paymentInvoiceStatusRepo.save(paymentInvoiceStatus);
      line.setStatus(persistedPaymentInvoiceStatus);
      detailRepo.save(line);           
    }
  
  }
  //@formatter:on

  /**
   * 
   * builds header section of the invoice
   * 
   * @param invoiceGroupNum
   * @param schemeParticipantSite
   * @param paymentType
   * @param payments
   * @param paymentMethod
   * @param entity
   * @return
   */
  private APInvoiceTransactionRecHeader buildHeader(BigDecimal invoiceGroupNum, MdtParticipantSite schemeParticipantSite, String paymentType, List<PaymentTransactionRec> payments,
      String paymentMethod, LegalEntityTuple entity, Map<String, String> additionalInfo, InvoiceAttributeCache attributesCache, Scheme scheme) {

    if (additionalInfo == null) {
      additionalInfo = new HashMap<>();
    }

    // building of invoice starts
    final APInvoiceTransactionRecHeader apTransactionHdr = new APInvoiceTransactionRecHeader();
    apTransactionHdr.setId(UUID.randomUUID().toString());
    apTransactionHdr.setLines(new ArrayList<>());
    final String description = Splitter.on('_').trimResults().omitEmptyStrings().splitToList(paymentType).stream().map(new Function<String, String>() {
      @Override
      public String apply(String t) {
        return StringUtils.capitalize(t.toLowerCase());
      }
    }).collect(Collectors.joining(" "));

    MdtParticipant participant = schemeParticipantSite.getParticipant();
    String participantTypeCode = schemeParticipantSite.getSiteType();
    final String businessUnit = attributeFinder.findBusinessUnitName(InvoiceConstants.AP.INVOICE_TYPE, participantTypeCode, paymentType, attributesCache, scheme);
    final String transactionNumber = "P" + String.valueOf(invoiceNumberGenerator.createOrFindTransactionNumber());
    final String invoiceDate = DateTimeSupport.now().format(DateTimeFormatter.ofPattern(YYYY_MM_DD));
    final String currency = InvoiceConstants.CURRENCY_CODE;
    final BigDecimal invoiceAmount = additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT) != null
        ? new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT))
        : sum(schemeParticipantSite, paymentType, payments);
    final String invoiceType = invoiceAmount.compareTo(BigDecimal.ZERO) >= 0 ? "STANDARD" : "CREDIT";
    final String legalEntity = attributeFinder.findLegalEntity(entity, paymentType, participantTypeCode, attributesCache, scheme);
    final String paymentTerms = attributeFinder.findPaymentTerms(InvoiceConstants.AP.INVOICE_TYPE, participantTypeCode, paymentType);
    final boolean calculateTaxDuringImport = attributeFinder.shouldCalculateTaxDuringImport(paymentType, participantTypeCode);
    final boolean addTaxToInvoiceAmount = attributeFinder.shouldAddTaxToInvoiceAmount(paymentType, participantTypeCode);
    final String paymentGroup = attributeFinder.findPaymentGroup(InvoiceConstants.AP.INVOICE_TYPE, participantTypeCode, paymentType, paymentMethod);
    final String supplierNumber = (paymentType.equals(PaymentTxnType.COLLECTION_FEES.name())) ? entity.getSupplierNumber() : ""+participant.getParticipantNumber();
    final String supplierSite = (paymentType.equals(PaymentTxnType.COLLECTION_FEES.name())) ? entity.getSchemeIdForLegalEntity() : schemeParticipantSite.getSiteNumber();

    apTransactionHdr.setBusinessUnit(businessUnit);

    LOG.info("Generating invoice number : {}", transactionNumber);
    apTransactionHdr.setInvoiceNumber(transactionNumber);
    apTransactionHdr.setInvoiceSource(isAuctionPayment(paymentType) ? "AUCTION" : participantTypeCode);
    apTransactionHdr.setInvoiceDate(invoiceDate);
    apTransactionHdr.setSupplierNumber(supplierNumber);
    apTransactionHdr.setSupplierSite(supplierSite);
    apTransactionHdr.setInvoiceCurrency(currency);
    apTransactionHdr.setPaymentCurrency(currency);
    apTransactionHdr.setPayGroup(paymentGroup);
    apTransactionHdr.setDescription(description);
    apTransactionHdr.setInvoiceAmount(invoiceAmount);
    apTransactionHdr.setInvoiceType(invoiceType);
    apTransactionHdr.setLegalEntity(legalEntity);
    apTransactionHdr.setPaymentTerms(paymentTerms);
    apTransactionHdr.setCalculateTaxDuringImport(calculateTaxDuringImport);
    apTransactionHdr.setAddTaxToInvoiceAmount(addTaxToInvoiceAmount);
    apTransactionHdr.setInvoiceGroup(invoiceGroupNum);
    apTransactionHdr.setScheme(scheme);

    return apTransactionHdr;
  }

  private BigDecimal sum(MdtParticipantSite schemeParticipant, String paymentType, List<PaymentTransactionRec> payments) {
    String participantTypeCode = schemeParticipant.getSiteType();
    if (participantTypeCode.equals(SchemeParticipantType.MRF.getSupplierType())) {
      if (paymentType.equals(PaymentTxnType.RECOVERY_AMOUNT_CLAIM.name())) {
        return sum(payments);
      }
    }
    if (participantTypeCode.equals(SchemeParticipantType.EXPORTER.getSupplierType())) {
      if (paymentType.equals(PaymentTxnType.EXPORT_REBATE.name())) {
        return sum(payments);
      }
    }
    if (participantTypeCode.equals(SchemeParticipantType.PROCESSOR.getSupplierType())) {
      if (paymentType.equals(PaymentTxnType.PROCESSING_FEES.name())) {
        return sum(payments);
      }
    }
    if (participantTypeCode.equals(SchemeParticipantType.CRP.getSupplierType())) {
      if (paymentType.equals(PaymentTxnType.GST_RECOVERY_AMOUNT.name()) || paymentType.equals(PaymentTxnType.COLLECTION_FEES.name()) || paymentType.equals(PaymentTxnType.REFUND_AMOUNT.name()) || paymentType.equals(PaymentTxnType.HANDLING_FEES.name())) {
        return sum(payments);
      }
    }
    if (participantTypeCode.equals(PaymentTxnType.CONSUMER.name())) {
      if (paymentType.equals(PaymentTxnType.REFUND_AMOUNT.name())) {
        return sum(payments);
      }
    }
    return BigDecimal.ZERO;
  }

  private BigDecimal sum(List<PaymentTransactionRec> paymentTransactionRecords) {
    BigDecimal totalPaymentAmount = BigDecimal.ZERO;
    for (PaymentTransactionRec rec : paymentTransactionRecords) {
      totalPaymentAmount = totalPaymentAmount.add(rec.getGrossAmount().setScale(2, RoundingMode.HALF_UP));
    }
    return totalPaymentAmount;
  }

  /**
   * Constructs an AP invoice transaction detail record (APInvoiceTransactionRecDetail) and fills it in with the details. It is not persisted by this
   * method.
   * 
   * @param schemeParticipant The scheme participant the invoice is being generated for. This is used to find tax classification code and the distribution meta data.
   * @param invoiceGroupNum  The invoice line group number
   * @param invoiceLineNum The invoice line number.
   * @param header The invoice header record to link to the detail to.
   * @param paymentType The payment transaction type from the {@code PaymentTransactionRec} records.
   * @param payment The payment transaction record related to the invoice detail line being created.
   * @param legalEntity The legal entity the invoice targets. If this is null, it will be looked up based on distribution meta data. See {@link com.serviceco.coex.payment.service.InvoiceAtrributeFinder#findDistributionLine}.
   * @param taxLine If true, the invoice line type will be looked up using index "2", otherwise index "1". See {@link com.serviceco.coex.payment.service.InvoiceAtrributeFinder#findTaxInvoiceLineType}.
   * @param additionalInfo Key/value pairs which override particular details. This can include the following keys: {@code InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE}, {@code InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF}, and {@code InvoiceConstants.AdditionInfo.INVOICE_AMOUNT}. If these keys are present, their values are used. If the keys are not present, the values are looked up from the other data provided. 
   * @param cachedPeriod Used to temporarily cache payment periods. This can be an empty Map initially.
   * @return Returns an {@code APInvoiceTransactionRecDetail} object which has been constructed and filled in with the details.
   */
  private APInvoiceTransactionRecDetail buildDetail(MdtParticipantSite schemeParticipant, BigDecimal invoiceGroupNum, BigDecimal invoiceLineNum,
      APInvoiceTransactionRecHeader header, String paymentType, PaymentTransactionRec payment, LegalEntityTuple legalEntity, boolean taxLine, Map<String, String> additionalInfo,
      Map<String, Period> cachedPeriod, InvoiceAttributeCache attributeCache, Scheme scheme) {

    if (additionalInfo == null) {
      additionalInfo = new HashMap<>();
    }
    final APInvoiceTransactionRecDetail invoiceLine = new APInvoiceTransactionRecDetail();
    invoiceLine.setId(UUID.randomUUID().toString());
    invoiceLine.setInvoiceApTxnHdr(header);

    if (cachedPeriod != null && !cachedPeriod.containsKey(payment.getPeriod())) {
      Period tempPeriod = dateTimeSupport.periodFactory(payment.getPeriod(), PeriodType.valueOf(payment.getPeriodType()));
      cachedPeriod.put(payment.getPeriod(), tempPeriod);
    }
    final Period detailPeriod = cachedPeriod.get(payment.getPeriod());
    final String periodStartDate = detailPeriod.getStart().format(DateTimeFormatter.ofPattern(InvoiceConstants.YYYY_MM_DD));
    final String periodEndDate = detailPeriod.getEnd().format(DateTimeFormatter.ofPattern(InvoiceConstants.YYYY_MM_DD));
    final BigDecimal volume = payment.getVolume();
    final BigDecimal unitSellingPrice = additionalInfo.get(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE) != null
        ? new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.UNIT_SELLING_PRICE))
        : isAuctionPayment(paymentType) ? payment.getUnitSellingPrice().abs() : payment.getUnitSellingPrice();
    String participantTypeCode = schemeParticipant.getSiteType();
    final String description = constructDescription(payment, paymentType, additionalInfo, participantTypeCode);

    final String materialName = getMaterialTypeName(paymentType, payment);
    final boolean finalMatch = false;
    final String taxClassificationCode = additionalInfo.get(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF) != null
        ? additionalInfo.get(InvoiceConstants.AdditionInfo.TAX_CLASSIFICATION_REF)
        : attributeFinder.findTaxClassificationCode(schemeParticipant, InvoiceConstants.AP.INVOICE_TYPE, paymentType);
    final String unitOfMessure = payment.getUom().equals("KILOGRAM") ? "Kg" : "Ea";

    final InvDistributionCodeLov distributionLineMetadata = attributeFinder.findDistributionLine(InvoiceConstants.AP.INVOICE_TYPE, participantTypeCode, payment,additionalInfo, attributeCache, scheme);
    if (distributionLineMetadata == null) {
      throw new RuntimeException("There is no matching distribution line metadata for " + InvoiceConstants.AP.INVOICE_TYPE + ", " + participantTypeCode + ", " + payment.getPaymentType() + "," + payment.getSchemeParticipantType() + ", " + scheme.getId() + ", " + payment.getMaterialType().getId());
    }
    // as per discussion on 12th Sept : final String entity = StringUtils.isNoneBlank(suppliedEntity) ? suppliedEntity : distributionLineMetadata.getEntity();
    final String entity = legalEntity != null ? legalEntity.getLegalEntityId() : distributionLineMetadata.getEntity();
    final String costCentre = distributionLineMetadata.getCostCentre();
    final String naturalAcc = distributionLineMetadata.getNaturalAcc();
    final String interCoAcc = distributionLineMetadata.getInterCoAcc();
    final String spare = distributionLineMetadata.getSpare();
    final String accountingMaterialTypeId = distributionLineMetadata.getMaterialTypeId();
    String invoiceLineType = "";
    if (taxLine) {
      invoiceLineType = attributeFinder.findTaxInvoiceLineType(InvoiceConstants.AP.INVOICE_TYPE, paymentType, "2");
    } else {
      invoiceLineType = attributeFinder.findTaxInvoiceLineType(InvoiceConstants.AP.INVOICE_TYPE, paymentType, "1");
    }

    final StringBuffer sb = new StringBuffer();
    //@formatter:off
    final String distributionCombination = sb.append(entity)
                                             .append("-")
                                             .append(accountingMaterialTypeId)
                                             .append("-")
                                             .append(costCentre)
                                             .append("-")
                                             .append(naturalAcc)
                                             .append("-")
                                             .append(interCoAcc)
                                             .append("-")
                                             .append(spare)
                                             .toString();
    //@formatter:on
    invoiceLine.setLineNumber(invoiceLineNum);
    final BigDecimal amount = additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT) == null ? getInvoiceLineAmount(participantTypeCode, payment, taxLine)
        : new BigDecimal(additionalInfo.get(InvoiceConstants.AdditionInfo.INVOICE_AMOUNT));
    invoiceLine.setLineType(invoiceLineType);
    invoiceLine.setAmount(amount);
    invoiceLine.setQuantity(volume);
    invoiceLine.setUnitPrice(unitSellingPrice);
    invoiceLine.setDescription(description);
    invoiceLine.setItemDescription(materialName);
    invoiceLine.setFinalMatch(finalMatch);
    invoiceLine.setDistributionCombination(distributionCombination);
    invoiceLine.setTaxClassificationCode(taxClassificationCode);
    invoiceLine.setProrateAcrossAllLineItems(finalMatch);
    invoiceLine.setLineGroupNumber(invoiceGroupNum);
    invoiceLine.setTrackAsAsset(finalMatch);
    invoiceLine.setUnitOfMeasure(unitOfMessure);
    invoiceLine.setPriceCorrectionLine(finalMatch);
    invoiceLine.setScheme(scheme);

    if (!isAuctionPayment(paymentType)) {
      invoiceLine.setPaymentPeriodStartDate(periodStartDate);
      invoiceLine.setPaymentPeriodEndDate(periodEndDate);
    } else {
      invoiceLine.setAuctionDate(periodStartDate);
    }
    invoiceLine.setPaymentTransactionRec(payment);

    return invoiceLine;
  }

	private String constructDescription(PaymentTransactionRec payment, String paymentType,
			Map<String, String> additionalInfo, String schemeParticipantType) {

		if (schemeParticipantType.equals(SchemeParticipantType.CRP.getSupplierType())) {
			if (paymentType.contains(PaymentTxnType.REFUND_AMOUNT.name())) {
				if (StringUtils.equals(payment.getEntryType(), EntryType.A.name())) {
					return InvoiceLineDescription.REFUND_AMOUNT_ADJUST.getDescription();
				}
				return InvoiceLineDescription.REFUND_AMOUNT.getDescription();
			}
			if (paymentType.contains(PaymentTxnType.HANDLING_FEE.name())) {
				if (StringUtils.equals(payment.getEntryType(), EntryType.A.name())) {
					return InvoiceLineDescription.HANDLING_FEE_ADJUST.getDescription();
				}
				return InvoiceLineDescription.HANDLING_FEE.getDescription();
			}
			if (paymentType.contains(PaymentTxnType.COLLECTION_FEES.name())) {
				return InvoiceLineDescription.COLLECTION_FEES.getDescription();
			}
			if (paymentType.contains(PaymentTxnType.GST_RECOVERY_AMOUNT.name())) {
				return InvoiceLineDescription.GST_RECOVERY_AMOUNT.getDescription();
			}
		} else {

			if (StringUtils.equals(payment.getEntryType(), EntryType.A.name())) {
				return AUDITED_REC_DESCRIPTION;
			}
			if (paymentType.contains("AUCTION")) {
				if (StringUtils.equals(schemeParticipantType, "RECYCLER")) {
					return String.format("PROCESSED MATERIALS, Auction Lot ID: %s, Manifest ID: %s",
							additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_ID),
							additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_FINAL_MANIFEST_ID));
				}
				return String.format("RESALE PAYMENT, Auction Lot ID: %s, Manifest ID: %s",
						additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_ID),
						additionalInfo.get(InvoiceConstants.AdditionInfo.LOT_ITEM_FINAL_MANIFEST_ID));

			}
		}

		return paymentType;
	}

  private boolean isAuctionPayment(String paymentType) {
    return paymentType.contains("AUCTION");
  }

  private String getMaterialTypeName(String paymentType, PaymentTransactionRec payment) {
    if (paymentType.endsWith("AUCTION") && payment.getMrfMaterialType() != null) {
      String paasName = payment.getMrfMaterialType().getName();
      if (InvoiceConstants.MATERIAL_TYPE_NAME_ERP_MAPPING.get(paasName) != null) {
        return InvoiceConstants.MATERIAL_TYPE_NAME_ERP_MAPPING.get(paasName);
      }
      return paasName;
    }
    return payment.getMaterialType().getName();
  }

  private BigDecimal getInvoiceLineAmount(String schemeParticipantType, PaymentTransactionRec payment, boolean taxLine) {
    if (payment.getPaymentType().equals(PaymentTxnType.GST_RECOVERY_AMOUNT.name())) {
      return payment.getGrossAmount();
    }
    if (payment.getPaymentType().equals(PaymentTxnType.COLLECTION_FEES.name())) {
      return payment.getGrossAmount();
    }
    if (payment.getPaymentType().equals(PaymentTxnType.REFUND_AMOUNT.name())) {
      if (taxLine)
        return payment.getGstAmount();
      return payment.getGrossAmount();
    }
    final BigDecimal amount = (StringUtils.equals(payment.getLineType(), "ITEM") == true) ? payment.getGrossAmount() : payment.getTaxableAmount();
    return amount;
  }

  /**
   * Updates the {@code PaymentInvoiceStatus} records linked to AP invoice transaction detail lines ({@code APInvoiceTransactionRecDetail}) according to the details
   * in the request.
   * 
   * <p>For each invoice provided in the request, the invoice header record (@code APInvoiceTransactionRecHeader} is looked up by the invoice number. 
   * All of the invoice lines within the invoice then have their associated PaymentInvoiceStatus updated to the requested status.</p>
   * 
   * <p>There is no validation done on the current status of the status. It is assumed the records are allowed to change to the new state.</p>
   * 
   * @param request A list of InvoiceStatusAssociation objects containing an invoice number and a new status.
   */
  public void updateStatus(List<InvoiceStatusAssociation> request) {

    // final List<InvoiceStatusAssociation> requests = request.getElements();
    for (final InvoiceStatusAssociation invoiceStatusAssociation : request) {
      final APInvoiceTransactionRecHeader invoice = apInvoiceTransactionRecrepo.findByInvoiceNumber(invoiceStatusAssociation.getInvoiceNumber());
      if (invoice != null) {
        final List<APInvoiceTransactionRecDetail> lines = invoice.getLines();
        for (final APInvoiceTransactionRecDetail line : lines) {
          final PaymentInvoiceStatus lineStatus = line.getStatus();
          lineStatus.setStatus(invoiceStatusAssociation.getStatus());
          paymentInvoiceStatusRepo.save(lineStatus);
        }
      }
    }

  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    final Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

}
