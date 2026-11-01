package com.roadresq.auth.vendor;



import com.roadresq.auth.utils.BaseEntity;
import com.roadresq.auth.utils.Eligibility;
import lombok.*;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Version;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("vendors")
public class Vendor extends BaseEntity {

    @Indexed(unique = true)
    private String userId;
    private Eligibility eligibility = Eligibility.PENDING;
    private boolean active;
    private boolean deleted;
    private String reviewReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;
    @Version
    private Long version;
}