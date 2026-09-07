import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const apiDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(apiDirectory, "../..");
const collection = path.join(apiDirectory, "soft-shop-microservices.postman_collection.json");
const reportDirectory = path.join(apiDirectory, "reports", "microservices");
const composeFile = path.join(projectRoot, "deploy", "docker-compose.yml");
const composeOverride = path.join(projectRoot, "deploy", "docker-compose.api-test.yml");
const envFile = path.join(projectRoot, "deploy", ".env");
const newmanCli = path.join(projectRoot, "node_modules", "newman", "bin", "newman.js");
const projectName = process.env.MICROSERVICES_TEST_PROJECT || "soft-shop-api-test";
const args = new Set(process.argv.slice(2));
const startCompose = args.has("--start-compose");
const keepEnvironment = args.has("--keep-environment") || process.env.KEEP_TEST_ENVIRONMENT === "true";
const readinessTimeoutMs = Number(process.env.MICROSERVICES_READY_TIMEOUT_MS || 180_000);

function argument(name, fallback) {
  const prefix = `--${name}=`;
  const value = process.argv.slice(2).find((item) => item.startsWith(prefix));
  return value ? value.slice(prefix.length) : fallback;
}

const urls = {
  user_url: argument("user-url", process.env.USER_SERVICE_URL || "http://127.0.0.1:8081"),
  catalog_url: argument("catalog-url", process.env.CATALOG_SERVICE_URL || "http://127.0.0.1:8082"),
  trade_url: argument("trade-url", process.env.TRADE_SERVICE_URL || "http://127.0.0.1:8083"),
  interaction_url: argument("interaction-url", process.env.INTERACTION_SERVICE_URL || "http://127.0.0.1:8084"),
  gateway_url: argument("gateway-url", process.env.GATEWAY_URL || "http://127.0.0.1:18080"),
};
// 经 Nginx 网关访问时，/api/interaction/health 未暴露（会回落到单体报 500），
// 可用 --interaction-health-path=/api/topics 指定网关可路由的探活路径。
const interactionHealthPath = argument("interaction-health-path", process.env.INTERACTION_HEALTH_PATH || "/api/interaction/health");
if (startCompose && !process.env.HTTP_PORT) {
  process.env.HTTP_PORT = new URL(urls.gateway_url).port || "80";
}

function run(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, {
    cwd: projectRoot,
    stdio: options.capture ? "pipe" : "inherit",
    encoding: options.capture ? "utf8" : undefined,
    env: process.env,
  });
  if (result.error) throw result.error;
  if (!options.allowFailure && result.status !== 0) {
    throw new Error(`${command} exited with code ${result.status ?? 1}`);
  }
  return result;
}

function resolveGitCommit() {
  const fromEnvironment = [
    process.env.GIT_COMMIT,
    process.env.COMMIT_ID,
    process.env.CODEARTS_COMMIT_ID,
    process.env.CI_COMMIT_SHA,
  ].find((value) => value && value.trim());
  if (fromEnvironment) return fromEnvironment.trim();

  const result = spawnSync("git", ["rev-parse", "HEAD"], {
    cwd: projectRoot,
    stdio: "pipe",
    encoding: "utf8",
    env: process.env,
  });
  if (!result.error && result.status === 0 && result.stdout?.trim()) {
    return result.stdout.trim();
  }
  return "unavailable";
}

function writeEnvironmentFailure(error) {
  fs.mkdirSync(reportDirectory, { recursive: true });
  const message = error?.stack || error?.message || String(error);
  fs.writeFileSync(
    path.join(reportDirectory, "failure-summary.md"),
    `# Microservices E2E environment failure\n\n${message}\n`,
    "utf8",
  );
}

const composeArgs = [
  "compose", "--project-name", projectName, "--env-file", envFile,
  "-f", composeFile, "-f", composeOverride,
];

function compose(...commandArgs) {
  return run("docker", [...composeArgs, ...commandArgs]);
}

async function waitFor(name, url, acceptedStatuses = [200]) {
  const started = Date.now();
  let lastError = "not attempted";
  while (Date.now() - started < readinessTimeoutMs) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3_000) });
      if (acceptedStatuses.includes(response.status)) {
        console.log(`[ready] ${name}: ${url} (${response.status})`);
        return;
      }
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = `${error.message} cause=${error.cause ? (error.cause.code || error.cause.message) : "none"} url=${url}`;
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  throw new Error(`${name} was not ready after ${readinessTimeoutMs} ms: ${lastError}`);
}

function collectComposeDiagnostics() {
  if (!startCompose) return;
  console.error("Collecting isolated Compose diagnostics...");
  run("docker", [...composeArgs, "ps"], { allowFailure: true });
  run("docker", [...composeArgs, "logs", "--no-color", "--tail", "200",
    "user-service", "catalog-service", "trade-service", "interaction-service"], { allowFailure: true });
}

function cleanup() {
  if (!startCompose || keepEnvironment) return;
  console.log("Stopping isolated API test environment...");
  run("docker", [...composeArgs, "down", "--volumes", "--remove-orphans"], { allowFailure: true });
}

function writeFailureSummary(reportPath, summaryPath) {
  const report = JSON.parse(fs.readFileSync(reportPath, "utf8"));
  const failures = report.run?.failures ?? [];
  const lines = [
    "# Microservices E2E failure summary",
    "",
    `Generated: ${new Date().toISOString()}`,
    `Failures: ${failures.length}`,
    "",
  ];
  for (const failure of failures) {
    const source = failure.source ?? {};
    const request = source.request ?? failure.parent?.request ?? {};
    const url = request.url?.raw ?? request.url ?? "unknown";
    lines.push(`## ${source.name ?? failure.error?.name ?? "Unknown failure"}`);
    lines.push(`- Request: ${request.method ?? "unknown"} ${url}`);
    lines.push(`- Assertion: ${failure.error?.test ?? failure.error?.message ?? "unknown"}`);
    lines.push(`- Actual: ${failure.error?.message ?? "No error message"}`);
    lines.push("");
  }
  if (failures.length === 0) lines.push("No Newman assertion failures were recorded. Check environment diagnostics.");
  fs.writeFileSync(summaryPath, `${lines.join("\n")}\n`, "utf8");
}

let exitCode = 0;
try {
  if (!fs.existsSync(newmanCli)) {
    throw new Error("Newman is not installed. Run `npm ci` first.");
  }
  if (startCompose) {
    if (!fs.existsSync(envFile)) throw new Error("deploy/.env is required for the isolated Compose test environment.");
    compose("up", "-d", "--build", "mysql", "backend", "user-service", "catalog-service", "trade-service", "interaction-service", "frontend");
  }

  await waitFor("user-service", `${urls.user_url}/api/auth/search-users?keyword=`, [200]);
  await waitFor("catalog-service", `${urls.catalog_url}/api/products`, [200]);
  await waitFor("trade-service", `${urls.trade_url}/api/cart`, [401]);
  await waitFor("interaction-service", `${urls.interaction_url}${interactionHealthPath}`, [200]);
  if (startCompose) await waitFor("gateway", `${urls.gateway_url}/api/products`, [200]);

  fs.mkdirSync(reportDirectory, { recursive: true });
  const metadata = {
    startedAt: new Date().toISOString(),
    collection: path.relative(projectRoot, collection),
    urls,
    isolatedCompose: startCompose,
    gitCommit: resolveGitCommit(),
  };
  fs.writeFileSync(path.join(reportDirectory, "run-metadata.json"), `${JSON.stringify(metadata, null, 2)}\n`);

  const result = run(process.execPath, [
    newmanCli, "run", collection,
    ...Object.entries(urls).flatMap(([key, value]) => ["--env-var", `${key}=${value}`]),
    "--timeout-request", "10000",
    "--reporters", "cli,junit,htmlextra,json",
    "--reporter-junit-export", path.join(reportDirectory, "newman-report.xml"),
    "--reporter-htmlextra-export", path.join(reportDirectory, "newman-report.html"),
    "--reporter-json-export", path.join(reportDirectory, "newman-report.json"),
  ], { allowFailure: true });
  writeFailureSummary(
    path.join(reportDirectory, "newman-report.json"),
    path.join(reportDirectory, "failure-summary.md"),
  );
  exitCode = result.status ?? 1;
  metadata.finishedAt = new Date().toISOString();
  metadata.exitCode = exitCode;
  fs.writeFileSync(path.join(reportDirectory, "run-metadata.json"), `${JSON.stringify(metadata, null, 2)}\n`);
  if (exitCode !== 0) {
    collectComposeDiagnostics();
  }
} catch (error) {
  exitCode = 1;
  console.error(error.stack || error.message);
  writeEnvironmentFailure(error);
  collectComposeDiagnostics();
} finally {
  cleanup();
}

process.exit(exitCode);
