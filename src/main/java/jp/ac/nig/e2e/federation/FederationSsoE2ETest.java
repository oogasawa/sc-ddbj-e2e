package jp.ac.nig.e2e.federation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * フェデレーション・SSOフローのE2Eテスト。
 *
 * 信頼モデル:
 *   SC-account → OP-account : 許可 (supercomputer-idp)
 *   AC-account → OP-account : 許可 (ac-account-idp)
 *   SC-account → AC-account : 禁止
 *   OP-account → SC-account : 禁止
 *   OP-account → AC-account : 禁止
 *   AC-account → SC-account : 禁止
 *
 * SSOテスト:
 *   FED-01: sc-accountにログイン済み → op-accountにSSOできる（再認証不要）
 *   FED-02: ac-accountにログイン済み → op-accountにSSOできる（再認証不要）
 *
 * 禁止方向テスト（ボタン不在確認）:
 *   FED-03: sc-accountのKeycloakにop-account-idpボタンがない（OP→SC禁止）
 *   FED-04: sc-accountのKeycloakにac-account-idpボタンがない（AC→SC禁止）
 *   FED-05: ac-accountのKeycloakにsupercomputer-idpボタンがない（SC→AC禁止）
 *   FED-06: ac-accountのKeycloakにop-account-idpボタンがない（OP→AC禁止）
 *
 * 前提: 各Keycloakレルムに e2e-test-user / E2eTestPass2024! が作成済みであること。
 */
public class FederationSsoE2ETest extends E2ETestBase {

    private static final String SC_BASE  = E2EConfig.HOST + "/sc-account";
    private static final String OP_BASE  = E2EConfig.HOST + "/op-account";
    private static final String AC_BASE  = E2EConfig.HOST + "/ac-account";

    // -------------------------------------------------------------------------
    // FED-01: SC → OP SSO
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-01: sc-accountにログイン済みでop-accountにSSOできる（再認証不要）")
    public void testScToOpSso() {
        // Step 1: sc-accountにログイン
        navigateTo(SC_BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        keycloakLogin(E2EConfig.TEST_USERNAME, E2EConfig.TEST_PASSWORD, "**/sc-account/**");
        assertUrlContains("/sc-account");
        System.out.println("  [FED-01] sc-account login OK: " + page.url());

        // Step 2: op-account/dashboardへアクセス → op-authにリダイレクト
        navigateTo(OP_BASE + "/dashboard");
        page.waitForURL("**/op-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        assertUrlContains("/op-auth/realms/submission");

        // Step 3: 「遺伝研スパコンでログイン」ボタンをクリック
        Locator scIdpBtn = page.locator(
            "a[id*='supercomputer'], a[href*='supercomputer-idp'], *:has-text('遺伝研スパコンでログイン')"
        ).first();
        if (!scIdpBtn.isVisible())
            throw new AssertionError("supercomputer-idp button not found on op-account Keycloak login page");
        scIdpBtn.click();

        // Step 4: sc-authにリダイレクト → 既存セッションで自動認証 → op-accountに戻る
        // sc-authにある既存セッションcookieにより、ログインフォームが出ずにop-accountに戻るはず
        page.waitForURL("**/op-account/**", new Page.WaitForURLOptions().setTimeout(30000));

        // Keycloakログインページに留まっていたら失敗（再認証が要求された）
        if (page.url().contains("/op-auth/") || page.url().contains("/sc-auth/"))
            throw new AssertionError(
                "SSO failed: still on Keycloak login page instead of op-account. URL: " + page.url());

        System.out.println("PASSED: SC→OP SSO succeeded without re-authentication. URL: " + page.url());
    }

    // -------------------------------------------------------------------------
    // FED-02: AC → OP SSO
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-02: ac-accountにログイン済みでop-accountにSSOできる（再認証不要）")
    public void testAcToOpSso() {
        // Step 1: ac-accountにログイン
        navigateTo(AC_BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        keycloakLogin(E2EConfig.TEST_USERNAME, E2EConfig.TEST_PASSWORD, "**/ac-account/**");
        assertUrlContains("/ac-account");
        System.out.println("  [FED-02] ac-account login OK: " + page.url());

        // Step 2: op-account/dashboardへアクセス → op-authにリダイレクト
        navigateTo(OP_BASE + "/dashboard");
        page.waitForURL("**/op-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        assertUrlContains("/op-auth/realms/submission");

        // Step 3: 「遺伝研スパコン個人ゲノム解析アカウント」ボタンをクリック
        Locator acIdpBtn = page.locator(
            "a[id*='ac-account'], a[href*='ac-account-idp'], *:has-text('個人ゲノム')"
        ).first();
        if (!acIdpBtn.isVisible())
            throw new AssertionError("ac-account-idp button not found on op-account Keycloak login page");
        acIdpBtn.click();

        // Step 4: ac-authにリダイレクト → 既存セッションで自動認証 → op-accountに戻る
        page.waitForURL("**/op-account/**", new Page.WaitForURLOptions().setTimeout(30000));

        if (page.url().contains("/op-auth/") || page.url().contains("/ac-auth/"))
            throw new AssertionError(
                "SSO failed: still on Keycloak login page instead of op-account. URL: " + page.url());

        System.out.println("PASSED: AC→OP SSO succeeded without re-authentication. URL: " + page.url());
    }

    // -------------------------------------------------------------------------
    // FED-03: OP→SC 禁止（sc-accountのKeycloakにop-account-idpボタンなし）
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-03: sc-accountのKeycloakにop-account-idpボタンがない（OP→SC禁止）")
    public void testOpToScForbidden() {
        navigateTo(SC_BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        boolean hasOpIdp = page.locator("a[href*='op-account-idp'], a[id*='op-account']").count() > 0
            || page.locator("*:has-text('Submission Account'), *:has-text('OP-account')").count() > 0;

        if (hasOpIdp)
            throw new AssertionError("op-account-idp button found in sc-account Keycloak — policy violation (OP→SC should be forbidden)");

        System.out.println("PASSED: op-account-idp button correctly absent from sc-account Keycloak");
    }

    // -------------------------------------------------------------------------
    // FED-04: AC→SC 禁止（sc-accountのKeycloakにac-account-idpボタンなし）
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-04: sc-accountのKeycloakにac-account-idpボタンがない（AC→SC禁止）")
    public void testAcToScForbidden() {
        navigateTo(SC_BASE + "/dashboard");
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        boolean hasAcIdp = page.locator("a[href*='ac-account-idp'], a[id*='ac-account']").count() > 0
            || page.locator("*:has-text('個人ゲノム'), *:has-text('AC-account')").count() > 0;

        if (hasAcIdp)
            throw new AssertionError("ac-account-idp button found in sc-account Keycloak — policy violation (AC→SC should be forbidden)");

        System.out.println("PASSED: ac-account-idp button correctly absent from sc-account Keycloak");
    }

    // -------------------------------------------------------------------------
    // FED-05: SC→AC 禁止（ac-accountのKeycloakにsupercomputer-idpボタンなし）
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-05: ac-accountのKeycloakにsupercomputer-idpボタンがない（SC→AC禁止）")
    public void testScToAcForbidden() {
        navigateTo(AC_BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        boolean hasScIdp = page.locator("a[href*='supercomputer-idp'], a[id*='supercomputer']").count() > 0
            || page.locator("*:has-text('遺伝研スパコン'), *:has-text('スパコン')").count() > 0;

        if (hasScIdp)
            throw new AssertionError("supercomputer-idp button found in ac-account Keycloak — policy violation (SC→AC should be forbidden)");

        System.out.println("PASSED: supercomputer-idp button correctly absent from ac-account Keycloak");
    }

    // -------------------------------------------------------------------------
    // FED-06: OP→AC 禁止（ac-accountのKeycloakにop-account-idpボタンなし）
    // -------------------------------------------------------------------------

    @E2ETest(description = "FED-06: ac-accountのKeycloakにop-account-idpボタンがない（OP→AC禁止）")
    public void testOpToAcForbidden() {
        navigateTo(AC_BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        boolean hasOpIdp = page.locator("a[href*='op-account-idp'], a[id*='op-account']").count() > 0
            || page.locator("*:has-text('Submission Account'), *:has-text('OP-account')").count() > 0;

        if (hasOpIdp)
            throw new AssertionError("op-account-idp button found in ac-account Keycloak — policy violation (OP→AC should be forbidden)");

        System.out.println("PASSED: op-account-idp button correctly absent from ac-account Keycloak");
    }
}
