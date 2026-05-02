package com.agentdesk.backend.rag;

import com.agentdesk.backend.common.error.BusinessException;
import com.agentdesk.backend.common.error.ErrorCode;
import com.agentdesk.backend.config.InfrastructureProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
@Profile("dev")
class MinioRagDocumentStorage implements RagDocumentStorage {

    private final MinioClient minioClient;
    private final InfrastructureProperties properties;

    MinioRagDocumentStorage(InfrastructureProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.minio().endpoint())
                .credentials(properties.minio().accessKey(), properties.minio().secretKey())
                .build();
    }

    @Override
    public StoredObject store(StoreCommand command) throws IOException {
        String bucket = properties.minio().bucket();
        String objectKey = objectKey(command);
        try {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(command.content()), command.content().length, -1)
                    .contentType(command.mimeType())
                    .build());
            return new StoredObject("s3://" + bucket + "/" + objectKey);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to store RAG source file.");
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String objectKey(StoreCommand command) {
        return "rag/%s/%s/%s/%s".formatted(
                command.projectId(),
                command.threadId(),
                command.documentId(),
                command.sourceName()
        );
    }
}
