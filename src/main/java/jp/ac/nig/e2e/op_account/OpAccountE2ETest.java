package jp.ac.nig.e2e.op_account;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * op-account (Submission Account) のE2Eテスト。
 *
 * 対象URL: https://sc.ddbj.nig.ac.jp/op-account
 * Keycloak: /op-auth/realms/submission
 *
 * ログイン方法:
 *   1. Submission Account (username/password)
 *   2. ORCID (sandbox.orcid.org) - kc_idp_hint=orcid
 *   3. 遺伝研スパコンでログイン (supercomputer-idp) → /sc-auth
 *   4. 遺伝研スパコン個人ゲノム解析アカウント (ac-account-idp) → /ac-auth
 *
 * テスト項目:
 *   OP-01: トップページにORCiDログインリンクがある
 *   OP-02: トップページにSubmission Accountログインリンクがある
 *   OP-03: Submission Accountログイン → Keycloakにリダイレクト
 *   OP-04: Keycloakログイン画面に「遺伝研スパコンでログイン」ボタンがある
 *   OP-05: Keycloakログイン画面に「個人ゲノム解析アカウント」ボタンがある
 *   OP-06: ORCiDリンクをクリックするとsandbox.orcid.orgにリダイレクトされる
 *   OP-07: 「遺伝研スパコンでログイン」をクリックすると/sc-authにリダイレクトされる
 *   OP-08: 「個人ゲノム解析アカウント」をクリックすると/ac-authにリダイレクトされる
 *   OP-09: KeycloakのIssuerがsc.ddbj.nig.ac.jpを指している（旧IPでない）
 */
public class OpAccountE2ETest extends E2ETestBase {

    private static final String BASE     = E2EConfig.HOST + "/op-account";
    private static final String KC_BASE  = E2EConfig.HOST + "/op-auth";
    private static final String KC_REALM = KC_BASE + "/realms/submission";

    // -------------------------------------------------------------------------
    // OP-01: ORCiDログインリンク
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-01: トップページにORCiDログインリンク(kc_idp_hint=orcid)がある")
    public void testOrcidLoginLinkPresent() {
        navigateTo(BASE);
        assertUrlContains("/op-account");

        // ランディングページのORCiDリンク: href に kc_idp_hint=orcid が含まれる
        String orcidHref = page.locator("a[href*='kc_idp_hint=orcid']").getAttribute("href");
        if (orcidHref == null)
            throw new AssertionError("ORCiD login link (kc_idp_hint=orcid) not found on landing page");

        System.out.println("PASSED: ORCiD login link found: " + orcidHref.substring(0, Math.min(80, orcidHref.length())));
    }

    // -------------------------------------------------------------------------
    // OP-02: Submission Accountログインリンク
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-02: トップページにSubmission Accountログインリンクがある")
    public void testSubmissionAccountLoginLinkPresent() {
        navigateTo(BASE);

        // dashboardリンクまたはKeycloakへの直接リンク
        boolean hasLoginLink = page.locator("a[href*='/op-account/dashboard']").count() > 0
            || page.locator("a[href*='op-auth']").count() > 0
            || page.locator("a:has-text('Submission Account'), a:has-text('ログイン')").count() > 0;

        if (!hasLoginLink)
            throw new AssertionError("Submission Account login link not found on landing page");

        System.out.println("PASSED: Submission Account login link found");
    }

    // -------------------------------------------------------------------------
    // OP-03: dashboardアクセス → Keycloakにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-03: 未認証でdashboardにアクセスするとKeycloakにリダイレクトされる")
    public void testUnauthenticatedRedirectToKeycloak() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/op-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/op-auth/realms/submission");
        System.out.println("PASSED: /op-account/dashboard → Keycloak redirect (" + page.url() + ")");
    }

    // -------------------------------------------------------------------------
    // OP-04: Keycloakに「遺伝研スパコンでログイン」ボタン
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-04: Keycloakログイン画面に「遺伝研スパコンでログイン」ボタンがある")
    public void testSupercomputerIdpButtonPresent() {
        navigateToKeycloakLogin();

        // supercomputer-idp: Keycloakのsocial login button
        boolean hasButton = page.locator("a[id*='supercomputer'], a[href*='supercomputer-idp']").count() > 0
            || page.locator("*:has-text('遺伝研スパコンでログイン')").count() > 0
            || page.locator("*:has-text('スパコン')").count() > 0;

        if (!hasButton)
            throw new AssertionError("supercomputer-idp button not found on Keycloak login page. "
                + "Page content: " + page.content().substring(0, 500));

        System.out.println("PASSED: 「遺伝研スパコンでログイン」button found on Keycloak login page");
    }

    // -------------------------------------------------------------------------
    // OP-05: Keycloakに「個人ゲノム解析アカウント」ボタン
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-05: Keycloakログイン画面に「個人ゲノム解析アカウント」ボタンがある")
    public void testAcAccountIdpButtonPresent() {
        navigateToKeycloakLogin();

        boolean hasButton = page.locator("a[id*='ac-account'], a[href*='ac-account-idp']").count() > 0
            || page.locator("*:has-text('個人ゲノム')").count() > 0
            || page.locator("*:has-text('AC-account')").count() > 0;

        if (!hasButton)
            throw new AssertionError("ac-account-idp button not found on Keycloak login page. "
                + "Page content: " + page.content().substring(0, 500));

        System.out.println("PASSED: 「個人ゲノム解析アカウント」button found on Keycloak login page");
    }

    // -------------------------------------------------------------------------
    // OP-06: ORCiDリンク → sandbox.orcid.orgにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-06: ORCiDログインリンクをクリックするとsandbox.orcid.orgにリダイレクトされる")
    public void testOrcidLoginRedirect() {
        navigateTo(BASE);

        // ランディングページのORCiDリンクをクリック
        page.locator("a[href*='kc_idp_hint=orcid']").first().click();
        page.waitForURL("**/orcid.org/**", new Page.WaitForURLOptions().setTimeout(15000));

        if (!page.url().contains("orcid.org"))
            throw new AssertionError("Expected redirect to orcid.org but got: " + page.url());

        System.out.println("PASSED: ORCiD login redirects to orcid.org (" + page.url().substring(0, 60) + "...)");
    }

    // -------------------------------------------------------------------------
    // OP-07: 「遺伝研スパコンでログイン」→ /sc-authにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-07: 「遺伝研スパコンでログイン」をクリックすると/sc-authにリダイレクトされる")
    public void testSupercomputerIdpRedirect() {
        navigateToKeycloakLogin();

        // supercomputer-idpボタンをクリック
        Locator btn = page.locator("a[id*='supercomputer'], a[href*='supercomputer-idp'], *:has-text('遺伝研スパコンでログイン')").first();
        btn.click();
        page.waitForURL("**/sc-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        assertUrlContains("/sc-auth/realms/sc-account");
        System.out.println("PASSED: supercomputer-idp → /sc-auth/realms/sc-account (" + page.url().substring(0, 80) + "...)");
    }

    // -------------------------------------------------------------------------
    // OP-08: 「個人ゲノム解析アカウント」→ /ac-authにリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-08: 「個人ゲノム解析アカウント」をクリックすると/ac-authにリダイレクトされる")
    public void testAcAccountIdpRedirect() {
        navigateToKeycloakLogin();

        Locator btn = page.locator("a[id*='ac-account'], a[href*='ac-account-idp'], *:has-text('個人ゲノム')").first();
        btn.click();
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));

        assertUrlContains("/ac-auth/realms/personal-genome");
        System.out.println("PASSED: ac-account-idp → /ac-auth/realms/personal-genome (" + page.url().substring(0, 80) + "...)");
    }

    // -------------------------------------------------------------------------
    // OP-09: OIDC discovery のissuerが正しい
    // -------------------------------------------------------------------------

    @E2ETest(description = "OP-09: OIDC discovery endpoint のissuerがsc.ddbj.nig.ac.jpを指している")
    public void testOidcDiscoveryIssuer() {
        navigateTo(KC_REALM + "/.well-known/openid-configuration");

        String body = page.locator("body, pre").textContent();
        if (!body.contains("sc.ddbj.nig.ac.jp"))
            throw new AssertionError("issuer does not contain sc.ddbj.nig.ac.jp");
        if (body.contains("192.168.5.") || body.contains("172.19.67."))
            throw new AssertionError("issuer still contains old IP address");

        System.out.println("PASSED: OIDC issuer is sc.ddbj.nig.ac.jp (no old IP)");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** dashboardにアクセスしてKeycloakログイン画面まで遷移する。 */
    private void navigateToKeycloakLogin() {
        navigateTo(BASE + "/dashboard");
        page.waitForURL("**/op-auth/**", new Page.WaitForURLOptions().setTimeout(10000));
        assertUrlContains("/op-auth/realms/submission");
    }
}
