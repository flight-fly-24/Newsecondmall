package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class AiAssistantE2ETest extends BaseE2ETest {

    @Test
    void userCanAskAiAssistantAndReceiveAnswer() {
        openPage("pages/ai-assistant/ai-assistant");
        String question = "平台担保交易是什么？";

        WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".composer .input input")));
        setInputValue(input, question);
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".send-btn"))));

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector(".q-bubble"), question));
        WebElement answer = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector(".a-bubble:not(.thinking)")));
        wait.until(currentDriver -> !answer.getText().trim().isBlank());

        assertFalse(answer.getText().trim().isBlank());
        saveScreenshot("EV-E2E-AI-ASSISTANT-01");
    }
}
