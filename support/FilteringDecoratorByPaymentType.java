package com.serviceco.coex.payment.support;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.serviceco.coex.payment.api.request.InvoicingRequest;
import com.serviceco.coex.payment.model.calculation.PaymentMetadata;
import com.serviceco.coex.payment.model.calculation.View;

/**
 * Filters payment transaction views by one or more payment types.
 * 
 * <p>See {@link #doFilter(InvoicingRequest, List)}</p>
 */
public class FilteringDecoratorByPaymentType extends FilteringDecoratorBySchemeParticipantId {
  
  protected List<PaymentMetadata> paymentMetadataList;

  /**
   * Constructs a filter which filters payment transaction views by one or more payment types
   * @param paymentMetadataList	The payment metadata to use to obtain all available payment types
   * @param filter Another filter to apply first before this one.
   */
  public FilteringDecoratorByPaymentType(List<PaymentMetadata> paymentMetadataList,Filter filter) {    
    super(filter);
    this.paymentMetadataList = paymentMetadataList;
  }

  /**
   * Filters payment transaction views by one or more payment types.
   * 
   * <p>If the request.paymentTransactionTypes contains the string "ALL", this will accept any payment types within the paymentMetadataList passed into the constructor.</p>
   * <p>If the request.paymentTransactionTypes does not contain "ALL" and the include flag is true, any payment transaction view which has a payment type that is NOT within request.paymentTransactionTypes will be filtered out.</p>
   * <p>If the request.paymentTransactionTypes does not contain "ALL" and the include flag is false, any payment transaction view which has a payment type that IS within request.paymentTransactionTypes will be filtered out.</p>
   * 
   * @param request The request containing the payment types to filter by.
   * @param request.paymentTransactionTypes A list of payment types to filter by, or "ALL" to allow all payment types.
   * @param request.include If true, the payment transaction types passed in will be allowed and everything else excluded. If false, the payment transaction types passed in will be excluded.
   * @param decorable The payment transaction views to filter.    
   * 
   */
  @Override
  public List<View> doFilter(InvoicingRequest request, List<View> decorable) {

    List<View> payments = super.doFilter(request, decorable);
    final List<String> argPaymentTransactionTypes = request.getPaymentTransactionTypes();

    if ((null != argPaymentTransactionTypes) && !argPaymentTransactionTypes.isEmpty()) {
      if (argPaymentTransactionTypes.contains("ALL")) {
        final List<String> paymentTypes = paymentMetadataList.stream().map( item -> {return item.getTransactionType();}).collect(Collectors.toList());
        payments = decorable.stream().filter(new Predicate<View>() {

          @Override
          public boolean test(View t) {
            
            return paymentTypes.contains(t.getPaymentType());
          }
        }).collect(Collectors.toList());
      } else {
        if (request.isInclude()) {
          payments = decorable.stream().filter(new Predicate<View>() {

            @Override
            public boolean test(View t) {
              return request.getPaymentTransactionTypes().contains(t.getPaymentType());
            }
          }).collect(Collectors.toList());
        } else {
          payments = decorable.stream().filter(new Predicate<View>() {

            @Override
            public boolean test(View t) {
              return !request.getPaymentTransactionTypes().contains(t.getPaymentType());
            }
          }).collect(Collectors.toList());

        }
      }
    }

    return payments;

  }

}
