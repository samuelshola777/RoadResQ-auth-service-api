package com.roadresq.auth.utils.fileupload.response;


import com.roadresq.auth.utils.fileupload.FileUploadCategory;
import com.roadresq.auth.utils.fileupload.FileUsageType;

public record FileUploadResponse(
        String fileUrl,
        String publicId,
        String originalFileName,
        String format,
        String resourceType,
        Long fileSizeBytes,
        FileUploadCategory category,
        FileUsageType usageType
) {
}