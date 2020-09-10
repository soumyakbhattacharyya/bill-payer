package com.serviceco.coex.payment.model.calculation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.calculation.PaymentTxnType;
import com.serviceco.coex.util.BigDecimalUtility;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates aggregated payment details based on a list of transactions. The transactions are aggregated by the scheme
 * participants and payment types.
 *
 * See {@link #of}
 *
 */
@Getter
@Setter
public class PaymentAggregateView {

  private static final Logger logger = LoggerFactory.getLogger(PaymentAggregateView.class);

  /**
   * The scheme participant type
   */
  private String schemeParticipantType;

  /**
   * The total gross amount (from all records)
   */
  private double totalAmount;

  /**
   * The payment period associated with the payment transactions
   */
  private String paymentPeriod;

  /**
   * Each of the scheme participants along with aggregated data based on the transactions associated
   * with them
   */
  private List<SchemeParticipantPayment> schemeParticipantPayments;

  @JsonIgnore
  private final SchemeParticipantType schemeParticipantTypeArg;

  @JsonIgnore
  private final List<PaymentTransactionRec> recordsArg;

  private PaymentAggregateView(SchemeParticipantType schemeParticipantType, List<PaymentTransactionRec> records) {

    schemeParticipantTypeArg = schemeParticipantType;
    recordsArg = records;
  }

  /**
   * <p>Generates aggregated payment details based on the transactions passed in ({@code records}). </p>
   * 
   * <p>Firstly, this method gets each unique scheme participant ID out of those mentioned in the payment transaction records passed in.
   * Next, this loops through each unique scheme participant ID and finds the associated payment transactions in {@code records}.</p>
   * 
   * <p>
   * For each scheme participant ID, a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView.SchemeParticipantPayment} is created containing details such as the total gross, total weight and payment date based on the particpant's transactions.
   * </p>
   * 
   * <p>
   * Within each {@code SchemeParticipantPayment}, a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView.SchemeParticipantPaymentTransaction} is also created for each payment type used. This contains the total gross, gst, tax, etc.
   * for all of the participant's transactions which are associated with a particular payment type.
   * </p> 
   * 
   * <p>
   * In addition to above, there is also an overall total (gross) amount calculated based on all of the records.
   * </p>
   * 
   * @param schemeParticipantType The type of participant associated with the payment records. This is passed through to the {@code PaymentAggregateView} returned.
   * @param records	The payment transactions to look at and aggregate.
   * @return Returns a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView} containing the generated details including a list of {@code SchemeParticipantPayment}'s and the overall total.
   */
  public static PaymentAggregateView of(SchemeParticipantType schemeParticipantType, List<PaymentTransactionRec> records) {

    if ((null != records) && !records.isEmpty()) {
      logger.info("received {} payment records to construct view", records.size());
      //@formatter:off
      final PaymentAggregateView $ = new PaymentAggregateView(schemeParticipantType,records);
      $.withSchemeParticipantType()
       .withPaymentPeriod()
           .addSchemeParticipantPaymentList()
            .withTotalAmount()
             .findSchemeParticipantId();
      //@formatter:on   
      return $;
    } else {
      logger.info("there are no records to construct a view from");
      return new PaymentAggregateView(schemeParticipantType, new ArrayList<>());
    }

  }

  private void findSchemeParticipantId() {
    //
  }

  /**
   * 
   * <p>Generates and adds aggregated payment details based on the transactions in {@code recordsArg}. </p>
   * 
   * Firstly, this method gets each unique scheme participant ID out of those mentioned in the payment transaction records passed in ({@code recordsArg}).
   * Next, this loops for each unique scheme participant ID and finds the associated payment transactions in {@code recordsArg}.
   * 
   * For each scheme participant ID, a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView.SchemeParticipantPayment} is created containing details such as the total gross, total weight and payment date.
   * 
   * Within each {@code SchemeParticipantPayment}, a {@link com.serviceco.coex.payment.model.calculation.PaymentAggregateView.SchemeParticipantPaymentTransaction} is also created for each payment type used. This contains the total gross, gst, tax, etc.
   * for all of the participant's transactions which are associated with a particular payment type. 
   * 
   * <p>The {@code SchemeParticipantPayment} objects are added to this instance's schemeParticipantPayments list.</p>
   * 
   * @return Returns this instance
   */
  private PaymentAggregateView addSchemeParticipantPaymentList() {

    schemeParticipantPayments = new ArrayList<>();
    final List<PaymentTransactionRec> paymentTransactionRecLines = recordsArg;

    // find distinct scheme participant ids
    final List<String> schemeParticipantIdentifiers = paymentTransactionRecLines.stream().filter(PaymentTransactionRec.distinctByKey(PaymentTransactionRec::getSchemeParticipantId))
        .map(new Function<PaymentTransactionRec, String>() {
          @Override
          public String apply(PaymentTransactionRec t) {

            return t.getSchemeParticipantId() + "#" + t.getSchemeParticipantName();
          }
        }).collect(Collectors.toList());

    final DecimalFormat df = new DecimalFormat("#.##");
    df.setRoundingMode(RoundingMode.HALF_UP);

    for (final String identifier : schemeParticipantIdentifiers) {
      final SchemeParticipantPayment schemeParticipantPayment = new SchemeParticipantPayment();
      final String[] segments = identifier.split("#");
      final String id = segments[0];
      final String name = segments[1];

      schemeParticipantPayment.setId(id);
      schemeParticipantPayment.setName(name);

      final List<PaymentTransactionRec> relevantTransactions = paymentTransactionRecLines.stream().filter(x -> x.getSchemeParticipantId().equals(id)).collect(Collectors.toList());

      BigDecimal totalWeightOrQuantityInBigDec = BigDecimal.ZERO;
      for (PaymentTransactionRec r : relevantTransactions) {
        if (SchemeParticipantType.CRP.name().equals(schemeParticipantType)) {
          if (!r.getPaymentType().equals(PaymentTxnType.COLLECTION_FEES.name())) {
            continue;
          }
        }
        totalWeightOrQuantityInBigDec = totalWeightOrQuantityInBigDec.add(r.getVolume());
      }
      final double totalWeightOrQuantity = round(BigDecimalUtility.asDouble(totalWeightOrQuantityInBigDec));

      final String paymentDate = relevantTransactions.stream().findFirst().get().getPaymentTimestamp().toString();

      BigDecimal totalGrossAmountInBigDec = BigDecimal.ZERO;
      for (PaymentTransactionRec r : relevantTransactions) {
        totalGrossAmountInBigDec = totalGrossAmountInBigDec.add(r.getGrossAmount());
      }
      final double totalGrossAmount = round(BigDecimalUtility.asDouble(totalGrossAmountInBigDec));

      BigDecimal totalTaxableAmountInBigDec = BigDecimal.ZERO;
      for (PaymentTransactionRec r : relevantTransactions) {
        totalTaxableAmountInBigDec = totalTaxableAmountInBigDec.add(r.getTaxableAmount());
      }
      final double totalTaxableAmount = round(BigDecimalUtility.asDouble(totalTaxableAmountInBigDec));

      BigDecimal totalGSTAmountInBigDec = BigDecimal.ZERO;
      for (PaymentTransactionRec r : relevantTransactions) {
        totalGSTAmountInBigDec = totalGSTAmountInBigDec.add(r.getGstAmount());
      }
      final double totalGSTAmount = round(BigDecimalUtility.asDouble(totalGSTAmountInBigDec));

      String overallStatus = derivePaymentStatus(relevantTransactions);

      schemeParticipantPayment.setPaymentState(overallStatus);

      final String unitOfMeasureCode = relevantTransactions.stream().findFirst().get().getUom();
      schemeParticipantPayment.setUnitOfMeasureCode(unitOfMeasureCode);

      schemeParticipantPayment.setTotalWeightOrQuantity(totalWeightOrQuantity);
      schemeParticipantPayment.setPaymentDate(paymentDate);
      schemeParticipantPayment.setTotalGrossAmount(totalGrossAmount);
      schemeParticipantPayment.setTotalTaxableAmount(totalTaxableAmount);
      schemeParticipantPayment.setTotalGSTAmount(totalGSTAmount);

      final Map<String, List<PaymentTransactionRec>> recordsGroupedByPaymentType = relevantTransactions.stream()
          .collect(Collectors.groupingBy(PaymentTransactionRec::getPaymentType));
      final Set<Entry<String, List<PaymentTransactionRec>>> entries = recordsGroupedByPaymentType.entrySet();
      for (final Entry<String, List<PaymentTransactionRec>> entry : entries) {
        final SchemeParticipantPaymentTransaction schemeParticipantPaymentTransaction = new SchemeParticipantPaymentTransaction();
        final String paymentType = entry.getKey();
        final List<PaymentTransactionRec> records = entry.getValue();
        final String paymentState = derivePaymentStatus(records);

        BigDecimal paymentGrossAmountInBigDec = BigDecimal.ZERO;
        for (PaymentTransactionRec r : records) {
          paymentGrossAmountInBigDec = paymentGrossAmountInBigDec.add(r.getGrossAmount());
        }
        final double paymentGrossAmount = round(BigDecimalUtility.asDouble(paymentGrossAmountInBigDec));

        BigDecimal paymentTaxableAmountInBigDec = BigDecimal.ZERO;
        for (PaymentTransactionRec r : records) {
          paymentTaxableAmountInBigDec = paymentTaxableAmountInBigDec.add(r.getTaxableAmount());
        }
        final double paymentTaxableAmount = round(BigDecimalUtility.asDouble(paymentTaxableAmountInBigDec));

        BigDecimal paymentGSTAmountInBigDec = BigDecimal.ZERO;
        for (PaymentTransactionRec r : records) {
          paymentGSTAmountInBigDec = paymentGSTAmountInBigDec.add(r.getGstAmount());
        }
        final double paymentGSTAmount = round(BigDecimalUtility.asDouble(paymentGSTAmountInBigDec));

        schemeParticipantPaymentTransaction.setPaymentType(paymentType);
        schemeParticipantPaymentTransaction.setPaymentState(paymentState);
        schemeParticipantPaymentTransaction.setPaymentGrossAmount(paymentGrossAmount);
        schemeParticipantPaymentTransaction.setPaymentTaxableAmount(paymentTaxableAmount);
        schemeParticipantPaymentTransaction.setPaymentGSTAmount(paymentGSTAmount);
        schemeParticipantPayment.getSchemeParticipantPaymentTransactions().add(schemeParticipantPaymentTransaction);
      }
      getSchemeParticipantPayments().add(schemeParticipantPayment);
    }

    return this;
  }

  private String derivePaymentStatus(List<PaymentTransactionRec> records) {

    if (schemeParticipantType.equals(SchemeParticipantType.CONSUMER.name())) {
      List<PaymentTransactionRec.PaymentStatus> combinedStatus = records.stream().map(t -> t.getStatus()).collect(Collectors.toList());
      if (combinedStatus.contains(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW)) {
        return PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW.name();
      }
    }
    return records.stream().findFirst().get().getStatus().name();

  }

  private PaymentAggregateView withPaymentPeriod() {

    paymentPeriod = recordsArg.stream().findFirst().get().getPaymentPeriod();

    return this;
  }

  private boolean shouldConsiderPaymentType(String paymentType) {

    return !(schemeParticipantTypeArg == SchemeParticipantType.CRP && !paymentType.equals(PaymentTxnType.COLLECTION_FEES.name()));
  }

  private PaymentAggregateView withTotalAmount() {

    totalAmount = round(getSchemeParticipantPayments().stream().mapToDouble(p -> p.totalGrossAmount).sum());
    return this;
  }

  private PaymentAggregateView withSchemeParticipantType() {

    schemeParticipantType = schemeParticipantTypeArg.toString();
    return this;
  }

  /**
   * member classes
   */
  @Getter
  @Setter
  public class SchemeParticipantPayment {

    private String id, name, unitOfMeasureCode, paymentDate, paymentState;

    private double totalWeightOrQuantity, totalGrossAmount, totalTaxableAmount, totalGSTAmount;

    private List<SchemeParticipantPaymentTransaction> schemeParticipantPaymentTransactions = new ArrayList<>();

  }

  @Getter
  @Setter
  public class SchemeParticipantPaymentTransaction {

    String paymentType, paymentState;

    double paymentGrossAmount, paymentTaxableAmount, paymentGSTAmount;

  }

  private double round(double value) {

    return Math.round(value * 100d) / 100d;
  }

}
