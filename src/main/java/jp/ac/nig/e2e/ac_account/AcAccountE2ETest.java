package jp.ac.nig.e2e.ac_account;

import com.microsoft.playwright.Page;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * ac-account (個人ゲノム解析アカウント) のE2Eテスト。
 *
 * 対象URL: https://sc.ddbj.nig.ac.jp/ac-account
 * Keycloak: /ac-auth/realms/personal-genome
 *
 * ログイン方法:
 *   1. username/password + OTP (2FA必須)
 *   2. ORCID (sandbox.orcid.org) + メール確認 (MailHog)
 *
 * セキュリティポリシー:
 *   - スパコンアカウント (supercomputer-idp) からのアクセスは禁止
 *
 * テスト項目:
 *   AC-01: トップページが表示される
 *   AC-02: 未認証でdashboardにアクセス → Keycloakにリダイレクト
 *   AC-03: Keycloakログイン画面にusername/passwordフォームがある
 *   AC-04: Keycloakログイン画面に「ORCID」ボタンがある
 *   AC-05: ORCiDボタンをクリックするとsandbox.orcid.orgにリダイレクトされる
 *   AC-06: Keycloakログイン画面に「遺伝研スパコン」ボタンがない（セキュリティポリシー確認）
 *   AC-07: KeycloakのIssuerがsc.ddbj.nig.ac.jpを指している（旧IPでない）
 */
public class AcAccountE2ETest extends E2ETestBase {

    private static final String BASE     = E2EConfig.HOST + "/ac-account";
    private static final String KC_REALM = E2EConfig.HOST + "/ac-auth/realms/personal-genome";

    // -------------------------------------------------------------------------
    // AC-01: トップページ
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-01: トップページが表示される")
    public void testLandingPage() {
        navigateTo(BASE);
        assertUrlContains("/ac-account");

        String content = page.content();
        if (content.isBlank() || content.contains("502 Bad Gateway") || content.contains("503"))
            throw new AssertionError("Landing page returned error: " + page.url());

        System.out.println("PASSED: ac-account landing page loaded");
    }

    // -------------------------------------------------------------------------
    // AC-02: 未認証でdashboardにアクセス → Keycloakにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-02: 未認証アクセスでKeycloakにリダイレクトされる")
    public void testUnauthenticatedRedirectToKeycloak() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/ac-auth/realms/personal-genome");
        System.out.println("PASSED: /ac-account/dashboard → Keycloak redirect (" + page.url() + ")");
    }

    // -------------------------------------------------------------------------
    // AC-03: Keycloakログイン画面にフォームがある
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-03: Keycloakログイン画面にusername/passwordフォームがある")
    public void testKeycloakLoginForm() {
        navigateToKeycloakLogin();

        // Keycloak v2 theme uses #username; v1/custom themes may use input[name='username']
        boolean hasUsernameField = page.locator("#username").count() > 0
            || page.locator("input[name='username']").count() > 0;
        if (!hasUsernameField)
            throw new AssertionError("username field not found. URL=" + page.url()
                + " Content=" + page.content().substring(0, 500));

        boolean hasPasswordField = page.locator("#password").count() > 0
            || page.locator("input[name='password']").count() > 0;
        if (!hasPasswordField)
            throw new AssertionError("password field not found");

        boolean hasSubmit = page.locator("input[type='submit']").count() > 0
            || page.locator("#kc-login").count() > 0
            || page.locator("button[type='submit']").count() > 0;
        if (!hasSubmit)
            throw new AssertionError("submit button not found");

        System.out.println("PASSED: Keycloak login form present");
    }

    // -------------------------------------------------------------------------
    // AC-04: Keycloakログイン画面に「ORCID」ボタンがある
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-04: Keycloakログイン画面に「ORCID」ボタンがある")
    public void testOrcidButtonPresent() {
        navigateToKeycloakLogin();

        boolean hasOrcid = page.locator("a[id*='orcid'], a[href*='orcid'], *:has-text('ORCID')").count() > 0;
        if (!hasOrcid)
            throw new AssertionError("ORCID button not found on Keycloak login page. "
                + "Content: " + page.content().substring(0, 500));

        System.out.println("PASSED: ORCID button found on Keycloak login page");
    }

    // -------------------------------------------------------------------------
    // AC-05: ORCiDボタン → sandbox.orcid.orgにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-05: ORCiDボタンをクリックするとsandbox.orcid.orgにリダイレクトされる")
    public void testOrcidButtonRedirect() {
        navigateToKeycloakLogin();

        page.locator("a[id*='orcid'], a[href*='orcid'], *:has-text('ORCID')").first().click();
        page.waitForURL("**/orcid.org/**", new Page.WaitForURLOptions().setTimeout(30000));

        if (!page.url().contains("orcid.org"))
            throw new AssertionError("Expected redirect to orcid.org but got: " + page.url());

        System.out.println("PASSED: ORCID button → orcid.org (" + page.url().substring(0, 60) + "...)");
    }

    // -------------------------------------------------------------------------
    // AC-06: 「遺伝研スパコン」ボタンがない（セキュリティポリシー）
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-06: Keycloakログイン画面に「遺伝研スパコン」ボタンがない（SC→AC禁止ポリシー）")
    public void testSupercomputerIdpNotPresent() {
        navigateToKeycloakLogin();

        boolean hasSupercomputer = page.locator("a[href*='supercomputer-idp']").count() > 0
            || page.locator("*:has-text('遺伝研スパコンでログイン')").count() > 0;

        if (hasSupercomputer)
            throw new AssertionError(
                "supercomputer-idp button should NOT exist in ac-account (policy: SC→AC is forbidden)");

        System.out.println("PASSED: supercomputer-idp button correctly absent from ac-account Keycloak");
    }

    // -------------------------------------------------------------------------
    // AC-07: OIDC discovery のissuerが正しい
    // -------------------------------------------------------------------------

    @E2ETest(description = "AC-07: OIDC discovery endpoint のissuerがsc.ddbj.nig.ac.jpを指している")
    public void testOidcDiscoveryIssuer() {
        navigateTo(KC_REALM + "/.well-known/openid-configuration");

        String body = page.content();
        if (!body.contains("sc.ddbj.nig.ac.jp"))
            throw new AssertionError("issuer does not contain sc.ddbj.nig.ac.jp");
        if (body.contains("192.168.5.") || body.contains("172.19.67."))
            throw new AssertionError("issuer still contains old IP address");

        System.out.println("PASSED: OIDC issuer is sc.ddbj.nig.ac.jp (no old IP)");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void navigateToKeycloakLogin() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/ac-auth/realms/personal-genome");
    }
}
