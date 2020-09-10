package com.serviceco.coex.payment.model.invoice.ap;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.scheme.participant.service.SchemeService;

/**
 * 
 * @author vchadha5
 *
 */
public class APInvoiceTransactionHdrMapper implements RowMapper<APInvoiceTransactionRecHeader> {

  private SchemeService schemeService;
  
  public APInvoiceTransactionHdrMapper(SchemeService schemeService) {
    this.schemeService = schemeService;
  }
  
  @Override
  public APInvoiceTransactionRecHeader mapRow(ResultSet rs, int rowNum) throws SQLException {

    APInvoiceTransactionRecHeader apInvoiceTransactionRecHeader = new APInvoiceTransactionRecHeader(); 

    apInvoiceTransactionRecHeader.setId(rs.getString("ID"));
    apInvoiceTransactionRecHeader.setInvoiceNumber(rs.getString("INVOICE_NUMBER"));

    apInvoiceTransactionRecHeader.setBusinessUnit(rs.getString("BUSINESS_UNIT"));

    apInvoiceTransactionRecHeader.setInvoiceSource(rs.getString("INVOICE_SOURCE"));

    apInvoiceTransactionRecHeader.setInvoiceAmount(rs.getBigDecimal("INVOICE_AMOUNT"));

    apInvoiceTransactionRecHeader.setInvoiceDate(rs.getString("INVOICE_DATE"));   

    apInvoiceTransactionRecHeader.setSupplierNumber(rs.getString("SUPPLIER_NUMBER"));   

    apInvoiceTransactionRecHeader.setSupplierSite(rs.getString("SUPPLIER_SITE"));   

    apInvoiceTransactionRecHeader.setInvoiceCurrency(rs.getString("INVOICE_CURRENCY"));

    apInvoiceTransactionRecHeader.setPaymentCurrency(rs.getString("PAYMENT_CURRENCY"));

    apInvoiceTransactionRecHeader.setDescription(rs.getString("DESCRIPTION")); 

    apInvoiceTransactionRecHeader.setInvoiceType(rs.getString("INVOICE_TYPE"));

    apInvoiceTransactionRecHeader.setLegalEntity(rs.getString("LEGAL_ENTITY"));

    apInvoiceTransactionRecHeader.setPaymentTerms(rs.getString("PAYMENT_TERMS"));

    apInvoiceTransactionRecHeader.setPayGroup(rs.getString("PAY_GROUP"));

    apInvoiceTransactionRecHeader.setCalculateTaxDuringImport(rs.getBoolean("CALCULATE_TAX_DURING_IMPORT"));

    apInvoiceTransactionRecHeader.setAddTaxToInvoiceAmount(rs.getBoolean("ADD_TAX_TO_INVOICE_AMOUNT"));

    apInvoiceTransactionRecHeader.setInvoiceGroup(rs.getBigDecimal("INVOICE_GROUP"));
    
    Long multiSchemeId = rs.getLong("MULTI_SCHEME_ID");
    Optional<Scheme> scheme = schemeService.getByMultiSchemeId(multiSchemeId);
    if (scheme.isPresent()) {
      apInvoiceTransactionRecHeader.setScheme(scheme.get());
    }

    return apInvoiceTransactionRecHeader;
  }

}
