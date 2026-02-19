package com.trendscope.backend.global.s3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponseDTO {

    private String presignedUrl;
    private String fileKey;

    public PresignedUrlResponseDTO(String presignedUrl, String fileKey, String folder) {
        this.presignedUrl = presignedUrl;
        this.fileKey = fileKey;
    }

}
