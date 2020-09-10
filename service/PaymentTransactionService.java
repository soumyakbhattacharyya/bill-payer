package com.serviceco.coex.payment.service;

import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.masterdata.model.MaterialType;
import com.serviceco.coex.masterdata.model.SchemePriceReference;
import com.serviceco.coex.model.Scheme;
import com.serviceco.coex.model.constant.PaymentTransactionType;
import com.serviceco.coex.model.dto.Period;
import com.serviceco.coex.payment.model.calculation.PaymentBatch;
import com.serviceco.coex.payment.model.calculation.PaymentTransactionRec;
import com.serviceco.coex.payment.repository.PaymentTransactionRecRepository;
import com.serviceco.coex.scheme.participant.model.MdtParticipant;
import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@Transactional
public class PaymentTransactionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PaymentTransactionService.class);

  @Autowired
  private PaymentTransactionRecRepository paymentTransactionRepository;

  //@formatter:off
  public PaymentTransactionRec createPaymentTransaction(final PaymentBatch batch
                                                      , final Period period
                                                      , Period paymentPeriod
                                                      , final MdtParticipantSite schemeParticipant
                                                      , final MaterialType materialType
                                                      , final BigDecimal totalVolume
                                                      , final SchemePriceReference schemePrice
                                                      , final BigDecimal grossAmount
                                                      , final BigDecimal taxableAmount
                                                      , final BigDecimal gstAmount
                                                      , Scheme scheme) {
    final PaymentTransactionRec paymentTransactionRec = new PaymentTransactionRec();
    paymentTransactionRec.setId(UUID.randomUUID().toString());
    paymentTransactionRec.setPaymentType(PaymentTransactionType.SCHEME_PRICE.getDescription());
    paymentTransactionRec.setPaymentBatch(batch);
    paymentTransactionRec.setSchemeParticipantId(schemeParticipant.getSiteNumber());
    MdtParticipant participant = schemeParticipant.getParticipant();
    paymentTransactionRec.setSchemeParticipantName(participant.getParticipantName());
    paymentTransactionRec.setSchemeParticipantType(schemeParticipant.getSiteType());
    paymentTransactionRec.setUnitSellingPrice(schemePrice.getSchemePrice());
    paymentTransactionRec.setMaterialType(materialType);
    paymentTransactionRec.setPaymentPeriod(paymentPeriod.toString());
    paymentTransactionRec.setPeriodType(period.getType().name());
    paymentTransactionRec.setPeriod(period.getValue());
    paymentTransactionRec.setEntryType(EntryType.R.toString());
    paymentTransactionRec.setVolumeHdrEntryType(EntryType.F.toString());
    paymentTransactionRec.setArrear("N");
    paymentTransactionRec.setGrossAmount(grossAmount);
    paymentTransactionRec.setTaxableAmount(taxableAmount);
    paymentTransactionRec.setGstAmount(gstAmount);
    paymentTransactionRec.setVolume(totalVolume);
    paymentTransactionRec.setLineType("ITEM");
    paymentTransactionRec.setUom("EA"); // TODO : confirm with team, valid values for UOM
    paymentTransactionRec.setPaymentTimestamp(batch.getStartTimeStamp());
    paymentTransactionRec.setStatus(PaymentTransactionRec.PaymentStatus.AWAITING_REVIEW);
    paymentTransactionRec.setScheme(scheme);

    paymentTransactionRepository.save(paymentTransactionRec);
    //@formatter:off
    LOGGER.info("successfully created payment transaction record - scheme participant {}, material type {}, period {}, amount {}"
    , schemeParticipant.getSiteNumber()
    , materialType.getId()
    , period
    , grossAmount);    
    //@formatter:on
    return paymentTransactionRec;
  }
  //@formatter:on
}
