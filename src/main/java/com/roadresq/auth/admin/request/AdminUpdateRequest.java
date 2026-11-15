package com.roadresq.auth.admin.request;

import com.roadresq.auth.utils.Gender;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
public record AdminUpdateRequest(
        @Size(min = 1, max = 80) String firstName,
        @Size(min = 1, max = 80) String lastName,
        @Pattern(regexp = "\\+?[0-9]{7,15}") String phoneNumber,
        @Size(max = 500) String profilePictureUrl,
        @Past LocalDate dateOfBirth, Gender gender) {}