import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const enforce = process.argv.includes("--enforce");
const modules = ["common", "user-service", "catalog-service", "trade-service", "interaction-service"];
const resultDirectory = path.join(root, "tests", "unit", "results");
fs.mkdirSync(resultDirectory, { recursive: true });

function xmlAttribute(text, name) {
  const match = text.match(new RegExp(`${name}="(\\d+)"`));
  return match ? Number(match[1]) : 0;
}

function coverage(module) {
  const csv = path.join(root, "services", module, "target", "site", "jacoco", "jacoco.csv");
  if (!fs.existsSync(csv)) return { line: null, branch: null };
  const rows = fs.readFileSync(csv, "utf8").trim().split(/\r?\n/);
  const headers = rows.shift().split(",");
  const totals = Object.fromEntries(headers.map((header) => [header, 0]));
  for (const row of rows) row.split(",").forEach((value, index) => { totals[headers[index]] += Number(value) || 0; });
  const ratio = (covered, missed) => covered + missed === 0 ? null : 100 * covered / (covered + missed);
  return {
    line: ratio(totals.LINE_COVERED, totals.LINE_MISSED),
    branch: ratio(totals.BRANCH_COVERED, totals.BRANCH_MISSED),
  };
}

const summaries = modules.map((module) => {
  const reportDirs = ["surefire-reports", "failsafe-reports"]
    .map((name) => path.join(root, "services", module, "target", name))
    .filter((directory) => fs.existsSync(directory));
  const xmls = reportDirs.flatMap((directory) => fs.readdirSync(directory)
    .filter((name) => name.startsWith("TEST-") && name.endsWith(".xml"))
    .map((name) => path.join(directory, name)));
  const counts = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  const causes = [];
  for (const name of xmls) {
    const content = fs.readFileSync(name, "utf8");
    for (const key of Object.keys(counts)) counts[key] += xmlAttribute(content, key);
    for (const match of content.matchAll(/<(?:failure|error)[^>]*message="([^"]+)"/g)) {
      causes.push(match[1].replace(/&#10;|&#13;/g, " ").replaceAll("&quot;", '"')
        .replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&amp;", "&").slice(0, 600));
    }
  }
  return { module, ...counts, coverage: coverage(module), causes: [...new Set(causes)].slice(0, 3) };
});

const pct = (value) => value == null ? "N/A" : `${value.toFixed(2)}%`;
const lines = [
  "# Microservice unit-test summary", "", `Generated: ${new Date().toISOString()}`, "",
  "| Module | Tests | Failures | Errors | Skipped | Line | Branch |",
  "|---|---:|---:|---:|---:|---:|---:|",
  ...summaries.map((s) => `| ${s.module} | ${s.tests} | ${s.failures} | ${s.errors} | ${s.skipped} | ${pct(s.coverage.line)} | ${pct(s.coverage.branch)} |`),
  "", "## Failure causes", "",
];
for (const summary of summaries.filter((item) => item.causes.length)) {
  lines.push(`### ${summary.module}`, ...summary.causes.map((cause) => `- ${cause}`), "");
}
if (!summaries.some((item) => item.causes.length)) lines.push("No test failures were reported.", "");
const knownUserFailure = summaries.find((item) => item.module === "user-service")?.causes
  .some((cause) => /Duplicate column name.*ROLE|ApplicationContext/i.test(cause));
if (knownUserFailure) lines.push(
  "## Known production issue", "",
  "user-service initializes `users.role` in schema.sql and AuthService.ensureSchema() attempts to add it again. The H2 context test intentionally remains failing until the service is fixed.", "",
);
const commonSerializationFailure = summaries.find((item) => item.module === "common")?.causes
  .some((cause) => /ApiResult|auth service unavailable|503 SERVICE_UNAVAILABLE/i.test(cause));
if (commonSerializationFailure) lines.push(
  "## Additional production issue", "",
  "common `AuthClient` cannot deserialize `ApiResult<AuthUser>` because `ApiResult` exposes no Jackson creator/default constructor. A successful auth response is consequently converted to HTTP 503. The test intentionally remains failing.", "",
);
fs.writeFileSync(path.join(resultDirectory, "summary.md"), `${lines.join("\n")}\n`);
fs.writeFileSync(path.join(resultDirectory, "summary.json"), `${JSON.stringify(summaries, null, 2)}\n`);
console.log(lines.slice(0, 6 + summaries.length).join("\n"));

let failed = summaries.some((item) => item.failures > 0 || item.errors > 0 || item.tests === 0);
if (enforce) {
  for (const item of summaries) {
    if (item.coverage.line == null || item.coverage.line < 60) failed = true;
    if (item.module !== "common" && (item.coverage.branch == null || item.coverage.branch < 50)) failed = true;
  }
}
process.exit(failed ? 1 : 0);
