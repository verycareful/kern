// Builds assets/preview/mermaid/mermaid.min.js and its licence notices.
//
// Mermaid's published bundle carries elkjs 0.9, which is EPL-2.0 only. This
// build uses the Mermaid version pinned in package.json with elkjs overridden to
// a release that is also offered under GPL-3.0-or-later, so the bundle can ship
// inside an AGPL-3.0 app. Every package that ends up in the bundle must carry a
// licence from ALLOWED_LICENSES, or the build stops.
//
// Run from the repository root with Node 18 or later and npm:
//   node scripts/mermaid/build.mjs
// Dependencies install from package-lock.json into build/mermaid-bundle with
// install scripts disabled; the outputs are written into assets/ and licenses/.

import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const source = join(root, "scripts", "mermaid");
const work = join(root, "build", "mermaid-bundle");
const output = join(root, "assets", "preview", "mermaid", "mermaid.min.js");
const notices = join(root, "licenses", "preview", "Mermaid-bundle-NOTICES.txt");

const ALLOWED_LICENSES = new Set([
  "MIT",
  "ISC",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "0BSD",
  "Apache-2.0",
  "(MPL-2.0 OR Apache-2.0)",
  "MPL-2.0 OR Apache-2.0",
  "EPL-2.0 OR GPL-3.0-or-later",
  "Unlicense",
]);

/**
 * A package's declared licence. A package that declares none is read from its
 * licence file, and only an MIT text is recognised; anything else stays unknown
 * and fails the allowlist.
 */
function licenseOf(pkg, text) {
  if (typeof pkg.license === "string") return pkg.license;
  if (pkg.license?.type) return pkg.license.type;
  if (text && /^(The )?MIT License/i.test(text)) return "MIT";
  return "UNKNOWN";
}

mkdirSync(work, { recursive: true });
for (const file of ["package.json", "package-lock.json"]) copyFileSync(join(source, file), join(work, file));
execFileSync("npm", ["ci", "--ignore-scripts", "--no-audit", "--no-fund"], { cwd: work, stdio: "inherit" });

const entry = join(work, "entry.js");
writeFileSync(entry, 'import mermaid from "mermaid";\nglobalThis.mermaid = mermaid;\n');

const esbuild = createRequire(join(work, "package.json"))("esbuild");
const result = await esbuild.build({
  entryPoints: [entry],
  absWorkingDir: work,
  bundle: true,
  format: "iife",
  minify: true,
  target: ["chrome90"],
  legalComments: "eof",
  metafile: true,
  outfile: join(work, "mermaid.min.js"),
  logLevel: "warning",
});

const packages = new Map();
for (const input of Object.keys(result.metafile.inputs)) {
  const match = input.match(/node_modules\/((?:@[^/]+\/)?[^/]+)\//g);
  if (!match) continue;
  const dir = join(work, input.slice(0, input.lastIndexOf(match[match.length - 1]) + match[match.length - 1].length));
  if (!packages.has(dir)) packages.set(dir, JSON.parse(readFileSync(join(dir, "package.json"), "utf8")));
}

const rejected = [];
const sections = [];
for (const [dir, pkg] of [...packages].sort((a, b) => a[1].name.localeCompare(b[1].name))) {
  const licenseFile = readdirSync(dir).find((name) => /^(licen[cs]e|copying)/i.test(name));
  const fileText = licenseFile ? readFileSync(join(dir, licenseFile), "utf8").trim() : null;
  const license = licenseOf(pkg, fileText);
  if (!ALLOWED_LICENSES.has(license)) rejected.push(`${pkg.name}@${pkg.version}: ${license}`);
  const text = fileText ?? `License: ${license} (no licence file in the package)`;
  sections.push(`${pkg.name} ${pkg.version} (${license})\n${"-".repeat(60)}\n${text}\n`);
}
if (rejected.length) {
  console.error("Packages with a licence outside the allowlist:\n  " + rejected.join("\n  "));
  process.exit(1);
}

const elk = [...packages.values()].find((pkg) => pkg.name === "elkjs");
if (!elk) throw new Error("elkjs is no longer in the bundle; revisit this build");

mkdirSync(dirname(output), { recursive: true });
copyFileSync(join(work, "mermaid.min.js"), output);
writeFileSync(
  notices,
  "Third-party software bundled in assets/preview/mermaid/mermaid.min.js, built by\n" +
    "scripts/mermaid/build.mjs. elkjs is used under GPL-3.0-or-later, the secondary\n" +
    "licence it offers alongside EPL-2.0; its source is at https://github.com/kieler/elkjs.\n\n" +
    sections.join("\n"),
);
console.log(`mermaid.min.js: ${packages.size} packages, elkjs ${elk.version}, ${existsSync(output) ? "written" : "missing"}`);
