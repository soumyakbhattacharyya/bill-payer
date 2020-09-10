package com.serviceco.coex.payment.model.calculation;

import lombok.Data;

@Data
public class CollectionFeeKey {

  private String crpId;

  private String transactionWeek;

  private String materialTypeId;

}
