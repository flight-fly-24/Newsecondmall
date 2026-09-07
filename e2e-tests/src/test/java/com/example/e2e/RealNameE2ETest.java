package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class RealNameE2ETest extends BaseE2ETest {

    @Test
    void buyerCanSubmitRealNameAndAdminCanApproveIt() throws Exception {
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        String username = "e2ebuyer" + suffix;
        String password = "e2ePass123";
        registerBuyer(username, password, "138" + suffix.substring(0, 8));

        loginWithCredentials(0, username, password);
        openPage("pages/user/index");
        WebElement realNameNav = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".side-item"))
                .stream()
                .filter(item -> item.getText().contains("实名认证"))
                .findFirst()
                .orElse(null));
        clickUniElement(realNameNav);

        List<WebElement> inputs = wait.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector(".realname-input input"), 2));
        setInputValue(inputs.get(0), "端测用户");
        setInputValue(inputs.get(1), "110101199001011234");
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".realname-panel .primary"))));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector(".realname-detail"), "待审核"));
        saveScreenshot("EV-E2E-REALNAME-SUBMITTED-01");

        ((JavascriptExecutor) driver).executeScript("window.localStorage.clear();");
        loginWithPreset(2, "admin", "admin123");
        openPage("pages/admin/dashboard");
        WebElement auditTab = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".role-nav-item"))
                .stream()
                .filter(tab -> tab.getText().contains("审核"))
                .findFirst()
                .orElse(null));
        clickUniElement(auditTab);

        WebElement auditCard = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".list-card"))
                .stream()
                .filter(card -> card.getText().contains(username))
                .findFirst()
                .orElse(null));
        saveScreenshot("EV-E2E-REALNAME-AUDIT-BEFORE-01");
        clickUniElement(auditCard.findElements(By.cssSelector(".inline-actions > *")).get(0));
        wait.until(ExpectedConditions.stalenessOf(auditCard));
        assertFalse(driver.getPageSource().contains(username));
        saveScreenshot("EV-E2E-REALNAME-AUDIT-AFTER-01");
    }

    private void registerBuyer(String username, String password, String phone) throws Exception {
        String apiBase = System.getProperty("e2e.apiUrl", "http://127.0.0.1:8080");
        String payload = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"phone\":\"" + phone + "\",\"role\":\"buyer\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
