package com.example.academicprofile.file;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.academicprofile.access.ResourceAccessAuditService;
import com.example.academicprofile.security.CurrentUser;

class FileControllerStreamingAuditTest {
    private FileStorageService files;
    private ResourceAccessAuditService audits;
    private FileController controller;

    @BeforeEach
    void setUp() {
        files = mock(FileStorageService.class);
        audits = mock(ResourceAccessAuditService.class);
        controller = new FileController(files, audits);
        CurrentUser actor = new CurrentUser(
                1L, 1L, "teacher", "teacher@example.test", "TEACHER", 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulCopyIsAuditedOnlyAfterBodyCompletes() throws Exception {
        when(files.readFile(any(), eq(9L), any())).thenReturn(authorized());
        var response = controller.previewFile(9L, new MockHttpServletRequest());

        response.getBody().writeTo(OutputStream.nullOutputStream());

        verify(audits).record(any(), eq(1L), eq("PREVIEW"), any(), eq(10L), eq(9L),
                any(), eq(true), any(), any());
    }

    @Test
    void interruptedCopyIsAuditedAsFailure() {
        when(files.readFile(any(), eq(9L), any())).thenReturn(authorized());
        var response = controller.downloadFile(9L, new MockHttpServletRequest());
        OutputStream interrupted = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("synthetic disconnect");
            }
        };

        assertThatThrownBy(() -> response.getBody().writeTo(interrupted))
                .isInstanceOf(IOException.class);
        verify(audits).record(any(), eq(1L), eq("DOWNLOAD"), any(), eq(10L), eq(9L),
                any(), eq(false), any(), any());
    }

    private FileStorageService.AuthorizedFileDownload authorized() {
        FileMetadata metadata = new FileMetadata();
        metadata.setId(9L);
        metadata.setTeacherId(1L);
        metadata.setBusinessType(FileBusinessType.PAPER.name());
        metadata.setBusinessId(10L);
        metadata.setOriginalName("synthetic.pdf");
        metadata.setMimeType("application/pdf");
        FileDownload download = new FileDownload(
                metadata, new ByteArrayResource("synthetic".getBytes()));
        return new FileStorageService.AuthorizedFileDownload(download, null);
    }
}
