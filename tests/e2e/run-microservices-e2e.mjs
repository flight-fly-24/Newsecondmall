import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const testDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(testDirectory, "../..");
const apiReportDirectory = path.join(projectRoot, "tests", "api", "reports", "microservices");
const uiTargetDirectory = path.join(projectRoot, "e2e-tests", "target");
const artifactDirectory = path.join(uiTargetDirectory, "e2e-artifacts");
const resultDirectory = path.join(projectRoot, "tests", "e2e", "results");
const envFile = path.join(projectRoot, "deploy", ".env");
const projectName = process.env.E2E_PROJECT_NAME || "soft-shop-microservices-e2e";
const networkName = process.env.E2E_NETWORK_NAME || `${projectName}-net`;
const hostPort = process.env.E2E_HTTP_PORT || "18080";
const timeoutMs = Number(process.env.E2E_READY_TIMEOUT_MS || 360_000);
const keepEnvironment = process.env.KEEP_TEST_ENVIRONMENT === "true";
const mavenImage = process.env.E2E_MAVEN_IMAGE || "maven:3.9.11-eclipse-temurin-17";

const composeArgs = [
  "compose", "--project-name", projectName, "--env-file", envFile,
  "-f", path.join(projectRoot, "deploy", "docker-compose.yml"),
  "-f", path.join(testDirectory, "docker-compose.e2e.yml"),
  "-f", path.join(projectRoot, "deploy", "docker-compose.api-test.yml"),
];
const environment = {
  ...process.env,
  HTTP_PORT: hostPort,
  E2E_NETWORK_NAME: networkName,
  IMAGE_TAG: process.env.IMAGE_TAG || "microservices-e2e",
};

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    env: environment,
    encoding: "utf8",
    stdio: options.capture ? "pipe" : "inherit",
  });
  if (result.error) throw result.error;
  if (!options.allowFailure && result.status !== 0) {
    throw new Error(`${command} failed with exit code ${result.status ?? 1}`);
  }
  return result;
}

function compose(args, options = {}) {
  return run("docker", [...composeArgs, ...args], options);
}

async function probe(name, url, statuses) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(4_000) });
    return statuses.includes(response.status) ? null : `${name}: HTTP ${response.status}`;
  } catch (error) {
    return `${name}: ${error.message}`;
  }
}

async function waitUntilReady() {
  const endpoints = [
    ["user-service", "http://127.0.0.1:8081/api/auth/search-users?keyword=", [200]],
    ["catalog-service", "http://127.0.0.1:8082/api/products", [200]],
    ["trade-service", "http://127.0.0.1:8083/api/cart", [401]],
    ["interaction-service", "http://127.0.0.1:8084/api/interaction/health", [200]],
    ["frontend gateway", `http://127.0.0.1:${hostPort}/api/products`, [200]],
  ];
  let failures = [];
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const stateResult = compose(["ps", "--all", "--format", "json"], { allowFailure: true, capture: true });
    const unhealthy = [];
    for (const line of stateResult.stdout.trim().split(/\r?\n/).filter(Boolean)) {
      try {
        const entries = Array.isArray(JSON.parse(line)) ? JSON.parse(line) : [JSON.parse(line)];
        for (const entry of entries) {
          const state = String(entry.State ?? "").toLowerCase();
          if (["restarting", "exited", "dead"].includes(state)) unhealthy.push(`${entry.Service}: ${entry.State}`);
        }
      } catch {
        // Older Compose versions may not support JSON output; endpoint probes remain authoritative.
      }
    }
    if (unhealthy.length > 0) throw new Error(`Services stopped before readiness:\n${unhealthy.join("\n")}`);
    failures = (await Promise.all(endpoints.map(([name, url, statuses]) => probe(name, url, statuses))))
      .filter(Boolean);
    const selenium = compose(["exec", "-T", "selenium", "curl", "-fsS", "http://127.0.0.1:4444/status"], {
      allowFailure: true, capture: true,
    });
    if (selenium.status !== 0 || !selenium.stdout.includes('"ready": true')) failures.push("selenium: not ready");
    if (failures.length === 0) return;
    await new Promise((resolve) => setTimeout(resolve, 5_000));
  }
  throw new Error(`Environment did not become ready within ${timeoutMs} ms:\n${failures.join("\n")}`);
}

function collectDiagnostics(reason) {
  fs.mkdirSync(artifactDirectory, { recursive: true });
  fs.mkdirSync(resultDirectory, { recursive: true });
  const ps = compose(["ps", "--all"], { allowFailure: true, capture: true });
  const logs = compose(["logs", "--no-color", "--timestamps"], { allowFailure: true, capture: true });
  fs.writeFileSync(path.join(artifactDirectory, "compose-ps.txt"), `${ps.stdout || ""}${ps.stderr || ""}`);
  fs.writeFileSync(path.join(artifactDirectory, "compose.log"), `${logs.stdout || ""}${logs.stderr || ""}`);
  const importantLogLines = `${logs.stdout || ""}${logs.stderr || ""}`.split(/\r?\n/)
    .filter((line) => /SQLSyntaxErrorException|ScriptStatementFailedException|Failed to execute SQL|APPLICATION FAILED TO START|Connection refused/i.test(line))
    .slice(-80);
  fs.writeFileSync(path.join(resultDirectory, "service-failures.md"), [
    "# Service failure signals", "", ...importantLogLines.map((line) => `- ${line}`),
    ...(importantLogLines.length === 0 ? ["No known startup failure signature was found; inspect compose.log."] : []), "",
  ].join("\n"));
  fs.writeFileSync(path.join(resultDirectory, "environment-summary.md"),
    `# Microservices E2E environment\n\nResult: ${reason}\n\nProject: ${projectName}\nNetwork: ${networkName}\nGateway: http://127.0.0.1:${hostPort}\n`);
}

function runApiTests() {
  run(process.execPath, [path.join(projectRoot, "tests", "api", "build-microservices-collection.mjs")]);
  return run(process.execPath, [
    path.join(projectRoot, "tests", "api", "run-microservices-newman.mjs"),
    "--user-url=http://127.0.0.1:8081",
    "--catalog-url=http://127.0.0.1:8082",
    "--trade-url=http://127.0.0.1:8083",
    "--interaction-url=http://127.0.0.1:8084",
    `--gateway-url=http://127.0.0.1:${hostPort}`,
  ], { allowFailure: true });
}

function runUiTests() {
  return run("docker", [
    "run", "--rm", "--network", networkName,
    "-v", `${projectRoot}:/work`,
    "-v", "soft-shop-e2e-m2-cache:/root/.m2",
    "-w", "/work/e2e-tests",
    "-e", "LANG=C.UTF-8", "-e", "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8",
    mavenImage, "mvn", "-B", "-ntp", "clean", "test",
    "-De2e.remoteUrl=http://selenium:4444/wd/hub",
    "-De2e.baseUrl=http://frontend", "-De2e.apiUrl=http://frontend",
    "-De2e.headless=true", "-De2e.timeoutSeconds=20",
  ], { allowFailure: true });
}

async function seedUiFixture() {
  const gateway = `http://127.0.0.1:${hostPort}`;
  const login = await fetch(`${gateway}/api/auth/login`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "seller", password: "seller123" }),
  });
  if (!login.ok) throw new Error(`UI fixture seller login failed: HTTP ${login.status}`);
  const loginBody = await login.json();
  const token = loginBody?.data?.token;
  if (!token) throw new Error("UI fixture seller login returned no token");
  const suffix = Date.now().toString(36);
  const publish = await fetch(`${gateway}/api/products`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      scene: "used", title: `自动化测试商品-${suffix}`,
      image: "https://example.test/e2e-product.png", category: "数码", price: 99.90,
      condition: "九成新", description: "Selenium 自动化固定商品", story: "automation",
      floorPrice: 80, location: "武汉",
    }),
  });
  if (!publish.ok) throw new Error(`UI fixture product publish failed: HTTP ${publish.status} ${await publish.text()}`);
  const productId = (await publish.json())?.data?.id;
  const adminLogin = await fetch(`${gateway}/api/auth/login`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "admin", password: "admin123" }),
  });
  if (!adminLogin.ok) throw new Error(`UI fixture admin login failed: HTTP ${adminLogin.status}`);
  const adminToken = (await adminLogin.json())?.data?.token;
  const approve = await fetch(`${gateway}/api/admin/audit/${productId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${adminToken}` },
    body: JSON.stringify({ action: "approve", reason: "Selenium fixture" }),
  });
  if (!approve.ok) throw new Error(`UI fixture product approval failed: HTTP ${approve.status} ${await approve.text()}`);
  console.log(`[fixture] UI product created: 自动化测试商品-${suffix}`);
}

let exitCode = 1;
let result = "environment failure";
try {
  if (!fs.existsSync(envFile)) throw new Error("deploy/.env is required; copy deploy/.env.example and configure it first");
  fs.rmSync(apiReportDirectory, { recursive: true, force: true });
  fs.rmSync(resultDirectory, { recursive: true, force: true });
  compose(["down", "--volumes", "--remove-orphans"], { allowFailure: true });
  compose(["up", "-d", "--build"]);
  await waitUntilReady();
  const api = runApiTests();
  if (api.status !== 0) {
    result = `API tests failed with exit code ${api.status ?? 1}; UI tests were skipped`;
    exitCode = api.status ?? 1;
  } else {
    await seedUiFixture();
    const ui = runUiTests();
    exitCode = ui.status ?? 1;
    result = exitCode === 0 ? "passed" : `UI tests failed with exit code ${exitCode}`;
  }
} catch (error) {
  result = error.stack || error.message;
  console.error(result);
  exitCode = 1;
} finally {
  collectDiagnostics(result);
  fs.mkdirSync(resultDirectory, { recursive: true });
  fs.writeFileSync(path.join(resultDirectory, "exit-code.txt"), `${exitCode}\n`);
  if (!keepEnvironment) compose(["down", "--volumes", "--remove-orphans"], { allowFailure: true });
}

process.exit(exitCode);
