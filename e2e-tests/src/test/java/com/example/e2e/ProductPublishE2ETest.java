package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class ProductPublishE2ETest extends BaseE2ETest {

    @Test
    void sellerCanPublishUsedProductForAudit() {
        loginWithPreset(1, "seller", "seller123");
        openPage("pages/publish/publish");

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "发布商品"));

        clickUniElement(wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector(".upload"))));
        WebElement fileInput = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[type='file']")));
        String imagePath = Path.of("..", "shopping_front", "static", "logo.png")
                .toAbsolutePath()
                .normalize()
                .toString();
        fileInput.sendKeys(imagePath);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector(".upload-tip"), "上传成功"));

        List<WebElement> inputs = wait.until(
                ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector(".input input"), 6));
        String title = "E2E测试商品-" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        setInputValue(inputs.get(0), title);
        setInputValue(inputs.get(1), "E2E自动化");
        setInputValue(inputs.get(2), "学习资料");
        setInputValue(inputs.get(3), "9.90");
        setInputValue(inputs.get(4), "九成新");
        setInputValue(inputs.get(5), "北京航空航天大学");
        setInputValue(inputs.get(6), "8.00");

        List<WebElement> textareas = wait.until(
                ExpectedConditions.numberOfElementsToBe(By.cssSelector(".textarea textarea"), 2));
        setInputValue(textareas.get(0), "端到端自动化测试发布的商品，请勿购买。");
        setInputValue(textareas.get(1), "用于验证卖家发布到进入审核列表的完整流程。");

        clickUniElement(wait.until(
                ExpectedConditions.elementToBeClickable(By.cssSelector(".submit"))));

        waitForUrlContains("/pages/user/published");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), title));
        assertTrue(driver.getPageSource().contains(title));
        saveScreenshot("EV-E2E-PRODUCT-PUBLISH-01");
    }
}
