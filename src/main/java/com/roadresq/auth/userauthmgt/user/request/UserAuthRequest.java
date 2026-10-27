package com.roadresq.auth.userauthmgt.user.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roadresq.auth.utils.Gender;


public record UserAuthRequest(
        @JsonProperty(required = true) String emailAddress,
        @JsonProperty(required = true) String password,
        Gender gender
) {
}
