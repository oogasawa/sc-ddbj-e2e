package jp.ac.nig.e2e.base;

/**
 * E2Eテスト設定。環境変数から読み込み、デフォルト値はNIG本番環境。
 */
public class E2EConfig {

    public static final String HOST          = getEnv("E2E_HOST",          "https://sc.ddbj.nig.ac.jp");
    public static final String MAILHOG_URL   = getEnv("E2E_MAILHOG_URL",   "http://172.19.17.7:30025");

    public static final boolean HEADLESS     = Boolean.parseBoolean(getEnv("E2E_HEADLESS",    "true"));
    public static final int     TIMEOUT_MS   = Integer.parseInt(    getEnv("E2E_TIMEOUT_MS",  "30000"));
    public static final int     SLOW_MO_MS   = Integer.parseInt(    getEnv("E2E_SLOW_MO_MS",  "0"));
    public static final String  SCREENSHOT_DIR = getEnv("E2E_SCREENSHOT_DIR", "target/e2e-screenshots");

    // テスト用アカウント (各Keycloakレルムにローカルユーザーとして作成済み)
    public static final String TEST_USERNAME = getEnv("E2E_TEST_USERNAME", "e2e-test-user");
    public static final String TEST_PASSWORD = getEnv("E2E_TEST_PASSWORD", "E2eTestPass2024!");

    private static String getEnv(String key, String defaultValue) {
        String v = System.getenv(key);
        return v != null ? v : defaultValue;
    }

    public static void printConfig() {
        System.out.println("=== E2E Configuration ===");
        System.out.println("HOST:           " + HOST);
        System.out.println("MAILHOG_URL:    " + MAILHOG_URL);
        System.out.println("HEADLESS:       " + HEADLESS);
        System.out.println("TIMEOUT_MS:     " + TIMEOUT_MS);
        System.out.println("SCREENSHOT_DIR: " + SCREENSHOT_DIR);
        System.out.println("TEST_USERNAME:  " + TEST_USERNAME);
        System.out.println("=========================");
    }
}
