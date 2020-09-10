package com.serviceco.coex.payment.model.calculation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.serviceco.coex.auction.model.LotItem;
import com.serviceco.coex.auction.model.LotItemManifest;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.model.EntityBase;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.mrf.model.MRFMaterialType;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import javax.persistence.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Represents a payment transaction record in the {@code PAYMENT_TRANSACTION_REC} database table.
 * <p>The payment transaction record can represent a payment from any source. 
 * It is constructed by the payment engine ({@link com.serviceco.coex.payment.api.ComputationOfPaymentTransaction} or {@link com.serviceco.coex.payment.api.ComputationOfPaymentTransactionAsync}) based on available volume data 
 * for a specific scheme participant type.</p>
 * 
 */
@Entity
@Table(name = "PAYMENT_TRANSACTION_REC")
@Getter
@Setter
public class PaymentTransactionRec extends EntityBase {

  private static final long serialVersionUID = 1L;

  @Column(name = "PAYMENT_TYPE", nullable = false, length = 50)
  private String paymentType;

  @Column(name = "PAYMENT_METHOD", length = 50)
  private String paymentMethod;

  @Column(name = "SCHEME_PARTICIPANT_ID", nullable = false, length = 50)
  private String schemeParticipantId;

  @Column(name = "SCHEME_PARTICIPANT_NAME", nullable = false, length = 100)
  private String schemeParticipantName;

  @Column(name = "SCHEME_PARTICIPANT_TYPE", nullable = false, length = 100)
  private String schemeParticipantType;

  @Column(name = "PAYMENT_PERIOD", nullable = false, length = 50)
  private String paymentPeriod;

  @Column(name = "PERIOD", nullable = false, length = 50)
  private String period;

  @Column(name = "PERIOD_TYPE", nullable = false, length = 1)
  private String periodType;

  @Column(name = "ENTRY_TYPE", nullable = false, length = 1)
  private String entryType;

  @Column(name = "VOLUME", nullable = false)
  private BigDecimal volume;

  @Column(name = "UNIT_SELLING_PRICE", nullable = false)
  private BigDecimal unitSellingPrice;

  @Column(name = "UOM", nullable = false)
  private String uom;

  @Column(name = "ARREAR", nullable = false, length = 1)
  private String arrear;

  @Column(name = "LINE_TYPE", nullable = false, length = 50)
  private String lineType;

  @Column(name = "GROSS_AMOUNT", nullable = false)
  private BigDecimal grossAmount;

  @Column(name = "TAXABLE_AMOUNT", nullable = false)
  private BigDecimal taxableAmount;

  @Column(name = "GST_AMOUNT", nullable = false)
  private BigDecimal gstAmount;

  @Temporal(TemporalType.TIMESTAMP)
  @Column(name = "PAYMENT_TIMESTAMP", nullable = false)
  private Date paymentTimestamp;

  @Column(name = "STATUS", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private PaymentStatus status;

  @Column(name = "VOLUME_HDR_ID", nullable = true)
  private String volumeHeaderId;

  // @Column(name = "AP_STATUS", nullable = true)
  // @Enumerated(EnumType.STRING)
  // private InvoiceStatus apStatus;
  //
  // @Column(name = "AR_STATUS", nullable = true)
  // @Enumerated(EnumType.STRING)
  // private InvoiceStatus arStatus;

  @ManyToOne
  @JoinColumn(name = "PROCESSOR_ID")
  private MdtParticipantSite processor;

  @ManyToOne
  @JoinColumn(name = "MATERIAL_TYPE_ID", referencedColumnName = "ID")
  private MaterialType materialType;

  @ManyToOne
  @JoinColumn(name = "PAYMENT_BATCH_ID", referencedColumnName = "ID")
  private PaymentBatch paymentBatch;

  @ManyToOne
  @JoinColumn(name = "MRF_MATERIAL_TYPE_ID", referencedColumnName = "ID")
  private MRFMaterialType mrfMaterialType;

  @OneToOne
  @JoinColumn(name = "LOT_ITEM_ID", referencedColumnName = "ID")
  private LotItem lotItem;

  @OneToOne
  @JoinColumn(name = "FINAL_MANIFEST_ID", referencedColumnName = "ID")
  private LotItemManifest finalManifest;

  @Column(name = "LOT_ITEM_MANIFEST_ID")
  private String lotItemManifestId;

  @Column(name = "VOLUME_HDR_ENTRY_TYPE")
  private String volumeHdrEntryType;

  /**
   * If set, this specifies the scheme this record applies. A null value means it applies to all. This is a new
   * column added to many tables and the code has only been updated to refer to this where its required. So it may not be used
   * at this point.
   */
  @JsonIgnore
  @JoinColumn(name = "MULTI_SCHEME_ID", referencedColumnName = "MULTI_SCHEME_ID", nullable = true)
  @ManyToOne
  private Scheme scheme;
  
  @Transient
  public Request calculationRequest;

  @Transient
  public StateTransitionRequest stateTransitionRequest;

  @Getter
  @Setter
  public static class SchemePricePopulationRequest {

    private String materialTypeId;

    private List<SchemePriceRecord> schemePriceRecords;

  }

  @Getter
  @Setter
  public static class SchemePriceRecord {

    private String period;

    private double price;

  }

  @Getter
  @Setter
  public static class StateTransitionRequest {

    private SchemeParticipantType schemeParticipantType;

    @JsonProperty("schemeParticipantPayments")
    private List<SchemeParticipantToStateMapper> schemeParticipantToStateMappers;

    private String callbackUrl;

    private String correlationId;

  }

  @Getter
  @Setter
  public static class SchemeParticipantToStateMapper {

    private String schemeParticipantId;

    private PaymentStatus status;

    @JsonCreator
    public static PaymentStatus forValue(String value) {

      return PaymentStatus.valueOf(StringUtils.lowerCase(value));
    }

    public SchemeParticipantToStateMapper(String schemeParticipantId, PaymentStatus status) {

      super();
      this.schemeParticipantId = schemeParticipantId;
      this.status = status;
    }

    public SchemeParticipantToStateMapper() {

    }

    @Override
    public int hashCode() {

      final int prime = 31;
      int result = 1;
      result = (prime * result) + ((schemeParticipantId == null) ? 0 : schemeParticipantId.hashCode());
      result = (prime * result) + ((status == null) ? 0 : status.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {

      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      final SchemeParticipantToStateMapper other = (SchemeParticipantToStateMapper) obj;
      if (schemeParticipantId == null) {
        if (other.schemeParticipantId != null)
          return false;
      } else if (!schemeParticipantId.equals(other.schemeParticipantId))
        return false;
      if (status != other.status)
        return false;
      return true;
    }

  }

  @Getter
  @Setter
  public class PaymentTransactionView {

    private String schemeParticipantType;

    private String period;

    private BigDecimal totalAmount;

  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class PaymentBatchExecutionSummary {

    public static String dateFormatter(Date date) {

      final String pattern = "MM-dd-yyyy HH:mm:ss.SSSZ";
      final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
      return simpleDateFormat.format(date);
    }

    private String paymentBatchId;

    private PaymentBatch.RUN_STATUS paymentBatchStatus;

    private String paymentBatchStartTimeStamp;

    private String paymentBatchEndTimeStamp;

    private Long numberOfPaymentTransactions;

    private Integer numberOfSchemeParticipants;

    private Double totalPaymentAmount;

    private String paymentPeriod;
    
    private String schemeId;

  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class StateTransitionSummary {

    private SchemeParticipantType schemeParticipantType;

    private Long numberOfPaymentTransactions;

    private Integer numberOfSchemeParticipants;

    private Double totalPaymentAmount;
    
    @JsonProperty("schemeParticipantPayments")
    private Set<SchemeParticipantToStateMapper> schemeParticipantToStateMappers;
    
    private String schemeId;

  }

  public static enum PaymentStatus {
    AWAITING_REVIEW, HOLD, AWAITING_APPROVAL, AWAITING_INVOICING, STALE;
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {

    final Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
  }

}
