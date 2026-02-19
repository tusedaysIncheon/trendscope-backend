package com.trendscope.backend.global.s3.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlRequestDTO {

    private String filename;
    private String folder;

}
