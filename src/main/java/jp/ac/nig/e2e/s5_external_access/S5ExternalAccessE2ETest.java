package jp.ac.nig.e2e.s5_external_access;

import com.microsoft.playwright.Page;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

/**
 * S5: 外部アクセス（HAProxy → Envoy Gateway → Pod）のE2Eテスト。
 *
 * HAProxy経由で各プロダクトに到達できることを確認する。
 * テスト実行元は 133.39.xxx.xxx（w206室）から https://sc.ddbj.nig.ac.jp へアクセス。
 *
 * テスト項目:
 *   S5-01: AC-account health endpoint が200を返す
 *   S5-02: OP-account health endpoint が200を返す
 *   S5-03: SC-account health endpoint が200を返す
 *   S5-04: AC-account ランディングページが表示される
 *   S5-05: OP-account ランディングページが表示される
 *   S5-06: SC-account ランディングページが表示される
 *   S5-07: AC-account 未認証ダッシュボードアクセスでKeycloakにリダイレクトされる
 *   S5-08: レスポンスに旧IPアドレス(192.168.5.x)が含まれていない
 */
public class S5ExternalAccessE2ETest extends E2ETestBase {

    private static final String AC_BASE = E2EConfig.HOST + "/ac-account";
    private static final String OP_BASE = E2EConfig.HOST + "/op-account";
    private static final String SC_BASE = E2EConfig.HOST + "/sc-account";

    // -------------------------------------------------------------------------
    // S5-01: AC-account health endpoint
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-01: AC-account health endpoint が200を返す")
    public void testAcAccountHealth() {
        var response = page.navigate(AC_BASE + "/q/health/ready");
        if (response == null || response.status() != 200)
            throw new AssertionError("AC-account health returned " +
                (response != null ? response.status() : "null"));
        String body = page.content();
        if (!body.contains("UP"))
            throw new AssertionError("AC-account health not UP: " + body.substring(0, Math.min(body.length(), 300)));
        System.out.println("PASSED: AC-account health endpoint returned 200 UP");
    }

    // -------------------------------------------------------------------------
    // S5-02: OP-account health endpoint
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-02: OP-account health endpoint が200を返す")
    public void testOpAccountHealth() {
        var response = page.navigate(OP_BASE + "/q/health/ready");
        if (response == null || response.status() != 200)
            throw new AssertionError("OP-account health returned " +
                (response != null ? response.status() : "null"));
        String body = page.content();
        if (!body.contains("UP"))
            throw new AssertionError("OP-account health not UP: " + body.substring(0, Math.min(body.length(), 300)));
        System.out.println("PASSED: OP-account health endpoint returned 200 UP");
    }

    // -------------------------------------------------------------------------
    // S5-03: SC-account health endpoint
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-03: SC-account health endpoint が200を返す")
    public void testScAccountHealth() {
        var response = page.navigate(SC_BASE + "/q/health/ready");
        if (response == null || response.status() != 200)
            throw new AssertionError("SC-account health returned " +
                (response != null ? response.status() : "null"));
        String body = page.content();
        if (!body.contains("UP"))
            throw new AssertionError("SC-account health not UP: " + body.substring(0, Math.min(body.length(), 300)));
        System.out.println("PASSED: SC-account health endpoint returned 200 UP");
    }

    // -------------------------------------------------------------------------
    // S5-04: AC-account ランディングページ
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-04: AC-account ランディングページが表示される")
    public void testAcAccountLanding() {
        navigateTo(AC_BASE);
        assertUrlContains("/ac-account");
        String content = page.content();
        if (content.isBlank() || content.contains("502 Bad Gateway") || content.contains("503 Service"))
            throw new AssertionError("AC-account landing page returned error");
        System.out.println("PASSED: AC-account landing page loaded via HAProxy");
    }

    // -------------------------------------------------------------------------
    // S5-05: OP-account ランディングページ
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-05: OP-account ランディングページが表示される")
    public void testOpAccountLanding() {
        navigateTo(OP_BASE);
        assertUrlContains("/op-account");
        String content = page.content();
        if (content.isBlank() || content.contains("502 Bad Gateway") || content.contains("503 Service"))
            throw new AssertionError("OP-account landing page returned error");
        System.out.println("PASSED: OP-account landing page loaded via HAProxy");
    }

    // -------------------------------------------------------------------------
    // S5-06: SC-account ランディングページ
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-06: SC-account ランディングページが表示される")
    public void testScAccountLanding() {
        navigateTo(SC_BASE);
        assertUrlContains("/sc-account");
        String content = page.content();
        if (content.isBlank() || content.contains("502 Bad Gateway") || content.contains("503 Service"))
            throw new AssertionError("SC-account landing page returned error");
        System.out.println("PASSED: SC-account landing page loaded via HAProxy");
    }

    // -------------------------------------------------------------------------
    // S5-07: 未認証ダッシュボード → Keycloakリダイレクト
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-07: AC-account 未認証ダッシュボードアクセスでKeycloakにリダイレクトされる")
    public void testAcAccountUnauthRedirect() {
        navigateTo(AC_BASE + "/dashboard");
        page.waitForURL("**/ac-auth/**", new Page.WaitForURLOptions().setTimeout(15000));
        assertUrlContains("/ac-auth/realms/personal-genome");
        System.out.println("PASSED: AC-account unauthenticated → Keycloak redirect");
    }

    // -------------------------------------------------------------------------
    // S5-08: レスポンスに旧IP(192.168.5.x)が含まれていない
    // -------------------------------------------------------------------------

    @E2ETest(description = "S5-08: レスポンスに旧IPアドレス(192.168.5.x)が含まれていない")
    public void testNoOldIpInResponse() {
        // AC Keycloak OIDC discovery
        navigateTo(E2EConfig.HOST + "/ac-auth/realms/personal-genome/.well-known/openid-configuration");
        String acBody = page.content();
        if (acBody.contains("192.168.5."))
            throw new AssertionError("AC Keycloak OIDC discovery contains old IP 192.168.5.x");

        // OP Keycloak OIDC discovery
        navigateTo(E2EConfig.HOST + "/op-auth/realms/submission/.well-known/openid-configuration");
        String opBody = page.content();
        if (opBody.contains("192.168.5."))
            throw new AssertionError("OP Keycloak OIDC discovery contains old IP 192.168.5.x");

        // SC Keycloak OIDC discovery
        navigateTo(E2EConfig.HOST + "/sc-auth/realms/sc-account/.well-known/openid-configuration");
        String scBody = page.content();
        if (scBody.contains("192.168.5."))
            throw new AssertionError("SC Keycloak OIDC discovery contains old IP 192.168.5.x");

        System.out.println("PASSED: No old IP (192.168.5.x) found in any OIDC discovery");
    }
}
