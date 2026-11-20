import { defineConfig } from "@playwright/test";
import { readdirSync } from "node:fs";
import path from "node:path";

const root = path.resolve(__dirname, "..");
const tls = process.env.TEST_TLS_SPKI;
const port = tls ? "18754" : "18753";
const baseURL = (tls ? "https" : "http") + "://127.0.0.1:" + port;
const schemas = readdirSync(path.join(root, "src/main/resources/db"))
  .filter(name => name.endsWith(".sql")).sort().map(name => "classpath:db/" + name);
const maven = process.env.TEST_MAVEN || "mvn";

export default defineConfig({
  testDir: "tests",
  workers: 1,
  use: { baseURL, channel: "chrome", viewport: { width: 1280, height: 900 },
    launchOptions: { args: tls ? ["--ignore-certificate-errors-spki-list=" + tls] : [] } },
  webServer: {
    cwd: root,
    command: `"${maven}" ${process.env.TEST_MAVEN_OPTIONS || ""} -B -ntp spring-boot:run -Dspring-boot.run.useTestClasspath=true`,
    url: baseURL,
    reuseExistingServer: process.env.TEST_REUSE_LOCAL_SERVER === "1",
    timeout: 120000,
    env: {
      SERVER_ADDRESS: "127.0.0.1", SERVER_PORT: port, HTTPS_ONLY: tls ? "true" : "false",
      SPRING_DATASOURCE_URL: "jdbc:h2:mem:browser;MODE=Oracle", SPRING_DATASOURCE_USERNAME: "sa",
      SPRING_DATASOURCE_PASSWORD: "local-test", SPRING_SQL_INIT_MODE: "always",
      SPRING_SQL_INIT_SCHEMA_LOCATIONS: schemas.join(","), SPRING_BATCH_JDBC_INITIALIZE_SCHEMA: "always"
    }
  }
});
