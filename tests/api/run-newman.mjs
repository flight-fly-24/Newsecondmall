import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const apiDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(apiDirectory, "../..");
const collection = path.join(apiDirectory, "soft-shop-api.postman_collection.json");
const reportDirectory = path.join(apiDirectory, "reports");
const fixture = path.join(apiDirectory, "fixtures", "setup.sql");
const composeFile = path.join(projectRoot, "deploy", "docker-compose.yml");
const envFile = path.join(projectRoot, "deploy", ".env");
const newmanCli = path.join(projectRoot, "node_modules", "newman", "bin", "newman.js");

const baseUrlArgument = process.argv.find((argument) => argument.startsWith("--base-url="));
const baseUrl = baseUrlArgument?.slice("--base-url=".length)
  || process.env.API_BASE_URL
  || "http://localhost";

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    stdio: options.input ? ["pipe", "inherit", "inherit"] : "inherit",
    input: options.input,
    env: process.env,
  });

  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

if (process.env.SKIP_API_FIXTURE_SETUP !== "true") {
  run(
    "docker",
    [
      "compose", "--env-file", envFile, "-f", composeFile,
      "exec", "-T", "mysql", "sh", "-lc",
      'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"',
    ],
    { input: fs.readFileSync(fixture) },
  );
}

fs.mkdirSync(reportDirectory, { recursive: true });
run(process.execPath, [
  newmanCli,
  "run", collection,
  "--env-var", `base_url=${baseUrl}`,
  "--reporters", "cli,junit,htmlextra",
  "--reporter-junit-export", path.join(reportDirectory, "newman-results.xml"),
  "--reporter-htmlextra-export", path.join(reportDirectory, "newman-report.html"),
]);
