package com.roadresq.auth.vendor;

public enum Eligibility {
    PENDING,        // Just registered, awaiting admin review
    APPROVED,       // Verified and can accept jobs
    REJECTED,       // Failed KYC/background check
    SUSPENDED,      // Temporarily banned (e.g., policy violation)
    EXPIRED
}
