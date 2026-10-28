import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const miniprogramRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../miniprogram");
const voidTags = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input",
  "link", "meta", "param", "progress", "slider", "source", "switch", "track", "wbr"
]);

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    if (["node_modules", "miniprogram_npm", "tests"].includes(entry.name)) return [];
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) return walk(fullPath);
    return entry.name.endsWith(".wxml") ? [fullPath] : [];
  });
}

function findTagEnd(source, start) {
  let quote = null;
  for (let index = start; index < source.length; index += 1) {
    const char = source[index];
    if (quote) {
      if (char === quote) quote = null;
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
    } else if (char === ">") {
      return index;
    }
  }
  return -1;
}

function checkFile(file) {
  const source = fs.readFileSync(file, "utf8");
  const stack = [];
  let index = 0;
  while (index < source.length) {
    const open = source.indexOf("<", index);
    if (open < 0) break;
    if (source.startsWith("<!--", open)) {
      const close = source.indexOf("-->", open + 4);
      if (close < 0) throw new Error(`${file}: unterminated comment`);
      index = close + 3;
      continue;
    }
    if (source.startsWith("<!", open) || source.startsWith("<?", open)) {
      const close = source.indexOf(">", open + 2);
      if (close < 0) throw new Error(`${file}: unterminated declaration`);
      index = close + 1;
      continue;
    }
    const close = findTagEnd(source, open + 1);
    if (close < 0) throw new Error(`${file}: unterminated tag`);
    const raw = source.slice(open + 1, close).trim();
    index = close + 1;
    if (!raw) continue;
    if (raw.startsWith("/")) {
      const name = raw.slice(1).trim().split(/\s+/)[0];
      const expected = stack.pop();
      if (!expected || expected.name !== name) {
        throw new Error(`${file}: closing </${name}> does not match <${expected?.name ?? "none"}>`);
      }
      continue;
    }
    const selfClosing = raw.endsWith("/");
    const name = raw.replace(/\/$/, "").trim().split(/\s+/)[0];
    if (!selfClosing && !voidTags.has(name)) {
      stack.push({ name, offset: open });
    }
  }
  if (stack.length > 0) {
    const top = stack[stack.length - 1];
    throw new Error(`${file}: unclosed <${top.name}> at offset ${top.offset}`);
  }
}

function checkRegisteredPages() {
  const app = JSON.parse(fs.readFileSync(path.join(miniprogramRoot, "app.json"), "utf8"));
  for (const page of app.pages ?? []) {
    for (const extension of ["ts", "json", "wxml", "wxss"]) {
      const file = path.join(miniprogramRoot, `${page}.${extension}`);
      if (!fs.existsSync(file)) throw new Error(`Registered page is missing ${extension}: ${page}`);
    }
  }
}

checkRegisteredPages();
const files = walk(miniprogramRoot).sort();
for (const file of files) checkFile(file);
console.log(`WXML check passed: ${files.length} files`);
