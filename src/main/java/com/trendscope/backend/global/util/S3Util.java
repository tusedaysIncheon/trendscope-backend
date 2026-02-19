package com.trendscope.backend.global.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Util {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * Presigned URL 발급 (프론트엔드가 이미지를 직접 업로드할 수 있는 5분짜리 티켓)
     *
     * @param originalFilename 원본 파일명 (예: myface.jpg)
     * @param folder           폴더명 (예: profile)
     * @return [0]: Presigned URL (업로드용), [1]: 실제 저장된 파일 경로 (DB 저장용)
     */

    public String[] getPresignedUrl(String originalFilename, String folder) {
        String filename = createObjectKey(folder, originalFilename);
        String url = createPresignedPutUrl(filename, Duration.ofMinutes(5));
        return new String[]{url, filename};
    }

    public String createObjectKey(String folder, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return folder + "/" + UUID.randomUUID() + "." + ext;
    }

    public String createPresignedPutUrl(String objectKey, Duration duration) {
        String ext = extractExtension(objectKey);
        String contentType = getContentType(ext);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        String url = presignedRequest.url().toString();

        log.info("presigned url : {}", url);
        return url;
    }

    public String createPresignedGetUrl(String objectKey, Duration duration) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        String url = presignedRequest.url().toString();
        log.info("presigned get url : {}", url);
        return url;
    }

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        s3Client.deleteObject(request);
        log.info("s3 object deleted: {}", objectKey);
    }

    public void uploadMultipartFile(String objectKey, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어 있습니다.");
        }

        String ext = extractExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = getContentType(ext);
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new IllegalArgumentException("파일 업로드 중 오류가 발생했습니다.", e);
        }
        log.info("s3 object uploaded: key={} size={}", objectKey, file.getSize());
    }

    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "bin";
        }
        int index = filename.lastIndexOf(".");
        if (index < 0 || index == filename.length() - 1) {
            return "bin";
        }
        return filename.substring(index + 1).toLowerCase();
    }

    private String getContentType(String ext) {
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "glb" -> "model/gltf-binary";
            default -> "application/octet-stream";
        };
    }


}
