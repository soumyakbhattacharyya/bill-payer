package com.serviceco.coex.payment.service;

import static com.serviceco.coex.payment.model.invoice.reference.QInvBuNameLov.invBuNameLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvDistributionCodeLov.invDistributionCodeLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvLegalIdentifierLov.invLegalIdentifierLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvPaymentTermsLov.invPaymentTermsLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvTaxClassificationRef.invTaxClassificationRef;
import static com.serviceco.coex.payment.model.invoice.reference.QInvTxnBatchSrcNameLov.invTxnBatchSrcNameLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvTxnLineTypeLov.invTxnLineTypeLov;
import static com.serviceco.coex.payment.model.invoice.reference.QInvTxnTypeNameLov.invTxnTypeNameLov;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.serviceco.coex.exception.InvoiceAttributeNotFoundException;
import com.serviceco.coex.exception.model.ExceptionConstants;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.calculation.PaymentTxnType;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.reference.InvDistributionCodeLov;
import com.serviceco.coex.payment.model.invoice.reference.InvPayGroupLov;
import com.serviceco.coex.payment.model.invoice.reference.InvPaymentTermsLov;
import com.serviceco.coex.payment.model.invoice.reference.QInvPayGroupLov;
import com.serviceco.coex.payment.model.invoice.reference.QInvTxnInvoiceLineTypeLov;
import com.serviceco.coex.payment.service.APInvoiceGenerationService.LegalEntityTuple;
import com.serviceco.coex.payment.support.DateTimeSupport;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import com.serviceco.coex.util.CaseConverter;

@Service
@Transactional
public class InvoiceAtrributeFinder {

  private static final Logger LOGGER = LoggerFactory.getLogger(InvoiceAtrributeFinder.class);

  @PersistenceContext
  private EntityManager em;

  private static Map<String, Map<String, Map<String, String>>> TAX_INVOICE_LINE_TYPE = new HashMap<>();

  private static Map<String, Map<String, Map<String, String>>> PAYMENT_TERMS = new HashMap<>();

  private static Map<String, Map<String, Map<String, String>>> PAYMENT_GROUP = new HashMap<>();


  public <T> T fetchInvoiceMetaData(EntityPath<T> table, BooleanExpression criteria) {

    return getQueryFactory().select(table).from(table).where(criteria).fetchFirst();
  }

  public JPAQueryFactory getQueryFactory() {
    final JPAQueryFactory factory = new JPAQueryFactory(em);
    return factory;
  }

  public String findBusinessUnitName(String invoiceType, String schemeParticipantType, String paymentGroup, InvoiceAttributeCache attributesCache, Scheme scheme) {

    String businessName = attributesCache.findBusinessUnitName(invoiceType, schemeParticipantType, paymentGroup, scheme);
    if (businessName != null) {
      return businessName;
    }
    
    try {
      final String businessUnit = fetchInvoiceMetaData(invBuNameLov,
          invBuNameLov.invoiceType.eq(invoiceType).and(invBuNameLov.schemeParticipantType.eq(schemeParticipantType))
          .and(invBuNameLov.paymentGroup.eq(paymentGroup))
          .and(invBuNameLov.multiSchemeId.eq(scheme.getMultiSchemeId()))).getValue();
      
      attributesCache.populateBusinessName(invoiceType, schemeParticipantType, paymentGroup, businessUnit);
      
      return businessUnit;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], scheme participant type [ {2} ] and payment group [ {3} ] ", "INV_BU_NAME_LOV", invoiceType,
          schemeParticipantType, paymentGroup);

    } catch (Exception e) {
      throw e;
    }
  }

  public String findBatchSource(String invoiceType, String paymentTransactionType) {

    try {
      final String batchSrc = fetchInvoiceMetaData(invTxnBatchSrcNameLov,
          invTxnBatchSrcNameLov.invoiceType.eq(invoiceType).and(invTxnBatchSrcNameLov.paymentGroup.eq(paymentTransactionType))).getValue();
      return batchSrc;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ] and payment transaction type [ {2} ]", "INV_TXN_BATCH_SRC_NAME_LOV", invoiceType,
          paymentTransactionType);

    } catch (Exception e) {
      throw e;
    }
  }

  public String findTransactionTypeName(String invoiceType, String paymentTransactionType) {

    try {
      final String transactionType = fetchInvoiceMetaData(invTxnTypeNameLov,
          invTxnTypeNameLov.invoiceType.eq(invoiceType).and(invTxnTypeNameLov.paymentGroup.eq(paymentTransactionType))).getName();
      return transactionType;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ] and payment transaction type [ {2} ]", "INV_TXN_TYPE_NAME_LOV", invoiceType, paymentTransactionType);

    } catch (Exception e) {
      throw e;
    }
  }

  public String findTransactionTypeId(String invoiceType, String paymentTransactionType, String name) {

    try {
      LOGGER.info("Invoice type in findtransactionType block{}", invoiceType);
      LOGGER.info("payment transaction Type in findtransactionType block{}", paymentTransactionType);
      LOGGER.info("name in findtransactionType block{}", name);
      final String transactionType = fetchInvoiceMetaData(invTxnTypeNameLov,
          invTxnTypeNameLov.invoiceType.eq(invoiceType).and(invTxnTypeNameLov.paymentGroup.eq(paymentTransactionType).and(invTxnTypeNameLov.name.eq(name)))).getValue();
      LOGGER.info("Transaction type after fetch data in findtransactionType block{}", transactionType);
      return transactionType;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], payment transaction type [ {2} ] and name [ {3} ]", "INV_TXN_TYPE_NAME_LOV", invoiceType,
          paymentTransactionType, name);

    } catch (Exception e) {
      throw e;
    }
  }

  public String findPaymentTerms(String invoiceType, String schemeParticipantType, String paymentType) {

    if (PAYMENT_TERMS.containsKey(invoiceType)) {
      if (PAYMENT_TERMS.get(invoiceType).containsKey(schemeParticipantType)) {
        if (PAYMENT_TERMS.get(invoiceType).get(schemeParticipantType).containsKey(paymentType)) {
          return PAYMENT_TERMS.get(invoiceType).get(schemeParticipantType).get(paymentType);
        }
      }
    }
    try {
      final InvPaymentTermsLov paymentTerm = getQueryFactory().select(invPaymentTermsLov).from(invPaymentTermsLov).where(invPaymentTermsLov.invoiceType.eq(invoiceType)
          .and(invPaymentTermsLov.schemeParticipantType.eq(schemeParticipantType).and(invPaymentTermsLov.paymentGroup.eq(paymentType)))).fetchOne();
      if (!PAYMENT_TERMS.containsKey(invoiceType)) {
        PAYMENT_TERMS.put(invoiceType, new HashMap<String, Map<String, String>>());
      }
      if (!PAYMENT_TERMS.get(invoiceType).containsKey(schemeParticipantType)) {
        PAYMENT_TERMS.get(invoiceType).put(schemeParticipantType, new HashMap<String, String>());
      }
      if (!PAYMENT_TERMS.get(invoiceType).get(schemeParticipantType).containsKey(paymentType)) {
        PAYMENT_TERMS.get(invoiceType).get(schemeParticipantType).put(paymentType, paymentTerm.getValue());
      }
      return paymentTerm.getValue();
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], scheme participant type [ {2} ] and payment type [ {3} ]", "INV_PAYMENT_TERMS_LOV", invoiceType,
          schemeParticipantType, paymentType);

    } catch (Exception e) {
      throw e;
    }

  }

  public String findPaymentGroup(String invoiceType, String schemeParticipantType, String paymentType, String paymentMethod) {

    try {
      String argPaymentType = "";

      if (StringUtils.equals(paymentType, PaymentTxnType.REFUND_AMOUNT.name())) {
        final String _paymentMethod = StringUtils.isBlank(paymentMethod) ? "SCHEME" : paymentMethod;
        argPaymentType = String.join("_", new String[] { paymentType, _paymentMethod });

      } else {
        argPaymentType = paymentType;
      }

      if (PAYMENT_GROUP.containsKey(invoiceType)) {
        if (PAYMENT_GROUP.get(invoiceType).containsKey(schemeParticipantType)) {
          if (PAYMENT_GROUP.get(invoiceType).get(schemeParticipantType).containsKey(argPaymentType)) {
            return PAYMENT_GROUP.get(invoiceType).get(schemeParticipantType).get(argPaymentType);
          }
        }
      }

      final InvPayGroupLov paymentGroup = getQueryFactory().select(QInvPayGroupLov.invPayGroupLov).select(QInvPayGroupLov.invPayGroupLov).from(QInvPayGroupLov.invPayGroupLov)
          .where(QInvPayGroupLov.invPayGroupLov.invoiceType.eq(invoiceType)
              .and(QInvPayGroupLov.invPayGroupLov.schemeParticipantType.eq(schemeParticipantType).and(QInvPayGroupLov.invPayGroupLov.paymentType.eq(argPaymentType)))).fetchOne();
      if (!PAYMENT_GROUP.containsKey(invoiceType)) {
        PAYMENT_GROUP.put(invoiceType, new HashMap<String, Map<String, String>>());
      }
      if (!PAYMENT_GROUP.get(invoiceType).containsKey(schemeParticipantType)) {
        PAYMENT_GROUP.get(invoiceType).put(schemeParticipantType, new HashMap<String, String>());
      }
      if (!PAYMENT_GROUP.get(invoiceType).get(schemeParticipantType).containsKey(argPaymentType)) {
        PAYMENT_GROUP.get(invoiceType).get(schemeParticipantType).put(argPaymentType, paymentGroup.getValue());
      }
      return paymentGroup.getValue();
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], scheme participant type [ {2} ], payment type [ {3} ] and payment method [ {4} ]", "INV_PAY_GROUP_LOV",
          invoiceType, schemeParticipantType, paymentType, paymentMethod);

    } catch (Exception e) {
      throw e;
    }

  }

  public String findTransactionDate() {

    final String transactionDate = DateTimeSupport.now().format(DateTimeFormatter.ofPattern(InvoiceConstants.YYYY_MM_DD));
    return transactionDate;
  }

  public boolean isBaseDueDateOnTransactionDate(String schemeParticipantType) {
    // TODO : remove this hardcoding
    return schemeParticipantType.equals("MANUFACTURER") ? true : false;
  }

  public String findTransactionLineType(String invoiceType) {

    try {
      final String transactionLineType = fetchInvoiceMetaData(invTxnLineTypeLov, invTxnLineTypeLov.invoiceType.eq(invoiceType)).getValue();
      return transactionLineType;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e, "Data missing in table [ {0} ] for invoice type [ {1} ]",
          "INV_TXN_LINE_TYPE_LOV", invoiceType);

    } catch (Exception e) {
      throw e;
    }

  }

  public String findTaxInvoiceLineType(String invoiceType, String paymentType, String index) {

    if (TAX_INVOICE_LINE_TYPE.containsKey(invoiceType)) {
      if (TAX_INVOICE_LINE_TYPE.get(invoiceType).containsKey(paymentType)) {
        if (TAX_INVOICE_LINE_TYPE.get(invoiceType).get(paymentType).containsKey(index)) {
          return TAX_INVOICE_LINE_TYPE.get(invoiceType).get(paymentType).get(index);
        }
      }
    }

    try {
      final QInvTxnInvoiceLineTypeLov qInvTxnInvoiceLineTypeLov = QInvTxnInvoiceLineTypeLov.invTxnInvoiceLineTypeLov;
      final String value = getQueryFactory().select(qInvTxnInvoiceLineTypeLov).from(qInvTxnInvoiceLineTypeLov)
          .where(qInvTxnInvoiceLineTypeLov.invoiceType.eq(invoiceType).and(qInvTxnInvoiceLineTypeLov.paymentType.eq(paymentType).and(qInvTxnInvoiceLineTypeLov.lineType.eq(index))))
          .fetchOne().getValue();
      if (!TAX_INVOICE_LINE_TYPE.containsKey(invoiceType)) {
        TAX_INVOICE_LINE_TYPE.put(invoiceType, new HashMap<String, Map<String, String>>());
      }
      if (!TAX_INVOICE_LINE_TYPE.get(invoiceType).containsKey(paymentType)) {
        TAX_INVOICE_LINE_TYPE.get(invoiceType).put(paymentType, new HashMap<String, String>());
      }
      if (!TAX_INVOICE_LINE_TYPE.get(invoiceType).get(paymentType).containsKey(index)) {
        TAX_INVOICE_LINE_TYPE.get(invoiceType).get(paymentType).put(index, value);
      }
      return value;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], payment type [ {2} ] and index [ {3} ]", "INV_TXN_INVOICE_LINE_TYPE_LOV", invoiceType, paymentType,
          index);

    } catch (Exception e) {
      throw e;
    }

  }

  public String buildTransactionLineDescription(String paymentTransactionType, Period period) {

    final String[] components = paymentTransactionType.split("_");
    final StringBuffer transactionType = new StringBuffer();
    for (final String str : components) {
      transactionType.append(" ");
      transactionType.append(CaseConverter.convertToCamelCase(str));
    }
    final String periodStartDate = period.getStart().format(DateTimeFormatter.ofPattern(InvoiceConstants.DD_MM_YYYY));
    final String periodEndDate = period.getEnd().format(DateTimeFormatter.ofPattern(InvoiceConstants.DD_MM_YYYY));
    return transactionType.append(" ").append("for").append(" ").append(periodStartDate).append(" ").append("to").append(" ").append(periodEndDate).toString().trim();
  }

  // public String findInvoiceLineType(String invoiceType, String paymentTransactionType) {
  // final String invoiceLineType = fetchInvoiceMetaData(invTxnInvoiceLineTypeLov, invTxnInvoiceLineTypeLov.invoiceType.eq(invoiceType)).getValue();
  // return invoiceLineType;
  // }

  public String findTaxClassificationCode(MdtParticipantSite schemeParticipant, String invoiceType, String paymentTransactionType) {

    try {
      String participantTypeCode = schemeParticipant.getSiteType();
      if (participantTypeCode.equals(SchemeParticipantType.CONSUMER.name()) && paymentTransactionType.equals(PaymentTxnType.REFUND_AMOUNT.name())) {
        return "GST FREE AP";
      }
      final String taxClassificationCode = fetchInvoiceMetaData(invTaxClassificationRef, invTaxClassificationRef.invoiceType.eq(invoiceType)
          .and(invTaxClassificationRef.paymentType.eq(paymentTransactionType).and(invTaxClassificationRef.schemeParticipant.eq(schemeParticipant)))).getValue();
      return taxClassificationCode;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], scheme participant id [ {2} ] and payment transaction type [ {3} ]", "INV_TAX_CLASSIFICATION_REF",
          invoiceType, schemeParticipant.getSiteNumber(), paymentTransactionType);

    } catch (Exception e) {
      throw e;
    }

  }

  public String findLegalEntityIdentifier(String invoiceType, String schemeParticipantType, String paymentTransactionType, InvoiceAttributeCache invoiceAttributes, Scheme scheme) {

    String legalCode = invoiceAttributes.findLegalEntityIdentifier(invoiceType, schemeParticipantType, paymentTransactionType, scheme);
    if (legalCode != null) {
      return legalCode;
    }

    try {
      legalCode = fetchInvoiceMetaData(invLegalIdentifierLov,
          invLegalIdentifierLov.invoiceType.eq(invoiceType).and(invLegalIdentifierLov.paymentType.eq(paymentTransactionType))
              .and(invLegalIdentifierLov.schemeParticipantType.eq(schemeParticipantType)
              .and(invLegalIdentifierLov.multiSchemeId.eq(scheme.getMultiSchemeId())))
              .and(invLegalIdentifierLov.value.notLike("FIX-ME%"))).getValue();
      invoiceAttributes.populateLegalEntityIdentifier(invoiceType, schemeParticipantType, paymentTransactionType, legalCode);
      return legalCode;
    } catch (NullPointerException e) {
      throw new InvoiceAttributeNotFoundException(ExceptionConstants.ERROR_CODES.INVOICE_ATTRIBUTE_NOT_FOUND, e,
          "Data missing in table [ {0} ] for union of invoice type [ {1} ], scheme participant type [ {2} ] and payment transaction type [ {3} ]", "INV_LEGAL_ENTITY_LOV",
          invoiceType, schemeParticipantType, paymentTransactionType);

    } catch (Exception e) {
      throw e;
    }
  }

  public String findLegalEntity(LegalEntityTuple entity, String paymentType, String schemeParticipantType, InvoiceAttributeCache invoiceAttributes, Scheme scheme) {

    if (entity != null) {
      if (schemeParticipantType.equals(SchemeParticipantType.CRP.name())) {
        if(paymentType.equals(PaymentTxnType.COLLECTION_FEES.name())){
          //return CONTAINER_EXCHANGE_QLD_LIMITED;
          return findLegalEntityIdentifier(InvoiceConstants.AP.INVOICE_TYPE, schemeParticipantType, paymentType, invoiceAttributes, scheme);
        }
        return entity.getLegalEntityName();
      } else if (paymentType.equals(PaymentTxnType.REFUND_AMOUNT.name()) && schemeParticipantType.equals(SchemeParticipantType.CONSUMER.name())) {
        return entity.getLegalEntityName();
      } else {
        return entity.getLegalEntityId();
      }
    }
    return findLegalEntityIdentifier(InvoiceConstants.AP.INVOICE_TYPE, schemeParticipantType, paymentType, invoiceAttributes, scheme);

  }

  public boolean isLineAmountIncludesTax(String invoiceType, String paymentType) {

    if (invoiceType.equals(InvoiceConstants.AR.INVOICE_TYPE) && paymentType.equals(PaymentTxnType.COLLECTION_FEES.name()))
      return true;
    return false;
  }

  public boolean shouldCalculateTaxDuringImport(String paymentType, String schemeParticipantType) {

    if (paymentType.equals(PaymentTxnType.REFUND_AMOUNT.name()) && StringUtils.equals(schemeParticipantType, SchemeParticipantType.CONSUMER.toString())) {
      return false;
    }
    return true;
  }

  public boolean shouldAddTaxToInvoiceAmount(String paymentType, String schemeParticipantType) {

    if (paymentType.equals(PaymentTxnType.REFUND_AMOUNT.name()) && StringUtils.equals(schemeParticipantType, SchemeParticipantType.CONSUMER.toString())) {
      return false;
    }
    return true;
  }
  
  public InvDistributionCodeLov findDistributionLine(String invoiceType, String schemeParticipantType, PaymentTransactionRec payment, 
      Map<String, String> additionalInfo, InvoiceAttributeCache attributeCache, Scheme scheme) {

    String paymentGroup = null;
    String platformFee = InvoiceConstants.AdditionInfo.IS_PLATFORM_FEE;

    if (additionalInfo.containsKey(platformFee)) {
      String isPlatformFee = additionalInfo.get(platformFee);
      if (isPlatformFee.equals("true")) {
        paymentGroup = "PLATFORM_COMMISSION_FEE";
      }
    }

    if (null == paymentGroup) {
      paymentGroup = schemeParticipantType.equals("RECYCLER") ? payment.getSchemeParticipantType() + "_" + payment.getPaymentType() : payment.getPaymentType();
    }

    String materialTypeName = null;
    if (payment.getMaterialType() != null) {
      materialTypeName = payment.getMaterialType().getId();
    } else {
      materialTypeName = payment.getMrfMaterialType().getId();
    }

    InvDistributionCodeLov distributionCode = attributeCache.findDistributionLine(invoiceType, schemeParticipantType, payment, additionalInfo, scheme);
    if (distributionCode == null) {
    
      BooleanBuilder compositeBuilder = new BooleanBuilder();
      compositeBuilder.and(invDistributionCodeLov.schemeParticipantType.eq(schemeParticipantType));
      compositeBuilder.and(invDistributionCodeLov.paymentGroup.eq(paymentGroup));
      compositeBuilder.and(invDistributionCodeLov.invoiceType.eq(invoiceType));
      compositeBuilder.and(invDistributionCodeLov.materialTypeName.eq(materialTypeName));
      compositeBuilder.and(invDistributionCodeLov.multiSchemeId.eq(scheme.getMultiSchemeId()));
      distributionCode = getQueryFactory().select(invDistributionCodeLov).from(invDistributionCodeLov).where(compositeBuilder).fetchFirst();

      if (distributionCode != null) {
        attributeCache.populateDistributionLine(distributionCode);
      }
    }
    return distributionCode;
  }

}
