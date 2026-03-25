package jp.ac.nig.e2e.federation;

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
 * SSOテスト (MANUAL — requires 2FA):
 *   FED-01: (MANUAL) sc-accountにログイン済み → op-accountにSSOできる（再認証不要）
 *   FED-02: (MANUAL) ac-accountにログイン済み → op-accountにSSOできる（再認証不要）
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
    // MANUAL TEST: SC-account requires 2FA (OTP).
    // Test procedure: Log into sc-account manually, then navigate to
    // op-account/dashboard and click "遺伝研スパコンでログイン" — should SSO
    // without re-authentication.
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // FED-02: AC → OP SSO
    // MANUAL TEST: AC-account requires 2FA (Email OTP).
    // Test procedure: Log into ac-account manually, then navigate to
    // op-account/dashboard and click "個人ゲノム" — should SSO
    // without re-authentication.
    // -------------------------------------------------------------------------

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
