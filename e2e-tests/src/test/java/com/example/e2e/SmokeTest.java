package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class SmokeTest extends BaseE2ETest {

    @Test
    void homePageCanBeOpened() {
        openPage("pages/home/home");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("body")));

        assertTrue(driver.getCurrentUrl().contains("pages/home/home"));
        assertTrue(driver.findElement(By.tagName("body")).isDisplayed());
    }
}
