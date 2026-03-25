package jp.ac.nig.e2e.base;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Paths;

/**
 * 全E2Eテストの基底クラス。Playwright browser/context/page を提供する。
 */
public abstract class E2ETestBase {

    protected static Playwright playwright;
    protected static Browser browser;
    protected BrowserContext context;
    protected Page page;

    public static void initBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(E2EConfig.HEADLESS)
            .setSlowMo(E2EConfig.SLOW_MO_MS));
    }

    public static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    public void setUp() {
        context = browser.newContext(new Browser.NewContextOptions()
            .setIgnoreHTTPSErrors(true)
            .setViewportSize(1920, 1080));
        context.setDefaultTimeout(E2EConfig.TIMEOUT_MS);
        page = context.newPage();
    }

    public void tearDown(String testName, boolean failed) {
        if (failed && page != null) {
            try {
                java.nio.file.Files.createDirectories(Paths.get(E2EConfig.SCREENSHOT_DIR));
                page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(E2EConfig.SCREENSHOT_DIR + "/" + testName + ".png"))
                    .setFullPage(true));
                System.out.println("    Screenshot: " + E2EConfig.SCREENSHOT_DIR + "/" + testName + ".png");
            } catch (Exception e) {
                System.err.println("    Warning: screenshot failed: " + e.getMessage());
            }
        }
        if (context != null) context.close();
    }

    protected void navigateTo(String url) {
        page.navigate(url);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    protected void assertUrlContains(String fragment) {
        String url = page.url();
        if (!url.contains(fragment))
            throw new AssertionError("Expected URL to contain '" + fragment + "' but was: " + url);
    }

    protected void assertVisible(String selector) {
        if (!page.locator(selector).isVisible())
            throw new AssertionError("Expected element to be visible: " + selector);
    }

    protected void assertNotVisible(String selector) {
        if (page.locator(selector).isVisible())
            throw new AssertionError("Expected element to be hidden: " + selector);
    }

    protected void assertPageContains(String text) {
        if (!page.content().contains(text))
            throw new AssertionError("Expected page to contain: " + text);
    }

    /**
     * Keycloakのusername/passwordログインフォームに認証情報を入力してログインする。
     * 呼び出し前に既にKeycloakログイン画面にいること。
     * ログイン後は指定のURLパターンが現れるまで待つ。
     */
    protected void keycloakLogin(String username, String password, String waitForUrlPattern) {
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.locator("input[type='submit'], #kc-login").first().click();
        page.waitForURL(waitForUrlPattern, new com.microsoft.playwright.Page.WaitForURLOptions()
            .setTimeout(E2EConfig.TIMEOUT_MS));
    }
}
