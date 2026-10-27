package com.roadresq.auth.utils.fileupload;

import lombok.Builder;

@Builder
public record CldUrlParts(String cloud, String resource, String type,
                          Integer version, String publicId, String format) {
}
