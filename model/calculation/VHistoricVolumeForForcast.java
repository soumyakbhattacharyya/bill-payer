package com.serviceco.coex.payment.model.calculation;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.annotations.Immutable;

import com.serviceco.coex.model.Model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity(name = "V_HISTORIC_VOL_FOR_FORCAST")
@Setter
@Getter
@Immutable
@ToString
public class VHistoricVolumeForForcast implements Model {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @Column(name = "SCHEME_PARTICIPANT_TYPE")
  private String schemeParticipantType;

  @Column(name = "SCHEME_PARTICIPANT_ID")
  private String schemeParticipantId;

  private String name;

  @Column(name = "MATERIAL_TYPE_ID")
  private String materialTypeId;

  @Column(name = "MATERIAL_NAME")
  private String materialName;

  private String period;

  @Column(name = "PERIOD_TYPE")
  private String periodType;

  @Column(name = "ENTRY_TYPE")
  private String entryType;

  @Column(name = "SUBMITTED_BY")
  private String submittedBy;

  @Column(name = "SUBMITTED_DATE")
  private String submittedDate;

  @Column(name = "SALES_VOLUME")
  private String salesVolume;
  private String uom;

  @Column(name = "VOLUME_HDR_ID")
  private String volumeHdrId;
  
  @Column(name = "MULTI_SCHEME_ID")
  private Long multiSchemeId;

}
