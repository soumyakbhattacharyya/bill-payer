package com.serviceco.coex.payment.model.invoice.ar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;

import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.scheme.participant.service.SchemeService;

public class InvoiceARTransactionMapper implements RowMapper<InvoiceARTransactionRec>{

  private SchemeService schemeService;
  
  public InvoiceARTransactionMapper(SchemeService schemeService) {
    this.schemeService = schemeService;
  }
  
	@Override
	public InvoiceARTransactionRec mapRow(ResultSet rs, int rowNum) throws SQLException {
		InvoiceARTransactionRec invoiceARTransactionRec = new InvoiceARTransactionRec();
		invoiceARTransactionRec.setId(rs.getString("ID"));
		invoiceARTransactionRec.setBusinessUnitName(rs.getString("BUSINESS_UNIT_NAME"));
		invoiceARTransactionRec.setBatchSource(rs.getString("BATCH_SOURCE"));
		invoiceARTransactionRec.setTransactionType(rs.getString("TRANSACTION_TYPE"));
		invoiceARTransactionRec.setTransactionTypeId(rs.getString("TRANSACTION_TYPE_ID"));
		invoiceARTransactionRec.setPaymentTerms(rs.getString("PAYMENT_TERMS"));
		invoiceARTransactionRec.setTransactionDate(rs.getString("TRANSACTION_DATE"));
		invoiceARTransactionRec.setBaseDueDateOnTransactionDate(rs.getBoolean("BASE_DUE_DATE"));
		invoiceARTransactionRec.setTransactionNumber(rs.getString("TXN_NUMBER"));
		invoiceARTransactionRec.setBillToCustomerAccountNumber(rs.getString("BILL_TO_CUST_ACC_NUMBER"));
		invoiceARTransactionRec.setBillToCustomerSiteNumber(rs.getString("BILL_TO_CUST_SITE_NUMBER"));
		invoiceARTransactionRec.setSoldToCustomerAccountNumber(rs.getString("SOLD_TO_CUST_ACC_NUMBER"));
		invoiceARTransactionRec.setTransactionLineType(rs.getString("TXN_LINE_TYPE"));
		invoiceARTransactionRec.setTransactionLineDescription(rs.getString("TXN_LINE_DESCRIPTION"));
		invoiceARTransactionRec.setCurrencyCode(rs.getString("CURRENCY_CODE"));
		invoiceARTransactionRec.setCurrencyConversionType(rs.getString("CURRENCY_CONVERSION_TYPE"));
		invoiceARTransactionRec.setTransactionLineAmount(rs.getBigDecimal("TXN_LINE_AMOUNT"));
		invoiceARTransactionRec.setTransactionLineQuantity(rs.getBigDecimal("TXN_LINE_QUANTITY"));
		invoiceARTransactionRec.setUnitSellingPrice(rs.getBigDecimal("UNIT_SELLING_PRICE"));
		invoiceARTransactionRec.setInvoiceSource(rs.getString("INVOICE_SOURCE"));
		invoiceARTransactionRec.setInvoiceLineNumber(rs.getString("INVOICE_LINE_NUMBER"));
		invoiceARTransactionRec.setInvoiceLineType(rs.getString("INVOICE_LINE_TYPE"));
		invoiceARTransactionRec.setTaxClassificationCode(rs.getString("CLASSIFICATION_ON_CODE"));
		invoiceARTransactionRec.setLegalEntityIdentifier(rs.getString("LEGAL_ENTITY_IDENTIFIER"));
		invoiceARTransactionRec.setUnitOfMeasureCode(rs.getString("UOM"));
		invoiceARTransactionRec.setDefaultTaxationCountry(rs.getString("DEFAULT_TAXATION_COUNTRY"));
		invoiceARTransactionRec.setLineAmountIncludesTax(rs.getBoolean("LINE_AMOUNT_INCLUDES_TAX"));
		invoiceARTransactionRec.setInventoryItemNumber(rs.getString("INVENTORY_ITEM_NUMBER"));
		invoiceARTransactionRec.setPaymentPeriodStartDate(rs.getString("PERIOD_START_DATE"));
		invoiceARTransactionRec.setPaymentPeriodEndDate(rs.getString("PERIOD_END_DATE"));
		invoiceARTransactionRec.setAuctionDate(rs.getString("AUCTION_DATE"));
		invoiceARTransactionRec.setAccountClass(rs.getString("ACCOUNT_CLASS"));
		invoiceARTransactionRec.setAmountPercentage(rs.getBigDecimal("AMOUNT_PERCENTAGE"));
		invoiceARTransactionRec.setEntity(rs.getString("ENTITY"));
		invoiceARTransactionRec.setMaterialType(rs.getString("MATERIAL_TYPE_CODE"));
		invoiceARTransactionRec.setCostCentre(rs.getString("COST_CENTRE"));
		invoiceARTransactionRec.setNaturalAcc(rs.getString("NATURAL_ACC"));
		invoiceARTransactionRec.setInterCoAcc(rs.getString("INTER_CO_ACC"));
		invoiceARTransactionRec.setSpare(rs.getString("SPARE"));
		Long multiSchemeId = rs.getLong("MULTI_SCHEME_ID");
		Optional<Scheme> scheme = schemeService.getByMultiSchemeId(multiSchemeId);
		if (scheme.isPresent()) {
		  invoiceARTransactionRec.setScheme(scheme.get());
		}

		return invoiceARTransactionRec;
	}
}
