package com.serviceco.coex.payment.service;

import java.util.HashMap;
import java.util.Map;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.model.invoice.reference.InvDistributionCodeLov;

/**
 * Contains cached invoice attributes for use during an invoice generation batch.
 * This includes scheme specific data which shouldn't be used across schemes.
 *
 */
public class InvoiceAttributeCache {

  private Scheme scheme;
  
  private Map<String, Map<String, Map<String, Map<String, InvDistributionCodeLov>>>> distributionLines = new HashMap<>();
  
  private Map<String, Map<String, Map<String, String>>> businessUnitName = new HashMap<>();

  private Map<String, Map<String, Map<String, String>>> legalEntityIdentifier = new HashMap<>();

  
  public InvoiceAttributeCache(Scheme scheme) {
    this.scheme = scheme;
  }

  /**
   * Populates a distribution code line entity into a cache for efficiently finding it up later using findDistributionLine.
   * @param distributionCode
   */
  public void populateDistributionLine(InvDistributionCodeLov distributionCode) {
    
    synchronized (distributionLines) {
      String invoiceType = distributionCode.getInvoiceType();
      String schemeParticipantType = distributionCode.getSchemeParticipantType();
      String paymentGroup = distributionCode.getPaymentGroup();
      String materialTypeName = distributionCode.getMaterialTypeName();
      
      if (!distributionLines.containsKey(invoiceType)) {
        distributionLines.put(invoiceType, new HashMap<String, Map<String, Map<String, InvDistributionCodeLov>>>());
      }
      if (!distributionLines.get(invoiceType).containsKey(schemeParticipantType)) {
        distributionLines.get(invoiceType).put(schemeParticipantType, new HashMap<String, Map<String, InvDistributionCodeLov>>());
      }
      if (!distributionLines.get(invoiceType).get(schemeParticipantType).containsKey(paymentGroup)) {
        distributionLines.get(invoiceType).get(schemeParticipantType).put(paymentGroup, new HashMap<String, InvDistributionCodeLov>());
      }
      if (!distributionLines.get(invoiceType).get(schemeParticipantType).get(paymentGroup).containsKey(materialTypeName)) {
        distributionLines.get(invoiceType).get(schemeParticipantType).get(paymentGroup).put(materialTypeName, distributionCode);
      }
    } 
    
  }
  
  /**
   * Find a cached distribution line 
   * @param invoiceType
   * @param schemeParticipantType
   * @param payment
   * @param additionalInfo
   * @return
   */
  public InvDistributionCodeLov findDistributionLine(String invoiceType, String schemeParticipantType, PaymentTransactionRec payment, 
      Map<String, String> additionalInfo, Scheme scheme) {
    
    if (!scheme.equals(this.scheme)) {
      throw new RuntimeException("The scheme does not match the data in the cache");
    }

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

    synchronized (distributionLines) {
      if (distributionLines.containsKey(invoiceType)) {
        if (distributionLines.get(invoiceType).containsKey(schemeParticipantType)) {
          if (distributionLines.get(invoiceType).get(schemeParticipantType).containsKey(paymentGroup)) {
            if (distributionLines.get(invoiceType).get(schemeParticipantType).get(paymentGroup).containsKey(materialTypeName)) {
              return distributionLines.get(invoiceType).get(schemeParticipantType).get(paymentGroup).get(materialTypeName);
            }
          }
        }
      }
    }
    return null;
  }
  
  /**
   * Find a business unit name in the cache
   * @param invoiceType
   * @param schemeParticipantType
   * @param paymentGroup
   * @param attributesCache
   * @param scheme This must match the scheme in the cache or an exception will be thrown
   * @return Returns the business unit name (or null if its not found)
   */
  public String findBusinessUnitName(String invoiceType, String schemeParticipantType, String paymentGroup, Scheme scheme) {

    if (!scheme.equals(this.scheme)) {
      throw new RuntimeException("The scheme does not match the data in the cache");
    }
    
    synchronized(businessUnitName) {
      if (businessUnitName.containsKey(invoiceType)) {
        if (businessUnitName.get(invoiceType).containsKey(schemeParticipantType)) {
          if (businessUnitName.get(invoiceType).get(schemeParticipantType).containsKey(paymentGroup)) {
            return businessUnitName.get(invoiceType).get(schemeParticipantType).get(paymentGroup);
          }
        }
      }
    }
    return null;
  }

  /**
   * Stores a business name in the cache
   * @param invoiceType
   * @param schemeParticipantType
   * @param paymentGroup
   * @param attributesCache
   * @param Returns the business unit name found, or null if there isn't a match
   */
  public void populateBusinessName(String invoiceType, String schemeParticipantType, String paymentGroup, String bu) {
    
    synchronized(businessUnitName) {
      if (!businessUnitName.containsKey(invoiceType)) {
        businessUnitName.put(invoiceType, new HashMap<String, Map<String, String>>());
      }
      if (!businessUnitName.get(invoiceType).containsKey(schemeParticipantType)) {
        businessUnitName.get(invoiceType).put(schemeParticipantType, new HashMap<String, String>());
      }
      if (!businessUnitName.get(invoiceType).get(schemeParticipantType).containsKey(paymentGroup)) {
        businessUnitName.get(invoiceType).get(schemeParticipantType).put(paymentGroup, bu);
      }
    }
  }
  
  /**
   * Finds a legal entity ID in the cache
   * @param invoiceType
   * @param schemeParticipantType
   * @param paymentTransactionType
   * @param scheme
   * @return Returns the entity found, or null if there isn't a match
   */
  public String findLegalEntityIdentifier(String invoiceType, String schemeParticipantType, String paymentTransactionType, Scheme scheme) {

    if (!scheme.equals(this.scheme)) {
      throw new RuntimeException("The scheme does not match the data in the cache");
    }
    
    synchronized(legalEntityIdentifier) {
      if (legalEntityIdentifier.containsKey(invoiceType)) {
        if (legalEntityIdentifier.get(invoiceType).containsKey(schemeParticipantType)) {
          if (legalEntityIdentifier.get(invoiceType).get(schemeParticipantType).containsKey(paymentTransactionType)) {
            return legalEntityIdentifier.get(invoiceType).get(schemeParticipantType).get(paymentTransactionType);
          }
        }
      }
    }
    return null;
  }

  /**
   * Populate a legal entity ID in the cache
   * @param invoiceType
   * @param schemeParticipantType
   * @param paymentTransactionType
   * @param legalCode
   */
  public void populateLegalEntityIdentifier(String invoiceType, String schemeParticipantType,
      String paymentTransactionType, String legalCode) {

    synchronized(legalEntityIdentifier) {
      if (!legalEntityIdentifier.containsKey(invoiceType)) {
        legalEntityIdentifier.put(invoiceType, new HashMap<String, Map<String, String>>());
      }
      if (!legalEntityIdentifier.get(invoiceType).containsKey(schemeParticipantType)) {
        legalEntityIdentifier.get(invoiceType).put(schemeParticipantType, new HashMap<String, String>());
      }
      if (!legalEntityIdentifier.get(invoiceType).get(schemeParticipantType).containsKey(paymentTransactionType)) {
        legalEntityIdentifier.get(invoiceType).get(schemeParticipantType).put(paymentTransactionType, legalCode);
      }
    }
  }
}
