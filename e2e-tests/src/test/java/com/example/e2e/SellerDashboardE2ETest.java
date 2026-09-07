package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class SellerDashboardE2ETest extends BaseE2ETest {

    @Test
    void sellerCanViewDashboardAndBusinessInformation() {
        loginWithPreset(1, "seller", "seller123");
        openPage("pages/seller/dashboard");

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "卖家工作台"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "今日店铺概览"));
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(
                By.cssSelector(".stat-card"), 0));

        assertTrue(driver.getPageSource().contains("待处理"));
        saveScreenshot("EV-E2E-SELLER-DASHBOARD-01");
    }
}
