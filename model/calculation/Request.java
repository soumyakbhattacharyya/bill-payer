package com.serviceco.coex.payment.model.calculation;

import java.util.List;

import com.serviceco.coex.model.constant.SchemeParticipantType;

public class Request {
  public SchemeParticipantType schemeParticipantType;
  public List<String> schemeParticipantIds;
  public boolean include;

  public Request() {
  }
}