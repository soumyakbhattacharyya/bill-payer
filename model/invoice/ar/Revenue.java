package com.serviceco.coex.payment.model.invoice.ar;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Revenue implements Serializable {
  private static final long serialVersionUID = 4955520779492782536L;
  private String entity;
  private String materialType;
  private String costCentre;
  private String naturalAcc;
  private String interCoAcc;
  private String spare;
}
