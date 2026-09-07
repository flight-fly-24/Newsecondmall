package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class CommunityE2ETest extends BaseE2ETest {

    @Test
    void buyerCanCreateTopicPostCommentAndLike() {
        loginWithPreset(0, "demo", "demo123");
        openPage("pages/browse/browse");
        waitForUrlContains("/pages/browse/browse");

        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".create-shortcut, .create-toggle"))));
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        String topicTitle = "E2E社区话题-" + suffix;
        List<WebElement> topicInputs = wait.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector(".topic-modal .form-input input"), 3));
        setInputValue(topicInputs.get(0), topicTitle);
        setInputValue(topicInputs.get(1), "测试话题");
        setInputValue(topicInputs.get(2), "E2E,自动化");
        setInputValue(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".form-textarea textarea"))), "用于验证社区完整互动流程。");
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".topic-modal .modal-submit"))));

        waitForUrlContains("/pages/topic/detail");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), topicTitle));
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".topic-fab"))));

        String postTitle = "E2E讨论帖-" + suffix;
        setInputValue(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".post-title-input input"))), postTitle);
        setInputValue(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".post-input textarea"))), "这是端到端测试发布的讨论内容。");
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".post-modal .modal-submit"))));

        WebElement postCard = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".post-card"))
                .stream()
                .filter(card -> card.getText().contains(postTitle))
                .findFirst()
                .orElse(null));
        List<WebElement> actions = postCard.findElements(By.cssSelector(".post-actions .action"));
        clickUniElement(actions.get(0));
        clickUniElement(actions.get(3));

        String comment = "E2E评论-" + suffix;
        setInputValue(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".comment-input input"))), comment);
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".comment-btn"))));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), comment));

        assertTrue(driver.getPageSource().contains(postTitle));
        assertTrue(driver.getPageSource().contains(comment));
        saveScreenshot("EV-E2E-COMMUNITY-01");
    }
}
