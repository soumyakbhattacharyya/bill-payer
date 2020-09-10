package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

import com.serviceco.coex.exporter.model.dto.EntryType;
import com.serviceco.coex.model.Model;
import com.serviceco.coex.model.constant.PeriodType;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "V_EXPORTER_PAYMENT_TXN")
@Getter
@Setter
public class VExporterPaymentTxn implements Model {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "ID")
  private String id;

  @Column(name = "EXPORTER_ID", length = 200)
  private String exporterId;

  @Column(name = "EXPORTER_NAME")
  private String exporterName;

  @Column(name = "MATERIAL_TYPE_ID", nullable = false, length = 50)
  private String materialTypeId;

  @Column(name = "PERIOD")
  private String period;

  @Column(name = "PERIOD_TYPE")
  @Enumerated(EnumType.STRING)
  private PeriodType periodType;

  @Column(name = "VOLUME")
  private BigDecimal volume;

  @Column(name = "ENTRY_TYPE")
  @Enumerated(EnumType.STRING)
  private EntryType entryType;

  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;
}
