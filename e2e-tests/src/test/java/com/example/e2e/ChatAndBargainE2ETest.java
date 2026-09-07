package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class ChatAndBargainE2ETest extends BaseE2ETest {

    @Test
    void buyerCanCreateConversationSendMessageBargainAndTransfer() {
        loginWithPreset(0, "demo", "demo123");
        openPage("pages/home/home");
        List<WebElement> cards = wait.until(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".goods-card"), 0));
        clickUniElement(cards.get(0));
        waitForUrlContains("/pages/goods/detail");

        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".bottom-action.ghost"))));
        waitForUrlContains("/pages/message/message");
        if (driver.findElements(By.cssSelector(".chat-pane .composer")).isEmpty()) {
            clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector(".conversation-item"))));
        }
        wait.until(ExpectedConditions.attributeToBe(
                By.cssSelector("[data-testid='chat-composer']"),
                "data-conversation-ready",
                "true"));

        String message = "E2E议价消息-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMddHHmmss")) + "，可以便宜一些吗？";
        setInputValue(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("[data-testid='chat-input'] textarea"))), message);
        wait.until(ExpectedConditions.attributeToBe(
                By.cssSelector("[data-testid='chat-input'] textarea"), "value", message));
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='chat-send']"))));
        waitFor(Duration.ofSeconds(30)).until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("[data-testid='chat-messages']"), message));

        WebElement bargainButton = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".header-actions .ghost-btn"))
                .stream()
                .filter(button -> button.getText().contains("AI 议价"))
                .findFirst()
                .orElse(null));
        clickUniElement(bargainButton);
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".ai-bubble"), 0));
        saveScreenshot("EV-E2E-CHAT-BARGAIN-01");

        WebElement humanButton = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".header-actions .ghost-btn"))
                .stream()
                .filter(button -> button.getText().contains("转人工"))
                .findFirst()
                .orElse(null));
        clickUniElement(humanButton);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector(".header-actions"), "切换回AI客服"));

        assertTrue(driver.getPageSource().contains(message));
        saveScreenshot("EV-E2E-CHAT-TRANSFER-01");
    }
}
