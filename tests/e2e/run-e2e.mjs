import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const testDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(testDirectory, "../..");
const artifactDirectory = path.join(projectRoot, "e2e-tests", "target", "e2e-artifacts");
const baseCompose = path.join(projectRoot, "deploy", "docker-compose.yml");
const e2eCompose = path.join(testDirectory, "docker-compose.e2e.yml");
const envFile = path.join(projectRoot, "deploy", ".env");
const projectName = process.env.E2E_PROJECT_NAME || "soft-shop-e2e";
const networkName = process.env.E2E_NETWORK_NAME || "soft-shop-e2e-net";
const hostPort = process.env.E2E_HTTP_PORT || "18080";
const mavenImage = process.env.E2E_MAVEN_IMAGE || "maven:3.9.11-eclipse-temurin-17";

if (!fs.existsSync(envFile)) {
  throw new Error("deploy/.env does not exist; copy deploy/.env.example and configure it first");
}

fs.mkdirSync(artifactDirectory, { recursive: true });
const composeArgs = [
  "compose", "--project-name", projectName,
  "--env-file", envFile,
  "-f", baseCompose,
  "-f", e2eCompose,
];
const childEnvironment = {
  ...process.env,
  HTTP_PORT: hostPort,
  E2E_NETWORK_NAME: networkName,
  IMAGE_TAG: process.env.IMAGE_TAG || "e2e-local",
};

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    env: childEnvironment,
    encoding: options.capture ? "utf8" : undefined,
    stdio: options.capture ? "pipe" : "inherit",
  });
  if (result.error) throw result.error;
  if (!options.allowFailure && result.status !== 0) {
    throw new Error(`${command} failed with exit code ${result.status}`);
  }
  return result;
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function waitUntilReady() {
  const deadline = Date.now() + 360_000;
  while (Date.now() < deadline) {
    const frontend = await fetch(`http://127.0.0.1:${hostPort}/api/products`)
      .then((response) => response.ok)
      .catch(() => false);
    const selenium = run("docker", [
      ...composeArgs, "exec", "-T", "selenium", "curl", "-fsS",
      "http://127.0.0.1:4444/status",
    ], { allowFailure: true, capture: true });
    if (frontend && selenium.status === 0 && selenium.stdout.includes('"ready": true')) {
      return;
    }
    await sleep(5000);
  }
  throw new Error("E2E environment did not become healthy within 6 minutes");
}

let exitCode = 1;
try {
  run("docker", [...composeArgs, "up", "-d", "--build"]);
  await waitUntilReady();
  const result = run("docker", [
    "run", "--rm",
    "--network", networkName,
    "-v", `${projectRoot}:/work`,
    "-v", "soft-shop-e2e-m2-cache:/root/.m2",
    "-w", "/work/e2e-tests",
    "-e", "LANG=C.UTF-8",
    "-e", "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8",
    mavenImage,
    "mvn", "-B", "-ntp", "clean", "test",
    "-De2e.remoteUrl=http://selenium:4444/wd/hub",
    "-De2e.baseUrl=http://frontend",
    "-De2e.apiUrl=http://frontend",
    "-De2e.headless=true",
    "-De2e.timeoutSeconds=20",
  ], { allowFailure: true });
  exitCode = result.status ?? 1;
} catch (error) {
  console.error(error.message);
} finally {
  const logs = run("docker", [...composeArgs, "logs", "--no-color"], {
    allowFailure: true,
    capture: true,
  });
  fs.writeFileSync(
    path.join(artifactDirectory, "compose.log"),
    `${logs.stdout || ""}${logs.stderr || ""}`,
    "utf8",
  );
  run("docker", [...composeArgs, "down", "-v", "--remove-orphans"], {
    allowFailure: true,
  });
}

process.exit(exitCode);
