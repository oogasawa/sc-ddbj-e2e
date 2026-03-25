package jp.ac.nig.e2e.s8_acl_sync;

import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * S8: ACL Sync 内部 Gateway のE2Eテスト。
 *
 * ac-internal-gateway (NodePort 31254) は acl-sync が計算ノードから
 * AC-account API と AC Keycloak にアクセスするための内部専用ゲートウェイ。
 *
 * ※ 内部GatewayのNodePortは通常外部から直接到達できない。
 *    テストにはSSHトンネルが必要:
 *    ssh -L 31254:localhost:31254 -J oogasawa-pg@gwa3.ddbj.nig.ac.jp oogasawa-pg@a007
 *
 * 環境変数:
 *   E2E_INTERNAL_GW_HOST — 内部Gatewayのホスト (デフォルト: http://localhost:31254)
 *   E2E_ACL_SYNC_CLIENT_SECRET — acl-sync-client の client secret
 *
 * テスト項目:
 *   S8-01: 内部Gateway経由で AC Keycloak の OIDC discovery が取得できる
 *   S8-02: 内部Gateway経由で acl-grants API が到達可能
 *   S8-03: 内部Gateway経由で client_credentials トークンを取得し acl-grants API で200が返る
 */
public class S8AclSyncGatewayE2ETest extends E2ETestBase {

    private static final String INTERNAL_GW = getEnvOrDefault("E2E_INTERNAL_GW_HOST", "http://localhost:31254");
    private static final String CLIENT_SECRET = System.getenv("E2E_ACL_SYNC_CLIENT_SECRET");

    private HttpClient httpClient;

    @Override
    public void setUp() {
        super.setUp();
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());

            httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HttpClient", e);
        }
    }

    // -------------------------------------------------------------------------
    // S8-01: 内部Gateway経由で Keycloak OIDC discovery
    // -------------------------------------------------------------------------

    @E2ETest(description = "S8-01: 内部Gateway経由でAC Keycloak OIDC discoveryが取得できる")
    public void testInternalGatewayKeycloakDiscovery() throws Exception {
        String url = INTERNAL_GW + "/ac-auth/realms/personal-genome/.well-known/openid-configuration";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new AssertionError("Cannot connect to internal gateway at " + INTERNAL_GW
                + ". Ensure SSH tunnel is active: "
                + "ssh -L 31254:localhost:31254 -J oogasawa-pg@gwa3.ddbj.nig.ac.jp oogasawa-pg@a007");
        }

        if (response.statusCode() != 200)
            throw new AssertionError("Internal gateway Keycloak discovery returned " + response.statusCode()
                + " body: " + response.body().substring(0, Math.min(response.body().length(), 300)));

        String body = response.body();
        if (!body.contains("openid-configuration") && !body.contains("issuer"))
            throw new AssertionError("Response does not look like OIDC discovery: "
                + body.substring(0, Math.min(body.length(), 300)));

        System.out.println("PASSED: Internal gateway → Keycloak OIDC discovery returned 200");
    }

    // -------------------------------------------------------------------------
    // S8-02: 内部Gateway経由で acl-grants API に到達可能
    // -------------------------------------------------------------------------

    @E2ETest(description = "S8-02: 内部Gateway経由で acl-grants API に到達可能（401=認証必要=ルーティングOK）")
    public void testInternalGatewayAclGrantsReachable() throws Exception {
        String url = INTERNAL_GW + "/ac-account/api/acl-grants";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new AssertionError("Cannot connect to internal gateway at " + INTERNAL_GW
                + ". Ensure SSH tunnel is active.");
        }

        // 401 = ルーティングは成功しているが認証が必要 → OK
        // 200 = 認証不要でアクセスできた → NG（セキュリティ的にまずい）
        // 404/502/503 = ルーティング失敗 → NG
        if (response.statusCode() == 401 || response.statusCode() == 302) {
            System.out.println("PASSED: Internal gateway → /ac-account/api/acl-grants returned "
                + response.statusCode() + " (routing OK, auth required)");
        } else if (response.statusCode() == 200) {
            throw new AssertionError("acl-grants API returned 200 without authentication — security issue!");
        } else {
            throw new AssertionError("Internal gateway → acl-grants returned unexpected status: "
                + response.statusCode() + " body: " + response.body().substring(0, Math.min(response.body().length(), 300)));
        }
    }

    // -------------------------------------------------------------------------
    // S8-03: 内部Gateway経由で完全なacl-syncフロー（トークン取得→API呼び出し）
    // -------------------------------------------------------------------------

    @E2ETest(description = "S8-03: 内部Gateway経由で client_credentials → acl-grants API 200")
    public void testInternalGatewayFullFlow() throws Exception {
        if (CLIENT_SECRET == null || CLIENT_SECRET.isBlank()) {
            throw new AssertionError("E2E_ACL_SYNC_CLIENT_SECRET is not set.");
        }

        // Step 1: 内部Gateway経由でKeycloakからトークン取得
        String tokenUrl = INTERNAL_GW
            + "/ac-auth/realms/personal-genome/protocol/openid-connect/token";

        String formBody = "grant_type=client_credentials"
            + "&client_id=" + URLEncoder.encode("acl-sync-client", StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        HttpResponse<String> tokenResponse;
        try {
            tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new AssertionError("Cannot connect to internal gateway. Ensure SSH tunnel is active.");
        }

        if (tokenResponse.statusCode() != 200)
            throw new AssertionError("Token request via internal gateway failed. Status: "
                + tokenResponse.statusCode() + " Body: " + tokenResponse.body());

        // Extract access_token
        String tokenBody = tokenResponse.body();
        int start = tokenBody.indexOf("\"access_token\":\"") + 16;
        int end = tokenBody.indexOf("\"", start);
        if (start < 16 || end < 0)
            throw new AssertionError("Failed to parse access_token: " + tokenBody);
        String token = tokenBody.substring(start, end);

        System.out.println("  [S8-03] Token obtained via internal gateway");

        // Step 2: 内部Gateway経由でacl-grants API呼び出し
        String grantsUrl = INTERNAL_GW + "/ac-account/api/acl-grants";

        HttpRequest grantsRequest = HttpRequest.newBuilder()
            .uri(URI.create(grantsUrl))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> grantsResponse = httpClient.send(grantsRequest, HttpResponse.BodyHandlers.ofString());

        if (grantsResponse.statusCode() != 200)
            throw new AssertionError("acl-grants API via internal gateway returned "
                + grantsResponse.statusCode() + " body: " + grantsResponse.body());

        String body = grantsResponse.body();
        if (!body.contains("\"grants\"") || !body.contains("\"generated_at\""))
            throw new AssertionError("Response missing expected fields: " + body.substring(0, Math.min(body.length(), 500)));

        System.out.println("PASSED: Full acl-sync flow via internal gateway succeeded. Response: "
            + body.substring(0, Math.min(body.length(), 200)));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static String getEnvOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return v != null ? v : defaultValue;
    }
}
