package com.serviceco.coex.payment.service;

import java.util.HashMap;
import java.util.Map;

public interface InvoiceConstants {

  public static interface AR {
    String INVOICE_TYPE = "AR";
  }

  public static interface AP {
    String INVOICE_TYPE = "AP";
  }

  public static interface AdditionInfo {

    String INVOICE_AMOUNT = "invoiceAmount";
    String UNIT_SELLING_PRICE = "unitSellingPrice";
    String TAX_CLASSIFICATION_REF = "taxClassificationRef";
    String LOT_ITEM_ID = "lotItemId";
    String LOT_ITEM_FINAL_MANIFEST_ID = "lotItemFinalManifestId";
    String VOLUME = "volume";
    String IS_ADJUSTMENT = "isAdjustment";
    String IS_PLATFORM_FEE = "isPlatformFee";

  }

  public static final String CURRENCY_CODE = "AUD";
  public static final String INVOICE_SOURCE = "PAAS";
  public static final String DEFAULT_TAXATION_COUNTRY = "AU";
  public static final String CURRENCY_CONVERSION_TYPE = "Corporate";
  public static final String YYYY_MM_DD = "yyyy/MM/dd";
  public static final String DD_MM_YYYY = "dd/MM/yyyy";
  public static final String EXPORTS = "EXP";
  public static final String GST_FREE_AP = "GST FREE AP";
  public static final String GST_FREE_AR = "GST FREE AR";

  public static Map<String, String> MATERIAL_TYPE_NAME_ERP_MAPPING = new HashMap<String, String>() {
    {
      put("PET Clear", "PET - Clear");
      put("PET Colour", "PET - Colour");
      put("PET Mixed Clear and Colour", "PET Mixed C&C");
    }
  };

}
