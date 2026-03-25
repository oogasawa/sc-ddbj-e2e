package jp.ac.nig.e2e.sc_account;

import com.microsoft.playwright.Page;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * sc-account (スパコンアカウント管理) のE2Eテスト。
 *
 * 対象URL: https://sc.ddbj.nig.ac.jp/sc-account
 * Keycloak: /sc-auth/realms/sc-account
 *
 * テスト項目:
 *   SC-01: トップページが表示される
 *   SC-02: 未認証でdashboardにアクセス → Keycloakにリダイレクト
 *   SC-03: Keycloakログイン画面にusername/passwordフォームがある
 *   SC-04: KeycloakのIssuerがsc.ddbj.nig.ac.jpを指している（旧IPでない）
 *   SC-05: ログアウト後に再アクセスするとKeycloakにリダイレクトされる
 */
public class ScAccountE2ETest extends E2ETestBase {

    private static final String BASE      = E2EConfig.HOST + "/sc-account";
    private static final String KC_REALM  = E2EConfig.HOST + "/sc-auth/realms/sc-account";

    // -------------------------------------------------------------------------
    // SC-01: トップページ
    // -------------------------------------------------------------------------

    @E2ETest(description = "SC-01: トップページが表示される")
    public void testLandingPage() {
        navigateTo(BASE);
        assertUrlContains("/sc-account");
        // ページに何らかのコンテンツがある
        String content = page.content();
        if (content.isBlank() || content.contains("502 Bad Gateway") || content.contains("503"))
            throw new AssertionError("Landing page returned error: " + page.url());
        System.out.println("PASSED: sc-account landing page loaded");
    }

    // -------------------------------------------------------------------------
    // SC-02: 未認証でdashboardにアクセス → Keycloakにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "SC-02: 未認証アクセスでKeycloakにリダイレクトされる")
    public void testUnauthenticatedRedirectToKeycloak() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/sc-auth/realms/sc-account");
        System.out.println("PASSED: /sc-account/dashboard → Keycloak redirect (" + page.url() + ")");
    }

    // -------------------------------------------------------------------------
    // SC-03: Keycloakログイン画面にフォームがある
    // -------------------------------------------------------------------------

    @E2ETest(description = "SC-03: Keycloakログイン画面にusername/passwordフォームがある")
    public void testKeycloakLoginForm() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(10000));

        assertVisible("#username");
        assertVisible("#password");
        assertVisible("input[type='submit'], #kc-login");
        System.out.println("PASSED: Keycloak login form present");
    }

    // -------------------------------------------------------------------------
    // SC-04: KeycloakのIssuerが正しいホスト名を指している
    // -------------------------------------------------------------------------

    @E2ETest(description = "SC-04: OIDC discovery endpoint のissuerがsc.ddbj.nig.ac.jpを指している")
    public void testOidcDiscoveryIssuer() {
        navigateTo(KC_REALM + "/.well-known/openid-configuration");

        String body = page.locator("body, pre").textContent();
        if (!body.contains("sc.ddbj.nig.ac.jp"))
            throw new AssertionError("issuer does not contain sc.ddbj.nig.ac.jp: " + body.substring(0, 200));
        if (body.contains("192.168.5.") || body.contains("172.19.67."))
            throw new AssertionError("issuer still contains old IP address: " + body.substring(0, 200));

        System.out.println("PASSED: OIDC issuer is sc.ddbj.nig.ac.jp (no old IP)");
    }

    // -------------------------------------------------------------------------
    // SC-05: ログアウト後に再アクセス → Keycloakにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "SC-05: ログアウト後に再アクセスするとKeycloakにリダイレクトされる")
    public void testLogoutRedirect() {
        // ログアウトエンドポイントを直接叩いてセッションをクリア
        navigateTo(BASE + "/logout");
        page.waitForTimeout(2000);

        // dashboardに再アクセス
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/sc-auth/realms/sc-account");
        System.out.println("PASSED: After logout, redirect to Keycloak confirmed");
    }
}
