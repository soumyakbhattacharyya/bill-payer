package com.serviceco.coex.payment.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.serviceco.coex.payment.service.PaymentTransactionHelperService;
import com.serviceco.coex.rest.annotation.NoRbac;
import com.serviceco.coex.rest.annotation.ResourceType;
import com.serviceco.coex.rest.support.ResourceConstants;

/**
 * A REST web service for payment "pre-calculation" operations. There are actually no web service methods defined for this service, so it does nothing.
 *
 */
@Component
@Path(ResourceConstants.URLS.SET_UP_CALCULATION_PRECONDITION)
@Produces("application/json")
@Consumes("application/json")
@NoRbac
@ResourceType("PAYMENT.CALCULATION_PRE_CONDITION")
@Deprecated
public class PaymentCalculationPreconditionResource {

  @Autowired
  PaymentTransactionHelperService helper;
//
//  @POST
//  @Path("/register-expected-sales-volumes")
//  @ActionType("TEMP_PERSIST_SALES_VOLUME")
//  public List<ForecastedSalesVolumeDTO> saveExpectedSalesVolumeTxt(List<ForecastedSalesVolumeDTO> dtos) {
//    return helper.save(dtos);
//  }

//  @POST
//  @Path("/register-seasonality-index")
//  @ActionType("TEMP_PERSIST_SEASONALITY_INDEX")
//  public List<SeasonalityIndexDTO> populateRandomSeasonalityIndex(List<SeasonalityIndexDTO> indices) {
//    return helper.populateRandomSeasonalityIndecies(indices);
//  }

//  @POST
//  @Path("/register-scheme-price")
//  @ActionType("TEMP_POPULATE_RANDOM_SCHEME_PRICE")
//  public List<SchemePriceReference> populateRandomSchemePrice(List<PaymentTransactionRec.SchemePricePopulationRequest> requests) {
//    return helper.populateRandomSchemePrice(requests);
//  }

}
