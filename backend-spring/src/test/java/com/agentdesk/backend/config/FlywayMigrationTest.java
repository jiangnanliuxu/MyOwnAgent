package com.agentdesk.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {

    @Test
    void initialMigrationExistsAndDefinesCoreTables() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V1__init_schema.sql");

        assertThat(migration.exists()).isTrue();

        String sql = migration.getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("create extension if not exists pgcrypto");
        assertThat(sql).contains("jsonb");
        assertThat(sql).contains("create table users");
        assertThat(sql).contains("create table projects");
        assertThat(sql).contains("create table user_configs");
        assertThat(sql).contains("create table folders");
        assertThat(sql).contains("create table roles");
        assertThat(sql).contains("create table threads");
        assertThat(sql).contains("create table messages");
        assertThat(sql).contains("create table thread_roles");
        assertThat(sql).contains("create table skills");
        assertThat(sql).contains("create table mcp_endpoints");
        assertThat(sql).contains("create table mcp_health_checks");
        assertThat(sql).contains("create table rag_documents");
        assertThat(sql).contains("create table rag_chunks");
        assertThat(sql).contains("create table rag_index_jobs");
        assertThat(sql).contains("create table task_logs");
        assertThat(sql).contains("streamable_http");
    }

    @Test
    void authMigrationStoresCredentialAndRefreshTokenHashes() throws Exception {
        ClassPathResource migration = new ClassPathResource("db/migration/V2__auth_tokens.sql");

        assertThat(migration.exists()).isTrue();

        String sql = migration.getContentAsString(StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql).contains("create table user_auth_credentials");
        assertThat(sql).contains("password_hash text not null");
        assertThat(sql).contains("create table refresh_tokens");
        assertThat(sql).contains("token_hash text unique not null");
        assertThat(sql).doesNotContain("refresh_token text");
    }
}
