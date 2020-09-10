package com.serviceco.coex.payment.model.invoice.ap;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import com.serviceco.coex.util.BigDecimalUtility;

import lombok.Getter;
import lombok.Setter;

/**
 * A data transfer object containing details of an Accounts Payable invoice.
 * 
 * @see com.serviceco.coex.payment.model.invoice.ap.APInvoiceTransactionRecHeader
 *
 */
@Getter
@Setter
public class APInvoiceTransactionDtlMapper implements RowMapper<APInvoiceTransactionDetail> {


  @Override
  public APInvoiceTransactionDetail mapRow(ResultSet rs, int rowNum) throws SQLException {

    final APInvoiceTransactionDetail detail = new APInvoiceTransactionDetail();

    detail.setAmount(BigDecimalUtility.asDouble(rs.getBigDecimal("AMOUNT")));

    detail.setDescription(rs.getString("DESCRIPTION"));

    detail.setDistributionCombination(rs.getString("DISTRIBUTION_COMBINATION"));

    detail.setFinalMatch(rs.getBoolean("FINAL_MATCH"));

    detail.setItemDescription(rs.getString("ITEM_DESCRIPTION"));

    detail.setLineGroupNumber(rs.getBigDecimal("LINE_GROUP_NUMBER").doubleValue());

    detail.setLineNumber(BigDecimalUtility.asDouble(rs.getBigDecimal("LINE_NUMBER")));

    detail.setLineType(rs.getString("LINE_TYPE"));

    detail.setPaymentPeriodStartDate(rs.getString("PAYMENT_PERIOD_START_DATE"));

    detail.setPaymentPeriodEndDate(rs.getString("PAYMENT_PERIOD_END_DATE"));

    detail.setAuctionDate(rs.getString("AUCTION_DATE"));

    detail.setPriceCorrectionLine(rs.getBoolean("PRICE_CORRECTION_LINE"));

    detail.setProrateAcrossAllLineItems(rs.getBoolean("PRORATE_ACROSS_ALL_LINE_ITEMS"));

    detail.setQuantity(BigDecimalUtility.asDouble(rs.getBigDecimal("QUANTITY"), 4));

    detail.setTaxClassificationCode(rs.getString("TAX_CLASSIFICATION_CODE"));

    detail.setTrackAsAsset(rs.getBoolean("TRACK_AS_ASSET"));

    detail.setUnitOfMeasure(rs.getString("UNIT_OF_MEASURE"));

    detail.setUnitPrice(BigDecimalUtility.asDouble(rs.getBigDecimal("UNIT_PRICE"), 6));

    return detail;

  }

}
