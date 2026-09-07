package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class BuyerAndStoreE2ETest extends BaseE2ETest {

    @Test
    void buyerCanFavoriteProductAndSeeItInPersonalCenter() {
        loginWithPreset(0, "demo", "demo123");
        String title = openFirstProduct();

        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='favorite-product']"))));
        openPage("pages/user/index");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "足迹收藏"));

        WebElement favoriteNav = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("[data-testid='buyer-nav-favorite']")));
        clickUniElement(favoriteNav);
        wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector("[data-testid='interaction-title']"))
                .stream()
                .anyMatch(element -> element.getText().contains(title)));

        assertTrue(driver.getPageSource().contains(title));
        saveScreenshot("EV-E2E-BUYER-FAVORITE-01");
    }

    @Test
    void buyerCanOpenAndFollowStore() {
        loginWithPreset(0, "demo", "demo123");
        openFirstProduct();

        List<WebElement> storeButtons = wait.until(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".store-btn"), 0));
        clickUniElement(storeButtons.get(0));
        waitForUrlContains("/pages/store/store");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "店铺介绍"));

        WebElement followButton = wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector(".follow-btn")));
        if (followButton.getText().contains("已关注")) {
            clickUniElement(followButton);
            wait.until(ExpectedConditions.not(ExpectedConditions.attributeContains(
                    By.cssSelector(".follow-btn"), "class", "followed")));
            followButton = wait.until(
                    ExpectedConditions.elementToBeClickable(By.cssSelector(".follow-btn")));
        }
        clickUniElement(followButton);
        wait.until(ExpectedConditions.attributeContains(
                By.cssSelector(".follow-btn"), "class", "followed"));

        assertTrue(driver.findElement(By.cssSelector(".follow-btn"))
                .getAttribute("class").contains("followed"));
        saveScreenshot("EV-E2E-STORE-FOLLOW-01");
    }

    private String openFirstProduct() {
        openPage("pages/home/home");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".js-home-grid")));
        WebElement card = wait.until(currentDriver -> {
            List<WebElement> items = currentDriver.findElements(
                    By.cssSelector(".js-home-grid .goods-card"));
            WebElement seeded = firstNamedCard(items, false);
            return seeded != null ? seeded : firstNamedCard(items, true);
        });
        String title = card.findElement(By.cssSelector(".goods-title")).getText().trim();
        assertFalse(title.isBlank());
        clickUniElement(card);
        waitForUrlContains("/pages/goods/detail");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), title));
        return title;
    }

    private static WebElement firstNamedCard(List<WebElement> items, boolean allowE2e) {
        for (WebElement item : items) {
            String text = item.findElement(By.cssSelector(".goods-title")).getText().trim();
            if (text.isBlank() || (!allowE2e && text.startsWith("E2E"))) {
                continue;
            }
            return item;
        }
        return null;
    }
}
