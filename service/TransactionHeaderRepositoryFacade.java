package com.serviceco.coex.payment.service;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Preconditions;
import com.serviceco.coex.crp.model.CRPClaimHeader;
import com.serviceco.coex.crp.repository.CRPClaimHeaderRepository;
import com.serviceco.coex.exporter.model.ExportVolumeHeader;
import com.serviceco.coex.exporter.model.dto.EntryStatus;
import com.serviceco.coex.exporter.repository.ExportVolumeHeaderRepository;
import com.serviceco.coex.manufacturer.model.SalesVolumeHdr;
import com.serviceco.coex.manufacturer.repository.SalesVolumeHeaderRepository;
import com.serviceco.coex.mrf.model.MRFClaimHdr;
import com.serviceco.coex.mrf.repository.MRFClaimHdrRepository;
import com.serviceco.coex.processor.model.ProcessorClaimHeader;
import com.serviceco.coex.processor.repository.ProcessorClaimHeaderRepository;

/**
 * Contains methods for updating volume/claim header records after their associated payment transaction records have been included in an invoice. 
 *
 */
@Service
@Transactional
public class TransactionHeaderRepositoryFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionHeaderRepositoryFacade.class);

  @Autowired
  private SalesVolumeHeaderRepository manufacturerSalesVolumeRepo;

  @Autowired
  private ExportVolumeHeaderRepository exporterSalesVolumeRepo;

  @Autowired
  private MRFClaimHdrRepository mrfClaimRepo;

  @Autowired
  private ProcessorClaimHeaderRepository processorClaimRepo;

  @Autowired
  private CRPClaimHeaderRepository crpClaimRepo;

  @Autowired
  private DataSource dataSource;

  public enum PaymentType {
    REFUND_AMOUNT, COLLECTION_FEES, HANDLING_FEES, GST_RECOVERY_AMOUNT, EXPORT_REBATE, RECOVERY_AMOUNT_CLAIM, PROCESSING_FEES, SCHEME_CONTRIBUTION, POSITIVE_AUCTION, NEGATIVE_AUCTION;
  }

  public enum PaymentMode {
    CASH, SCHEME;
  }

  /**
   * Updates the status of a specific volume/claim record to INVOICED, based on the payment type and record ID.
   * 
   * @param type	The type of payment. This determines which database table contains the volume/claim header record.
   * @param mode	Not used.
   * @param headerId	The ID of the volume/claim record to update
   */
  public void updateHeader(PaymentType type, PaymentMode mode, String headerId) {
    Preconditions.checkArgument(type != null);
    // TODO: uncomment when we support CRP: Preconditions.checkArgument(StringUtils.isNotBlank(headerId));

    switch (type) {
    case SCHEME_CONTRIBUTION:
      final Optional<SalesVolumeHdr> salesVolumeHeaderOpt = manufacturerSalesVolumeRepo.findById(headerId);
      if (salesVolumeHeaderOpt.isPresent()) {
        final SalesVolumeHdr salesVolumeHdr = salesVolumeHeaderOpt.get();
        salesVolumeHdr.setEntryStatus(EntryStatus.INVOICED);
        manufacturerSalesVolumeRepo.save(salesVolumeHdr);
      }

      break;
    case PROCESSING_FEES:
      final Optional<ProcessorClaimHeader> processorClaimHeaderOpt = processorClaimRepo.findById(headerId);
      if (processorClaimHeaderOpt.isPresent()) {
        final ProcessorClaimHeader processorClaimHdr = processorClaimHeaderOpt.get();
        processorClaimHdr.setEntryStatus(EntryStatus.INVOICED);
        processorClaimRepo.save(processorClaimHdr);
      }

      break;

    case EXPORT_REBATE:
      if (headerId != null) {
        final Optional<ExportVolumeHeader> exportVolumeOpt = exporterSalesVolumeRepo.findById(headerId);
        if (exportVolumeOpt.isPresent()) {
          final ExportVolumeHeader exportVolHdr = exportVolumeOpt.get();
          exportVolHdr.setEntryStatus(EntryStatus.INVOICED);
          exporterSalesVolumeRepo.save(exportVolHdr);
        }
      }

      break;

    case RECOVERY_AMOUNT_CLAIM:
      final Optional<MRFClaimHdr> mrfClaimHrdOpt = mrfClaimRepo.findById(headerId);
      if (mrfClaimHrdOpt.isPresent()) {
        final MRFClaimHdr mrfClaimHdr = mrfClaimHrdOpt.get();
        mrfClaimHdr.setEntryStatus(EntryStatus.INVOICED);
        mrfClaimRepo.save(mrfClaimHdr);
      }

      break;

    case HANDLING_FEES:
      final Optional<CRPClaimHeader> crpClaimHrdOpt = crpClaimRepo.findById(headerId);
      if (crpClaimHrdOpt.isPresent()) {
        final CRPClaimHeader crpClaimHdr = crpClaimHrdOpt.get();
        crpClaimHdr.setEntryStatus(EntryStatus.INVOICED);
        crpClaimRepo.save(crpClaimHdr);
      }

      break;

    // TODO : CRP pending

    default:
      break;
    }
  }

  /**
   * Updates the status of consumer refund records (CONSUMER_REFUND_TXN_HDR perhaps?) after they have been included
   * in an invoice.
   * 
   * <p>The status is updated using a {@code PROC_CONSUMER_REFUND} database procedure.</p>
   * 
   * @param paymentBatchIds
   * @param schemeParticipantId
   */
  public void updateHeader(List<String> paymentBatchIds, String schemeParticipantId) {
    // Removing duplicate records
    HashSet<String> batchIds = new HashSet<>(paymentBatchIds);
    String commaSeparatedBatchIds = StringUtils.join(batchIds, ',');

    try (Connection conn = dataSource.getConnection(); CallableStatement stmt = conn.prepareCall("CALL PROC_CONSUMER_REFUND(?,?)");) {
      stmt.setString(1, schemeParticipantId);
      stmt.setString(2, commaSeparatedBatchIds);
      stmt.execute();
    } catch (SQLException e) {
      LOGGER.info("Unable to update status for header");
      // printing explicitly to console
      e.printStackTrace();
      throw new RuntimeException("Unable to update status for header" + e.getMessage());
    }
  }

  /**
   * Updates exporter refund transaction header records to indicate they have been included in an invoice.
   *  
   * <p>The update is done by calling a PROC_EXPORTER_REFUND_TXN_HDR database procedure.</p>
   *  
   * @param paymentBatchIds	The payment batch IDs associated with the payment transaction records which were included in the invoice
   * @param schemeParticipantId The ID of the scheme participant associated with the payments.
   */
  public void updateExporterRefundTxnHeader(List<String> paymentBatchIds, String schemeParticipantId) {
    // Removing duplicate records
    HashSet<String> batchIds = new HashSet<>(paymentBatchIds);
    String commaSeparatedBatchIds = StringUtils.join(batchIds, ',');

    try (Connection conn = dataSource.getConnection(); CallableStatement stmt = conn.prepareCall("CALL PROC_EXPORTER_REFUND_TXN_HDR(?,?)");) {
      stmt.setString(1, schemeParticipantId);
      stmt.setString(2, commaSeparatedBatchIds);
      stmt.execute();
    } catch (SQLException e) {
      LOGGER.info("Unable to update status for header");
      // printing explicitly to console
      e.printStackTrace();
      throw new RuntimeException("Unable to update status for header" + e.getMessage());
    }
  }

}
