package com.roadresq.auth.vendor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VendorRepository extends MongoRepository<Vendor, String> {
    Optional<Vendor> findByIdAndDeletedFalse(String id);
    Optional<Vendor> findByUserIdAndDeletedFalse(String userId);
    Page<Vendor> findAllByDeletedFalse(Pageable pageable);
    Page<Vendor> findAllByDeletedFalseAndEligibility(String eligibility, Pageable pageable);
}