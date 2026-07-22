package com.example.academicprofile.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.academicprofile.common.exception.BusinessException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Component
public class PdfConversionClient {

    private static final int MAX_ERROR_RESPONSE_BYTES = 4096;
    private static final int MAX_ERROR_SUMMARY_CHARS = 300;
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parse(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private static final Pattern UNRESOLVED_TAG = Pattern.compile("\\{\\{[^{}]+}}", Pattern.DOTALL);
    private static final byte[] PDF_HEADER = new byte[] { '%', 'P', 'D', 'F', '-' };

    private final PdfConversionProperties properties;
    private final OkHttpClient client;

    public PdfConversionClient(PdfConversionProperties properties) {
        this.properties = properties;
        this.client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .callTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    public byte[] convertDocx(byte[] docxContent, Long taskId) {
        validateEnabled();
        validateInput(docxContent);

        RequestBody fileBody = RequestBody.create(docxContent, DOCX_MEDIA_TYPE);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("files", taskFileName(taskId), fileBody)
                .build();
        Request request;
        try {
            request = new Request.Builder()
                    .url(normalizedBaseUrl() + "/forms/libreoffice/convert")
                    .post(body)
                    .build();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF conversion service URL is invalid");
        }

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw upstreamFailure(response.code(), errorSummary(response.body()));
            }
            validateContentType(response.body());
            byte[] pdfContent = readBounded(response.body());
            validatePdf(pdfContent);
            return pdfContent;
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedIOException ex) {
            throw new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "PDF conversion service timed out");
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PDF conversion service is unavailable");
        }
    }

    private void validateEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "PDF 转换服务未启用");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF conversion service URL is not configured");
        }
    }

    private void validateInput(byte[] docxContent) {
        if (docxContent == null || docxContent.length == 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Generated Word file is empty");
        }
        if (docxContent.length > properties.getMaxInputSize().toBytes()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Generated Word file exceeds PDF conversion limit");
        }
    }

    private void validateContentType(ResponseBody responseBody) {
        MediaType contentType = responseBody == null ? null : responseBody.contentType();
        if (contentType == null
                || !"application".equalsIgnoreCase(contentType.type())
                || !"pdf".equalsIgnoreCase(contentType.subtype())) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "PDF conversion service returned a non-PDF response");
        }
    }

    private byte[] readBounded(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PDF conversion service returned an empty response");
        }
        long maxBytes = properties.getMaxOutputSize().toBytes();
        long contentLength = responseBody.contentLength();
        if (contentLength > maxBytes) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF exceeds the response size limit");
        }

        int initialSize = contentLength > 0 && contentLength <= Integer.MAX_VALUE ? (int) contentLength : 8192;
        try (InputStream input = responseBody.byteStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream(initialSize)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessException(HttpStatus.BAD_GATEWAY,
                            "Converted PDF exceeds the response size limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void validatePdf(byte[] pdfContent) {
        if (pdfContent.length == 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PDF conversion service returned an empty file");
        }
        if (pdfContent.length > properties.getMaxOutputSize().toBytes()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF exceeds the response size limit");
        }
        if (!hasPdfHeader(pdfContent)) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PDF conversion service returned an invalid PDF file");
        }
        try (PDDocument document = Loader.loadPDF(pdfContent)) {
            if (document.isEncrypted()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF must not be encrypted");
            }
            if (document.getNumberOfPages() <= 0) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF has no pages");
            }
            String text = new PDFTextStripper().getText(document);
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF contains no extractable text");
            }
            if (text.contains("{{aiContent}}") || UNRESOLVED_TAG.matcher(text).find()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Converted PDF contains unresolved template tags");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "PDF conversion service returned a damaged PDF file");
        }
    }

    private boolean hasPdfHeader(byte[] content) {
        if (content.length < PDF_HEADER.length) {
            return false;
        }
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (content[index] != PDF_HEADER[index]) {
                return false;
            }
        }
        return true;
    }

    private String errorSummary(ResponseBody responseBody) {
        if (responseBody == null) {
            return "";
        }
        try (InputStream input = responseBody.byteStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[512];
            int remaining = MAX_ERROR_RESPONSE_BYTES;
            while (remaining > 0) {
                int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read == -1) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            String decoded = output.toString(StandardCharsets.UTF_8);
            StringBuilder sanitized = new StringBuilder(decoded.length());
            for (int index = 0; index < decoded.length(); index++) {
                char value = decoded.charAt(index);
                sanitized.append(Character.isISOControl(value) ? ' ' : value);
            }
            String summary = sanitized.toString()
                    .replaceAll("\\s{2,}", " ")
                    .replaceAll("(?i)bearer\\s+[A-Za-z0-9._-]+", "Bearer [redacted]")
                    .trim();
            return summary.length() <= MAX_ERROR_SUMMARY_CHARS
                    ? summary
                    : summary.substring(0, MAX_ERROR_SUMMARY_CHARS);
        } catch (IOException ex) {
            return "";
        }
    }

    private BusinessException upstreamFailure(int statusCode, String errorSummary) {
        String detail = StringUtils.hasText(errorSummary) ? ": " + errorSummary : "";
        if (statusCode == HttpStatus.GATEWAY_TIMEOUT.value()) {
            return new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "PDF conversion service timed out" + detail);
        }
        if (statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()
                || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PDF conversion service is busy or unavailable" + detail);
        }
        return new BusinessException(HttpStatus.BAD_GATEWAY,
                "PDF conversion request failed with HTTP " + statusCode + detail);
    }

    private String taskFileName(Long taskId) {
        if (taskId == null || taskId <= 0) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "AI task ID is invalid for PDF conversion");
        }
        return "ai-task-" + taskId + ".docx";
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
