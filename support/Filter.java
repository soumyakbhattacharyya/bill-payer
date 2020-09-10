package com.serviceco.coex.payment.support;

import java.util.List;

import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.View;

public interface Filter {
  
  List<View> doFilter(InvoicingRequest request, List<View> decorable);

}
