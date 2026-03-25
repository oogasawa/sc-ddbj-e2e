package jp.ac.nig.e2e.s6_identity_brokering;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * S6: Identity Brokering (AC↔SC アカウント連携) のE2Eテスト。
 *
 * AC-account の Keycloak に supercomputer-idp が設定されており、
 * AC-accountユーザーが自分の遺伝研スパコンアカウントとリンクできること。
 * ただし supercomputer-idp はログインページには表示されない (hideOnLogin=true)。
 *
 * テスト項目:
 *   S6-01: AC Keycloak に supercomputer-idp の broker endpoint が存在する
 *   S6-02: AC Keycloak ログインページに supercomputer-idp ボタンが表示されない (hideOnLogin)
 *   S6-03: OP-account Keycloak に ac-account-idp ボタンが表示される
 *   S6-04: SC Keycloak OIDC discovery が正しいホストを返す（brokeringの基盤確認）
 *   S6-05: (MANUAL) AC-account にログイン後、ダッシュボードにアクセスできる（2FA必須のため手動テスト）
 */
public class S6IdentityBrokeringE2ETest extends E2ETestBase {

    private static final String AC_BASE = E2EConfig.HOST + "/ac-account";
    private static final String OP_BASE = E2EConfig.HOST + "/op-account";
    private static final String AC_KC   = E2EConfig.HOST + "/ac-auth";
    private static final String SC_KC   = E2EConfig.HOST + "/sc-auth";

    // -------------------------------------------------------------------------
    // S6-01: supercomputer-idp の broker endpoint が存在する
    // -------------------------------------------------------------------------

    @E2ETest(description = "S6-01: AC Keycloak に supercomputer-idp broker endpoint が存在する（404でない）")
    public void testSupercomputerIdpEndpointExists() {
        // Keycloak の broker endpoint にアクセス
        // 正常なIdPなら認可URLへリダイレクト、存在しなければ404
        var response = page.navigate(
            AC_KC + "/realms/personal-genome/broker/supercomputer-idp/endpoint");

        if (response == null)
            throw new AssertionError("No response from broker endpoint");

        // 404 = IdP未設定、それ以外（302リダイレクト or エラーページ）= IdP設定済み
        String body = page.content();
        if (body.contains("Identity Provider not found") || body.contains("404"))
            throw new AssertionError("supercomputer-idp not found in AC Keycloak. "
                + "Status: " + response.status() + " Body: " + body.substring(0, Math.min(body.length(), 300)));

        System.out.println("PASSED: supercomputer-idp broker endpoint exists (status=" + response.status() + ")");
    }

    // -------------------------------------------------------------------------
    // S6-02: ログインページに supercomputer-idp ボタンが表示されない
    // -------------------------------------------------------------------------

    @E2ETest(description = "S6-02: AC Keycloak ログインページに supercomputer-idp ボタンが表示されない (hideOnLogin)")
    public void testSupercomputerIdpHiddenOnLoginPage() {
        navigateTo(AC_BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        assertUrlContains("/ac-auth/realms/personal-genome");

        // supercomputer-idp のリンクやボタンが表示されていないこと
        boolean hasScIdpLink = page.locator("a[href*='supercomputer-idp']").count() > 0;
        boolean hasScIdpText = page.locator("*:has-text('遺伝研スパコンでログイン')").count() > 0;

        if (hasScIdpLink || hasScIdpText)
            throw new AssertionError("supercomputer-idp button is visible on AC login page — "
                + "hideOnLogin should be true. This means SC-account users could log into AC-account directly.");

        System.out.println("PASSED: supercomputer-idp is hidden on AC Keycloak login page (hideOnLogin=true)");
    }

    // -------------------------------------------------------------------------
    // S6-03: OP-account Keycloak に ac-account-idp ボタンが表示される
    // -------------------------------------------------------------------------

    @E2ETest(description = "S6-03: OP-account Keycloak に ac-account-idp (個人ゲノム) ボタンが表示される")
    public void testAcIdpPresentInOpKeycloak() {
        navigateTo(OP_BASE + "/dashboard");
        page.waitForURL("**/op-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        assertUrlContains("/op-auth/realms/submission");

        boolean hasAcIdp = page.locator("a[href*='ac-account-idp'], a[id*='ac-account']").count() > 0
            || page.locator("*:has-text('個人ゲノム')").count() > 0;

        if (!hasAcIdp)
            throw new AssertionError("ac-account-idp button not found on OP-account Keycloak login page. "
                + "AC→OP federation should be enabled.");

        System.out.println("PASSED: ac-account-idp button present on OP-account Keycloak login page");
    }

    // -------------------------------------------------------------------------
    // S6-04: SC Keycloak OIDC discovery のホスト確認
    // -------------------------------------------------------------------------

    @E2ETest(description = "S6-04: SC Keycloak OIDC discovery が sc.ddbj.nig.ac.jp を返す（brokering基盤確認）")
    public void testScKeycloakDiscovery() {
        navigateTo(SC_KC + "/realms/sc-account/.well-known/openid-configuration");
        String body = page.content();

        if (!body.contains("sc.ddbj.nig.ac.jp"))
            throw new AssertionError("SC Keycloak OIDC discovery does not contain sc.ddbj.nig.ac.jp");

        // brokering 先として正しいホストが設定されていることを確認
        if (body.contains("192.168.5.") || body.contains("172.19.67."))
            throw new AssertionError("SC Keycloak OIDC discovery still contains old IP address — "
                + "identity brokering would fail from NIG");

        System.out.println("PASSED: SC Keycloak OIDC discovery returns sc.ddbj.nig.ac.jp (brokering-ready)");
    }

    // -------------------------------------------------------------------------
    // S6-05: AC-account ログイン → ダッシュボード
    // MANUAL TEST: AC-account requires 2FA (Email OTP).
    // Automating 2FA login via Playwright + MailHog is possible but fragile.
    // This test is better performed manually.
    // -------------------------------------------------------------------------
}
