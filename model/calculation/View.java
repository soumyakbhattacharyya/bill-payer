package com.serviceco.coex.payment.model.calculation;

import com.serviceco.coex.model.Model;

public interface View extends Model {

  public String getId();

  public String getSchemeParticipantId();

  public String getSchemeParticipantType();

  public String getPaymentTransactionId();

  public String getPaymentType();

  public String getStatus();

}
