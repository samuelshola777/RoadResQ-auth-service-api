package com.roadresq.auth.provider;


import com.roadresq.auth.utils.BaseEntity;
import lombok.*;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("providers")
public class Provider extends BaseEntity {
    public enum ServiceType {
        TOWING,
        FUEL_DELIVERY,
        BATTERY_JUMP_START
    }

    @Indexed(unique = true)
    private String userId;

    private String businessName, equipment;
    private Set<ServiceType> services = new HashSet<>();
    private Set<String> coverageAreas = new HashSet<>();
    private String eligibility = "PENDING";
}
