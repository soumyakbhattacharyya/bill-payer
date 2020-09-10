package com.serviceco.coex.payment.service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.serviceco.coex.config.SecureDataSourceConfig;

import oracle.jdbc.OracleConnection;

/**
 * A service class which invokes the Direct Payments database APIs. 
 *
 */
@Service
public class DirectPaymentService {
  
    
  @Autowired
  private SecureDataSourceConfig secureDataSourceConfig;
  
  /**
   * Executes the PYMT_DIRECT_API.PROCESS_POS_TRANSACTIONS procedure in the database. A list of recently created
   * consumer transaction header IDs are passed through to initiate the real time payment processing on these records.
   * @param consumerTxnHeaderIds The IDs of the {@link com.serviceco.coex.crp.model.ConsumerRefundTransactionHeader} records just created.
   * @throws SQLException Thrown if there was an unexpected error executing the procedure.
   */
  public void processPosTransactions(List<String> consumerTxnHeaderIds, Long multiSchemeId) throws SQLException {
    
    DataSource secDataSource = secureDataSourceConfig.getOrCreateSecureDataSource();
    
    try (Connection poolledConnection = secDataSource.getConnection()) {
      OracleConnection connection = poolledConnection.unwrap(OracleConnection.class);
      
      StringBuilder posTxnsXmlString = new StringBuilder("<?xml version = \"1.0\"?><TRANSACTIONS>");
      
      for (String txnId : consumerTxnHeaderIds) {
        posTxnsXmlString.append("<TXN_HEADER_ID>")
          .append(txnId)
          .append("</TXN_HEADER_ID>");
      }
      posTxnsXmlString.append("</TRANSACTIONS>");
      
      SQLXML transactionsAsXml = connection.createSQLXML(); 
      transactionsAsXml.setString(posTxnsXmlString.toString());
      try (CallableStatement statement = connection.prepareCall("{call PYMT_DIRECT_API.PROCESS_POS_TRANSACTIONS(?, ?)}")) {
        statement.setLong(1, multiSchemeId);
        statement.setSQLXML(2, transactionsAsXml);
        statement.execute();
      }
    }
  }

  
}
