package com.serviceco.coex.payment.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serviceco.coex.scheme.participant.model.MdtParticipantSite;

import lombok.Builder;

/**
 * Determines which scheme participants should be looked at during payment processing based on the parameters provided
 *
 */
@Builder
public class PartitionSupport {

  private static final Logger logger = LoggerFactory.getLogger(PartitionSupport.class);

  final List<MdtParticipantSite> allSchemeParticipants;
  final List<MdtParticipantSite> schemeParticipants;
  final Optional<Boolean> include;

  /**
   * Determines which scheme participants should be looked at during processing.
   * The list of scheme participants is either: 
   * (a) All scheme participants in the schemeParticipants list (if include = true or  null)
   * (b) All scheme participants in the allSchemeParticipants list excluding those in schemeParticipants (if include = false)
   * (c) All scheme participants in allSchemeParticipants (if schemeParticipants is null or empty)
   * @return
   */
  public List<MdtParticipantSite> partition() {
    List<MdtParticipantSite> schemeParticipantsToReturn = null;
    Boolean strictIncludeMode = false;
    // partition
    if ((schemeParticipants != null) && !schemeParticipants.isEmpty()) {
      strictIncludeMode = include.get() != null ? include.get() : Boolean.TRUE;
      if (strictIncludeMode) {
        logger.info("executing in strict include mode {}", strictIncludeMode);
        logger.info("following scheme participants will be included for processing  {}", schemeParticipants);
        schemeParticipantsToReturn = copy(schemeParticipants);
      } else {
        logger.info("executing in strict exclude mode {}", strictIncludeMode);
        logger.info("following scheme participants will be excluded for processing  {}", schemeParticipants);
        final boolean removeExcludedSchemeParticipants = allSchemeParticipants.removeAll(schemeParticipants);
        if (removeExcludedSchemeParticipants) {
          logger.info("following scheme participants will be excluded for processing  {}", allSchemeParticipants);
        }
        schemeParticipantsToReturn = copy(allSchemeParticipants);
      }
    } else {
      schemeParticipantsToReturn = copy(allSchemeParticipants);
    }

    return schemeParticipantsToReturn;
  }

  /**
   * Returns a new list containing all of the elements in the list passed in
   */
  private List<MdtParticipantSite> copy(List<MdtParticipantSite> allParticipants) {
    return new ArrayList<MdtParticipantSite>(allParticipants);
  }
}
