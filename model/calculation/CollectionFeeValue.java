package com.serviceco.coex.payment.model.calculation;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CollectionFeeValue {

  private BigDecimal grossAmountSum;

  private final BigDecimal taxableAmountSum = BigDecimal.ZERO;

  private final BigDecimal gstAmountSum = BigDecimal.ZERO;

  private BigDecimal volume;
  
  // private String paymentMethod;

}
