package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class ProductAuditE2ETest extends BaseE2ETest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void adminCanApproveE2EProduct() throws Exception {
        String productTitle = preparePendingProduct();
        loginWithPreset(2, "admin", "admin123");
        openPage("pages/admin/audit");

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "审核管理"));
        WebElement targetCard = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".card"))
                .stream()
                .filter(card -> card.getText().contains(productTitle))
                .findFirst()
                .orElse(null));
        saveScreenshot("EV-E2E-PRODUCT-AUDIT-BEFORE-01");

        WebElement approve = targetCard.findElements(By.cssSelector(".actions > *")).get(0);
        clickUniElement(approve);
        wait.until(ExpectedConditions.stalenessOf(targetCard));

        assertFalse(driver.findElements(By.cssSelector(".name"))
                .stream()
                .anyMatch(name -> name.getText().trim().equals(productTitle)));
        saveScreenshot("EV-E2E-PRODUCT-AUDIT-AFTER-01");
    }

    private String preparePendingProduct() throws Exception {
        String apiBase = System.getProperty("e2e.apiUrl", "http://127.0.0.1:8080");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(apiBase + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"seller\",\"password\":\"seller123\"}"))
                .build();
        HttpResponse<String> loginResponse = client.send(
                loginRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode());
        Matcher matcher = TOKEN_PATTERN.matcher(loginResponse.body());
        assertTrue(matcher.find(), "卖家登录响应中没有 Token");

        String title = "E2E审核专用商品-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        String payload = "{"
                + "\"scene\":\"used\","
                + "\"title\":\"" + title + "\","
                + "\"image\":\"/static/logo.png\","
                + "\"category\":\"学习资料\","
                + "\"price\":9.90,"
                + "\"condition\":\"九成新\","
                + "\"description\":\"管理员审核端到端测试数据\","
                + "\"story\":\"测试数据\","
                + "\"floorPrice\":8.00,"
                + "\"location\":\"北京航空航天大学\"} ";
        HttpRequest publishRequest = HttpRequest.newBuilder(URI.create(apiBase + "/api/products"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + matcher.group(1))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> publishResponse = client.send(
                publishRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, publishResponse.statusCode());
        return title;
    }
}
