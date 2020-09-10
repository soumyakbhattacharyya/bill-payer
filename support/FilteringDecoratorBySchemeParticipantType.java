package com.serviceco.coex.payment.support;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.serviceco.coex.model.constant.SchemeParticipantType;
import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.View;

/**
 * Filters payment transaction views ({@link com.serviceco.coex.payment.model.calculation.View}) by the scheme participant type
 * provided in an {@link com.serviceco.coex.payment.api.request.InvoicingRequest} object.
 * 
 * See {@link #doFilter(InvoicingRequest, List)}
 *
 */
public class FilteringDecoratorBySchemeParticipantType implements Filter {

  protected Filter filter;

  /**
   * This version of the construction should not be used as the filter passed in will be ignored
   * @param filter
   */
  public FilteringDecoratorBySchemeParticipantType(Filter filter) {
    this.filter = filter;
  }

  /**
   * Constructs a filter which filters payment transaction views by the scheme participant type provided in a request.
   */
  public FilteringDecoratorBySchemeParticipantType() {
  }

  /**
   * <p>Filters the payment transaction views by the scheme participant type provided in a request.</p>
   * 
   * <p>
   * For large manufacturers, the request.schemeParticipantType.name is compared with the view's scheme participant type.<br>
   * For small manufacturers, the request.schemeParticipantType.supplierType is compared with the view's scheme participant type.<br>
   * For any other scheme participant type in the request, the return list will be null.
   * </p>
   * 
   * @param request The request which contains the scheme participant type
   * @param request.schemeParticipantType The scheme participant type to filter the payment transaction views by.
   * @param decorable The payment transaction views to filter.
   * @return Returns a list of payment transaction views which match the scheme participant type / supplier type if the request.schemeParticipantType is a large/small manufacturer, or null if the request.schemeParticipantType is not a manufacturer. 
   *  
   */
  @Override
  public List<View> doFilter(InvoicingRequest request, List<View> decorable) {

    final SchemeParticipantType schemeParticipantType = request.getSchemeParticipantType();
    List<View> filteredPayments = null;

    switch (schemeParticipantType) {
    case LRG_MANUFACTURER:

      filteredPayments = decorable.stream().filter(new Predicate<View>() {

        @Override
        public boolean test(View t) {
          return StringUtils.equals(t.getSchemeParticipantType(), schemeParticipantType.name());
        }
      }).collect(Collectors.toList());

      break;
    case SML_MANUFACTURER:

      filteredPayments = decorable.stream().filter(new Predicate<View>() {

        @Override
        public boolean test(View t) {
          return StringUtils.equals(t.getSchemeParticipantType(), schemeParticipantType.getSupplierType());
        }
      }).collect(Collectors.toList());
      ;

      break;

    default:
      break;
    }

    return filteredPayments;
  }

}
