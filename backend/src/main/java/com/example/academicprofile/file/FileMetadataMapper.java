package com.example.academicprofile.file;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {
    @Select("""
            SELECT COUNT(*)
            FROM file_metadata
            WHERE ciphertext_status = 'ENCRYPTED' AND deleted = 0
            """)
    long countEncryptedAttachments();

    @Select("""
            SELECT DISTINCT encryption_key_id
            FROM file_metadata
            WHERE ciphertext_status = 'ENCRYPTED' AND deleted = 0
            """)
    List<String> selectEncryptionKeyIds();

    @Update("""
            UPDATE file_metadata
            SET ciphertext_status = 'MIGRATING'
            WHERE id = #{id} AND ciphertext_status = 'PLAINTEXT' AND deleted = 0
            """)
    int claimEncryptionMigration(Long id);

    @Update("""
            UPDATE file_metadata
            SET ciphertext_status = 'PLAINTEXT'
            WHERE id = #{id} AND ciphertext_status = 'MIGRATING'
            """)
    int releaseEncryptionMigration(Long id);

    @Update("""
            UPDATE file_metadata
            SET legacy_storage_path = NULL
            WHERE id = #{id} AND ciphertext_status = 'ENCRYPTED'
            """)
    int clearLegacyStoragePath(Long id);
}
