package com.example.academicprofile.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

import com.example.academicprofile.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class PdfConversionClientLocalMockTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void disabledFeatureReturnsServiceUnavailableWithoutRequest() {
        PdfConversionProperties properties = new PdfConversionProperties();
        PdfConversionClient client = new PdfConversionClient(properties);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> client.convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("PDF 转换服务未启用", exception.getMessage());
    }

    @Test
    void sendsOneMultipartRequestAndReturnsValidatedPdf() throws Exception {
        byte[] pdf = pdfWithText("confirmed content");
        AtomicInteger requests = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requests.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            sendKnownLength(exchange, 200, pdf);
        });

        byte[] result = enabledClient(defaultProperties()).convertDocx(new byte[] { 1, 2, 3 }, 42L);

        assertArrayEquals(pdf, result);
        assertEquals(1, requests.get());
        assertTrue(requestBody.get().contains("name=\"files\""));
        assertTrue(requestBody.get().contains("filename=\"ai-task-42.docx\""));
    }

    @Test
    void acceptsPdfContentTypeWithParameters() throws Exception {
        byte[] pdf = pdfWithText("confirmed content");
        startServer(exchange -> sendKnownLength(exchange, 200, pdf, "application/pdf; charset=UTF-8"));

        byte[] result = enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L);

        assertArrayEquals(pdf, result);
    }

    @Test
    void missingContentTypeIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, pdfWithText("confirmed content"), null));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("non-PDF response"));
    }

    @Test
    void nonPdfContentTypeIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(
                exchange,
                200,
                pdfWithText("confirmed content"),
                "text/plain; charset=UTF-8"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("non-PDF response"));
    }

    @Test
    void knownContentLengthOverLimitIsRejectedBeforeBodyRead() throws Exception {
        startServer(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 32);
            exchange.close();
        });
        PdfConversionProperties properties = defaultProperties();
        properties.setMaxOutputSize(DataSize.ofBytes(16));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(properties).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("size limit"));
    }

    @Test
    void unknownContentLengthOverLimitStopsStreamingRead() throws Exception {
        startServer(exchange -> {
            byte[] response = new byte[32];
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        PdfConversionProperties properties = defaultProperties();
        properties.setMaxOutputSize(DataSize.ofBytes(16));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(properties).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("size limit"));
    }

    @Test
    void upstreamFailureIsBadGatewayAndIsNotRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        startServer(exchange -> {
            requests.incrementAndGet();
            sendKnownLength(
                    exchange,
                    500,
                    "short upstream error".getBytes(StandardCharsets.UTF_8),
                    "text/plain; charset=UTF-8");
        });

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals(1, requests.get());
        assertTrue(exception.getMessage().contains("short upstream error"));
    }

    @Test
    void longUpstreamErrorIsReadWithinLimitAndSummaryIsTruncated() throws Exception {
        byte[] response = ("x".repeat(10_000) + "unread-tail").getBytes(StandardCharsets.UTF_8);
        startServer(exchange -> sendKnownLength(exchange, 500, response, "text/plain; charset=UTF-8"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        String prefix = "PDF conversion request failed with HTTP 500: ";
        assertTrue(exception.getMessage().startsWith(prefix));
        String summary = exception.getMessage().substring(prefix.length());
        assertEquals(300, summary.length());
        assertEquals("x".repeat(300), summary);
    }

    @Test
    void upstreamErrorSummaryRemovesLineBreaksAndControlCharacters() throws Exception {
        byte[] response = "line1\r\nline2\u0000\u0007end".getBytes(StandardCharsets.UTF_8);
        startServer(exchange -> sendKnownLength(exchange, 500, response, "text/plain; charset=UTF-8"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertTrue(exception.getMessage().endsWith("line1 line2 end"));
        assertTrue(exception.getMessage().chars().noneMatch(Character::isISOControl));
    }

    @Test
    void upstreamUnavailableIsServiceUnavailable() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 503, new byte[0]));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void callTimeoutIsGatewayTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(1500);
                sendKnownLength(exchange, 200, pdfWithText("late response"));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        PdfConversionProperties properties = defaultProperties();
        properties.setTimeoutSeconds(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(properties).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void emptyResponseIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, new byte[0]));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void damagedPdfIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, "%PDF-damaged".getBytes(StandardCharsets.US_ASCII)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void nonPdfHeaderIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, "not-a-pdf".getBytes(StandardCharsets.US_ASCII)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("invalid PDF"));
    }

    @Test
    void zeroPagePdfIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, zeroPagePdf()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("no pages"));
    }

    @Test
    void encryptedPdfIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, encryptedPdf()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void blankPdfIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, blankPdf()));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("extractable text"));
    }

    @Test
    void unresolvedTemplateTagIsRejected() throws Exception {
        startServer(exchange -> sendKnownLength(exchange, 200, pdfWithText("{{otherTag}}")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> enabledClient(defaultProperties()).convertDocx(new byte[] { 1 }, 1L));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertTrue(exception.getMessage().contains("unresolved template tags"));
    }

    private PdfConversionProperties defaultProperties() {
        PdfConversionProperties properties = new PdfConversionProperties();
        properties.setEnabled(true);
        properties.setConnectTimeoutSeconds(1);
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private PdfConversionClient enabledClient(PdfConversionProperties properties) {
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return new PdfConversionClient(properties);
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forms/libreoffice/convert", handler);
        server.start();
    }

    private void sendKnownLength(HttpExchange exchange, int status, byte[] body) throws IOException {
        sendKnownLength(exchange, status, body, "application/pdf");
    }

    private void sendKnownLength(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    private byte[] blankPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] zeroPagePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] encryptedPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password",
                    "user-password",
                    new AccessPermission());
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
