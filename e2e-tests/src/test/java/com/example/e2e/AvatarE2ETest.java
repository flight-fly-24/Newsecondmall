package com.example.e2e;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class AvatarE2ETest extends BaseE2ETest {

    @Test
    @Disabled("uni.chooseImage 在 H5 无头 Edge 中不触发文件选择回调，需有头或人工复测")
    void buyerCanUploadAndDisplayNewAvatar() {
        loginWithPreset(0, "demo", "demo123");
        openPage("pages/user/index");

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".profile-avatar")));
        String previousSource = driver.findElements(By.cssSelector(".profile-avatar .avatar-img"))
                .stream()
                .findFirst()
                .map(element -> element.getAttribute("src"))
                .orElse("");
        int existingFileInputs = driver.findElements(By.cssSelector("input[type='file']")).size();
        clickUniElement(wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".profile-avatar"))));
        List<WebElement> fileInputs = wait.until(currentDriver -> {
            List<WebElement> currentInputs = currentDriver.findElements(
                    By.cssSelector("input[type='file']"));
            return currentInputs.size() > existingFileInputs ? currentInputs : null;
        });
        WebElement fileInput = fileInputs.get(fileInputs.size() - 1);
        String imagePath = Path.of(
                "..", "shopping_front", "static", "stores", "songuo-digital.jpg")
                .toAbsolutePath()
                .normalize()
                .toString();
        fileInput.sendKeys(imagePath);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                fileInput);

        WebElement avatar = wait.until(currentDriver -> currentDriver
                .findElements(By.cssSelector(".profile-avatar .avatar-img"))
                .stream()
                .filter(element -> {
                    String source = element.getAttribute("src");
                    return source != null && !source.isBlank() && !source.equals(previousSource);
                })
                .findFirst()
                .orElse(null));
        assertFalse(avatar.getAttribute("src").isBlank());
        saveScreenshot("EV-E2E-AVATAR-01");
    }
}
