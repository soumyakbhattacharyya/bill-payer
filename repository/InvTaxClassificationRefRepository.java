package com.serviceco.coex.payment.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.serviceco.coex.payment.model.invoice.reference.InvTaxClassificationRef;

public interface InvTaxClassificationRefRepository extends JpaRepository<InvTaxClassificationRef, String> {
	
	List<InvTaxClassificationRef> findBySchemeParticipantSiteNumber(String schemeParticipantSiteNumber);

}
