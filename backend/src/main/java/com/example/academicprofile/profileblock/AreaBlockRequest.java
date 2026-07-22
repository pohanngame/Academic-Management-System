package com.example.academicprofile.profileblock;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AreaBlockRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 5000) String description,
        Integer sortOrder,
        Boolean isPublic) {
}
