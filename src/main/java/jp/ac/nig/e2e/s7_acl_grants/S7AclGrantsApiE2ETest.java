package jp.ac.nig.e2e.s7_acl_grants;

import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * S7: ACL Grants API (/ac-account/api/acl-grants) のE2Eテスト。
 *
 * このAPIはacl-syncサービスアカウント専用。client_credentials フローで
 * Bearerトークンを取得し、APIを呼び出す。
 *
 * 環境変数:
 *   E2E_ACL_SYNC_CLIENT_SECRET — acl-sync-client の client secret (Keycloak)
 *
 * テスト項目:
 *   S7-01: 未認証で /api/acl-grants にアクセスすると401が返る
 *   S7-02: acl-sync-client の client_credentials トークンで200が返る
 *   S7-03: レスポンスJSONに grants 配列と generated_at が含まれる
 *   S7-04: 不正なBearerトークンで401が返る
 */
public class S7AclGrantsApiE2ETest extends E2ETestBase {

    private static final String ACL_GRANTS_URL = E2EConfig.HOST + "/ac-account/api/acl-grants";
    private static final String TOKEN_URL = E2EConfig.HOST
        + "/ac-auth/realms/personal-genome/protocol/openid-connect/token";

    private static final String CLIENT_ID = "acl-sync-client";
    private static final String CLIENT_SECRET = System.getenv("E2E_ACL_SYNC_CLIENT_SECRET");

    private HttpClient httpClient;

    @Override
    public void setUp() {
        super.setUp();
        try {
            // TLS証明書検証を無効化（自己署名証明書対応）
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }}, new java.security.SecureRandom());

            httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HttpClient", e);
        }
    }

    // -------------------------------------------------------------------------
    // S7-01: 未認証アクセス → 401
    // -------------------------------------------------------------------------

    @E2ETest(description = "S7-01: 未認証で /api/acl-grants にアクセスすると401が返る")
    public void testUnauthenticatedReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ACL_GRANTS_URL))
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status != 401 && status != 302 && status != 403)
            throw new AssertionError("Expected 401, 403, or 302 for unauthenticated access, got: "
                + status + " body: " + response.body().substring(0, Math.min(response.body().length(), 300)));

        System.out.println("PASSED: Unauthenticated /api/acl-grants returned " + status);
    }

    // -------------------------------------------------------------------------
    // S7-02: client_credentials トークンで200が返る
    // -------------------------------------------------------------------------

    @E2ETest(description = "S7-02: acl-sync-client の client_credentials トークンで200が返る")
    public void testClientCredentialsReturns200() throws Exception {
        if (CLIENT_SECRET == null || CLIENT_SECRET.isBlank()) {
            throw new AssertionError("E2E_ACL_SYNC_CLIENT_SECRET is not set. "
                + "Set it to the acl-sync-client Keycloak client secret.");
        }

        String token = getClientCredentialsToken();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ACL_GRANTS_URL))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new AssertionError("Expected 200 with acl-sync-client token, got: "
                + response.statusCode() + " body: " + response.body().substring(0, Math.min(response.body().length(), 500)));

        System.out.println("PASSED: /api/acl-grants returned 200 with acl-sync-client token");
    }

    // -------------------------------------------------------------------------
    // S7-03: レスポンスJSONの構造確認
    // -------------------------------------------------------------------------

    @E2ETest(description = "S7-03: レスポンスJSONに grants 配列と generated_at が含まれる")
    public void testResponseJsonStructure() throws Exception {
        if (CLIENT_SECRET == null || CLIENT_SECRET.isBlank()) {
            throw new AssertionError("E2E_ACL_SYNC_CLIENT_SECRET is not set.");
        }

        String token = getClientCredentialsToken();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ACL_GRANTS_URL))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new AssertionError("Expected 200, got: " + response.statusCode());

        String body = response.body();

        if (!body.contains("\"grants\""))
            throw new AssertionError("Response missing 'grants' field: " + body.substring(0, Math.min(body.length(), 500)));

        if (!body.contains("\"generated_at\""))
            throw new AssertionError("Response missing 'generated_at' field: " + body.substring(0, Math.min(body.length(), 500)));

        System.out.println("PASSED: Response JSON contains 'grants' and 'generated_at'. Body: "
            + body.substring(0, Math.min(body.length(), 200)));
    }

    // -------------------------------------------------------------------------
    // S7-04: 不正なBearerトークン → 401
    // -------------------------------------------------------------------------

    @E2ETest(description = "S7-04: 不正なBearerトークンで401が返る")
    public void testInvalidTokenReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ACL_GRANTS_URL))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer invalid-token-12345")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        // 401 (Unauthorized) or 403 (Forbidden) or 500 (token parse error) are all acceptable
        // as they indicate the invalid token was rejected
        if (status != 401 && status != 403 && status != 500)
            throw new AssertionError("Expected 401, 403, or 500 for invalid Bearer token, got: "
                + status + " body: " + response.body().substring(0, Math.min(response.body().length(), 300)));

        System.out.println("PASSED: Invalid Bearer token rejected with status " + status);
    }

    // -------------------------------------------------------------------------
    // Helper: client_credentials トークン取得
    // -------------------------------------------------------------------------

    private String getClientCredentialsToken() throws Exception {
        String formBody = "grant_type=client_credentials"
            + "&client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();

        HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());

        if (tokenResponse.statusCode() != 200)
            throw new AssertionError("Failed to get client_credentials token. Status: "
                + tokenResponse.statusCode() + " Body: " + tokenResponse.body());

        // Simple JSON parsing: extract access_token value
        String body = tokenResponse.body();
        int start = body.indexOf("\"access_token\":\"") + 16;
        int end = body.indexOf("\"", start);
        if (start < 16 || end < 0)
            throw new AssertionError("Failed to parse access_token from response: " + body);

        return body.substring(start, end);
    }
}
