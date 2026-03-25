package jp.ac.nig.e2e;

import jp.ac.nig.e2e.ac_account.AcAccountE2ETest;
import jp.ac.nig.e2e.base.E2EConfig;
import jp.ac.nig.e2e.base.E2ETest;
import jp.ac.nig.e2e.base.E2ETestBase;
import jp.ac.nig.e2e.federation.FederationSsoE2ETest;
import jp.ac.nig.e2e.op_account.OpAccountE2ETest;
import jp.ac.nig.e2e.sc_account.ScAccountE2ETest;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * E2Eテスト実行エントリポイント。
 *
 * 使い方:
 *   # 全テスト実行
 *   mvn compile exec:java
 *
 *   # 特定クラスのみ実行
 *   mvn compile exec:java -Dexec.args="ScAccountE2ETest"
 *   mvn compile exec:java -Dexec.args="OpAccountE2ETest"
 *   mvn compile exec:java -Dexec.args="AcAccountE2ETest"
 *
 *   # 環境変数でホストを変更
 *   E2E_HOST=https://172.19.67.209 mvn compile exec:java
 */
public class E2ETestRunner {

    private static final List<Class<? extends E2ETestBase>> ALL_TESTS = List.of(
        ScAccountE2ETest.class,
        OpAccountE2ETest.class,
        AcAccountE2ETest.class,
        FederationSsoE2ETest.class
    );

    private int passed = 0;
    private int failed = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        E2EConfig.printConfig();
        Files.createDirectories(Paths.get(E2EConfig.SCREENSHOT_DIR));

        E2ETestRunner runner = new E2ETestRunner();
        if (args.length > 0) {
            runner.runByName(args[0]);
        } else {
            runner.runAll();
        }
        runner.printSummary();
        System.exit(runner.failed > 0 ? 1 : 0);
    }

    private void runAll() {
        for (Class<? extends E2ETestBase> cls : ALL_TESTS) {
            runClass(cls);
        }
    }

    private void runByName(String name) {
        for (Class<? extends E2ETestBase> cls : ALL_TESTS) {
            if (cls.getSimpleName().equals(name)) {
                runClass(cls);
                return;
            }
        }
        System.err.println("Unknown test class: " + name);
        System.err.println("Available: " + ALL_TESTS.stream()
            .map(Class::getSimpleName).toList());
        System.exit(1);
    }

    private void runClass(Class<? extends E2ETestBase> cls) {
        System.out.println("\n=== " + cls.getSimpleName() + " ===");
        try {
            E2ETestBase.initBrowser();
            E2ETestBase instance = cls.getDeclaredConstructor().newInstance();
            for (Method m : cls.getMethods()) {
                if (m.isAnnotationPresent(E2ETest.class)) {
                    runMethod(instance, m);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize: " + e.getMessage());
        } finally {
            E2ETestBase.closeBrowser();
        }
    }

    private void runMethod(E2ETestBase instance, Method m) {
        String name = instance.getClass().getSimpleName() + "." + m.getName();
        E2ETest annotation = m.getAnnotation(E2ETest.class);
        System.out.printf("  %-60s ... ", annotation.description());
        boolean failed = false;
        try {
            instance.setUp();
            m.invoke(instance);
            passed++;
        } catch (Exception e) {
            failed = true;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            System.out.println("FAILED");
            System.out.println("      " + cause.getMessage());
            failures.add(name + ": " + cause.getMessage());
            this.failed++;
        } finally {
            try { instance.tearDown(name, failed); } catch (Exception ignore) {}
        }
    }

    private void printSummary() {
        System.out.println("\n=== Summary ===");
        System.out.printf("Passed: %d  Failed: %d  Total: %d%n", passed, failed, passed + failed);
        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            failures.forEach(f -> System.out.println("  - " + f));
        }
        System.out.println("===============");
    }
}
