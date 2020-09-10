package com.serviceco.coex.payment.support;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.View;

/**
 * Filters the payment transaction views by the scheme participant IDs in the request.
 * 
 * See {@link #doFilter}
 *
 */
public class FilteringDecoratorBySchemeParticipantId extends FilteringDecoratorBySchemeParticipantType {

  /**
   * Constructs a filter which filters the payment transaction views by the scheme participant IDs in the request.
   * @param filter Another filter to apply first before this one
   */
  public FilteringDecoratorBySchemeParticipantId(Filter filter) {
    super(filter);
  }

  /**
   * Filters the payment transaction views by the scheme participant IDs in the request.
   * <p>If the request.schemeParticipantIds contains "ALL", all payment transaction views passed in will be returned without any filtering.</p>
   * <p>If the request.schemeParticipantIds does not contain "ALL" and the include flag is true, any payment transaction views which have a scheme participant ID which is NOT in the list will be filtered out.</p>
   * <p>If the request.schemeParticipantIds does not contain "ALL" and the include flag is false, any payment transaction views which have a scheme participant ID which IS in the list will be filtered out.</p>
   *
   * @param request The request object which contains the scheme participant IDs
   * @param request.schemeParticipantIds The IDs of the scheme participants to include or exclude. This may also contain "ALL" to include all of the participants.
   * @param request.include If true, the transaction views NOT matching the scheme participant IDs will be filtered out. If false, the transaction views matching the scheme participant IDs will be filtered out.
   *  
   */
  @Override
  public List<View> doFilter(InvoicingRequest request, List<View> decorable) {

    List<View> payments = super.doFilter(request, decorable);
    final List<String> schemeParticipantIds = request.getSchemeParticipantIds();

    if ((null != schemeParticipantIds) && !schemeParticipantIds.isEmpty()) {
      if (schemeParticipantIds.contains("ALL")) {
        return decorable;
      } else {
        if (request.isInclude()) {
          payments = decorable.stream().filter(new Predicate<View>() {

            @Override
            public boolean test(View t) {
              return request.getSchemeParticipantIds().contains(t.getSchemeParticipantId());
            }
          }).collect(Collectors.toList());
        } else {
          payments = decorable.stream().filter(new Predicate<View>() {

            @Override
            public boolean test(View t) {
              return !request.getSchemeParticipantIds().contains(t.getSchemeParticipantId());
            }
          }).collect(Collectors.toList());

        }
      }
    }

    return payments;

  }

}
