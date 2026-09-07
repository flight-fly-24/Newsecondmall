package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class ProductBrowseE2ETest extends BaseE2ETest {

    @Test
    void userCanBrowseProductAndOpenDetail() {
        openPage("pages/home/home");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".js-home-grid")));
        List<WebElement> cards = wait.until(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".goods-card"), 0));

        String productTitle = cards.get(0).findElement(By.cssSelector(".goods-title")).getText().trim();
        assertFalse(productTitle.isBlank());

        cards.get(0).click();

        wait.until(ExpectedConditions.urlContains("/pages/goods/detail"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), productTitle));

        assertTrue(driver.getCurrentUrl().contains("/pages/goods/detail"));
        assertTrue(driver.getPageSource().contains(productTitle));
        saveScreenshot("EV-E2E-PRODUCT-DETAIL-01");
    }

    @Test
    void userCanSearchForExistingProduct() {
        openPage("pages/home/home");

        String productTitle = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".goods-title"))
                .stream()
                .map(element -> element.getText().trim())
                .filter(title -> !title.isBlank())
                .findFirst()
                .orElse(null));

        WebElement searchInput = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".search-input input")));
        setInputValue(searchInput, productTitle);
        clickUniElement(wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector(".search-action"))));

        wait.until(currentDriver -> currentDriver.findElements(By.cssSelector(".goods-title"))
                .stream()
                .anyMatch(element -> element.getText().trim().equals(productTitle)));

        assertTrue(driver.findElements(By.cssSelector(".goods-title"))
                .stream()
                .anyMatch(element -> element.getText().trim().equals(productTitle)));
        saveScreenshot("EV-E2E-PRODUCT-SEARCH-01");
    }
}
