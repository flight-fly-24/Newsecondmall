package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

class LoginE2ETest extends BaseE2ETest {

    @Test
    void buyerCanLoginAndCreateSession() {
        openPage("pages/auth/login");

        List<WebElement> inputs = wait.until(
                ExpectedConditions.numberOfElementsToBe(By.cssSelector(".input input"), 2));

        wait.until(ExpectedConditions.attributeToBe(inputs.get(0), "value", "demo"));
        wait.until(ExpectedConditions.attributeToBe(inputs.get(1), "value", "demo123"));

        WebElement submit = wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector(".submit")));

        JavascriptExecutor javascript = (JavascriptExecutor) driver;
        javascript.executeScript(
                "arguments[0].dispatchEvent(new MouseEvent('click', {bubbles: true}));",
                submit);
        Object token = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(5))
                .pollingEvery(Duration.ofMillis(50))
                .until(currentDriver -> {
                    Object currentToken = javascript.executeScript(
                            "return window.localStorage.getItem('auth_token');");
                    return currentToken != null && !currentToken.toString().isBlank()
                            ? currentToken
                            : null;
        });

        assertTrue(token.toString().length() >= 20);
        saveScreenshot("EV-E2E-LOGIN-01");
    }
}
